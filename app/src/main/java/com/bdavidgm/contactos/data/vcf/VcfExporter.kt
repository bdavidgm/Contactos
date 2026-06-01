package com.bdavidgm.contactos.data.vcf

import android.util.Base64
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.phone.buildMobileE164Digits
import java.io.File
import java.util.Locale
import kotlin.math.min

/** Tamaño máximo por defecto para incrustar una foto en Base64 dentro del VCF. */
const val DEFAULT_MAX_INLINE_PHOTO_BYTES: Long = 12L * 1024 * 1024

sealed class VcfPhotoExportStrategy {
    /** Incluir PHOTO;ENCODING=BASE64 si el fichero existe y no supera [maxBytes]. */
    data class InlineBase64(val maxBytes: Long = DEFAULT_MAX_INLINE_PHOTO_BYTES) : VcfPhotoExportStrategy()

    /**
     * Referencia [X-CONTACTOS-PHOTO] con ruta relativa y lista de archivos para empaquetar en ZIP.
     * Orden de [contacts] determina nombres `photos/p_NNNNN.ext`.
     */
    data object SidecarForZip : VcfPhotoExportStrategy()
}

object VcfExporter {

    /**
     * Exporta contactos a texto vCard.
     * @param photoStrategy inline Base64 (con plegado RFC 2425) o rutas sidecar para ZIP.
     */
    fun exportContacts(
        contacts: List<ContactEntity>,
        customByContactId: Map<Long, List<Pair<String, String>>>,
        photoStrategy: VcfPhotoExportStrategy = VcfPhotoExportStrategy.InlineBase64(),
    ): VcfExportResult {
        val sidecars = mutableListOf<Pair<String, ByteArray>>()
        val vcf = buildString {
            contacts.forEachIndexed { index, c ->
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
                    val e164Home = buildMobileE164Digits(c.landlineDialCode, c.landlinePhone)
                    val home = e164Home?.let { "+$it" } ?: c.landlinePhone
                    appendLine("TEL;HOME:${escapeText(home)}")
                }
                if (c.email.isNotBlank()) {
                    appendLine("EMAIL;INTERNET:${escapeText(c.email)}")
                }
                if (c.url.isNotBlank()) {
                    appendLine("URL:${escapeText(c.url)}")
                }
                if (c.socialFacebook.isNotBlank()) {
                    appendLine("X-FACEBOOK:${escapeText(c.socialFacebook)}")
                }
                if (c.socialInstagram.isNotBlank()) {
                    appendLine("X-INSTAGRAM:${escapeText(c.socialInstagram)}")
                }
                if (c.socialTelegram.isNotBlank()) {
                    appendLine("X-TELEGRAM:${escapeText(c.socialTelegram)}")
                }
                if (c.socialX.isNotBlank()) {
                    appendLine("X-TWITTER:${escapeText(c.socialX)}")
                }
                if (c.socialDiscord.isNotBlank()) {
                    appendLine("X-DISCORD:${escapeText(c.socialDiscord)}")
                }
                if (c.socialLinkedIn.isNotBlank()) {
                    appendLine("X-LINKEDIN:${escapeText(c.socialLinkedIn)}")
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
                when (photoStrategy) {
                    is VcfPhotoExportStrategy.InlineBase64 -> {
                        appendPhotoInline(this, c, photoStrategy.maxBytes)
                    }
                    VcfPhotoExportStrategy.SidecarForZip -> {
                        appendPhotoSidecar(this, c, index, sidecars)
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
        return VcfExportResult(vcfText = vcf, sidecarPhotos = sidecars)
    }

    private fun appendPhotoSidecar(
        sb: StringBuilder,
        c: ContactEntity,
        index: Int,
        sidecars: MutableList<Pair<String, ByteArray>>,
    ) {
        val path = c.photoPath ?: return
        val file = File(path)
        if (!file.exists() || !file.isFile) return
        val ext = when {
            path.endsWith(".png", true) -> "png"
            else -> "jpg"
        }
        val zipPath = "photos/p_${index.toString().padStart(5, '0')}.$ext"
        runCatching { file.readBytes() }.getOrNull()?.let { bytes ->
            sidecars += zipPath to bytes
            sb.appendLine("X-CONTACTOS-PHOTO:${escapeText(zipPath)}")
        }
    }

    private fun appendPhotoInline(sb: StringBuilder, c: ContactEntity, maxBytes: Long) {
        val path = c.photoPath ?: return
        val file = File(path)
        if (!file.exists() || !file.isFile) return
        val len = file.length()
        if (len > maxBytes) return
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return
        val type = when {
            path.endsWith(".png", true) -> "PNG"
            else -> "JPEG"
        }
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val logical = "PHOTO;ENCODING=BASE64;TYPE=$type:$b64"
        sb.append(foldVCardLogicalLine(logical))
        sb.appendLine()
    }

    /**
     * Plegado tipo vCard 2.1: líneas de como mucho 75 octetos; continuación con un espacio al inicio.
     */
    internal fun foldVCardLogicalLine(logicalLine: String): String {
        val max = 75
        if (logicalLine.length <= max) return logicalLine
        val sb = StringBuilder(logicalLine.length + logicalLine.length / max)
        var i = 0
        while (i < logicalLine.length) {
            val end = min(i + max, logicalLine.length)
            if (i > 0) sb.append("\n ")
            sb.append(logicalLine, i, end)
            i = end
        }
        return sb.toString()
    }

    private fun escapeText(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,")

    private fun escapeSemi(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;")
}
