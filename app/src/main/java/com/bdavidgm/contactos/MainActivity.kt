package com.bdavidgm.contactos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bdavidgm.contactos.ui.ContactNavHost
import com.bdavidgm.contactos.ui.theme.ContactosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ContactosApplication

        setContent {
            ContactosTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ContactNavHost(appContainer = app.appContainer)
                }
            }
        }
    }
}
