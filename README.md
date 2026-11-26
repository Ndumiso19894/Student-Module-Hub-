# Student Module Hub — Stage 4 (debug / verification build)

Native Kotlin + Jetpack Compose + Material 3 app, based on the Stage 3 project. The app uses the documented, community-maintained Supabase Kotlin client with a public project URL/publishable key. No secret key or service-role credential is included. Authentication, encrypted session storage and PKCE recovery code from Stage 3 are retained but have not been re-verified on a device in this stage.

## Stage 5 — PDF study materials

- Admins can attach or replace PDF files on existing document records from the Android document picker.
- Students can download attached PDFs using Android's save-file picker.
- Files are stored in the private `study-materials` bucket; the app never uses public file URLs.
- Uploads are limited to PDF files up to 25 MiB. Storage access is controlled by Supabase Storage RLS: admins manage files; students can read only files linked to published documents under published modules and qualifications.
- Before using these features, run `backend/migrations/20261126_stage5_private_pdf_storage.sql` once in the Supabase SQL Editor as project owner. This adds `documents.storage_path`, creates the private bucket and installs the Storage policies. Do not make the bucket public.
- Upload a document's metadata first, then use **Upload PDF** beside that document in Administrator → Qualifications & modules → module → Documents. Publish the document and its parent module and qualification before students can download it.

## What is implemented
- Student profile and live qualification → module → document browsing, including private PDF downloads when files are attached and all parent records are published.
- Admin dashboard: read-only list of profiles (name, ID, role and creation date); create/edit/publish/unpublish qualifications, modules and document metadata; assign modules to qualifications, documents to modules; upload/replace PDF files; delete with confirmation.
- SQL row-level security from Stage 3 restricts reads to published records and published parents for students, with admin access to catalogue management. Public signup trigger creates a `student` profile; clients cannot update `role`.
- Applied Stage 4 migration prevents cascading deletion of modules/documents when a parent is deleted. Delete dependents explicitly first.
- Student suspension, account editing and role management are deliberately not implemented in the app. This also prevents admins accidentally demoting their own account through the app. Student list does not expose email addresses from auth.users.

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
- The original Stage 4 baseline passed `testDebugUnitTest assembleDebug` before the latest authentication changes. The current revision contains nine unit tests for authentication form validation; these new tests have not yet been run because the uploaded repository is still missing `gradle/wrapper/gradle-wrapper.jar`.
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
`backend/migrations/20261113_stage4_prevent_cascade.sql` was applied as Supabase migration `20261113111751`; do not rerun. `backend/schema.sql` describes the previous initial migration. Existing tables and policies were reused. No Drive integration was started.

## Build
JDK 17, Android SDK platform 35, Gradle wrapper 8.9: `./gradlew testDebugUnitTest assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`. Public project configuration is in gradle.properties. This is a debug-signed APK, not a production release; production needs release signing, backups, monitoring, SMTP, comprehensive role tests, and independent security review.

## Repository upload note
The text source and project configuration have been uploaded. The binary `gradle/wrapper/gradle-wrapper.jar` was not included in this upload, so the checked-in `gradlew` launchers will need that standard Gradle wrapper JAR restored before they can run. This repository upload is therefore not yet a fully self-contained build checkout.