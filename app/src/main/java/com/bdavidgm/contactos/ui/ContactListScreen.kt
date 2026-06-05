package com.bdavidgm.contactos.ui

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bdavidgm.contactos.ContactosApplication
import com.bdavidgm.contactos.data.repo.ContactRepository
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
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val vm: ContactListViewModel = viewModel(
        factory = ContactListViewModelFactory(app, repository),
    )

    val search by vm.search.collectAsStateWithLifecycle()
    val contactRows by vm.contactRows.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedContactIds.collectAsStateWithLifecycle()
    val menuOpen by vm.menuOpen.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()
    val pendingDeleteSingle by vm.pendingDeleteSingle.collectAsStateWithLifecycle()
    val pendingExportSubset by vm.pendingExportSubset.collectAsStateWithLifecycle()
    val showBulkDeleteDialog by vm.showBulkDeleteDialog.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val exportVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard"),
        onResult = { uri -> vm.onExportVcfDocumentCreated(uri) },
    )

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri -> vm.onExportZipDocumentCreated(uri) },
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> vm.onImportDocumentPicked(uri) },
    )

    LaunchedEffect(Unit) {
        vm.openExportVcfPicker.collect {
            exportVcfLauncher.launch("contactos_export.vcf")
        }
    }

    LaunchedEffect(Unit) {
        vm.openExportZipPicker.collect {
            exportZipLauncher.launch("contactos_export.zip")
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.consumeSnackbarMessage()
    }

    pendingDeleteSingle?.let { target ->
        AlertDialog(
            onDismissRequest = { vm.dismissDeleteSingleDialog() },
            title = { Text("Eliminar contacto") },
            text = {
                Text("¿Seguro que deseas eliminar a \"${target.displayName}\"?")
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmDeleteSingleDialog() }) {
                    Text("Sí")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissDeleteSingleDialog() }) {
                    Text("No")
                }
            },
        )
    }

    if (pendingExportSubset != null) {
        val n = selectedIds.size
        AlertDialog(
            onDismissRequest = { vm.dismissPendingExportSubset() },
            title = { Text("Exportar contactos") },
            text = {
                Text("¿Está seguro que quiere exportar los $n contactos seleccionados?")
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmPendingExportSubset() }) {
                    Text("Sí")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissPendingExportSubset() }) {
                    Text("No")
                }
            },
        )
    }

    if (showBulkDeleteDialog) {
        val n = selectedIds.size
        AlertDialog(
            onDismissRequest = { vm.dismissBulkDeleteDialog() },
            title = { Text("Eliminar contactos") },
            text = {
                Text("¿Seguro que deseas eliminar los $n contactos seleccionados?")
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmBulkDeleteDialog() }) {
                    Text("Sí")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissBulkDeleteDialog() }) {
                    Text("No")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val selectionMode = selectedIds.isNotEmpty()
                    val titleText = when {
                        !selectionMode -> "Contactos"
                        isPortrait -> "Con... (${selectedIds.size})"
                        else -> "Contactos (${selectedIds.size})"
                    }
                    val titleStyle = when {
                        !selectionMode -> MaterialTheme.typography.titleLarge
                        selectionMode && isPortrait -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = titleText,
                        style = titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TopBarOnCeleste,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TopBarCeleste,
                    scrolledContainerColor = TopBarCeleste,
                    titleContentColor = TopBarOnCeleste,
                    actionIconContentColor = TopBarOnCeleste,
                ),
                actions = {
                    val selectionMode = selectedIds.isNotEmpty()
                    val compactBar = selectionMode && isPortrait
                    val actionIconSize = if (compactBar) 22.dp else 24.dp
                    if (selectionMode) {
                        IconButton(onClick = { vm.selectAllVisibleContacts() }) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = "Seleccionar todos",
                                tint = TopBarOnCeleste,
                                modifier = Modifier.size(actionIconSize),
                            )
                        }
                        IconButton(onClick = { vm.invertSelectionOnVisibleContacts() }) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = "Invertir selección",
                                tint = TopBarOnCeleste,
                                modifier = Modifier.size(actionIconSize),
                            )
                        }
                        IconButton(onClick = { vm.showBulkDeleteDialog() }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Eliminar seleccionados",
                                tint = TopBarOnCeleste,
                                modifier = Modifier.size(actionIconSize),
                            )
                        }
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancelar selección",
                                tint = TopBarOnCeleste,
                                modifier = Modifier.size(actionIconSize),
                            )
                        }
                    }
                    IconButton(onClick = { vm.setMenuOpen(true) }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Más opciones",
                            tint = TopBarOnCeleste,
                            modifier = Modifier.size(if (selectionMode && isPortrait) 22.dp else 24.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { vm.setMenuOpen(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Importar VCF o ZIP") },
                            onClick = {
                                vm.setMenuOpen(false)
                                importLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "text/x-vcard",
                                        "text/vcard",
                                        "application/zip",
                                        "*/*",
                                    ),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar VCF") },
                            onClick = {
                                vm.setMenuOpen(false)
                                vm.onExportVcfToolbarMenuClicked()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar ZIP") },
                            onClick = {
                                vm.setMenuOpen(false)
                                vm.onExportZipToolbarMenuClicked()
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
                    ContactRow(
                        row = row,
                        isSelected = row.contactId in selectedIds,
                        onRowClick = {
                            if (!vm.handleContactRowClick(row.contactId)) {
                                onEditContact(row.contactId)
                            }
                        },
                        onRowLongPress = { vm.toggleContactSelection(row.contactId) },
                        onDeleteRequest = { vm.showDeleteSingleDialog(row) },
                        onSms = { vm.openSmsForContact(row) },
                        onDial = { vm.openDialForContact(row) },
                        onWhatsApp = { vm.openWhatsAppForContact(row) },
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    row: ContactListRowUi,
    isSelected: Boolean,
    onRowClick: () -> Unit,
    onRowLongPress: () -> Unit,
    onDeleteRequest: () -> Unit,
    onSms: () -> Unit,
    onDial: () -> Unit,
    onWhatsApp: () -> Unit,
) {
    var rowMenuExpanded by remember(row.contactId) { mutableStateOf(false) }
    val rowBg = if (isSelected) {
        TopBarCeleste.copy(alpha = 0.38f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onRowClick,
                    onLongClick = onRowLongPress,
                ),
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
                        onSms()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Llamada") },
                    onClick = {
                        rowMenuExpanded = false
                        onDial()
                    },
                )
                DropdownMenuItem(
                    text = { Text("WhatsApp") },
                    onClick = {
                        rowMenuExpanded = false
                        onWhatsApp()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar contacto") },
                    onClick = {
                        rowMenuExpanded = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}
