package com.bdavidgm.contactos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bdavidgm.contactos.ContactosApplication
import com.bdavidgm.contactos.data.repo.ContactRepository
import com.bdavidgm.contactos.phone.buildMobileE164Digits
import com.bdavidgm.contactos.viewmodels.ContactListViewModel
import com.bdavidgm.contactos.viewmodels.ContactListViewModelFactory
import java.io.File

/** Fondo azul celeste para la barra superior de la lista principal. */
private val TopBarCeleste = Color(0xFF87CEEB)
private val TopBarOnCeleste = Color(0xFF0A3D5C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    repository: ContactRepository,
    onAddContact: () -> Unit,
    onEditContact: (Long) -> Unit,
) {
    val app = LocalContext.current.applicationContext as ContactosApplication
    val vm: ContactListViewModel = viewModel(
        factory = ContactListViewModelFactory(app, repository),
    )

    val search by vm.search.collectAsStateWithLifecycle()
    val contactRows by vm.contactRows.collectAsStateWithLifecycle()
    val menuOpen by vm.menuOpen.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard"),
        onResult = { uri -> vm.onExportDocumentCreated(uri) },
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> vm.onImportDocumentPicked(uri) },
    )

    LaunchedEffect(Unit) {
        vm.openExportDocumentPicker.collect {
            exportLauncher.launch("contactos_export.vcf")
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.consumeSnackbarMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contactos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TopBarCeleste,
                    scrolledContainerColor = TopBarCeleste,
                    titleContentColor = TopBarOnCeleste,
                    actionIconContentColor = TopBarOnCeleste,
                ),
                actions = {
                    IconButton(onClick = { vm.setMenuOpen(true) }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { vm.setMenuOpen(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Importar VCF") },
                            onClick = {
                                vm.setMenuOpen(false)
                                importLauncher.launch(arrayOf("text/*", "text/x-vcard", "text/vcard", "*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importar ejemplo (contacts.vcf)") },
                            onClick = {
                                vm.setMenuOpen(false)
                                vm.onImportSampleFromAssets()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar VCF") },
                            onClick = {
                                vm.setMenuOpen(false)
                                vm.onExportMenuClicked()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = vm::onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar contactos") },
                singleLine = true,
                shape = RoundedCornerShape(32.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(contactRows, key = { it.contactId }) { row ->
                    ContactRow(row = row, onClick = { onEditContact(row.contactId) })
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = onAddContact,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TopBarCeleste,
                        contentColor = TopBarOnCeleste,
                    ),
                ) {
                    Text("Agregar")
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    row: ContactListRowUi,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var rowMenuExpanded by remember(row.contactId) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (row.showPhoto && row.photoPath != null) {
                    AsyncImage(
                        model = File(row.photoPath),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TopBarCeleste.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = row.initials,
                            style = MaterialTheme.typography.titleMedium,
                            color = TopBarOnCeleste,
                        )
                    }
                }
            }
            Text(
                text = row.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Box {
            IconButton(
                onClick = { rowMenuExpanded = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Opciones del contacto",
                    tint = TopBarOnCeleste,
                )
            }
            DropdownMenu(
                expanded = rowMenuExpanded,
                onDismissRequest = { rowMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("SMS") },
                    onClick = {
                        rowMenuExpanded = false
                        openSmsToContact(context, row)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Llamada") },
                    onClick = {
                        rowMenuExpanded = false
                        openDialContact(context, row)
                    },
                )
                DropdownMenuItem(
                    text = { Text("WhatsApp") },
                    onClick = {
                        rowMenuExpanded = false
                        openWhatsAppChat(context, row)
                    },
                )
            }
        }
    }
}

private fun primaryPhoneDigits(row: ContactListRowUi): String? {
    val mobile = buildMobileE164Digits(row.mobileDialCode, row.mobilePhone)
    if (!mobile.isNullOrBlank()) return mobile
    val land = buildMobileE164Digits(row.landlineDialCode, row.landlinePhone)
    if (!land.isNullOrBlank()) return land
    return null
}

private fun toastNoNumber(context: Context) {
    Toast.makeText(context, "Este contacto no tiene número", Toast.LENGTH_SHORT).show()
}

private fun openSmsToContact(context: Context, row: ContactListRowUi) {
    val d = primaryPhoneDigits(row) ?: return toastNoNumber(context)
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$d"))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir la app de mensajes", Toast.LENGTH_SHORT).show()
    }
}

private fun openDialContact(context: Context, row: ContactListRowUi) {
    val d = primaryPhoneDigits(row) ?: return toastNoNumber(context)
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(d)}"))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
    }
}

/**
 * E.164 sin `+` para WhatsApp: móvil o fijo con código de país del contacto.
 */
private fun digitsForWhatsApp(row: ContactListRowUi): String? {
    val mobile = buildMobileE164Digits(row.mobileDialCode, row.mobilePhone)
    if (!mobile.isNullOrBlank()) return mobile
    return buildMobileE164Digits(row.landlineDialCode, row.landlinePhone)
}

private fun openWhatsAppChat(context: Context, row: ContactListRowUi) {
    val phone = digitsForWhatsApp(row) ?: return toastNoNumber(context)
    if (phone.isBlank()) return toastNoNumber(context)
    // `?phone=` evita ambigüedades con ceros iniciales en el segmento de ruta de wa.me
    val uri = Uri.parse("https://api.whatsapp.com/send").buildUpon()
        .appendQueryParameter("phone", phone)
        .build()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}
