package org.studentmodulehub.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import kotlinx.coroutines.withContext

val MaterialCategories = listOf(
    "Study Guides", "Tutorial Letters", "Assignment Questions", "Examination Question Papers",
    "Examination Memorandums", "Prescribed Books", "Lecture Notes", "Other Study Materials"
)

class Backend(context: Context) {
    val configured get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    private val vault = SessionVault(context)
    private val sessionManager = object : SessionManager {
        override suspend fun saveSession(session: UserSession) { vault.save(Json.encodeToString(UserSession.serializer(), session)) }
        override suspend fun loadSession(): UserSession? = try { vault.load()?.let { Json.decodeFromString(UserSession.serializer(), it) } } catch (_: Exception) { vault.clear(); null }
        override suspend fun deleteSession() { vault.clear() }
    }
    private val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        install(Auth) { flowType = FlowType.PKCE; scheme = "org.studentmodulehub.app"; host = "auth"; sessionManager = this@Backend.sessionManager }
        install(Postgrest)
    }
    private fun normalizeEmail(email: String): String = email.trim().lowercase(java.util.Locale.ROOT)

    suspend fun login(email: String, password: String): Profile {
        client.auth.signInWith(Email) { this.email = normalizeEmail(email); this.password = password }
        return profileInternal()
    }
    suspend fun signInWithGoogle() {
        client.auth.signInWith(Google, redirectUrl = "org.studentmodulehub.app://auth/google")
    }
    suspend fun register(name: String, email: String, password: String): Profile? {
        client.auth.signUpWith(Email, redirectUrl = "org.studentmodulehub.app://auth/confirm") {
            this.email = normalizeEmail(email); this.password = password
            data = buildJsonObject { put("display_name", name.trim()) }
        }
        return if (client.auth.currentSessionOrNull() != null) profileInternal() else null
    }
    suspend fun recover(email: String) {
        client.auth.resetPasswordForEmail(normalizeEmail(email), "org.studentmodulehub.app://auth/recovery")
    }
    suspend fun completeCallback(code: String) { client.auth.exchangeCodeForSession(code) }
    suspend fun updatePassword(password: String) { client.auth.updateUser { this.password = password }; client.auth.signOut() }
    suspend fun restore(): Profile? {
        client.auth.awaitInitialization()
        if (client.auth.currentSessionOrNull() == null) return null
        return profileInternal()
    }
    suspend fun logout() { client.auth.signOut() }
    private suspend fun profileInternal(): Profile {
        val user = client.auth.retrieveUserForCurrentSession()
        val rows = JSONArray(client.from("profiles").select { filter { eq("id", user.id) } }.data)
        check(rows.length() == 1) { "Profile unavailable. Check backend schema and policies." }
        val row = rows.getJSONObject(0)
        return Profile(row.getString("id"), row.optString("display_name"), user.email.orEmpty(), row.optString("role", "student"))
    }
    suspend fun colleges(): List<College> {
        val rows = JSONArray(client.from("colleges").select().data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> College(o.getString("id"), o.getString("name")) } }.sortedBy { it.name }
    }
    suspend fun qualifications(collegeId: String? = null): List<Qualification> {
        val rows = JSONArray(client.from("qualifications").select {
            if (!collegeId.isNullOrBlank()) filter { eq("college_id", collegeId) }
        }.data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o ->
            Qualification(o.getString("id"), o.getString("title"), o.optString("description"), o.optBoolean("published"),
                o.optString("college_id"), o.optString("qualification_code"), o.optString("qualification_level"),
                if (o.isNull("academic_year")) null else o.optInt("academic_year"), o.optString("official_url"))
        } }.sortedBy { it.title }
    }
    suspend fun modules(qualificationId: String): List<Module> {
        require(qualificationId.matches(Regex("[0-9a-fA-F-]{36}")))
        val rows = JSONArray(client.from("modules").select { filter { eq("qualification_id", qualificationId) } }.data)
        return parseModules(rows)
    }
    suspend fun allModules(): List<Module> {
        val rows = JSONArray(client.from("modules").select().data)
        return parseModules(rows)
    }
    private fun parseModules(rows: JSONArray): List<Module> {
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> Module(o.getString("id"), o.getString("title"), o.optString("code"), o.optString("description"), o.optBoolean("published"),
            if (o.isNull("credits")) null else o.optInt("credits"), if (o.isNull("nqf_level")) null else o.optInt("nqf_level"),
            o.optString("semester"), if (o.isNull("academic_year")) null else o.optInt("academic_year"), o.optString("official_url"), o.optString("qualification_id")) } }.sortedBy { it.title }
    }
    suspend fun documents(moduleId: String): List<Document> {
        val rows = JSONArray(client.from("documents").select { filter { eq("module_id", moduleId) } }.data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> Document(o.getString("id"), o.getString("title"), o.optString("description"), o.optBoolean("published"), o.optString("category", "Other Study Materials"),
            if (o.isNull("academic_year")) null else o.optInt("academic_year"), o.optString("semester")) } }.sortedBy { it.title }
    }
    suspend fun drivePdfs(): List<DrivePdf> = driveRequest(org.json.JSONObject().put("action", "list")).let { response ->
        val files = response.optJSONArray("files") ?: JSONArray()
        (0 until files.length()).map { index ->
            files.getJSONObject(index).let { file ->
                DrivePdf(file.getString("id"), file.getString("name"), file.optString("size"), file.optString("modifiedTime"))
            }
        }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }

    suspend fun scanDriveMaterials(limit: Int = 8): org.json.JSONObject =
        importerRequest(org.json.JSONObject().put("action", "scan").put("limit", limit))

    suspend fun driveImportQueue(status: String = ""): List<DriveImportCandidate> {
        val response = importerRequest(org.json.JSONObject().put("action", "queue").put("status", status))
        val rows = response.optJSONArray("candidates") ?: JSONArray()
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            val module = item.optJSONObject("module")
            DriveImportCandidate(
                item.getString("id"), item.optString("file_name"), item.optString("suggested_title"), item.optString("suggested_module_id"),
                module?.optString("title").orEmpty(), module?.optString("code").orEmpty(),
                item.optString("suggested_category", "Other Study Materials"),
                item.optDouble("confidence", 0.0), item.optString("status"),
                item.optJSONArray("evidence")?.toString().orEmpty()
            )
        }
    }

    suspend fun approveDriveCandidate(candidateId: String, moduleId: String, category: String, title: String) {
        val result = importerRequest(org.json.JSONObject().put("action", "approve")
            .put("candidate_id", candidateId).put("module_id", moduleId).put("category", category).put("title", title))
        check(result.optBoolean("imported")) { "The PDF could not be imported." }
    }

    suspend fun rejectDriveCandidate(candidateId: String) {
        val result = importerRequest(org.json.JSONObject().put("action", "reject").put("candidate_id", candidateId))
        check(result.optBoolean("rejected")) { "The candidate could not be rejected." }
    }

    private suspend fun importerRequest(payload: org.json.JSONObject): org.json.JSONObject {
        val accessToken = client.auth.currentSessionOrNull()?.accessToken
            ?: throw java.io.IOException("Your session has expired. Sign in again.")
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val connection = java.net.URL("${BuildConfig.SUPABASE_URL}/functions/v1/drive-importer")
                .openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 120000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val result = try { org.json.JSONObject(body) } catch (_: Exception) {
                    throw java.io.IOException("The importer returned an unreadable response.")
                }
                if (status !in 200..299) throw java.io.IOException(result.optString("error").ifBlank { "Importer failed (HTTP $status)." })
                result
            } finally { connection.disconnect() }
        }
    }

    suspend fun linkDrivePdf(documentId: String, fileId: String) {
        require(documentId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(fileId.matches(Regex("[a-zA-Z0-9_-]{10,200}")))
        val response = driveRequest(org.json.JSONObject()
            .put("action", "link")
            .put("document_id", documentId)
            .put("file_id", fileId))
        check(response.optBoolean("linked")) { "The PDF could not be linked." }
    }

    suspend fun downloadMaterial(documentId: String, destination: java.io.File) {
        require(documentId.matches(Regex("[0-9a-fA-F-]{36}")))
        val accessToken = client.auth.currentSessionOrNull()?.accessToken
            ?: throw java.io.IOException("Your session has expired. Sign in again.")
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            val connection = java.net.URL("${BuildConfig.SUPABASE_URL}/functions/v1/drive-materials")
                .openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                val payload = org.json.JSONObject().put("action", "download").put("document_id", documentId)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val detail = try { org.json.JSONObject(errorBody).optString("error") } catch (_: Exception) { "" }
                    throw java.io.IOException(detail.ifBlank { "Material download failed (HTTP $status)." })
                }
                check(connection.contentType?.contains("application/pdf", ignoreCase = true) == true) {
                    "The materials service did not return a PDF."
                }
                connection.inputStream.use { input ->
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                check(destination.exists() && destination.length() > 0L) { "The PDF is empty." }
            } catch (e: Exception) {
                destination.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun driveRequest(payload: org.json.JSONObject): org.json.JSONObject {
        val accessToken = client.auth.currentSessionOrNull()?.accessToken
            ?: throw java.io.IOException("Your session has expired. Sign in again.")
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val connection = java.net.URL("${BuildConfig.SUPABASE_URL}/functions/v1/drive-materials")
                .openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val result = try { org.json.JSONObject(body) } catch (_: Exception) {
                    throw java.io.IOException("The materials service returned an unreadable response.")
                }
                if (status !in 200..299) {
                    throw java.io.IOException(result.optString("error").ifBlank { "Materials request failed (HTTP $status)." })
                }
                result
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun students(): List<StudentAccount> {
        val rows = JSONArray(client.from("profiles").select().data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> StudentAccount(o.getString("id"), o.optString("display_name"), o.optString("role"), o.optString("created_at")) } }.sortedBy { it.name }
    }
    suspend fun saveItem(table: String, id: String?, title: String, description: String, code: String, parentId: String?, published: Boolean, category: String = "Other Study Materials", level: String = "", academicYear: Int? = null, credits: Int? = null, nqfLevel: Int? = null, semester: String = "", officialUrl: String = "") {
        require(table in listOf("qualifications", "modules", "documents")); require(title.isNotBlank())
        if (id == null) {
            val payload = buildJsonObject {
                put("title", title.trim()); put("description", description.trim()); put("published", published)
                if (table == "qualifications") { put("college_id", requireNotNull(parentId)); put("qualification_code", code.trim()); put("qualification_level", level.trim()); if (academicYear != null) put("academic_year", academicYear); put("official_url", officialUrl.trim()) }
                if (table == "modules") { put("qualification_id", requireNotNull(parentId)); put("code", code.trim()); if (credits != null) put("credits", credits); if (nqfLevel != null) put("nqf_level", nqfLevel); put("semester", semester.trim()); if (academicYear != null) put("academic_year", academicYear); put("official_url", officialUrl.trim()) }
                if (table == "documents") { put("module_id", requireNotNull(parentId)); put("category", category); if (academicYear != null) put("academic_year", academicYear); put("semester", semester.trim()) }
            }
            client.from(table).insert(payload)
        } else {
            client.from(table).update({
                set("title", title.trim()); set("description", description.trim()); set("published", published)
                if (table == "qualifications") { set("college_id", requireNotNull(parentId)); set("qualification_code", code.trim()); set("qualification_level", level.trim()); if (academicYear != null) set("academic_year", academicYear); set("official_url", officialUrl.trim()) }
                if (table == "modules") { set("qualification_id", requireNotNull(parentId)); set("code", code.trim()); if (credits != null) set("credits", credits); if (nqfLevel != null) set("nqf_level", nqfLevel); set("semester", semester.trim()); if (academicYear != null) set("academic_year", academicYear); set("official_url", officialUrl.trim()) }
                if (table == "documents") { set("module_id", requireNotNull(parentId)); set("category", category); if (academicYear != null) set("academic_year", academicYear); set("semester", semester.trim()) }
            }) { filter { eq("id", id) }; select() }.let { check(JSONArray(it.data).length() == 1) { "No record was updated." } }
        }
    }
    suspend fun deleteItem(table: String, id: String) {
        require(table in listOf("qualifications", "modules", "documents"))
        val result = client.from(table).delete { filter { eq("id", id) }; select() }
        check(JSONArray(result.data).length() == 1) { "No record was deleted." }
    }

}
class BackendException(val code: Int): Exception("Service request failed (HTTP $code).")
data class College(val id: String, val name: String)
data class Document(val id: String, val title: String, val description: String, val published: Boolean, val category: String = "Other Study Materials", val academicYear: Int? = null, val semester: String = "")
data class DrivePdf(val id: String, val name: String, val sizeBytes: String, val modifiedAt: String)
data class StudentAccount(val id: String, val name: String, val role: String, val createdAt: String)
data class Profile(val id: String, val name: String, val email: String, val role: String)
data class Qualification(val id: String, val title: String, val description: String, val published: Boolean, val collegeId: String = "", val qualificationCode: String = "", val level: String = "", val academicYear: Int? = null, val officialUrl: String = "")
data class Module(val id: String, val title: String, val code: String, val description: String, val published: Boolean, val credits: Int? = null, val nqfLevel: Int? = null, val semester: String = "", val academicYear: Int? = null, val officialUrl: String = "", val qualificationId: String = "")
/** Encrypted session at rest; key stays in Android Keystore and backup is disabled. */
private class SessionVault(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val alias = "smh_session_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("encrypted", Base64.encodeToString(data, Base64.NO_WRAP)).commit())
    }
    fun load(): String? {
        val data = prefs.getString("encrypted", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        require(data.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0,12)))
        return String(cipher.doFinal(data.copyOfRange(12,data.size)), Charsets.UTF_8)
    }
    fun clear() { prefs.edit().clear().commit() }
}
data class DriveImportCandidate(
    val id: String, val fileName: String, val suggestedTitle: String, val suggestedModuleId: String, val moduleTitle: String,
    val moduleCode: String, val category: String, val confidence: Double, val status: String, val evidence: String
)
