-- Google Drive source mapping. PDF bytes stay in the private Drive folder.
-- Apply once in the existing Supabase SQL Editor.
create table if not exists public.document_sources (
  document_id uuid primary key references public.documents(id) on delete cascade,
  provider text not null default 'google_drive' check (provider = 'google_drive'),
  external_file_id text not null unique,
  mime_type text not null default 'application/pdf' check (mime_type = 'application/pdf'),
  size_bytes bigint,
  drive_modified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.document_sources enable row level security;
revoke all on public.document_sources from anon, authenticated;
-- Intentionally no client-facing policies: only the Edge Function service role reads this mapping.
