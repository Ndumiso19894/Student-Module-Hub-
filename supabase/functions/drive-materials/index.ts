// Supabase Edge Function: private Google Drive materials gateway.
// Required secrets: SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY,
// GOOGLE_SERVICE_ACCOUNT_EMAIL, GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY, GOOGLE_DRIVE_FOLDER_ID.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });

function b64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function textB64url(value: string): string {
  return b64url(new TextEncoder().encode(value));
}
async function googleAccessToken(): Promise<string> {
  const email = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_EMAIL");
  const pem = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY");
  if (!email || !pem) throw new Error("Google Drive service account is not configured.");
  const now = Math.floor(Date.now() / 1000);
  const header = textB64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = textB64url(JSON.stringify({
    iss: email,
    scope: "https://www.googleapis.com/auth/drive.readonly",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = header + "." + claim;
  const cleanPem = pem.replace(/\\n/g, "\n").replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const der = Uint8Array.from(atob(cleanPem), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned),
  ));
  const assertion = unsigned + "." + b64url(signature);
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!tokenResponse.ok) throw new Error("Google authorization failed.");
  const token = await tokenResponse.json();
  return token.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed." }, 405);

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.startsWith("Bearer ")) return json({ error: "Sign-in required." }, 401);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: userData, error: userError } = await userClient.auth.getUser();
    if (userError || !userData.user) return json({ error: "Invalid or expired session." }, 401);

    const admin = createClient(supabaseUrl, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: profile, error: profileError } = await admin
      .from("profiles").select("role").eq("id", userData.user.id).maybeSingle();
    if (profileError || !profile) return json({ error: "Account profile unavailable." }, 403);

    const body = await req.json();
    const action = body?.action;
    const isAdmin = profile.role === "admin";

    if (action === "list") {
      if (!isAdmin) return json({ error: "Administrator access required." }, 403);
      const folderId = Deno.env.get("GOOGLE_DRIVE_FOLDER_ID");
      if (!folderId) return json({ error: "Drive folder is not configured." }, 503);
      const token = await googleAccessToken();
      const q = "'" + folderId.replace(/\\/g, "\\\\").replace(/'/g, "\\'") +
        "' in parents and trashed = false and mimeType = 'application/pdf'";
      const params = new URLSearchParams({
        q, pageSize: "1000", orderBy: "name",
        fields: "nextPageToken,files(id,name,mimeType,size,modifiedTime)",
      });
      const driveResponse = await fetch("https://www.googleapis.com/drive/v3/files?" + params, {
        headers: { Authorization: "Bearer " + token },
      });
      if (!driveResponse.ok) return json({ error: "Could not list Drive files." }, 502);
      const driveData = await driveResponse.json();
      return json({ files: driveData.files ?? [], nextPageToken: driveData.nextPageToken ?? null });
    }

    if (action === "link") {
      if (!isAdmin) return json({ error: "Administrator access required." }, 403);
      const documentId = String(body?.document_id ?? "");
      const fileId = String(body?.file_id ?? "");
      if (!/^[0-9a-f-]{36}$/i.test(documentId) || !/^[a-zA-Z0-9_-]{10,200}$/.test(fileId)) {
        return json({ error: "Invalid document or Drive file ID." }, 400);
      }
      const { data: document } = await admin.from("documents").select("id").eq("id", documentId).maybeSingle();
      if (!document) return json({ error: "Document record not found." }, 404);
      const folderId = Deno.env.get("GOOGLE_DRIVE_FOLDER_ID");
      if (!folderId) return json({ error: "Drive folder is not configured." }, 503);
      const token = await googleAccessToken();
      const fileResponse = await fetch(
        "https://www.googleapis.com/drive/v3/files/" + encodeURIComponent(fileId) +
          "?fields=id,name,mimeType,size,modifiedTime,parents",
        { headers: { Authorization: "Bearer " + token } },
      );
      if (!fileResponse.ok) return json({ error: "Drive file not found or not accessible." }, 404);
      const file = await fileResponse.json();
      if (file.mimeType !== "application/pdf" || !(file.parents ?? []).includes(folderId)) {
        return json({ error: "Only PDF files inside the configured study folder can be linked." }, 400);
      }
      const { error: saveError } = await admin.from("document_sources").upsert({
        document_id: documentId,
        provider: "google_drive",
        external_file_id: file.id,
        mime_type: file.mimeType,
        size_bytes: file.size ? Number(file.size) : null,
        drive_modified_at: file.modifiedTime ?? null,
        updated_at: new Date().toISOString(),
      }, { onConflict: "document_id" });
      if (saveError) return json({ error: "Could not save the Drive link." }, 500);
      return json({ linked: true, document_id: documentId, file_name: file.name });
    }

    if (action === "download") {
      const documentId = String(body?.document_id ?? "");
      if (!/^[0-9a-f-]{36}$/i.test(documentId)) return json({ error: "Invalid document." }, 400);
      const { data: document, error: docError } = await admin
        .from("documents").select("id,title,published,module_id").eq("id", documentId).maybeSingle();
      if (docError || !document || (!isAdmin && !document.published)) return json({ error: "Material not found." }, 404);
      const { data: module } = await admin
        .from("modules").select("id,published,qualification_id").eq("id", document.module_id).maybeSingle();
      if (!module || (!isAdmin && !module.published)) return json({ error: "Material not available." }, 404);
      const { data: qualification } = await admin
        .from("qualifications").select("published").eq("id", module.qualification_id).maybeSingle();
      if (!qualification || (!isAdmin && !qualification.published)) return json({ error: "Material not available." }, 404);
      const { data: source, error: sourceError } = await admin
        .from("document_sources").select("external_file_id,mime_type").eq("document_id", documentId).maybeSingle();
      if (sourceError || !source || source.mime_type !== "application/pdf") {
        return json({ error: "PDF has not been linked to this material yet." }, 404);
      }
      const token = await googleAccessToken();
      const driveResponse = await fetch(
        "https://www.googleapis.com/drive/v3/files/" + encodeURIComponent(source.external_file_id) + "?alt=media",
        { headers: { Authorization: "Bearer " + token } },
      );
      if (!driveResponse.ok || !driveResponse.body) return json({ error: "Drive could not provide this PDF." }, 502);
      const safeName = (document.title || "study-material").replace(/[\\/:*?"<>|\r\n]/g, "_").slice(0, 120) + ".pdf";
      const headers = new Headers(cors);
      headers.set("Content-Type", "application/pdf");
      headers.set("Content-Disposition", 'attachment; filename="' + safeName.replace(/"/g, "") + '"');
      headers.set("Cache-Control", "private, no-store");
      const length = driveResponse.headers.get("Content-Length");
      if (length) headers.set("Content-Length", length);
      return new Response(driveResponse.body, { status: 200, headers });
    }

    return json({ error: "Unknown action." }, 400);
  } catch (error) {
    console.error("drive-materials:", error);
    return json({ error: "The materials service could not complete the request." }, 500);
  }
});
