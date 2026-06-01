package com.bdavidgm.contactos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    /** Código de país solo dígitos (p. ej. 53, 34), sin +. */
    val mobileDialCode: String = "53",
    /** Número móvil nacional (sin prefijo de país). */
    val mobilePhone: String = "",
    /** Código de país del fijo, solo dígitos (p. ej. 53), sin +. */
    val landlineDialCode: String = "53",
    /** Número fijo nacional (sin prefijo de país). */
    val landlinePhone: String = "",
    val notes: String = "",
    val email: String = "",
    /** Sitio web o enlace (texto libre). */
    val url: String = "",
    /** Perfil o URL (texto libre). */
    val socialFacebook: String = "",
    val socialInstagram: String = "",
    val socialTelegram: String = "",
    /** X (antes Twitter): @usuario, URL o texto libre. */
    val socialX: String = "",
    val socialDiscord: String = "",
    val socialLinkedIn: String = "",
    val birthday: String = "",
    val address: String = "",
    /** Ruta en almacenamiento interno de la app (no URI externa). */
    val photoPath: String? = null,
)
