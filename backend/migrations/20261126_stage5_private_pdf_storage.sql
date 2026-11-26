-- Stage 5: private PDF storage for published study materials.
-- Run once in Supabase SQL Editor as the project owner.
alter table public.documents add column if not exists storage_path text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('study-materials', 'study-materials', false, 26214400, array['application/pdf'])
on conflict (id) do update
set public = false, file_size_limit = 26214400, allowed_mime_types = array['application/pdf'];

drop policy if exists "study_materials_admin_manage" on storage.objects;
create policy "study_materials_admin_manage"
on storage.objects for all to authenticated
using (
  bucket_id = 'study-materials'
  and public.is_admin()
)
with check (
  bucket_id = 'study-materials'
  and public.is_admin()
  and name like 'documents/%'
);

drop policy if exists "study_materials_students_read_published" on storage.objects;
create policy "study_materials_students_read_published"
on storage.objects for select to authenticated
using (
  bucket_id = 'study-materials'
  and name ~ '^documents/[0-9a-fA-F-]{36}/[^/]+$'
  and exists (
    select 1
    from public.documents d
    join public.modules m on m.id = d.module_id
    join public.qualifications q on q.id = m.qualification_id
    where d.storage_path = storage.objects.name
      and d.published = true
      and m.published = true
      and q.published = true
  )
);
