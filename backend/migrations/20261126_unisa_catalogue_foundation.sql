-- UNISA catalogue foundation. Safe to apply to the existing Student Module Hub project.
create table if not exists public.colleges (
  id text primary key,
  name text not null unique,
  official_url text not null default '',
  created_at timestamptz not null default now()
);
insert into public.colleges(id,name,official_url) values
('CAS','College of Accounting Sciences','https://www.unisa.ac.za/'),
('CAES','College of Agriculture and Environmental Sciences','https://www.unisa.ac.za/'),
('CEMS','College of Economic and Management Sciences','https://www.unisa.ac.za/'),
('CEDU','College of Education','https://www.unisa.ac.za/'),
('CHS','College of Human Sciences','https://www.unisa.ac.za/'),
('CLAW','College of Law','https://www.unisa.ac.za/'),
('CSET','College of Science, Engineering and Technology','https://www.unisa.ac.za/'),
('GSBL','Graduate School of Business Leadership','https://www.unisa.ac.za/')
on conflict (id) do update set name=excluded.name, official_url=excluded.official_url;

alter table public.qualifications
  add column if not exists college_id text references public.colleges(id) on delete restrict,
  add column if not exists qualification_code text not null default '',
  add column if not exists qualification_level text not null default '',
  add column if not exists academic_year integer,
  add column if not exists official_url text not null default '';

alter table public.modules
  add column if not exists credits integer,
  add column if not exists nqf_level integer,
  add column if not exists semester text not null default '',
  add column if not exists academic_year integer,
  add column if not exists official_url text not null default '';

alter table public.documents
  add column if not exists category text not null default 'Other Study Materials',
  add column if not exists academic_year integer,
  add column if not exists semester text not null default '';

alter table public.documents drop constraint if exists documents_category_check;
alter table public.documents add constraint documents_category_check check (category in (
  'Study Guides','Tutorial Letters','Assignment Questions','Examination Question Papers',
  'Examination Memorandums','Prescribed Books','Lecture Notes','Other Study Materials'
));

create index if not exists qualifications_college_idx on public.qualifications(college_id);
create index if not exists documents_category_idx on public.documents(category);
alter table public.colleges enable row level security;
revoke all on public.colleges from anon, authenticated;
grant select on public.colleges to authenticated;
grant insert, update, delete on public.colleges to authenticated;
drop policy if exists colleges_read on public.colleges;
create policy colleges_read on public.colleges for select to authenticated using (true);
drop policy if exists colleges_admin on public.colleges;
create policy colleges_admin on public.colleges for all to authenticated using (public.is_admin()) with check (public.is_admin());
