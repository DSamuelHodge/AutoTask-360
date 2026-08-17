package com.example.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface CredentialStore {
    fun list(): List<PairedCredential>
    fun get(id: String): PairedCredential?
    fun findByToken(token: String): PairedCredential?
    fun put(credential: PairedCredential)
    fun revoke(id: String): Boolean
    fun markUsed(id: String, at: Long)
}

class InMemoryCredentialStore : CredentialStore {
    private val rows = ConcurrentHashMap<String, PairedCredential>()

    override fun list(): List<PairedCredential> =
        rows.values.sortedByDescending { it.createdAt }

    override fun get(id: String): PairedCredential? = rows[id]

    override fun findByToken(token: String): PairedCredential? {
        val hash = TokenHasher.hash(token)
        return rows.values.firstOrNull { !it.revoked && it.tokenHash == hash }
    }

    override fun put(credential: PairedCredential) {
        rows[credential.id] = credential
    }

    override fun revoke(id: String): Boolean {
        val existing = rows[id] ?: return false
        rows[id] = existing.copy(revoked = true)
        return true
    }

    override fun markUsed(id: String, at: Long) {
        val existing = rows[id] ?: return
        rows[id] = existing.copy(lastUsedAt = at)
    }
}

class PrefsCredentialStore(context: Context) : CredentialStore {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun list(): List<PairedCredential> = load().values.sortedByDescending { it.createdAt }

    @Synchronized
    override fun get(id: String): PairedCredential? = load()[id]

    @Synchronized
    override fun findByToken(token: String): PairedCredential? {
        val hash = TokenHasher.hash(token)
        return load().values.firstOrNull { !it.revoked && it.tokenHash == hash }
    }

    @Synchronized
    override fun put(credential: PairedCredential) {
        val all = load().toMutableMap()
        all[credential.id] = credential
        save(all)
    }

    @Synchronized
    override fun revoke(id: String): Boolean {
        val all = load().toMutableMap()
        val existing = all[id] ?: return false
        all[id] = existing.copy(revoked = true)
        save(all)
        return true
    }

    @Synchronized
    override fun markUsed(id: String, at: Long) {
        val all = load().toMutableMap()
        val existing = all[id] ?: return
        all[id] = existing.copy(lastUsedAt = at)
        save(all)
    }

    private fun load(): Map<String, PairedCredential> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyMap()
        }
        val out = linkedMapOf<String, PairedCredential>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val credential = fromJson(obj)
            out[credential.id] = credential
        }
        return out
    }

    private fun save(all: Map<String, PairedCredential>) {
        val arr = JSONArray()
        all.values.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(credential: PairedCredential): JSONObject {
        val scopes = JSONArray()
        credential.scopes.forEach { scopes.put(it.name) }
        val actions = JSONArray()
        credential.approvedActions.forEach { actions.put(it) }
        return JSONObject()
            .put("id", credential.id)
            .put("name", credential.name)
            .put("tokenHash", credential.tokenHash)
            .put("scopes", scopes)
            .put("approvedActions", actions)
            .put("createdAt", credential.createdAt)
            .put("lastUsedAt", credential.lastUsedAt)
            .put("revoked", credential.revoked)
    }

    private fun fromJson(obj: JSONObject): PairedCredential {
        val scopes = linkedSetOf<AccessScope>()
        val scopeArr = obj.optJSONArray("scopes") ?: JSONArray()
        for (i in 0 until scopeArr.length()) {
            AccessScope.parse(scopeArr.optString(i))?.let { scopes += it }
        }
        val actions = linkedSetOf<String>()
        val actionArr = obj.optJSONArray("approvedActions") ?: JSONArray()
        for (i in 0 until actionArr.length()) {
            actions += actionArr.optString(i).uppercase()
        }
        return PairedCredential(
            id = obj.optString("id"),
            name = obj.optString("name"),
            tokenHash = obj.optString("tokenHash"),
            scopes = scopes,
            approvedActions = actions,
            createdAt = obj.optLong("createdAt"),
            lastUsedAt = obj.optLong("lastUsedAt"),
            revoked = obj.optBoolean("revoked")
        )
    }

    companion object {
        private const val PREFS = "autotask_access_credentials"
        private const val KEY = "credentials"
    }
}

object TokenHasher {
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun newToken(): String = "atc-" + UUID.randomUUID().toString().replace("-", "")

    fun newId(): String = "cred-" + UUID.randomUUID().toString().replace("-", "").take(12)
}
