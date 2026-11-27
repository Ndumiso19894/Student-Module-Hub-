import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import * as pdfjs from "npm:pdfjs-dist@4.10.38/legacy/build/pdf.mjs";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info, x-drive-importer-secret",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });

function b64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function textB64url(value: string): string { return b64url(new TextEncoder().encode(value)); }

async function googleAccessToken(): Promise<string> {
  const email = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_EMAIL");
  const pem = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY");
  if (!email || !pem) throw new Error("Google Drive service account is not configured.");
  const now = Math.floor(Date.now() / 1000);
  const unsigned = textB64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." +
    textB64url(JSON.stringify({
      iss: email, scope: "https://www.googleapis.com/auth/drive.readonly",
      aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
    }));
  const cleanPem = pem.replace(/\\n/g, "\n").replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const der = Uint8Array.from(atob(cleanPem), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const assertion = unsigned + "." + b64url(signature);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion,
    }),
  });
  if (!response.ok) throw new Error("Google authorization failed.");
  return (await response.json()).access_token as string;
}

async function driveList(token: string, folderId: string) {
  const files: any[] = [];
  let pageToken = "";
  do {
    const params = new URLSearchParams({
      q: "'" + folderId.replace(/\\/g, "\\\\").replace(/'/g, "\\'") + "' in parents and trashed = false",
      pageSize: "1000",
      fields: "nextPageToken,files(id,name,mimeType,size,modifiedTime,parents,shortcutDetails(targetId,targetMimeType))",
      orderBy: "name",
    });
    if (pageToken) params.set("pageToken", pageToken);
    const response = await fetch("https://www.googleapis.com/drive/v3/files?" + params, {
      headers: { Authorization: "Bearer " + token },
    });
    if (!response.ok) throw new Error("Google Drive folder scan failed.");
    const page = await response.json();
    files.push(...(page.files ?? []));
    pageToken = page.nextPageToken ?? "";
  } while (pageToken);
  return files;
}

async function driveFullTextSearchAny(token: string, folderId: string, terms: string[], targetFileId: string): Promise<boolean> {
  if (!terms.length) return false;
  const clauses = terms.map((term) => {
    const safe = term.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
    return "fullText contains '" + safe + "'";
  });
  const params = new URLSearchParams({
    q: "'" + folderId + "' in parents and trashed = false and (" + clauses.join(" or ") + ")",
    pageSize: "1000",
    fields: "nextPageToken,files(id)",
  });
  let pageToken = "";
  do {
    if (pageToken) params.set("pageToken", pageToken);
    const response = await fetch("https://www.googleapis.com/drive/v3/files?" + params, {
      headers: { Authorization: "Bearer " + token },
    });
    if (!response.ok) throw new Error("Google Drive indexed-text search failed (HTTP " + response.status + ").");
    const data = await response.json();
    if ((data.files ?? []).some((file: any) => file.id === targetFileId)) return true;
    pageToken = data.nextPageToken ?? "";
  } while (pageToken);
  return false;
}

async function findIndexedModuleCode(token: string, folderId: string, fileId: string, modules: any[]): Promise<string | null> {
  const uniqueCodes = [...new Set(modules.map((m) => String(m.code ?? "").trim().toUpperCase()).filter((code) => /^[A-Z0-9-]{4,20}$/.test(code)))];
  const batchSize = 100;
  for (let start = 0; start < uniqueCodes.length; start += batchSize) {
    const batch = uniqueCodes.slice(start, start + batchSize);
    if (!await driveFullTextSearchAny(token, folderId, batch, fileId)) continue;
    let candidates = batch;
    while (candidates.length > 1) {
      const middle = Math.ceil(candidates.length / 2);
      const left = candidates.slice(0, middle);
      if (await driveFullTextSearchAny(token, folderId, left, fileId)) candidates = left;
      else candidates = candidates.slice(middle);
    }
    return candidates[0] ?? null;
  }
  return null;
}

async function findIndexedCategories(token: string, folderId: string, fileId: string) {
  const probes = [
    { name: "Tutorial Letters", terms: ["tutorial"] },
    { name: "Assignment Questions", terms: ["assignment"] },
    { name: "Examination Memorandums", terms: ["memorandum", "memo"] },
    { name: "Examination Question Papers", terms: ["examination", "exam"] },
    { name: "Study Guides", terms: ["study", "guide"] },
    { name: "Prescribed Books", terms: ["textbook", "prescribed"] },
    { name: "Lecture Notes", terms: ["lecture"] },
  ];
  const matches: string[] = [];
  for (const probe of probes) {
    if (await driveFullTextSearchAny(token, folderId, probe.terms, fileId)) matches.push(probe.name);
  }
  return matches;
}

async function driveInventory(token: string, rootId: string) {
  const queue = [rootId];
  const visited = new Set<string>();
  const pdfs: any[] = [];
  const mimeCounts: Record<string, number> = {};
  const folderNames: Record<string, string> = {};
  let totalFiles = 0;
  while (queue.length && visited.size < 5000) {
    const batch: string[] = [];
    while (queue.length && batch.length < 5 && visited.size + batch.length < 5000) {
      const next = queue.shift()!;
      if (!visited.has(next) && !batch.includes(next)) batch.push(next);
    }
    batch.forEach((folder) => visited.add(folder));
    const pages = await Promise.all(batch.map((folder) => driveList(token, folder)));
    for (const children of pages) {
      for (const item of children) {
        if (item.mimeType === "application/vnd.google-apps.folder") {
          folderNames[item.id] = item.name ?? "";
          if (!visited.has(item.id)) queue.push(item.id);
        } else if (item.mimeType === "application/vnd.google-apps.shortcut") {
          const targetId = item.shortcutDetails?.targetId;
          const targetMime = item.shortcutDetails?.targetMimeType;
          if (targetId && targetMime === "application/vnd.google-apps.folder") {
            if (!visited.has(targetId)) queue.push(targetId);
          } else if (targetId && targetMime === "application/pdf") {
            const targetResponse = await fetch("https://www.googleapis.com/drive/v3/files/" + encodeURIComponent(targetId) + "?fields=id,name,mimeType,size,modifiedTime,parents", {
              headers: { Authorization: "Bearer " + token },
            });
            if (targetResponse.ok) {
              const target = await targetResponse.json();
              totalFiles++;
              mimeCounts["application/pdf"] = (mimeCounts["application/pdf"] || 0) + 1;
              pdfs.push({ ...target, name: item.name || target.name });
            }
          } else {
            totalFiles++;
            mimeCounts[item.mimeType] = (mimeCounts[item.mimeType] || 0) + 1;
          }
        } else {
          totalFiles++;
          const mime = item.mimeType || "unknown";
          mimeCounts[mime] = (mimeCounts[mime] || 0) + 1;
          if (mime === "application/pdf") pdfs.push(item);
        }
      }
    }
  }
  return { pdfs, totalFiles, foldersVisited: visited.size, mimeCounts, folderNames };
}

async function extractPdfText(token: string, fileId: string): Promise<{ text: string; pages: number; frontMatter: string }> {
  const response = await fetch(
    "https://www.googleapis.com/drive/v3/files/" + encodeURIComponent(fileId) + "?alt=media",
    { headers: { Authorization: "Bearer " + token } },
  );
  if (!response.ok) throw new Error("PDF download failed.");
  const declaredSize = Number(response.headers.get("Content-Length") ?? 0);
  if (declaredSize > 20 * 1024 * 1024) throw new Error("PDF exceeds the 20 MB first-pass limit.");
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (bytes.length > 20 * 1024 * 1024) throw new Error("PDF exceeds the 20 MB first-pass limit.");
  const task = pdfjs.getDocument({ data: bytes, useSystemFonts: true, isEvalSupported: false, disableFontFace: true, verbosity: 0 });
  const doc = await task.promise;
  const pages = doc.numPages;
  const pieces: string[] = [];
  const frontMatterPieces: string[] = [];
  const pagesToRead = Math.min(pages, 12);
  for (let n = 1; n <= pagesToRead; n++) {
    const page = await doc.getPage(n);
    const content = await page.getTextContent();
    const pageText = content.items.map((item: any) =>
      (typeof item.str === "string" ? item.str : "") + (item.hasEOL ? "\n" : " ")
    ).join("");
    pieces.push(pageText);
    if (n <= 3) frontMatterPieces.push(pageText);
  }
  await doc.destroy();
  const text = pieces.join("\n").slice(0, 120000);
  return { text, pages, frontMatter: frontMatterPieces.join("\n") };
}


async function storeDrivePdf(token: string, fileId: string, moduleId: string, admin: any): Promise<string> {
  const response = await fetch("https://www.googleapis.com/drive/v3/files/" + encodeURIComponent(fileId) + "?alt=media", {
    headers: { Authorization: "Bearer " + token },
  });
  if (!response.ok || !response.body) throw new Error("Google Drive could not provide the PDF for storage.");
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (!bytes.length || bytes.length > 50 * 1024 * 1024) throw new Error("PDF is empty or exceeds the 50 MB storage limit.");
  const path = moduleId + "/" + fileId + ".pdf";
  const { error } = await admin.storage.from("study-materials").upload(path, bytes, {
    contentType: "application/pdf", upsert: true, cacheControl: "3600",
  });
  if (error) throw new Error("Could not save the PDF in app storage: " + error.message);
  return path;
}

const categories = [
  { name: "Tutorial Letters", terms: ["tutorial letter", "tutorial letters", "tl01", "tl 01"] },
  { name: "Assignment Questions", terms: ["assignment", "assessment", "compulsory assignment", "assignment question"] },
  { name: "Examination Memorandums", terms: ["memorandum", "memoranda", "marking guideline", "marking guidelines", "memo"] },
  { name: "Examination Question Papers", terms: ["examination paper", "exam paper", "previous examination", "examination questions"] },
  { name: "Study Guides", terms: ["study guide", "learning guide", "study material guide"] },
  { name: "Prescribed Books", terms: ["prescribed book", "textbook", "text book"] },
  { name: "Lecture Notes", terms: ["lecture notes", "lecture note", "class notes"] },
];

function inferTitle(text: string, fileName: string, moduleTitle = ""): string {
  const fallback = fileName.replace(/\.pdf$/i, "").replace(/[_]+/g, " ").replace(/\s+/g, " ").trim();
  const genericFilename = /^(copy of )?\d{3,}([ _-][a-f0-9]{8,})?$/i.test(fallback) ||
    /^(document|scan|download|file|untitled)( \d+)?$/i.test(fallback);
  const cleaned = text.split(/\n+/)
    .map((line) => line.replace(/\s+/g, " ").trim())
    .filter((line) => line.length >= 10 && line.length <= 150)
    .filter((line) => !/(©|copyright|university of south africa|\bUNISA\b|all rights reserved|printed|page \d|study@unisa|module code|muckleneuk|pretoria|student number|licensed to|department of|faculty of|\bnotice\s+\d|\bwww\.|https?:\/\/)/i.test(line))
    .filter((line) => !/^[-\d\s.:/]+$/.test(line));
  const useful = cleaned.find((line) => /[a-z]{3,}/i.test(line) && !/^[A-Z\s]{12,}$/.test(line));
  const title = !genericFilename ? fallback : (useful || fallback);
  return (title || (moduleTitle ? moduleTitle + " study material" : "UNISA study material")).slice(0, 240);
}

function inferCategoryFromFilename(fileName: string): string | null {
  const name = fileName.toLowerCase().replace(/[_-]+/g, " ");
  if (/\b(tutorial letter|tutorial letters|tl\\s*0?1)\b/.test(name)) return "Tutorial Letters";
  if (/\b(assignment|assessment)\b/.test(name)) return "Assignment Questions";
  if (/\b(memorandum|memoranda|marking guideline|marking guidelines)\b/.test(name)) return "Examination Memorandums";
  if (/\b(examination paper|exam paper|exam questions|examination questions)\b/.test(name)) return "Examination Question Papers";
  if (/\b(study guide|learning guide)\b/.test(name)) return "Study Guides";
  if (/\b(textbook|text book|prescribed book)\b/.test(name)) return "Prescribed Books";
  if (/\b(lecture notes|lecture note|class notes)\b/.test(name)) return "Lecture Notes";
  return null;
}

function identify(text: string, modules: any[], categoryText = text) {
  const upper = text.toUpperCase();
  const matches = modules.filter((m) => {
    const code = String(m.code ?? "").trim().toUpperCase();
    if (!code || code.length < 4) return false;
    const escapedCode = code.replace(/[^A-Z0-9]/g, "\\$&");
    return new RegExp("(^|[^A-Z0-9])" + escapedCode + "([^A-Z0-9]|$)").test(upper);
  });
  const byCode = new Map<string, any[]>();
  for (const match of matches) {
    const code = String(match.code).trim().toUpperCase();
    byCode.set(code, [...(byCode.get(code) ?? []), match]);
  }
  const codeGroups = [...byCode.entries()];
  const chosenGroup = codeGroups.length === 1
    ? [...codeGroups[0][1]].sort((a, b) => String(b.title ?? "").length - String(a.title ?? "").length)
    : [];
  const chosenModule = chosenGroup[0] ?? null;
  const categoryUpper = categoryText.toUpperCase();
  const categoryMatches = categories.filter((c) => c.terms.some((term) => categoryUpper.includes(term.toUpperCase())));
  const category = categoryMatches[0]?.name ?? "Other Study Materials";
  let confidence = codeGroups.length === 1
    ? (chosenGroup.length === 1 ? 0.98 : 0.90)
    : codeGroups.length > 1 ? 0.55 : 0;
  if (codeGroups.length === 1 && chosenGroup.length === 1 && categoryMatches.length === 1) confidence = 0.995;
  const evidence = [
    ...matches.map((m) => ({ type: "module_code", value: m.code, module_title: m.title })),
    ...categoryMatches.map((c) => ({ type: "category_terms", value: c.name })),
  ];
  return { module: chosenModule, matches, codeGroups, categoryMatches, category, confidence, evidence };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed." }, 405);
  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const cronSecret = req.headers.get("x-drive-importer-secret") ?? "";
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    let isScheduledRun = false;
    if (cronSecret) {
      const { data: cronAuth } = await admin.from("drive_importer_cron_auth")
        .select("secret").eq("id", 1).maybeSingle();
      if (!cronAuth?.secret || cronAuth.secret !== cronSecret) return json({ error: "Invalid scheduled-run credentials." }, 401);
      isScheduledRun = true;
    } else {
      if (!authHeader.startsWith("Bearer ")) return json({ error: "Sign-in required." }, 401);
      const userClient = createClient(supabaseUrl, anonKey, {
        global: { headers: { Authorization: authHeader } },
        auth: { persistSession: false, autoRefreshToken: false },
      });
      const { data: userData, error: userError } = await userClient.auth.getUser();
      if (userError || !userData.user) return json({ error: "Invalid or expired session." }, 401);
      const { data: profile } = await admin.from("profiles").select("role").eq("id", userData.user.id).maybeSingle();
      if (profile?.role !== "admin") return json({ error: "Administrator access required." }, 403);
    }

    const body = await req.json();
    const action = String(body?.action ?? "");
    if (action === "scan") {
      return json({
        deprecated: true,
        scanned: 0,
        message: "Content-analysis importing is retired. Student Module Hub now reads module folders and PDFs directly from Google Drive."
      });
    }
    if (action === "legacy_scan_disabled") {
      const rootId = Deno.env.get("GOOGLE_DRIVE_FOLDER_ID");
      if (!rootId) return json({ error: "Drive folder is not configured." }, 503);
      const token = await googleAccessToken();
      const inventory = await driveInventory(token, rootId);
      const files = inventory.pdfs;
      const { data: modules, error: moduleError } = await admin.from("modules").select("id,code,title");
      if (moduleError) throw new Error("Could not load the module catalogue.");
      const { data: existing, error: existingError } = await admin.from("drive_import_candidates").select("drive_file_id,status,last_scanned_at,drive_modified_at");
      if (existingError) throw new Error("Could not load the import queue.");
      const known = new Map((existing ?? []).map((row: any) => [row.drive_file_id, row]));
      const rescan = body?.rescan === true;
      const pending = files.filter((file) => {
        const previous = known.get(file.id);
        if (!previous) return true;
        if (previous.status === "imported" || previous.status === "rejected") return false;
        if (rescan || previous.drive_modified_at !== (file.modifiedTime ?? null)) return true;
        if (["unclassified", "needs_review", "pending"].includes(previous.status)) {
          return Date.now() - new Date(previous.last_scanned_at).getTime() > 6 * 60 * 60 * 1000;
        }
        return false;
      });
      pending.sort((a: any, b: any) => {
        const aKnown = known.get(a.id), bKnown = known.get(b.id);
        if (!aKnown && bKnown) return -1;
        if (aKnown && !bKnown) return 1;
        return new Date(aKnown?.last_scanned_at ?? 0).getTime() - new Date(bKnown?.last_scanned_at ?? 0).getTime();
      });
      const limit = Math.max(1, Math.min(Number(body?.limit ?? 20), 20));
      const batch = pending.slice(0, limit);
      const results: any[] = [];
      for (const file of batch) {
        let extracted = "";
        let frontMatter = "";
        let pageCount = 0;
        let indexedSearchUsed = false;
        let parseError = "";
        try {
          const parsed = await extractPdfText(token, file.id);
          extracted = parsed.text;
          pageCount = parsed.pages;
          frontMatter = parsed.frontMatter;
        } catch (error) {
          parseError = error instanceof Error ? error.message : "PDF text extraction failed.";
        }
        const parentFolderName = inventory.folderNames[file.parents?.[0] ?? ""] ?? "";
        let found = identify(extracted + "\\n" + file.name + "\\n" + parentFolderName, modules ?? [], typeof frontMatter === "string" ? frontMatter : extracted);
        if (!parseError && found.codeGroups.length === 0) {
          indexedSearchUsed = true;
          const indexedCode = await findIndexedModuleCode(token, file.parents?.[0] ?? rootId, file.id, modules ?? []);
          if (indexedCode) {
            found = identify(extracted + "\\n" + indexedCode, modules ?? [], typeof frontMatter === "string" ? frontMatter : extracted);
            found.evidence.push({ type: "drive_ocr_index", value: indexedCode });
          }
        }
        if (!parseError && found.codeGroups.length === 1 && found.categoryMatches.length === 0) {
          const indexedCategories = await findIndexedCategories(token, file.parents?.[0] ?? rootId, file.id);
          if (indexedCategories.length === 1) {
            found.category = indexedCategories[0];
            found.categoryMatches = [{ name: indexedCategories[0] }];
            found.confidence = Math.min(0.94, found.confidence);
            found.evidence.push({ type: "drive_ocr_category_index", value: indexedCategories[0] });
          } else if (indexedCategories.length > 1) {
            found.evidence.push({ type: "drive_ocr_category_ambiguous", value: indexedCategories });
          }
        }
        const filenameCategory = inferCategoryFromFilename(file.name);
        if (filenameCategory) {
          found.category = filenameCategory;
          found.categoryMatches = [{ name: filenameCategory }];
          if (found.codeGroups.length === 1 && found.codeGroups[0][1].length === 1) found.confidence = 0.995;
          found.evidence.push({ type: "filename_category", value: filenameCategory });
        }
        const status = parseError || found.codeGroups.length === 0 ? "unclassified" :
          found.confidence >= 0.95 && found.categoryMatches.length === 1 ? "pending" : "needs_review";
        const { data: saved, error: saveError } = await admin.from("drive_import_candidates").upsert({
          drive_file_id: file.id,
          file_name: file.name,
          suggested_title: inferTitle(extracted, file.name, found.module?.title ?? ""),
          mime_type: file.mimeType,
          size_bytes: file.size ? Number(file.size) : null,
          drive_modified_at: file.modifiedTime ?? null,
          parent_folder_id: (file.parents ?? [])[0] ?? null,
          suggested_module_id: found.module?.id ?? null,
          suggested_category: found.category,
          confidence: found.confidence,
          evidence: [...found.evidence, ...(parseError ? [{ type: "extraction_issue", value: parseError }] : []), { type: "pdf_pages_read", value: Math.min(pageCount, 12) }, { type: "drive_index_search_used", value: indexedSearchUsed }],
          status,
          last_scanned_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        }, { onConflict: "drive_file_id" }).select("id,drive_file_id,file_name,suggested_title,suggested_module_id,suggested_category,confidence,status").single();
        if (saveError) throw new Error("Could not save a scan result.");
        if (status === "pending" && found.module && found.categoryMatches.length === 1) {
          const { data: existingSource } = await admin.from("document_sources")
            .select("document_id,storage_path").eq("external_file_id", file.id).maybeSingle();
          if (existingSource?.document_id) {
            if (!existingSource.storage_path) {
              const storagePath = await storeDrivePdf(token, file.id, found.module.id, admin);
              await admin.from("document_sources").update({ storage_path: storagePath, updated_at: new Date().toISOString() }).eq("document_id", existingSource.document_id);
            }
            await admin.from("drive_import_candidates").update({
              status: "imported", imported_document_id: existingSource.document_id, updated_at: new Date().toISOString(),
            }).eq("id", saved.id);
            saved.status = "imported";
          } else {
            const { data: module } = await admin.from("modules")
              .select("id,code,semester").eq("id", found.module.id).maybeSingle();
            if (module) {
              const storagePath = await storeDrivePdf(token, file.id, module.id, admin);
              const suggestedTitle = inferTitle(extracted, file.name, found.module?.title ?? "");
              const { data: document, error: docError } = await admin.from("documents").insert({
                module_id: module.id,
                title: suggestedTitle,
                description: "Automatically identified from Google Drive. Original filename: " + file.name,
                category: found.category,
                semester: module.semester || "Not specified",
                published: true,
              }).select("id").single();
              if (!docError && document) {
                const { error: sourceError } = await admin.from("document_sources").upsert({
                  document_id: document.id,
                  provider: "google_drive",
                  external_file_id: file.id,
                  mime_type: "application/pdf",
                  size_bytes: file.size ? Number(file.size) : null,
                  drive_modified_at: file.modifiedTime ?? null,
                  storage_path: storagePath,
                  updated_at: new Date().toISOString(),
                }, { onConflict: "document_id" });
                if (!sourceError) {
                  await admin.from("drive_import_candidates").update({
                    status: "imported", imported_document_id: document.id, updated_at: new Date().toISOString(),
                  }).eq("id", saved.id);
                  saved.status = "imported";
                } else {
                  await admin.from("documents").delete().eq("id", document.id);
                }
              }
            }
          }
        }
        results.push(saved);
      }
      return json({
        scanned: results.length,
        total_pdfs_found: files.length,
        total_files_found: inventory.totalFiles,
        folders_scanned: inventory.foldersVisited,
        non_pdf_files: inventory.totalFiles - files.length,
        file_types_found: inventory.mimeCounts,
        remaining_unscanned: Math.max(0, pending.length - batch.length),
        scheduled: isScheduledRun,
        results,
        note: "Every nested folder is inventoried. Text PDFs use direct extraction; when text is missing, matching uses Google Drive's indexed document text, which includes OCR-indexed text for supported scanned PDFs. Non-PDF files are inventoried but are not yet importable as app materials.",
      });
    }

    if (action === "fulltext_test") {
      const fileId = String(body?.file_id ?? "");
      const term = String(body?.term ?? "");
      const folderId = String(body?.folder_id ?? Deno.env.get("GOOGLE_DRIVE_FOLDER_ID") ?? "");
      if (!/^[a-zA-Z0-9_-]{10,200}$/.test(fileId) || !/^[A-Z0-9-]{4,20}$/i.test(term) || !folderId) {
        return json({ error: "A valid file ID, search term and folder are required." }, 400);
      }
      const token = await googleAccessToken();
      const indexedMatch = await driveFullTextSearchAny(token, folderId, [term], fileId);
      return json({ indexed_match: indexedMatch, term });
    }

    if (action === "queue") {
      const status = String(body?.status ?? "");
      let query = admin.from("drive_import_candidates")
        .select("id,drive_file_id,file_name,suggested_title,parent_folder_id,suggested_module_id,suggested_category,confidence,evidence,status,imported_document_id,last_scanned_at")
        .order("confidence", { ascending: false }).limit(100);
      if (status) query = query.eq("status", status);
      const { data, error } = await query;
      if (error) throw new Error("Could not load the import review queue.");
      const ids = [...new Set((data ?? []).map((x: any) => x.suggested_module_id).filter(Boolean))];
      const { data: modules } = ids.length ? await admin.from("modules").select("id,code,title").in("id", ids) : { data: [] };
      const moduleMap = new Map((modules ?? []).map((m: any) => [m.id, m]));
      return json({ candidates: (data ?? []).map((item: any) => ({ ...item, module: moduleMap.get(item.suggested_module_id) ?? null })) });
    }

    if (action === "approve") {
      const candidateId = String(body?.candidate_id ?? "");
      const moduleId = String(body?.module_id ?? "");
      const category = String(body?.category ?? "");
      const requestedTitle = String(body?.title ?? "").trim();
      if (!/^[0-9a-f-]{36}$/i.test(candidateId) || !/^[0-9a-f-]{36}$/i.test(moduleId)) {
        return json({ error: "A valid candidate and module are required." }, 400);
      }
      if (!categories.some((c) => c.name === category) && category !== "Other Study Materials") {
        return json({ error: "Invalid material category." }, 400);
      }
      const { data: candidate } = await admin.from("drive_import_candidates")
        .select("*").eq("id", candidateId).maybeSingle();
      if (!candidate) return json({ error: "Candidate not found." }, 404);
      if (candidate.status === "imported") return json({ error: "This PDF has already been imported." }, 409);
      const { data: module } = await admin.from("modules").select("id,title,code,qualification_id,semester").eq("id", moduleId).maybeSingle();
      if (!module) return json({ error: "Selected module does not exist." }, 404);
      const title = (requestedTitle || candidate.suggested_title || candidate.file_name || "UNISA study material")
        .replace(/\.pdf$/i, "").slice(0, 240);
      const token = await googleAccessToken();
      const storagePath = await storeDrivePdf(token, candidate.drive_file_id, moduleId, admin);
      const { data: existingSource } = await admin.from("document_sources")
        .select("document_id").eq("external_file_id", candidate.drive_file_id).maybeSingle();
      let documentId = existingSource?.document_id ?? null;
      if (existingSource?.document_id) {
        documentId = existingSource.document_id;
        await admin.from("document_sources").update({ storage_path: storagePath, updated_at: new Date().toISOString() }).eq("document_id", documentId);
        const { error: updateDocumentError } = await admin.from("documents").update({
          module_id: moduleId, title,
          description: "Imported from Google Drive. Original filename: " + candidate.file_name,
          category, semester: module.semester || "Not specified", published: false,
        }).eq("id", documentId);
        if (updateDocumentError) throw new Error("Could not update the existing unpublished material.");
      } else {
        const { data: document, error: docError } = await admin.from("documents").insert({
          module_id: moduleId, title,
          description: "Imported from Google Drive. Original filename: " + candidate.file_name,
          category, semester: module.semester || "Not specified", published: false,
        }).select("id").single();
        if (docError || !document) throw new Error("Could not create the module material record.");
        documentId = document.id;
        const { error: sourceError } = await admin.from("document_sources").upsert({
          document_id: documentId, provider: "google_drive",
          external_file_id: candidate.drive_file_id, mime_type: "application/pdf", storage_path: storagePath,
          size_bytes: candidate.size_bytes, drive_modified_at: candidate.drive_modified_at,
          updated_at: new Date().toISOString(),
        }, { onConflict: "document_id" });
        if (sourceError) {
          await admin.from("documents").delete().eq("id", documentId);
          throw new Error("Could not link the original Google Drive PDF.");
        }
      }
      const { error: updateError } = await admin.from("drive_import_candidates").update({
        suggested_module_id: moduleId, suggested_category: category,
        suggested_title: title, status: "imported", imported_document_id: documentId,
        updated_at: new Date().toISOString(),
      }).eq("id", candidateId);
      if (updateError) throw new Error("Material linked, but the import queue could not be updated.");
      return json({ imported: true, document_id: documentId, module_code: module.code, title });
    }

    if (action === "reject") {
      const candidateId = String(body?.candidate_id ?? "");
      if (!/^[0-9a-f-]{36}$/i.test(candidateId)) return json({ error: "Invalid candidate." }, 400);
      const { error } = await admin.from("drive_import_candidates")
        .update({ status: "rejected", updated_at: new Date().toISOString() }).eq("id", candidateId);
      if (error) throw new Error("Could not reject this candidate.");
      return json({ rejected: true });
    }
    return json({ error: "Unknown action." }, 400);
  } catch (error) {
    console.error("drive-importer:", error);
    return json({ error: error instanceof Error ? error.message : "The importer could not complete the request." }, 500);
  }
});
