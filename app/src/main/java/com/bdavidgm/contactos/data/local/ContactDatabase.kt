package com.bdavidgm.contactos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ContactEntity::class, CustomFieldEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class ContactDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE contacts ADD COLUMN mobileDialCode TEXT NOT NULL DEFAULT '53'",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE contacts ADD COLUMN landlineDialCode TEXT NOT NULL DEFAULT '53'",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE contacts ADD COLUMN url TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN socialFacebook TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contacts ADD COLUMN socialInstagram TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contacts ADD COLUMN socialTelegram TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contacts ADD COLUMN socialX TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contacts ADD COLUMN socialDiscord TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contacts ADD COLUMN socialLinkedIn TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): ContactDatabase =
            Room.databaseBuilder(context, ContactDatabase::class.java, "contactos.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
