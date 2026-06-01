package com.bdavidgm.contactos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query(
        """
        SELECT * FROM contacts
        WHERE (:query = '')
           OR LOWER(firstName || ' ' || lastName) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(lastName || ' ' || firstName) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(company) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(mobilePhone) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(landlinePhone) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(email) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(url) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(socialFacebook) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(socialInstagram) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(socialTelegram) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(socialX) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(socialDiscord) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(socialLinkedIn) LIKE '%' || LOWER(:query) || '%'
        ORDER BY LOWER(lastName), LOWER(firstName)
        """,
    )
    fun observeContacts(query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY LOWER(lastName), LOWER(firstName)")
    suspend fun getAllContacts(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContact(id: Long): ContactEntity?

    @Query("SELECT * FROM contact_custom_fields WHERE contactId = :contactId ORDER BY id")
    suspend fun getCustomFields(contactId: Long): List<CustomFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFields(fields: List<CustomFieldEntity>): List<Long>

    @Query("DELETE FROM contact_custom_fields WHERE contactId = :contactId")
    suspend fun deleteCustomFieldsForContact(contactId: Long): Int

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: Long): Int

    @Transaction
    suspend fun replaceCustomFields(contactId: Long, fields: List<CustomFieldEntity>) {
        deleteCustomFieldsForContact(contactId)
        if (fields.isNotEmpty()) {
            insertCustomFields(fields)
        }
    }

    @Transaction
    suspend fun insertContactWithFields(
        contact: ContactEntity,
        fields: List<Pair<String, String>>,
    ): Long {
        val id = insertContact(contact)
        val entities = fields
            .map { (label, value) -> CustomFieldEntity(contactId = id, label = label, value = value) }
            .filter { it.label.isNotBlank() || it.value.isNotBlank() }
        if (entities.isNotEmpty()) insertCustomFields(entities)
        return id
    }

    @Transaction
    suspend fun updateContactWithFields(
        contact: ContactEntity,
        fields: List<Pair<String, String>>,
    ) {
        insertContact(contact)
        replaceCustomFields(
            contact.id,
            fields.map { (label, value) ->
                CustomFieldEntity(contactId = contact.id, label = label, value = value)
            }.filter { it.label.isNotBlank() || it.value.isNotBlank() },
        )
    }
}
