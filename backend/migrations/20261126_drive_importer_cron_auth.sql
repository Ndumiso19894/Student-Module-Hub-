-- Private credential used only by the scheduled Drive importer.
create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;

create table if not exists public.drive_importer_cron_auth (
  id integer primary key check (id = 1),
  secret text not null
);
alter table public.drive_importer_cron_auth enable row level security;
revoke all on public.drive_importer_cron_auth from anon, authenticated;

do $$
declare
  v_secret text;
begin
  select secret into v_secret from public.drive_importer_cron_auth where id = 1;
  if v_secret is null then
    v_secret := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
    insert into public.drive_importer_cron_auth(id, secret) values (1, v_secret)
    on conflict (id) do nothing;
  end if;
  if not exists (select 1 from vault.secrets where name = 'drive_importer_cron_secret') then
    perform vault.create_secret(v_secret, 'drive_importer_cron_secret', 'Private authentication for scheduled Student Module Hub Drive scans');
  end if;
end $$;
