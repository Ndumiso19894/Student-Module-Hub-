-- Staging queue for content-based Google Drive material identification.
-- Only the privileged Edge Function may access this table; students never see candidates.
create table if not exists public.drive_import_candidates (
  id uuid primary key default gen_random_uuid(),
  drive_file_id text not null unique,
  file_name text not null,
  mime_type text not null default 'application/pdf',
  size_bytes bigint,
  drive_modified_at timestamptz,
  parent_folder_id text,
  suggested_module_id uuid references public.modules(id) on delete set null,
  suggested_category text not null default 'Other Study Materials'
    check (suggested_category in ('Study Guides','Tutorial Letters','Assignment Questions','Examination Question Papers','Examination Memorandums','Prescribed Books','Lecture Notes','Other Study Materials')),
  confidence numeric(5,4) not null default 0 check (confidence >= 0 and confidence <= 1),
  evidence jsonb not null default '[]'::jsonb,
  status text not null default 'pending'
    check (status in ('pending','needs_review','approved','rejected','imported','unclassified')),
  imported_document_id uuid references public.documents(id) on delete set null,
  content_sha256 text,
  last_scanned_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists drive_import_candidates_status_idx
  on public.drive_import_candidates(status, confidence desc);
create index if not exists drive_import_candidates_module_idx
  on public.drive_import_candidates(suggested_module_id);
alter table public.drive_import_candidates enable row level security;
revoke all on public.drive_import_candidates from anon, authenticated;
