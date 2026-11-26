-- Optional sample catalogue. Run in the SQL editor after schema.sql.
-- All content is explicitly labelled SAMPLE. No student or administrator accounts are created.
with q as (
  insert into public.qualifications(title, description, published)
  values ('SAMPLE: Diploma in Business Studies', 'Sample data for testing only.', true) returning id
)
insert into public.modules(qualification_id, title, code, description, published)
select id, 'SAMPLE: Introduction to Management', 'SAMPLE-BUS101', 'Sample data for testing only.', true from q;