package com.example.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical JSON value at command boundaries. Replaces `Map<String, Any?>`
 * and raw JSON strings once a payload has crossed the facade.
 */
sealed class JsonValue {
    data class Text(val value: String) : JsonValue()
    data class NumberValue(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class ArrayValue(val values: List<JsonValue>) : JsonValue()
    data class ObjectValue(val fields: Map<String, JsonValue>) : JsonValue()
    object Null : JsonValue()

    fun asObjectOrNull(): ObjectValue? = this as? ObjectValue

    fun asTextOrNull(): String? = (this as? Text)?.value

    fun asBooleanOrNull(): Boolean? = (this as? Bool)?.value

    fun isWholeNumber(): Boolean =
        this is NumberValue && value.isFinite() && value % 1.0 == 0.0

    fun asLongOrNull(): Long? {
        if (this !is NumberValue || !isWholeNumber()) return null
        if (value < Long.MIN_VALUE.toDouble() || value > Long.MAX_VALUE.toDouble()) return null
        return value.toLong()
    }

    fun asIntOrNull(): Int? {
        val long = asLongOrNull() ?: return null
        if (long < Int.MIN_VALUE || long > Int.MAX_VALUE) return null
        return long.toInt()
    }

    fun asDoubleOrNull(): Double? = (this as? NumberValue)?.value

    fun toJson(): Any = when (this) {
        is Text -> value
        is NumberValue -> canonicalNumber()
        is Bool -> value
        is ArrayValue -> JSONArray().also { array -> values.forEach { array.put(it.toJson()) } }
        is ObjectValue -> toJsonObject()
        Null -> JSONObject.NULL
    }

    fun toJsonObject(): JSONObject {
        val obj = this as? ObjectValue ?: return JSONObject()
        val json = JSONObject()
        obj.fields.forEach { (key, value) -> json.put(key, value.toJson()) }
        return json
    }

    fun toCompactString(): String = when (this) {
        is ObjectValue -> toJsonObject().toString()
        is ArrayValue -> (toJson() as JSONArray).toString()
        else -> toJson().toString()
    }

    fun toAny(): Any? = when (this) {
        is Text -> value
        is NumberValue -> asLongOrNull() ?: value
        is Bool -> value
        is ArrayValue -> values.map { it.toAny() }
        is ObjectValue -> toAnyMap()
        Null -> null
    }

    fun toAnyMap(): Map<String, Any?> {
        val obj = this as? ObjectValue ?: return emptyMap()
        return obj.fields.mapValues { it.value.toAny() }
    }

    private fun NumberValue.canonicalNumber(): Number {
        val long = asLongOrNull()
        return long ?: value
    }

    companion object {
        val emptyObject = ObjectValue(emptyMap())

        fun from(value: Any?): JsonValue = when (value) {
            null, JSONObject.NULL -> Null
            is JsonValue -> value
            is Boolean -> Bool(value)
            is Int -> NumberValue(value.toDouble())
            is Long -> NumberValue(value.toDouble())
            is Float -> NumberValue(value.toDouble())
            is Double -> NumberValue(value)
            is Number -> NumberValue(value.toDouble())
            is String -> Text(value)
            is JSONObject -> fromObject(value)
            is JSONArray -> fromArray(value)
            is Map<*, *> -> ObjectValue(
                value.entries.mapNotNull { (key, entry) ->
                    val name = key as? String ?: return@mapNotNull null
                    name to from(entry)
                }.toMap()
            )
            is List<*> -> ArrayValue(value.map { from(it) })
            else -> Text(value.toString())
        }

        fun fromObject(json: JSONObject): ObjectValue {
            val fields = linkedMapOf<String, JsonValue>()
            json.keys().forEach { key ->
                fields[key] = from(json.opt(key))
            }
            return ObjectValue(fields)
        }

        fun fromArray(json: JSONArray): ArrayValue {
            val values = ArrayList<JsonValue>(json.length())
            for (i in 0 until json.length()) {
                values.add(from(json.opt(i)))
            }
            return ArrayValue(values)
        }

        fun parseObject(raw: String, emptyIfBlank: Boolean = true): ObjectValue {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "{}") {
                if (emptyIfBlank) return emptyObject
            }
            return fromObject(JSONObject(trimmed))
        }

        fun parseArray(raw: String, emptyIfBlank: Boolean = true): ArrayValue {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "[]") {
                if (emptyIfBlank) return ArrayValue(emptyList())
            }
            return fromArray(JSONArray(trimmed))
        }
    }
}
