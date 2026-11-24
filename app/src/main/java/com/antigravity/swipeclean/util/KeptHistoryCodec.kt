package com.antigravity.swipeclean.util

data class KeptBackupPayload(
    val version: Int = CURRENT_VERSION,
    val updatedAtMs: Long = 0L,
    val items: List<KeptMediaRecord> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

object KeptHistoryCodec {

    fun encode(payload: KeptBackupPayload): String {
        val sb = StringBuilder(256 + payload.items.size * 112)
        sb.append("{\"version\":").append(payload.version)
        sb.append(",\"updatedAtMs\":").append(payload.updatedAtMs)
        sb.append(",\"items\":[")
        payload.items.forEachIndexed { index, record ->
            if (index > 0) sb.append(',')
            sb.append("{\"fp\":")
            appendJsonString(sb, record.fingerprint)
            sb.append(",\"name\":")
            appendJsonString(sb, record.displayName)
            sb.append(",\"video\":").append(record.isVideo)
            sb.append(",\"size\":").append(record.sizeBytes)
            sb.append(",\"at\":").append(record.keptAtMs)
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decode(raw: String): KeptBackupPayload? {
        if (raw.isBlank()) return null
        return runCatching {
            val root = Parser(raw).parseValue() as? Map<*, *> ?: return null
            val version = (root["version"] as? Number)?.toInt() ?: 1
            val updatedAtMs = (root["updatedAtMs"] as? Number)?.toLong() ?: 0L
            val rawItems = root["items"] as? List<*> ?: emptyList<Any>()
            val dedup = LinkedHashMap<String, KeptMediaRecord>(rawItems.size)
            for (entry in rawItems) {
                val obj = entry as? Map<*, *> ?: continue
                val fp = (obj["fp"] as? String)?.trim().orEmpty()
                if (fp.isEmpty()) continue
                val name = (obj["name"] as? String).orEmpty()
                val isVideo = obj["video"] as? Boolean ?: false
                val size = (obj["size"] as? Number)?.toLong() ?: 0L
                val keptAt = (obj["at"] as? Number)?.toLong() ?: 0L
                val existing = dedup[fp]
                if (existing == null || keptAt > existing.keptAtMs) {
                    dedup[fp] = KeptMediaRecord(
                        fingerprint = fp,
                        displayName = name,
                        isVideo = isVideo,
                        sizeBytes = maxOf(0L, size),
                        keptAtMs = maxOf(0L, keptAt)
                    )
                }
            }
            KeptBackupPayload(
                version = version,
                updatedAtMs = maxOf(0L, updatedAtMs),
                items = dedup.values.toList()
            )
        }.getOrNull()
    }

    private fun appendJsonString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u")
                        val hex = ch.code.toString(16).padStart(4, '0')
                        sb.append(hex)
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected char '$c' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (peek(',')) {
                    pos++
                } else if (peek('}')) {
                    pos++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek(']')) {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                if (peek(',')) {
                    pos++
                } else if (peek(']')) {
                    pos++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
            return list
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw IllegalArgumentException("Invalid escape '\\$esc'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("Invalid boolean at $pos")
            }
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalArgumentException("Invalid null at $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
            var isDouble = false
            if (pos < src.length && src[pos] == '.') {
                isDouble = true
                pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val token = src.substring(start, pos)
            return if (isDouble) token.toDouble() else token.toLong()
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun expect(ch: Char) {
            if (pos >= src.length || src[pos] != ch) {
                throw IllegalArgumentException("Expected '$ch' at $pos")
            }
            pos++
        }

        private fun peek(ch: Char): Boolean = pos < src.length && src[pos] == ch
    }
}
