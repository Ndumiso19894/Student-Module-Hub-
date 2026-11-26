-- Run once in the Supabase SQL editor of a NEW project, as the project owner.
-- Auth is managed by Supabase Auth. No private keys belong in the Android app.
create extension if not exists pgcrypto;
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 120),
  role text not null default 'student' check (role in ('student','admin')),
  created_at timestamptz not null default now()
);
create table public.qualifications (
  id uuid primary key default gen_random_uuid(),
  title text not null, description text not null default '', published boolean not null default false,
  created_at timestamptz not null default now()
);
create table public.modules (
  id uuid primary key default gen_random_uuid(), qualification_id uuid not null references public.qualifications(id) on delete cascade,
  title text not null, code text not null default '', description text not null default '', published boolean not null default false,
  created_at timestamptz not null default now()
);
create table public.documents (
  id uuid primary key default gen_random_uuid(), module_id uuid not null references public.modules(id) on delete cascade,
  title text not null, description text not null default '', published boolean not null default false,
  created_at timestamptz not null default now()
);
create index modules_qualification_idx on public.modules(qualification_id);
create index documents_module_idx on public.documents(module_id);

create function public.create_profile_for_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles(id, display_name) values (new.id, left(coalesce(new.raw_user_meta_data->>'display_name',''),120));
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.create_profile_for_user();

-- SECURITY DEFINER avoids recursive profiles policies. The caller cannot choose the ID.
create function public.is_admin() returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.profiles where id = auth.uid() and role = 'admin')
$$;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;
alter table public.profiles enable row level security;
alter table public.qualifications enable row level security;
alter table public.modules enable row level security;
alter table public.documents enable row level security;
revoke all on public.profiles, public.qualifications, public.modules, public.documents from anon, authenticated;
grant select on public.profiles, public.qualifications, public.modules, public.documents to authenticated;
-- Students can update only their own display name, never role or ID.
grant update(display_name) on public.profiles to authenticated;
grant insert, update, delete on public.qualifications, public.modules, public.documents to authenticated;
create policy profiles_read on public.profiles for select to authenticated using (id = auth.uid() or public.is_admin());
create policy profiles_update_self on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
create policy qualifications_read on public.qualifications for select to authenticated using (published or public.is_admin());
create policy qualifications_admin on public.qualifications for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy modules_read on public.modules for select to authenticated using (
  public.is_admin() or (published and exists(select 1 from public.qualifications q where q.id = qualification_id and q.published))
);
create policy modules_admin on public.modules for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy documents_read on public.documents for select to authenticated using (
  public.is_admin() or (published and exists(select 1 from public.modules m join public.qualifications q on q.id=m.qualification_id where m.id=module_id and m.published and q.published))
);
create policy documents_admin on public.documents for all to authenticated using (public.is_admin()) with check (public.is_admin());
-- Bootstrap an admin only in trusted SQL editor AFTER that user has registered:
-- update public.profiles set role='admin' where id = (select id from auth.users where email = 'YOUR_ADMIN_EMAIL');
-- Never expose the service-role key or allow users to set role in signup metadata.