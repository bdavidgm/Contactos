package com.bdavidgm.contactos.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.data.repo.ContactRepository
import com.bdavidgm.contactos.phone.buildMobileE164Digits
import com.bdavidgm.contactos.ui.ContactListRowFormatter
import com.bdavidgm.contactos.ui.ContactListRowUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PendingExportSubsetKind { Vcf, Zip }

@OptIn(ExperimentalCoroutinesApi::class)
class ContactListViewModel(
    application: Application,
    private val repository: ContactRepository,
) : AndroidViewModel(application) {

    private val appContext get() = getApplication<Application>().applicationContext

    private val searchQuery = MutableStateFlow("")

    val search: StateFlow<String> = searchQuery.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = searchQuery
        .flatMapLatest { q -> repository.observeContacts(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contactRows: StateFlow<List<ContactListRowUi>> = contacts
        .map { list -> list.map { ContactListRowFormatter.toRowUi(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedContactIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedContactIds: StateFlow<Set<Long>> = _selectedContactIds.asStateFlow()

    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _pendingDeleteSingle = MutableStateFlow<ContactListRowUi?>(null)
    val pendingDeleteSingle: StateFlow<ContactListRowUi?> = _pendingDeleteSingle.asStateFlow()

    private val _pendingExportSubset = MutableStateFlow<PendingExportSubsetKind?>(null)
    val pendingExportSubset: StateFlow<PendingExportSubsetKind?> = _pendingExportSubset.asStateFlow()

    private val _showBulkDeleteDialog = MutableStateFlow(false)
    val showBulkDeleteDialog: StateFlow<Boolean> = _showBulkDeleteDialog.asStateFlow()

    private val _openExportVcfPicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openExportVcfPicker = _openExportVcfPicker.asSharedFlow()

    private val _openExportZipPicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openExportZipPicker = _openExportZipPicker.asSharedFlow()

    private var pendingExportVcfUtf8: String? = null
    private var pendingExportZipBytes: ByteArray? = null
    private var clearSelectionAfterExport: Boolean = false

    fun onSearchChange(value: String) {
        searchQuery.value = value
    }

    fun setMenuOpen(open: Boolean) {
        _menuOpen.value = open
    }

    fun consumeSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun toggleContactSelection(contactId: Long) {
        _selectedContactIds.update { cur ->
            if (contactId in cur) cur - contactId else cur + contactId
        }
    }

    fun clearSelection() {
        _selectedContactIds.value = emptySet()
    }

    /** Selecciona todos los contactos visibles (lista filtrada por búsqueda actual). */
    fun selectAllVisibleContacts() {
        _selectedContactIds.value = contacts.value.map { it.id }.toSet()
    }

    /** Invierte la selección sobre los contactos visibles; otros ids seleccionados se conservan. */
    fun invertSelectionOnVisibleContacts() {
        val visible = contacts.value.map { it.id }.toSet()
        _selectedContactIds.update { cur ->
            val next = cur.toMutableSet()
            for (id in visible) {
                if (id in next) next.remove(id) else next.add(id)
            }
            next
        }
    }

    /** `true` si la pulsación se gestionó como selección; si `false`, la UI debe abrir edición. */
    fun handleContactRowClick(contactId: Long): Boolean {
        if (_selectedContactIds.value.isEmpty()) return false
        toggleContactSelection(contactId)
        return true
    }

    fun showDeleteSingleDialog(row: ContactListRowUi) {
        _pendingDeleteSingle.value = row
    }

    fun dismissDeleteSingleDialog() {
        _pendingDeleteSingle.value = null
    }

    fun confirmDeleteSingleDialog() {
        val row = _pendingDeleteSingle.value ?: return
        _pendingDeleteSingle.value = null
        deleteContact(row.contactId)
    }

    fun dismissPendingExportSubset() {
        _pendingExportSubset.value = null
    }

    fun confirmPendingExportSubset() {
        val kind = _pendingExportSubset.value ?: return
        _pendingExportSubset.value = null
        when (kind) {
            PendingExportSubsetKind.Vcf -> confirmExportSelectedSubset(true)
            PendingExportSubsetKind.Zip -> confirmExportSelectedSubset(false)
        }
    }

    fun showBulkDeleteDialog() {
        _showBulkDeleteDialog.value = true
    }

    fun dismissBulkDeleteDialog() {
        _showBulkDeleteDialog.value = false
    }

    fun confirmBulkDeleteDialog() {
        _showBulkDeleteDialog.value = false
        deleteSelectedContacts()
    }

    fun onExportVcfToolbarMenuClicked() {
        if (_selectedContactIds.value.isNotEmpty()) {
            _pendingExportSubset.value = PendingExportSubsetKind.Vcf
        } else {
            onExportVcfMenuClicked(null)
        }
    }

    fun onExportZipToolbarMenuClicked() {
        if (_selectedContactIds.value.isNotEmpty()) {
            _pendingExportSubset.value = PendingExportSubsetKind.Zip
        } else {
            onExportZipMenuClicked(null)
        }
    }

    fun deleteSelectedContacts() {
        val ids = _selectedContactIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { repository.deleteContacts(ids) }
            }
            clearSelection()
            _snackbarMessage.value = "Eliminados ${ids.size} contactos"
        }
    }

    fun confirmExportSelectedSubset(isVcf: Boolean) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) {
                repository.getOrderedContactIdsForExport(_selectedContactIds.value)
            }
            if (ids.isEmpty()) {
                _snackbarMessage.value = "No hay contactos para exportar"
                return@launch
            }
            if (isVcf) {
                onExportVcfMenuClicked(ids)
            } else {
                onExportZipMenuClicked(ids)
            }
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { repository.deleteContact(id) }
            }
            _selectedContactIds.update { it - id }
            _snackbarMessage.value = "Contacto eliminado"
        }
    }

    fun onImportDocumentPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    repository.importContacts(uri)
                }.getOrElse { 0 }
            }
            _snackbarMessage.value = "Importados: $n contactos"
        }
    }

    /**
     * @param subsetIds `null` o vacío = exportar todos; si no, solo esos ids (orden de la lista).
     */
    fun onExportVcfMenuClicked(subsetIds: List<Long>? = null) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                if (subsetIds.isNullOrEmpty()) {
                    repository.exportAllToVcfString()
                } else {
                    repository.exportContactsToVcfString(subsetIds)
                }
            }
            if (data.isBlank()) {
                _snackbarMessage.value = "No hay contactos para exportar"
                return@launch
            }
            pendingExportVcfUtf8 = data
            clearSelectionAfterExport = !subsetIds.isNullOrEmpty()
            _openExportVcfPicker.emit(Unit)
        }
    }

    fun onExportZipMenuClicked(subsetIds: List<Long>? = null) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                if (subsetIds.isNullOrEmpty()) {
                    repository.exportAllToZipBytes()
                } else {
                    repository.exportContactsToZipBytes(subsetIds)
                }
            }
            if (bytes.isEmpty()) {
                _snackbarMessage.value = "No hay contactos para exportar"
                return@launch
            }
            pendingExportZipBytes = bytes
            clearSelectionAfterExport = !subsetIds.isNullOrEmpty()
            _openExportZipPicker.emit(Unit)
        }
    }

    fun onExportVcfDocumentCreated(uri: Uri?) {
        val data = pendingExportVcfUtf8
        pendingExportVcfUtf8 = null
        val doClear = clearSelectionAfterExport
        clearSelectionAfterExport = false
        if (uri == null || data == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            if (doClear) clearSelection()
            _snackbarMessage.value = "VCF exportado"
        }
    }

    fun onExportZipDocumentCreated(uri: Uri?) {
        val data = pendingExportZipBytes
        pendingExportZipBytes = null
        val doClear = clearSelectionAfterExport
        clearSelectionAfterExport = false
        if (uri == null || data == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data)
                    }
                }
            }
            if (doClear) clearSelection()
            _snackbarMessage.value = "ZIP exportado (VCF y fotos)"
        }
    }

    fun openSmsForContact(row: ContactListRowUi) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val d = primaryPhoneDigits(row) ?: run {
                _snackbarMessage.value = "Este contacto no tiene número"
                return@launch
            }
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$d"))
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (_: Exception) {
                _snackbarMessage.value = "No se pudo abrir la app de mensajes"
            }
        }
    }

    fun openDialForContact(row: ContactListRowUi) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val d = primaryPhoneDigits(row) ?: run {
                _snackbarMessage.value = "Este contacto no tiene número"
                return@launch
            }
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(d)}"))
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (_: Exception) {
                _snackbarMessage.value = "No se pudo abrir el marcador"
            }
        }
    }

    fun openWhatsAppForContact(row: ContactListRowUi) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val phone = digitsForWhatsApp(row) ?: run {
                _snackbarMessage.value = "Este contacto no tiene número"
                return@launch
            }
            if (phone.isBlank()) {
                _snackbarMessage.value = "Este contacto no tiene número"
                return@launch
            }
            val uri = Uri.parse("https://api.whatsapp.com/send").buildUpon()
                .appendQueryParameter("phone", phone)
                .build()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (_: Exception) {
                _snackbarMessage.value = "No se pudo abrir WhatsApp"
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

    private fun digitsForWhatsApp(row: ContactListRowUi): String? {
        val mobile = buildMobileE164Digits(row.mobileDialCode, row.mobilePhone)
        if (!mobile.isNullOrBlank()) return mobile
        return buildMobileE164Digits(row.landlineDialCode, row.landlinePhone)
    }
}

class ContactListViewModelFactory(
    private val application: Application,
    private val repository: ContactRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ContactListViewModel::class.java))
        return ContactListViewModel(application, repository) as T
    }
}
