package com.bdavidgm.contactos.data.vcf

import android.util.Base64
import java.util.Locale

object VcfParser {

    fun parse(content: String): List<VcfContact> {
        val unfolded = unfold(content)
        val blocks = unfolded.split(Regex("(?i)BEGIN:VCARD")).mapNotNull { block ->
            val trimmed = block.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val endIdx = trimmed.indexOf("END:VCARD", ignoreCase = true)
            if (endIdx < 0) return@mapNotNull null
            trimmed.substring(0, endIdx).trim()
        }
        return blocks.mapNotNull { parseCard(it) }
    }

    private fun unfold(text: String): String {
        return text.replace("\r\n", "\n")
            .replace(Regex("\n[ \t]"), "")
    }

    private fun parseCard(block: String): VcfContact? {
        val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var fn: String? = null
        var nFamily = ""
        var nGiven = ""
        var nMiddle = ""
        var org: String? = null
        var note: String? = null
        var email: String? = null
        var bday: String? = null
        val adrParts = mutableListOf<String>()
        var mobile: String? = null
        var landline: String? = null
        val custom = mutableListOf<Pair<String, String>>()
        var photoB64: String? = null
        var photoType: String? = null

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
                    if (encoding == "B" || encoding == "BASE64") {
                        photoB64 = value.replace("\n", "").replace("\r", "")
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
            birthday = bday.orEmpty(),
            address = adrParts.joinToString("\n").trim(),
            photoBase64 = photoB64,
            photoType = photoType,
            customFields = custom,
        )
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
        val nameUpper = namePart.uppercase(Locale.US)
        val params = if (left.contains(';')) {
            left.substringAfter(';').split(';').map { it.trim() }
        } else {
            emptyList()
        }
        return Triple(nameUpper, params, unescape(value))
    }

    private fun unescape(v: String): String =
        v.replace("\\n", "\n").replace("\\,", ",").replace("\\\\", "\\")

    fun decodePhotoBase64(b64: String): ByteArray? =
        runCatching { Base64.decode(b64.trim(), Base64.DEFAULT) }.getOrNull()
}
