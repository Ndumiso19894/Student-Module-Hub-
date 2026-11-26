-- Poll the configured Google Drive folder and its subfolders every five minutes.
select cron.unschedule(jobid)
from cron.job
where jobname = 'drive-importer-auto-scan';

select cron.schedule(
  'drive-importer-auto-scan',
  '*/5 * * * *',
  $job$
    select net.http_post(
      url := 'https://kcejjvrbuuynwxobafzg.supabase.co/functions/v1/drive-importer',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'apikey', 'sb_publishable_TdjOedpSvAJMas-yNuateA_SdqpIf7E',
        'x-drive-importer-secret',
          (select decrypted_secret from vault.decrypted_secrets where name = 'drive_importer_cron_secret')
      ),
      body := '{"action":"scan","limit":8}'::jsonb,
      timeout_milliseconds := 10000
    );
  $job$
);
