# Student Module Hub — Stage 4 (debug / verification build)

Native Kotlin + Jetpack Compose + Material 3 app, based on the Stage 3 project. The app uses the documented, community-maintained Supabase Kotlin client with a public project URL/publishable key. No secret key or service-role credential is included. Authentication, encrypted session storage and PKCE recovery code from Stage 3 are retained but have not been re-verified on a device in this stage.

## What is implemented
- Student profile and live qualification → module → document-metadata browsing, with empty states. Actual PDFs are not connected.
- Admin dashboard: read-only list of profiles (name, ID, role and creation date); create/edit/publish/unpublish qualifications, modules and document metadata; assign modules to qualifications, documents to modules; delete with confirmation.
- SQL row-level security from Stage 3 restricts reads to published records and published parents for students, with admin access to catalogue management. Public signup trigger creates a `student` profile; clients cannot update `role`.
- Applied Stage 4 migration prevents cascading deletion of modules/documents when a parent is deleted. Delete dependents explicitly first.
- Student suspension, account editing and role management are deliberately not implemented in the app. This also prevents admins accidentally demoting their own account through the app. Student list does not expose email addresses from auth.users.

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