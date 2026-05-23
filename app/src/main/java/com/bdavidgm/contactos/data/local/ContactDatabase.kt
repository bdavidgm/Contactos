package com.bdavidgm.contactos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ContactEntity::class, CustomFieldEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ContactDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    companion object {
        fun build(context: Context): ContactDatabase =
            Room.databaseBuilder(context, ContactDatabase::class.java, "contactos.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
