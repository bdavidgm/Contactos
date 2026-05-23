package com.bdavidgm.contactos

import android.app.Application
import com.bdavidgm.contactos.di.AppContainer
import com.bdavidgm.contactos.di.DefaultAppContainer

class ContactosApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}
