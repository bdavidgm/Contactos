package com.bdavidgm.contactos.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.data.repo.ContactRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _openExportDocumentPicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openExportDocumentPicker = _openExportDocumentPicker.asSharedFlow()

    private var pendingExportUtf8: String? = null

    fun onSearchChange(value: String) {
        searchQuery.value = value
    }

    fun setMenuOpen(open: Boolean) {
        _menuOpen.value = open
    }

    fun consumeSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { repository.deleteContact(id) }
            }
            _snackbarMessage.value = "Contacto eliminado"
        }
    }

    fun onImportDocumentPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        repository.importFromVcf(input)
                    } ?: 0
                }.getOrElse { 0 }
            }
            _snackbarMessage.value = "Importados: $n contactos"
        }
    }

    fun onExportMenuClicked() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { repository.exportAllToVcfString() }
            pendingExportUtf8 = data
            _openExportDocumentPicker.emit(Unit)
        }
    }

    fun onExportDocumentCreated(uri: Uri?) {
        val data = pendingExportUtf8
        pendingExportUtf8 = null
        if (uri == null || data == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            _snackbarMessage.value = "Exportación completada"
        }
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
