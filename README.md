# Student Module Hub — Google Drive folder library (folder-based release)

Native Kotlin + Jetpack Compose + Material 3 app, based on the Stage 3 project. The app uses the documented, community-maintained Supabase Kotlin client with a public project URL/publishable key. No secret key or service-role credential is included. Authentication, encrypted session storage and PKCE recovery code from Stage 3 are retained but have not been re-verified on a device in this stage.

## What is implemented
- Student browsing is now folder-based: each first-level folder inside the configured Google Drive study folder is displayed as a module (for example, `FOR3704`).
- PDFs directly inside each module folder are listed using their original Google Drive filenames and streamed through the authenticated private Drive gateway when a student opens them. PDFs are not copied into Supabase Storage and are not renamed or classified by the Edge Function.
- Admin dashboard: read-only list of profiles; create/edit/publish qualifications, modules and material records; assign qualifications to colleges, modules to qualifications, and materials to modules; set qualification codes/levels and material categories; link PDFs from the configured private Google Drive folder.
- The catalogue database and admin editor support official qualification codes, levels, academic years and source URLs, plus module credits/NQF levels/semester and material academic year/semester metadata. Existing records are preserved; qualification college assignments are nullable until an administrator classifies them.
- The previous UNISA colleges, qualifications and curriculum records remain in the database as an archived catalogue. Student browsing no longer exposes that catalogue; administrators can still open it from the admin panel.
- SQL row-level security from Stage 3 restricts reads to published records and published parents for students, with admin access to catalogue management. Public signup trigger creates a `student` profile; clients cannot update `role`.
- Applied Stage 4 migration prevents cascading deletion of modules/documents when a parent is deleted. Delete dependents explicitly first.
- Student suspension, account editing and role management are deliberately not implemented in the app. This also prevents admins accidentally demoting their own account through the app. Student list does not expose email addresses from auth.users.

## Google Drive folder-based module library

The configured Google Drive folder is the source of truth. Create one folder per module directly inside it, using the module code as the folder name (for example, `FOR3704`). Put that module's PDFs directly inside its folder. The student app lists those first-level folders as modules and lists PDFs in each folder using their original filenames.

The app does not inspect PDF contents, infer module codes, classify documents, rename files, create catalogue records, or copy PDFs into Supabase Storage. Opening a PDF streams it through the authenticated `drive-materials` Edge Function. That function verifies that the requested module folder is a direct child of the configured root and that the PDF is a direct child of that module folder before streaming it. Students must be signed in. Google Drive remains private; service-account credentials remain server-side.

The old content-analysis importer is retired. Its scheduled calls return a small deprecation response rather than downloading or parsing PDFs. Existing import candidates, documents, qualification records, module records and college records are preserved. The old catalogue is labelled as archived and is no longer part of student browsing.

Nested subfolders are not yet displayed: place PDFs directly inside each module folder. File renaming is deliberately separate and can later be handled as an optional Google Colab batch-cleanup tool, without blocking publication.

Relevant Google documentation:
- Drive automatically indexes supported documents, PDFs and images for search: https://developers.google.com/workspace/drive/api/guides/file-metadata
- Drive full-text search query syntax: https://developers.google.com/workspace/drive/api/guides/ref-search-terms
- Supabase scheduled Edge Functions: https://supabase.com/docs/guides/functions/schedule-functions

## Google sign-in setup

The app now includes a **Continue with Google** option on the welcome, sign-in and registration screens. It uses Supabase's Google OAuth flow and returns to the app through `org.studentmodulehub.app://auth/google`. A Google account signing in for the first time is created through Supabase Auth; the existing database signup trigger should create its student profile.

Before Google sign-in can work:
1. In Google Cloud Console, create an OAuth 2.0 Client ID of type **Web application**.
2. In its **Authorized redirect URIs**, add the Supabase Auth callback URL shown on the Supabase Dashboard under Authentication → Sign In / Providers → Google. For this project it follows the form `https://<project-ref>.supabase.co/auth/v1/callback`.
3. In Supabase Dashboard → Authentication → Sign In / Providers → Google, enable Google and enter the Web Client ID and Client Secret from Google Cloud.
4. In Supabase Dashboard → Authentication → URL Configuration → Redirect URLs, add exactly `org.studentmodulehub.app://auth/google`. Keep the existing email confirmation and recovery redirect URLs too.
5. Save the settings and test on an installed Android build. Do not put the Google Client Secret in the Android app, Gradle properties committed to GitHub, or any public repository.

This implementation opens Google's OAuth consent flow in the browser; it does not use the separate native Credential Manager button flow. Google sign-in has been added to source but is not yet build-verified or tested against the live provider.

## Verification results
- The latest Android source changes passed GitHub Actions run #13 (`45871121871`): `testDebugUnitTest assembleDebug` completed successfully. Subsequent database seed and documentation commits do not change Android source. This verifies compilation and unit-test task execution, but does not replace physical-device or authenticated role/RLS testing.
- The repository does not include `gradle/wrapper/gradle-wrapper.jar`; the validation workflow installs Gradle 8.9 directly and runs `gradle testDebugUnitTest assembleDebug`.
- The current authentication UI changes have not yet been built or tested on a physical Android device.
- Migration `stage4_prevent_catalogue_cascade_deletes` was applied to the real project and verified: both FKs use ON DELETE RESTRICT.
- Live database inspection: both role policies and RLS were present; profile role default is student; `authenticated` does not have UPDATE privilege on the role column.
- Anonymous live HTTP GET of profiles, qualifications, modules and documents: all HTTP 401. Anonymous qualification POST: HTTP 401.
- NOT tested: real student/admin account login; student/admin CRUD and RLS scenarios; registration, confirmation, persistence, recovery and logout on Android; phone UI interactions. This debug build is not ready to be treated as production without those tests and security review.

## First administrator — project owner only
1. Register and confirm the account you personally want to make administrator. Never share its password or reset link.
2. Open your Supabase dashboard in your phone browser, select **Ndumiso19894’s Project**, and open **SQL Editor**. Only project owners/trusted database administrators should have this access.
3. In the query below replace `YOUR_CONFIRMED_EMAIL` with the exact email address for your own registered account, inside the single quotes. Check it carefully. Run the query once:

```sql
update public.profiles as p
set role = 'admin'
from auth.users as u
where p.id = u.id
  and lower(u.email) = lower('YOUR_CONFIRMED_EMAIL')
  and u.email_confirmed_at is not null
returning p.id, p.role;
```

4. Confirm exactly one returned row says `admin`. Zero rows means the address is wrong, unconfirmed, or has no profile; do not guess. More than one is unexpected: stop and investigate. No account is promoted by the public app. Sign out and back in to refresh the role shown in the app. The SQL editor is privileged: anyone who can run arbitrary SQL there can bypass RLS, so protect your Supabase account and enable MFA. Do not include passwords, API secrets or service keys in this query.

## Phone test checklist
1. Install `Student-Module-Hub-Stage4-debug.apk`. It has the same package and signing certificate as Stage 3 and higher versionCode, so it should update. If Android requests permission, allow installation from your download app.
2. Register/confirm two distinct accounts using inboxes you control. The Supabase default email provider can restrict recipients/rate; configure your own SMTP for general registration. Keep passwords private.
3. Promote only the intended admin account with the SQL above. Sign in as admin. Open Administrator → Qualifications & modules. Create a clearly labelled test qualification and module, create published and draft document metadata, and test editing, publishing/unpublishing. Try deleting a qualification with a module: it should be blocked until dependent records are removed. None of these results has been observed yet.
4. Sign out, sign in as the other student. Confirm no admin dashboard appears; browse published qualification/module/document metadata and verify drafts and content under unpublished parents are absent. Sign out and sign in again as admin to verify the dashboard still works.
5. Test sign out, reopening the app, and recovery email on the same device. Report exactly what succeeds/fails; do not send passwords, access tokens or email/reset links.
6. Separate authenticated API/RLS negative tests still need to be run with separate student and admin sessions; UI hiding alone does not prove security.

## Database changes
`backend/migrations/20261113_stage4_prevent_cascade.sql` was applied as Supabase migration `20261113111751`; do not rerun. `backend/migrations/20261126_unisa_catalogue_foundation.sql` was applied to the live Supabase project as migration `20261126174301`. It creates the eight qualification-awarding colleges, qualification metadata, module metadata and controlled material categories while preserving existing qualifications, modules, documents, Drive links and RLS permissions. The College of Graduate Studies is not listed as a qualification-awarding college because UNISA states that qualifications are not awarded there.

The live data import now contains 340 qualification records across all eight colleges, sourced from UNISA's official 2027 undergraduate, honours, postgraduate diploma, master's and doctoral listings. There are now 493 verified curriculum-linked modules across fifteen qualifications/streams (494 module rows total including one pre-existing test row). The qualification table contains 342 rows: 341 linked to the eight colleges and one earlier unlinked test row. Recent additions include Higher Certificate in Criminal Justice (90006), Diploma in Security Management (98221), BA in Criminology (98681), BA Forensic Science and Technology (90002), Higher Certificate in Accounting Sciences (98201), and BA Police Science (98683). Reproducible, idempotent seeds are in `backend/seeds/unisa_verified_module_curricula_2026.sql` and `backend/seeds/unisa_verified_module_curricula_2026_batch_2.sql` through `backend/seeds/unisa_verified_module_curricula_2026_batch_4.sql`. The 2027 curriculum for that qualification is not yet published on UNISA's page, so the 2026 module list is clearly labelled by academic year. Many qualification entries and most module curricula still need further verification and import; the catalogue is not yet complete. See `backend/seeds/UNISA_CATALOGUE_IMPORT_STATUS.md` for the per-college counts, imported curricula, sources and completeness boundary. Do not infer missing module codes or publish unverified curriculum data. UNISA official sources: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Undergraduate-qualifications/Qualifications and https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications.

## Build
JDK 17, Android SDK platform 35, Gradle wrapper 8.9: `./gradlew testDebugUnitTest assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`. Public project configuration is in gradle.properties. This is a debug-signed APK, not a production release; production needs release signing, backups, monitoring, SMTP, comprehensive role tests, and independent security review.

## Repository upload note
The text source and project configuration have been uploaded. The binary `gradle/wrapper/gradle-wrapper.jar` was not included in this upload, so the checked-in `gradlew` launchers will need that standard Gradle wrapper JAR restored before they can run. This repository upload is therefore not yet a fully self-contained build checkout.