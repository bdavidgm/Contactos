package com.bdavidgm.contactos.data.repo

import android.content.Context
import android.net.Uri
import com.bdavidgm.contactos.data.local.ContactDao
import com.bdavidgm.contactos.data.photo.ContactPhotoCompressor
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.data.vcf.VcfContact
import com.bdavidgm.contactos.data.vcf.VcfExporter
import com.bdavidgm.contactos.data.vcf.VcfParser
import com.bdavidgm.contactos.data.vcf.VcfPhotoExportStrategy
import com.bdavidgm.contactos.phone.PhoneCountries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ContactRepository(
    private val appContext: Context,
    private val dao: ContactDao,
) {

    fun observeContacts(search: String): Flow<List<ContactEntity>> =
        dao.observeContacts(search.trim())

    suspend fun getContact(id: Long): ContactEntity? = dao.getContact(id)

    suspend fun getCustomFields(id: Long): List<Pair<String, String>> =
        dao.getCustomFields(id).map { it.label to it.value }

    suspend fun saveContact(
        contact: ContactEntity,
        customFields: List<Pair<String, String>>,
        pendingPhotoUri: Uri?,
    ): Long = withContext(Dispatchers.IO) {
        val id = if (contact.id == 0L) {
            dao.insertContactWithFields(contact, customFields)
        } else {
            dao.updateContactWithFields(contact, customFields)
            contact.id
        }
        if (pendingPhotoUri != null) {
            val path = copyPhotoToInternal(pendingPhotoUri, id)
            if (path != null) {
                val current = dao.getContact(id) ?: return@withContext id
                dao.insertContact(current.copy(photoPath = path))
            }
        }
        id
    }

    suspend fun deleteContact(id: Long) {
        withContext(Dispatchers.IO) {
            dao.getContact(id)?.photoPath?.let { File(it).delete() }
            dao.deleteContact(id)
        }
    }

    /**
     * Importa desde un [Uri]: VCF (texto, con fotos en Base64) o ZIP con `contactos.vcf` + carpeta `photos/`.
     */
    suspend fun importContacts(uri: Uri): Int = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { importContactsFromStreamImpl(it) } ?: 0
    }

    suspend fun importContactsFromStream(input: InputStream): Int = withContext(Dispatchers.IO) {
        importContactsFromStreamImpl(input)
    }

    suspend fun importFromVcf(input: InputStream): Int = importContactsFromStream(input)

    private suspend fun importContactsFromStreamImpl(input: InputStream): Int {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(256 * 1024)
        val header = ByteArray(4)
        val read = buffered.read(header, 0, 4)
        buffered.reset()
        return if (read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            importFromZipStream(buffered)
        } else {
            val text = buffered.bufferedReader(Charsets.UTF_8).use { it.readText() }
            importParsedWithSidecars(text, null)
        }
    }

    private suspend fun importFromZipStream(input: InputStream): Int {
        val entries = readZipEntries(input)
        val vcfText = pickVcfTextFromZip(entries) ?: return 0
        val lookup = buildZipPhotoLookup(entries)
        return importParsedWithSidecars(vcfText, lookup)
    }

    private suspend fun importParsedWithSidecars(
        vcfText: String,
        zipPhotoLookup: Map<String, ByteArray>?,
    ): Int {
        val parsed = VcfParser.parse(vcfText)
        var count = 0
        for (vc in parsed) {
            val entity = vc.toEntity()
            val id = dao.insertContactWithFields(entity, vc.customFields)
            savePhotoFromImport(vc, id, zipPhotoLookup)
            count++
        }
        return count
    }

    private suspend fun savePhotoFromImport(
        vc: VcfContact,
        contactId: Long,
        zipLookup: Map<String, ByteArray>?,
    ) {
        val side = vc.sidecarPhotoPath?.trim()?.replace('\\', '/')?.trimStart('/').orEmpty()
        if (side.isNotBlank() && zipLookup != null) {
            val bytes = resolveZipPhotoBytes(side, zipLookup)
            if (bytes != null) {
                writeContactPhotoFile(contactId, bytes, isPng = side.endsWith(".png", true))
                return
            }
        }
        val b64 = vc.photoBase64 ?: return
        val bytes = VcfParser.decodePhotoBase64(b64) ?: return
        val isPng = vc.photoType?.uppercase() == "PNG" ||
            vc.photoType?.contains("PNG", ignoreCase = true) == true
        writeContactPhotoFile(contactId, bytes, isPng = isPng)
    }

    private suspend fun writeContactPhotoFile(contactId: Long, bytes: ByteArray, isPng: Boolean) {
        val ext = if (isPng) "png" else "jpg"
        val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "contact_$contactId.$ext")
        file.writeBytes(bytes)
        val current = dao.getContact(contactId) ?: return
        dao.insertContact(current.copy(photoPath = file.absolutePath))
    }

    suspend fun exportAllToVcfString(): String = withContext(Dispatchers.IO) {
        val all = dao.getAllContacts()
        val map = all.associate { c ->
            c.id to dao.getCustomFields(c.id).map { it.label to it.value }
        }
        VcfExporter.exportContacts(all, map, VcfPhotoExportStrategy.InlineBase64()).vcfText
    }

    /** ZIP con `contactos.vcf` y archivos referenciados por `X-CONTACTOS-PHOTO` (carpeta `photos/`). */
    suspend fun exportAllToZipBytes(): ByteArray = withContext(Dispatchers.IO) {
        val all = dao.getAllContacts()
        val map = all.associate { c ->
            c.id to dao.getCustomFields(c.id).map { it.label to it.value }
        }
        val result = VcfExporter.exportContacts(all, map, VcfPhotoExportStrategy.SidecarForZip)
        ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("contactos.vcf"))
                zos.write(result.vcfText.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                for ((path, bytes) in result.sidecarPhotos) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        }
    }

    private fun copyPhotoToInternal(uri: Uri, contactId: Long): String? {
        return runCatching {
            val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
            val dest = File(dir, "contact_$contactId.jpg")
            val compressed = ContactPhotoCompressor.compressPickedPhotoToJpegMaxBytes(appContext, uri)
            if (compressed != null) {
                dest.writeBytes(compressed)
            } else {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
            }
            dest.absolutePath
        }.getOrNull()
    }

    private fun VcfContact.toEntity() = ContactEntity(
        firstName = firstName,
        lastName = lastName,
        company = company,
        mobileDialCode = PhoneCountries.DEFAULT_DIAL_CODE,
        mobilePhone = mobilePhone,
        landlineDialCode = PhoneCountries.DEFAULT_DIAL_CODE,
        landlinePhone = landlinePhone,
        notes = notes,
        email = email,
        url = url,
        socialFacebook = socialFacebook,
        socialInstagram = socialInstagram,
        socialTelegram = socialTelegram,
        socialX = socialX,
        socialDiscord = socialDiscord,
        socialLinkedIn = socialLinkedIn,
        birthday = birthday,
        address = address,
        photoPath = null,
    )

    /** Copia un asset o fichero de ejemplo empaquetado (opcional). */
    fun newTempImportFileName(): String = "contactos_import_${UUID.randomUUID()}.vcf"
}

private fun normalizeZipPath(name: String): String =
    name.replace('\\', '/').trimStart('/')

private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
    val map = linkedMapOf<String, ByteArray>()
    ZipInputStream(input.buffered()).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            if (entry.isDirectory) {
                zis.closeEntry()
                continue
            }
            val norm = normalizeZipPath(entry.name)
            map[norm] = zis.readBytes()
            zis.closeEntry()
        }
    }
    return map
}

private fun pickVcfTextFromZip(entries: Map<String, ByteArray>): String? {
    val vcfKeys = entries.keys.filter { it.endsWith(".vcf", ignoreCase = true) }
    if (vcfKeys.isEmpty()) return null
    val preferredName = vcfKeys.firstOrNull { key ->
        key.equals("contactos.vcf", ignoreCase = true) ||
            key.endsWith("/contactos.vcf", ignoreCase = true)
    } ?: vcfKeys.firstOrNull { key ->
        key.equals("contacts.vcf", ignoreCase = true) ||
            key.endsWith("/contacts.vcf", ignoreCase = true)
    } ?: vcfKeys.minByOrNull { it.lowercase() }
    return preferredName?.let { entries[it]?.toString(Charsets.UTF_8) }
}

private fun buildZipPhotoLookup(raw: Map<String, ByteArray>): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    for ((k, v) in raw) {
        if (k.endsWith(".vcf", ignoreCase = true)) continue
        val norm = normalizeZipPath(k)
        out[norm] = v
        val base = norm.substringAfterLast('/')
        if (base.isNotBlank()) out[base] = v
    }
    return out
}

private fun resolveZipPhotoBytes(side: String, zipLookup: Map<String, ByteArray>): ByteArray? {
    val s = normalizeZipPath(side)
    zipLookup[s]?.let { return it }
    zipLookup[side.trimStart('/')]?.let { return it }
    val base = s.substringAfterLast('/')
    return zipLookup[base]
}
