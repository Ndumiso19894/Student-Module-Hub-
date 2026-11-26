-- Keep Drive PDFs in private Supabase Storage so student downloads do not depend on Drive availability.
alter table public.document_sources add column if not exists storage_path text;
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('study-materials', 'study-materials', false, 52428800, array['application/pdf'])
on conflict (id) do update
set public = false, file_size_limit = 52428800, allowed_mime_types = array['application/pdf'];
