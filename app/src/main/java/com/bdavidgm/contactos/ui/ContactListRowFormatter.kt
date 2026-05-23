package com.bdavidgm.contactos.ui

import com.bdavidgm.contactos.data.local.ContactEntity
import java.io.File

/**
 * Presentación de una fila de la lista (sin Compose): texto e iniciales.
 */
data class ContactListRowUi(
    val contactId: Long,
    val displayName: String,
    val initials: String,
    val photoPath: String?,
    val showPhoto: Boolean,
    val mobileDialCode: String,
    val mobilePhone: String,
    val landlineDialCode: String,
    val landlinePhone: String,
)

object ContactListRowFormatter {

    fun toRowUi(contact: ContactEntity): ContactListRowUi {
        val path = contact.photoPath
        val showPhoto = !path.isNullOrBlank() && File(path).exists()
        return ContactListRowUi(
            contactId = contact.id,
            displayName = displayName(contact),
            initials = contactInitials(contact),
            photoPath = if (showPhoto) path else null,
            showPhoto = showPhoto,
            mobileDialCode = contact.mobileDialCode,
            mobilePhone = contact.mobilePhone,
            landlineDialCode = contact.landlineDialCode,
            landlinePhone = contact.landlinePhone,
        )
    }

    fun displayName(c: ContactEntity): String {
        val full = "${c.firstName} ${c.lastName}".trim()
        if (full.isNotBlank()) return full
        if (c.company.isNotBlank()) return c.company
        if (c.mobilePhone.isNotBlank()) return c.mobilePhone
        if (c.landlinePhone.isNotBlank()) return c.landlinePhone
        return "Sin nombre"
    }

    fun contactInitials(c: ContactEntity): String {
        val n = c.firstName.trim().firstOrNull()?.uppercaseChar()
        val a = c.lastName.trim().firstOrNull()?.uppercaseChar()
        return buildString {
            if (n != null) append(n)
            if (a != null) append(a)
            if (isEmpty()) {
                val label = displayName(c).trim()
                val letters = label.filter { it.isLetter() }
                when {
                    letters.isEmpty() -> append('?')
                    letters.length >= 2 -> {
                        append(letters[0].uppercaseChar())
                        append(letters[1].uppercaseChar())
                    }
                    else -> append(letters[0].uppercaseChar())
                }
            }
        }.take(2)
    }
}
