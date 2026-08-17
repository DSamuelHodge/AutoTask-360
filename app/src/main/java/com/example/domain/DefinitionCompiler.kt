package com.example.domain

import com.example.data.AutomationProfile
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Compiles automation definitions on write and caches the result by
 * `id + revision`. Callers must invalidate or replace the cache entry when a
 * newer revision is persisted.
 */
object DefinitionCompiler {
    private val cache = ConcurrentHashMap<String, CompiledAutomation>()

    fun compile(input: JSONObject, existingRevision: Long = 0L): CompiledAutomation {
        val definition = try {
            DefinitionCodec.fromJson(input, existingRevision)
        } catch (e: InvalidAutomationException) {
            throw e
        } catch (e: Exception) {
            throw InvalidAutomationException(
                listOf(ValidationError("definition", e.localizedMessage ?: "malformed JSON"))
            )
        }
        DefinitionValidator.validate(definition)
        return CompiledAutomation(definition)
    }

    fun compile(profile: AutomationProfile): CompiledAutomation {
        val compiled = try {
            val definition = DefinitionCodec.fromProfile(profile)
            DefinitionValidator.validate(definition)
            CompiledAutomation(definition)
        } catch (e: InvalidAutomationException) {
            throw e
        } catch (e: Exception) {
            throw InvalidAutomationException(
                listOf(ValidationError("definition", e.localizedMessage ?: "malformed profile JSON"))
            )
        }
        return compiled
    }

    fun compilePatch(existing: AutomationProfile, patch: JSONObject): CompiledAutomation {
        val merged = try {
            DefinitionCodec.mergePatch(existing, patch)
        } catch (e: InvalidAutomationException) {
            throw e
        } catch (e: Exception) {
            throw InvalidAutomationException(
                listOf(ValidationError("definition", e.localizedMessage ?: "malformed JSON patch"))
            )
        }
        return compile(merged, existing.revision)
    }

    fun getOrCompile(profile: AutomationProfile): CompiledAutomation {
        val cached = cache[profile.id]
        if (cached != null &&
            cached.revision == profile.revision &&
            cached.schemaVersion == profile.schemaVersion
        ) {
            return cached
        }
        val compiled = compile(profile)
        cache[profile.id] = compiled
        return compiled
    }

    fun put(compiled: CompiledAutomation) {
        cache[compiled.id] = compiled
    }

    fun invalidate(id: String) {
        cache.remove(id)
    }

    fun cached(id: String): CompiledAutomation? = cache[id]

    fun resetForTests() {
        cache.clear()
    }
}
