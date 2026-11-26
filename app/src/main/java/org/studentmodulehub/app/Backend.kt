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
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray

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
    suspend fun login(email: String, password: String): Profile {
        client.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
        return profileInternal()
    }
    suspend fun register(name: String, email: String, password: String) {
        client.auth.signUpWith(Email, redirectUrl = "org.studentmodulehub.app://auth/confirm") {
            this.email = email.trim(); this.password = password
            data = buildJsonObject { put("display_name", name.trim()) }
        }
    }
    suspend fun recover(email: String) { client.auth.resetPasswordForEmail(email.trim(), "org.studentmodulehub.app://auth/recovery") }
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
    suspend fun qualifications(): List<Qualification> {
        val rows = JSONArray(client.from("qualifications").select().data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> Qualification(o.getString("id"), o.getString("title"), o.optString("description"), o.optBoolean("published")) } }.sortedBy { it.title }
    }
    suspend fun modules(qualificationId: String): List<Module> {
        require(qualificationId.matches(Regex("[0-9a-fA-F-]{36}")))
        val rows = JSONArray(client.from("modules").select { filter { eq("qualification_id", qualificationId) } }.data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> Module(o.getString("id"), o.getString("title"), o.optString("code"), o.optString("description"), o.optBoolean("published")) } }.sortedBy { it.title }
    }
    suspend fun documents(moduleId: String): List<Document> {
        val rows = JSONArray(client.from("documents").select { filter { eq("module_id", moduleId) } }.data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> Document(o.getString("id"), o.getString("title"), o.optString("description"), o.optBoolean("published")) } }.sortedBy { it.title }
    }
    suspend fun students(): List<StudentAccount> {
        val rows = JSONArray(client.from("profiles").select().data)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { o -> StudentAccount(o.getString("id"), o.optString("display_name"), o.optString("role"), o.optString("created_at")) } }.sortedBy { it.name }
    }
    suspend fun saveItem(table: String, id: String?, title: String, description: String, code: String, parentId: String?, published: Boolean) {
        require(table in listOf("qualifications", "modules", "documents")); require(title.isNotBlank())
        if (id == null) {
            val payload = buildJsonObject {
                put("title", title.trim()); put("description", description.trim()); put("published", published)
                if (table == "modules") { put("qualification_id", requireNotNull(parentId)); put("code", code.trim()) }
                if (table == "documents") put("module_id", requireNotNull(parentId))
            }
            client.from(table).insert(payload)
        } else {
            client.from(table).update({
                set("title", title.trim()); set("description", description.trim()); set("published", published)
                if (table == "modules") { set("qualification_id", requireNotNull(parentId)); set("code", code.trim()) }
                if (table == "documents") set("module_id", requireNotNull(parentId))
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
data class Document(val id: String, val title: String, val description: String, val published: Boolean)
data class StudentAccount(val id: String, val name: String, val role: String, val createdAt: String)
data class Profile(val id: String, val name: String, val email: String, val role: String)
data class Qualification(val id: String, val title: String, val description: String, val published: Boolean)
data class Module(val id: String, val title: String, val code: String, val description: String, val published: Boolean)
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