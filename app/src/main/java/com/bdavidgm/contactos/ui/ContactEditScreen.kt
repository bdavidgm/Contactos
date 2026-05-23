package com.bdavidgm.contactos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bdavidgm.contactos.data.repo.ContactRepository
import com.bdavidgm.contactos.viewmodels.ContactEditViewModel
import com.bdavidgm.contactos.viewmodels.ContactEditViewModelFactory
import kotlinx.coroutines.launch
import java.io.File

private val TopBarCeleste = Color(0xFF87CEEB)
private val TopBarOnCeleste = Color(0xFF0A3D5C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    repository: ContactRepository,
    contactId: Long,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val vm: ContactEditViewModel = viewModel(
        factory = ContactEditViewModelFactory(repository, contactId),
        key = "edit_$contactId",
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? -> vm.onImagePicked(uri) },
    )

    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = TopBarCeleste,
        scrolledContainerColor = TopBarCeleste,
        titleContentColor = TopBarOnCeleste,
        navigationIconContentColor = TopBarOnCeleste,
        actionIconContentColor = TopBarOnCeleste,
    )

    if (ui.loadError) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Contacto", color = TopBarOnCeleste) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = TopBarOnCeleste,
                            )
                        }
                    },
                    colors = topBarColors,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No se encontró el contacto.")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TopBarOnCeleste,
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val model: Any? = when {
                ui.pendingPhotoUri != null -> ui.pendingPhotoUri
                ui.existingPhotoReadable && !ui.existingPhotoPath.isNullOrBlank() ->
                    File(ui.existingPhotoPath!!)

                else -> null
            }
            val celesteButtonColors = ButtonDefaults.buttonColors(
                containerColor = TopBarCeleste,
                contentColor = TopBarOnCeleste,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = "Foto del contacto",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Spacer(Modifier.size(96.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f),
                    colors = celesteButtonColors,
                ) {
                    Text("Elegir imagen")
                }
                Button(
                    onClick = {
                        scope.launch {
                            if (vm.save()) onSaved()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !ui.isLoading,
                    colors = celesteButtonColors,
                ) {
                    Text("Guardar")
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = ui.firstName,
                onValueChange = vm::updateFirstName,
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.lastName,
                onValueChange = vm::updateLastName,
                label = { Text("Apellidos") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.company,
                onValueChange = vm::updateCompany,
                label = { Text("Empresa") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.mobilePhone,
                onValueChange = vm::updateMobile,
                label = { Text("Teléfono móvil") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.landlinePhone,
                onValueChange = vm::updateLandline,
                label = { Text("Teléfono fijo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.notes,
                onValueChange = vm::updateNotes,
                label = { Text("Notas") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.email,
                onValueChange = vm::updateEmail,
                label = { Text("Correo electrónico") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.birthday,
                onValueChange = vm::updateBirthday,
                label = { Text("Cumpleaños") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Ej.: 1990-05-23 o 19900523", style = MaterialTheme.typography.bodySmall) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.address,
                onValueChange = vm::updateAddress,
                label = { Text("Dirección") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(16.dp))
            Text("Campos personalizados", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            ui.customFields.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = row.label,
                        onValueChange = { vm.updateCustomLabel(index, it) },
                        label = { Text("Nombre del campo") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(onClick = { vm.removeCustomField(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar campo")
                    }
                }
                OutlinedTextField(
                    value = row.value,
                    onValueChange = { vm.updateCustomValue(index, it) },
                    label = { Text("Valor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 1,
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { vm.addCustomField() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TopBarCeleste,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Añadir campo personalizado")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
