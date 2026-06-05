package com.bdavidgm.contactos.data.vcf

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale

object VcfParser {

    fun parse(content: String): List<VcfContact> {
        val normalized = content.replace("\r\n", "\n")
        val blocks = normalized.split(Regex("(?i)BEGIN:VCARD")).mapNotNull { block ->
            val trimmed = block.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val endIdx = trimmed.indexOf("END:VCARD", ignoreCase = true)
            if (endIdx < 0) return@mapNotNull null
            trimmed.substring(0, endIdx).trim()
        }
        return blocks.mapNotNull { parseCard(it) }
    }

    /** Plegado vCard (línea de continuación que empieza por espacio o tab). Debe aplicarse tras unir soft breaks QP. */
    private fun unfoldVCardFoldedLines(lines: List<String>): List<String> {
        val out = mutableListOf<StringBuilder>()
        for (line in lines) {
            if (line.isEmpty()) continue
            if (out.isNotEmpty() && (line[0] == ' ' || line[0] == '\t')) {
                out.last().append(line, 1, line.length)
            } else {
                out.add(StringBuilder(line))
            }
        }
        return out.map { it.toString() }
    }

    /**
     * Une líneas físicas cuando vCard 2.1 usa quoted-printable con soft break (`=` al final de línea).
     * Evita mezclar con [PHOTO;ENCODING=BASE64] (no lleva QUOTED-PRINTABLE).
     */
    internal fun mergeQuotedPrintableSoftBreakLines(lines: List<String>): List<String> {
        val normalized = lines.map { it.trimEnd('\r') }
        val out = mutableListOf<String>()
        var i = 0
        while (i < normalized.size) {
            var line = normalized[i]
            if (line.isBlank()) {
                i++
                continue
            }
            i++
            val colon = line.indexOf(':')
            val left = if (colon >= 0) line.substring(0, colon) else ""
            val isQp = isQuotedPrintableProperty(left)
            while (isQp && line.endsWith('=') && i < normalized.size) {
                var next = normalized[i]
                i++
                if (next.isBlank()) continue
                next = next.trimStart(' ', '\t')
                line = line.dropLast(1) + next
            }
            out.add(line)
        }
        return out
    }

    private fun isQuotedPrintableProperty(propertyLeft: String): Boolean {
        val u = propertyLeft.uppercase(Locale.US)
        return u.contains("ENCODING=QUOTED-PRINTABLE") ||
            Regex("ENCODING=QP\\b", RegexOption.IGNORE_CASE).containsMatchIn(propertyLeft)
    }

    private fun parseCard(block: String): VcfContact? {
        val physical = block.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        val qpMerged = mergeQuotedPrintableSoftBreakLines(physical)
        val lines = unfoldVCardFoldedLines(qpMerged).map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var fn: String? = null
        var nFamily = ""
        var nGiven = ""
        var nMiddle = ""
        var org: String? = null
        var note: String? = null
        var email: String? = null
        var url: String? = null
        var bday: String? = null
        val adrParts = mutableListOf<String>()
        var mobile: String? = null
        var landline: String? = null
        var socialFacebook: String? = null
        var socialInstagram: String? = null
        var socialTelegram: String? = null
        var socialX: String? = null
        var socialDiscord: String? = null
        var socialLinkedIn: String? = null
        val custom = mutableListOf<Pair<String, String>>()
        var photoB64: String? = null
        var photoType: String? = null
        var sidecarPhotoPath: String? = null

        for (raw in lines) {
            val (nameUpper, params, value) = splitProperty(raw) ?: continue
            when (nameUpper) {
                "VERSION" -> Unit
                "FN" -> fn = value
                "N" -> {
                    val segs = value.split(";")
                    nFamily = segs.getOrElse(0) { "" }.trim()
                    nGiven = segs.getOrElse(1) { "" }.trim()
                    nMiddle = segs.getOrElse(2) { "" }.trim()
                }
                "ORG" -> org = value.replace("\\;", ";").trim()
                "NOTE" -> note = (note?.let { "$it\n" } ?: "") + value.replace("\\n", "\n")
                "EMAIL" -> if (email.isNullOrBlank()) email = value.trim()
                "URL" -> if (url.isNullOrBlank()) url = value.trim()
                "X-FACEBOOK" -> if (socialFacebook.isNullOrBlank()) socialFacebook = value.trim()
                "X-INSTAGRAM" -> if (socialInstagram.isNullOrBlank()) socialInstagram = value.trim()
                "X-TELEGRAM" -> if (socialTelegram.isNullOrBlank()) socialTelegram = value.trim()
                "X-TWITTER", "X-X" -> if (socialX.isNullOrBlank()) socialX = value.trim()
                "X-DISCORD" -> if (socialDiscord.isNullOrBlank()) socialDiscord = value.trim()
                "X-LINKEDIN" -> if (socialLinkedIn.isNullOrBlank()) socialLinkedIn = value.trim()
                "X-CONTACTOS-PHOTO" -> {
                    val p = value.trim().replace('\\', '/').trimStart('/')
                    if (p.isNotBlank() && sidecarPhotoPath.isNullOrBlank()) sidecarPhotoPath = p
                }
                "BDAY" -> if (bday.isNullOrBlank()) bday = value.trim()
                "ADR" -> adrParts += formatAdr(value)
                "TEL" -> {
                    val p = params.joinToString(";").uppercase(Locale.US)
                    when {
                        p.contains("CELL") && mobile.isNullOrBlank() -> mobile = value.trim()
                        p.contains("HOME") && landline.isNullOrBlank() -> landline = value.trim()
                        p.contains("VOICE") && !p.contains("FAX") && mobile.isNullOrBlank() && landline.isNullOrBlank() ->
                            mobile = value.trim()
                        mobile.isNullOrBlank() && landline.isNullOrBlank() -> mobile = value.trim()
                    }
                }
                "PHOTO" -> {
                    val encoding = params.firstOrNull { it.startsWith("ENCODING=", true) }
                        ?.substringAfter("=", "")
                        ?.uppercase(Locale.US)
                    val type = params.firstOrNull { it.startsWith("TYPE=", true) }
                        ?.substringAfter("=", "")
                    val valueUri = params.any { it.equals("VALUE=URI", true) || it.startsWith("VALUE=URI:", true) }
                    if (valueUri && value.startsWith("http", ignoreCase = true)) {
                        // Fotos solo por URL: no descargamos aquí.
                    } else if (value.startsWith("data:", ignoreCase = true)) {
                        val comma = value.indexOf(',')
                        if (comma > 0) {
                            val meta = value.substring(0, comma).lowercase(Locale.US)
                            val b64 = value.substring(comma + 1).replace("\n", "").replace("\r", "").trim()
                            if ("base64" in meta && photoB64.isNullOrBlank()) {
                                photoB64 = b64
                                photoType = when {
                                    "image/png" in meta -> "PNG"
                                    "image/jpeg" in meta || "image/jpg" in meta -> "JPEG"
                                    else -> type
                                }
                            }
                        }
                    } else if (encoding == "B" || encoding == "BASE64") {
                        if (photoB64.isNullOrBlank()) {
                            photoB64 = value.replace("\n", "").replace("\r", "")
                            photoType = type
                        }
                    } else if (photoB64.isNullOrBlank() && looksLikeRawBase64Photo(value)) {
                        photoB64 = value.replace("\n", "").replace("\r", "").trim()
                        photoType = type
                    }
                }
                else -> {
                    if (nameUpper.startsWith("X-") || nameUpper.startsWith("ITEM")) {
                        val label = nameUpper.removePrefix("X-").replace("_", " ").trim()
                        if (label.isNotBlank()) {
                            custom += label to value.trim()
                        }
                    }
                }
            }
        }

        val (first, last) = mapNames(fn, nFamily, nGiven, nMiddle)
        return VcfContact(
            firstName = first,
            lastName = last,
            company = org.orEmpty(),
            mobilePhone = mobile.orEmpty(),
            landlinePhone = landline.orEmpty(),
            notes = note.orEmpty(),
            email = email.orEmpty(),
            url = url.orEmpty(),
            socialFacebook = socialFacebook.orEmpty(),
            socialInstagram = socialInstagram.orEmpty(),
            socialTelegram = socialTelegram.orEmpty(),
            socialX = socialX.orEmpty(),
            socialDiscord = socialDiscord.orEmpty(),
            socialLinkedIn = socialLinkedIn.orEmpty(),
            birthday = bday.orEmpty(),
            address = adrParts.joinToString("\n").trim(),
            photoBase64 = photoB64,
            photoType = photoType,
            sidecarPhotoPath = sidecarPhotoPath,
            customFields = custom,
        )
    }

    /** Heurística para vCards con PHOTO sin ENCODING explícito pero valor en Base64. */
    private fun looksLikeRawBase64Photo(value: String): Boolean {
        val cleaned = value.replace("\n", "").replace("\r", "").trim()
        if (cleaned.length < 120) return false
        if (':' in cleaned) return false
        for (ch in cleaned) {
            when {
                ch.isLetterOrDigit() -> {}
                ch == '+' || ch == '/' || ch == '=' -> {}
                ch.isWhitespace() -> {}
                else -> return false
            }
        }
        return true
    }

    private fun formatAdr(value: String): String {
        val p = value.split(";").map { it.replace("\\;", ";").trim() }
        // ADR:;;Street;City;Region;Zip;Country
        val street = p.getOrNull(2).orEmpty()
        val city = p.getOrNull(3).orEmpty()
        val region = p.getOrNull(4).orEmpty()
        val zip = p.getOrNull(5).orEmpty()
        val country = p.getOrNull(6).orEmpty()
        return listOf(street, listOf(zip, city, region).filter { it.isNotBlank() }.joinToString(" "), country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private fun mapNames(fn: String?, family: String, given: String, middle: String): Pair<String, String> {
        val nombreFromN = listOf(given, middle).filter { it.isNotBlank() }.joinToString(" ").trim()
        val apellidos = family.trim()
        if (nombreFromN.isNotBlank() || apellidos.isNotBlank()) {
            return nombreFromN to apellidos
        }
        val f = fn?.trim().orEmpty()
        if (f.isBlank()) return "" to ""
        val parts = f.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size == 1) return parts[0] to ""
        return parts.dropLast(1).joinToString(" ") to parts.last()
    }

    private fun splitProperty(line: String): Triple<String, List<String>, String>? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        val left = line.substring(0, idx)
        val value = line.substring(idx + 1)
        val namePart = left.substringBefore(';', left)
        // Android / vCard 3: propiedades agrupadas "item1.N" → "N"
        val nameUpper = canonicalPropertyName(namePart)
        val params = if (left.contains(';')) {
            left.substringAfter(';').split(';').map { it.trim() }
        } else {
            emptyList()
        }
        val decodedValue = decodePropertyValue(nameUpper, params, value)
        return Triple(nameUpper, params, unescape(decodedValue))
    }

    /** Nombre de propiedad sin prefijo de grupo (p. ej. `item1.N` → `N`). */
    private fun canonicalPropertyName(namePart: String): String =
        namePart.trim().uppercase(Locale.US).substringAfterLast('.')

    private fun charsetParam(params: List<String>): String? =
        params.firstOrNull { it.startsWith("CHARSET=", true) }
            ?.substringAfter("=", "")
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }

    /**
     * Decodifica quoted-printable cuando viene declarado, o por heurística (=XX hex típico de Android)
     * en campos de texto como N/FN si falta ENCODING=QUOTED-PRINTABLE.
     */
    private fun decodePropertyValue(nameUpper: String, params: List<String>, value: String): String {
        val charsetName = charsetParam(params)
        return when {
            isQuotedPrintableParams(params) -> decodeQuotedPrintable(value, charsetName)
            nameUpper in QpHeuristicPropertyNames && valueLooksLikeQuotedPrintable(value) ->
                decodeQuotedPrintable(value, charsetName)
            else -> value
        }
    }

    private val QpHeuristicPropertyNames = setOf(
        "N", "FN", "ORG", "TITLE", "NOTE", "ADR", "EMAIL", "URL", "LABEL", "BDAY",
    )

    private fun isQuotedPrintableParams(params: List<String>): Boolean =
        params.any { p ->
            if (!p.startsWith("ENCODING=", true)) return@any false
            when (p.substringAfter("=", "").trim().uppercase(Locale.US)) {
                "QUOTED-PRINTABLE", "QP" -> true
                else -> false
            }
        }

    /**
     * Decodifica el cuerpo quoted-printable (RFC 2045) a texto usando [charsetName] o UTF-8.
     * Los soft line breaks deben haberse unido antes en [mergeQuotedPrintableSoftBreakLines].
     */
    internal fun decodeQuotedPrintable(encoded: String, charsetName: String?): String {
        val charset = charsetFromVcard(charsetName)
        val out = ByteArrayOutputStream(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '=' && i + 2 < encoded.length) {
                val hi = Character.digit(encoded[i + 1], 16)
                val lo = Character.digit(encoded[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    out.write((hi shl 4) or lo)
                    i += 3
                    continue
                }
            }
            if (c == '\r' || c == '\n') {
                i++
                continue
            }
            when {
                c.code < 128 -> out.write(c.code)
                else -> out.write(encoded.substring(i, i + 1).toByteArray(charset))
            }
            i++
        }
        return String(out.toByteArray(), charset)
    }

    private fun charsetFromVcard(charsetName: String?): Charset {
        val cleaned = charsetName?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: return Charsets.UTF_8
        return try {
            Charset.forName(cleaned)
        } catch (_: Throwable) {
            Charsets.UTF_8
        }
    }

    /** True si el valor parece cuerpo quoted-printable (=hexhex…), p. ej. export Android sin ENCODING explícito. */
    private fun valueLooksLikeQuotedPrintable(value: String): Boolean {
        val s = value.trim()
        if (s.length < 6 || !s.startsWith('=')) return false
        return Regex("=(?:[0-9A-Fa-f]{2})").findAll(s).count() >= 2
    }

    private fun unescape(v: String): String =
        v.replace("\\n", "\n").replace("\\,", ",").replace("\\\\", "\\")

    fun decodePhotoBase64(b64: String): ByteArray? =
        runCatching { Base64.decode(b64.trim(), Base64.DEFAULT) }.getOrNull()
}
