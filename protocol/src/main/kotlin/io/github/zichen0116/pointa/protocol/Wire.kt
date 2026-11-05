package io.github.zichen0116.pointa.protocol

import kotlinx.serialization.json.*

internal class ProtocolFailure(val code: String) : Exception(code)
internal fun ensure(value: Boolean, code: String = "invalid_message") { if (!value) throw ProtocolFailure(code) }

internal object Wire {
    private val json = Json { isLenient = false; allowSpecialFloatingPointValues = false }

    fun parse(text: String): JsonObject {
        ensure(text.toByteArray(Charsets.UTF_8).size <= 4096, "message_too_large")
        var depth = 0
        var i = 0
        val keys = mutableSetOf<String>()
        while (i < text.length) {
            when (text[i]) {
                '{', '[' -> { depth++; ensure(depth <= 8, "message_too_deep") }
                '}', ']' -> depth--
                '"' -> {
                    val start = i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                    ensure(i < text.length)
                    var next = i + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (depth == 1 && next < text.length && text[next] == ':') {
                        val key = runCatching { json.parseToJsonElement(text.substring(start, i + 1)).jsonPrimitive.content }.getOrElse { throw ProtocolFailure("invalid_message") }
                        ensure(keys.add(key), "duplicate_property")
                    }
                }
            }
            i++
        }
        return runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: throw ProtocolFailure("invalid_message")
    }

    fun message(type: String, fields: JsonObjectBuilder.() -> Unit = {}): String = buildJsonObject {
        put("v", 1); put("type", type); fields()
    }.toString()
}

internal fun JsonObject.text(key: String, max: Int = 128): String {
    val value = this[key] as? JsonPrimitive
    ensure(value != null && value.isString, "invalid_$key")
    return value!!.content.also { ensure(it.length in 1..max && it.none(Char::isISOControl), "invalid_$key") }
}

internal fun JsonObject.number(key: String): Long {
    val value = this[key] as? JsonPrimitive
    ensure(value != null && !value.isString && value.content.matches(Regex("-?(0|[1-9][0-9]*)")), "invalid_$key")
    return value!!.longOrNull ?: throw ProtocolFailure("invalid_$key")
}

internal fun JsonObject.boolean(key: String): Boolean {
    val value = this[key] as? JsonPrimitive
    ensure(value != null && !value.isString, "invalid_$key")
    return value!!.booleanOrNull ?: throw ProtocolFailure("invalid_$key")
}

internal fun JsonObject.effect(): LaserEffect {
    val effect = this["effect"] as? JsonObject ?: throw ProtocolFailure("invalid_effect")
    val size = effect.number("size")
    val trail = effect.number("trailMs")
    ensure(size in 6..40 && trail in 0..1200, "invalid_effect")
    return LaserEffect(effect.text("color", 7), size.toInt(), trail.toInt(), effect.text("smoothing", 8))
        .also { ensure(it.isValid(), "invalid_effect") }
}

internal fun JsonObject.display(): DisplayInfo? {
    val value = this["display"] ?: return null
    if (value == JsonNull) return null
    val display = value as? JsonObject ?: throw ProtocolFailure("invalid_display")
    val width = display.number("width"); val height = display.number("height")
    ensure(width in 1..65536 && height in 1..65536, "invalid_display")
    return DisplayInfo(width.toInt(), height.toInt())
}
