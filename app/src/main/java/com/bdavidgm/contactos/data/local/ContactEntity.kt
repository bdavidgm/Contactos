package com.bdavidgm.contactos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val mobilePhone: String = "",
    val landlinePhone: String = "",
    val notes: String = "",
    val email: String = "",
    val birthday: String = "",
    val address: String = "",
    /** Ruta en almacenamiento interno de la app (no URI externa). */
    val photoPath: String? = null,
)
