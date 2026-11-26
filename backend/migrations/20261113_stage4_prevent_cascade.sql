-- Applied to the existing project. Require explicit removal of dependent records.
alter table public.modules drop constraint modules_qualification_id_fkey;
alter table public.modules add constraint modules_qualification_id_fkey foreign key (qualification_id) references public.qualifications(id) on delete restrict;
alter table public.documents drop constraint documents_module_id_fkey;
alter table public.documents add constraint documents_module_id_fkey foreign key (module_id) references public.modules(id) on delete restrict;