package com.bdavidgm.contactos.data.repo

import android.content.Context
import android.net.Uri
import com.bdavidgm.contactos.data.local.ContactDao
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.data.vcf.VcfContact
import com.bdavidgm.contactos.data.vcf.VcfExporter
import com.bdavidgm.contactos.data.vcf.VcfParser
import com.bdavidgm.contactos.phone.PhoneCountries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

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

    suspend fun importFromVcf(input: InputStream): Int = withContext(Dispatchers.IO) {
        val text = input.bufferedReader().use { it.readText() }
        val parsed = VcfParser.parse(text)
        var count = 0
        for (vc in parsed) {
            val entity = vc.toEntity()
            val id = dao.insertContactWithFields(entity, vc.customFields)
            savePhotoFromVcf(vc, id)
            count++
        }
        count
    }

    private suspend fun savePhotoFromVcf(vc: VcfContact, contactId: Long) {
        val b64 = vc.photoBase64 ?: return
        val bytes = VcfParser.decodePhotoBase64(b64) ?: return
        val ext = when (vc.photoType?.uppercase()) {
            "PNG" -> "png"
            else -> "jpg"
        }
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
        VcfExporter.exportContacts(all, map)
    }

    private fun copyPhotoToInternal(uri: Uri, contactId: Long): String? {
        return runCatching {
            val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
            val dest = File(dir, "contact_$contactId.jpg")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        }.getOrNull()
    }

    private fun VcfContact.toEntity() = ContactEntity(
        firstName = firstName,
        lastName = lastName,
        company = company,
        mobileDialCode = PhoneCountries.DEFAULT_DIAL_CODE,
        mobilePhone = mobilePhone,
        landlinePhone = landlinePhone,
        notes = notes,
        email = email,
        birthday = birthday,
        address = address,
        photoPath = null,
    )

    /** Copia un asset o fichero de ejemplo empaquetado (opcional). */
    fun newTempImportFileName(): String = "contactos_import_${UUID.randomUUID()}.vcf"
}
