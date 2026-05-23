package com.bdavidgm.contactos.di

import android.content.Context
import com.bdavidgm.contactos.data.local.ContactDatabase
import com.bdavidgm.contactos.data.repo.ContactRepository

/**
 * Punto único de construcción de dependencias de la app (sin framework DI).
 */
interface AppContainer {
    val contactRepository: ContactRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    private val database by lazy {
        ContactDatabase.build(appContext)
    }

    override val contactRepository: ContactRepository by lazy {
        ContactRepository(appContext, database.contactDao())
    }
}
