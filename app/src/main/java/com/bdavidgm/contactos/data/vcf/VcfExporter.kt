package com.bdavidgm.contactos.data.vcf

import android.util.Base64
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.phone.buildMobileE164Digits
import java.io.File
import java.util.Locale

object VcfExporter {

    fun exportContacts(
        contacts: List<ContactEntity>,
        customByContactId: Map<Long, List<Pair<String, String>>>,
    ): String = buildString {
        for (c in contacts) {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:2.1")
            val family = escapeSemi(c.lastName)
            val given = escapeSemi(c.firstName)
            appendLine("N:$family;$given;;;")
            val fn = "${c.firstName} ${c.lastName}".trim().ifBlank { c.company.ifBlank { c.mobilePhone } }
            appendLine("FN:${escapeText(fn)}")
            if (c.company.isNotBlank()) {
                appendLine("ORG:${escapeSemi(c.company)}")
            }
            if (c.mobilePhone.isNotBlank()) {
                val e164 = buildMobileE164Digits(c.mobileDialCode, c.mobilePhone)
                val cell = e164?.let { "+$it" } ?: c.mobilePhone
                appendLine("TEL;CELL;PREF:${escapeText(cell)}")
            }
            if (c.landlinePhone.isNotBlank()) {
                appendLine("TEL;HOME:${escapeText(c.landlinePhone)}")
            }
            if (c.email.isNotBlank()) {
                appendLine("EMAIL;INTERNET:${escapeText(c.email)}")
            }
            if (c.address.isNotBlank()) {
                appendLine("ADR;HOME:;;${escapeSemi(c.address)};;;;")
            }
            if (c.birthday.isNotBlank()) {
                appendLine("BDAY:${escapeText(c.birthday)}")
            }
            if (c.notes.isNotBlank()) {
                appendLine("NOTE:${escapeText(c.notes.replace("\n", "\\n"))}")
            }
            c.photoPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.length() < 900_000) {
                    val bytes = file.readBytes()
                    val type = when {
                        path.endsWith(".png", true) -> "PNG"
                        else -> "JPEG"
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    appendLine("PHOTO;ENCODING=BASE64;TYPE=$type:$b64")
                }
            }
            val customs = customByContactId[c.id].orEmpty()
            for ((label, value) in customs) {
                if (label.isBlank() && value.isBlank()) continue
                val key = "X-" + label.uppercase(Locale.getDefault()).replace(' ', '_')
                appendLine("$key:${escapeText(value)}")
            }
            appendLine("END:VCARD")
            appendLine()
        }
    }

    private fun escapeText(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,")

    private fun escapeSemi(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;")
}
