package com.bdavidgm.contactos.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.data.repo.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CustomFieldEditor(
    val label: String = "",
    val value: String = "",
)

data class ContactEditUiState(
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val mobilePhone: String = "",
    val landlinePhone: String = "",
    val notes: String = "",
    val email: String = "",
    val birthday: String = "",
    val address: String = "",
    val existingPhotoPath: String? = null,
    val existingPhotoReadable: Boolean = false,
    val pendingPhotoUri: Uri? = null,
    val customFields: List<CustomFieldEditor> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: Boolean = false,
)

class ContactEditViewModel(
    private val repository: ContactRepository,
    private val contactId: Long,
) : ViewModel() {

    private val _ui = MutableStateFlow(ContactEditUiState(isLoading = contactId != 0L))
    val ui: StateFlow<ContactEditUiState> = _ui.asStateFlow()

    init {
        if (contactId != 0L) {
            viewModelScope.launch {
                val c = repository.getContact(contactId)
                if (c == null) {
                    _ui.update { it.copy(isLoading = false, loadError = true) }
                    return@launch
                }
                val customs = repository.getCustomFields(contactId)
                val path = c.photoPath
                _ui.update {
                    it.copy(
                        isLoading = false,
                        firstName = c.firstName,
                        lastName = c.lastName,
                        company = c.company,
                        mobilePhone = c.mobilePhone,
                        landlinePhone = c.landlinePhone,
                        notes = c.notes,
                        email = c.email,
                        birthday = c.birthday,
                        address = c.address,
                        existingPhotoPath = path,
                        existingPhotoReadable = !path.isNullOrBlank() && File(path).exists(),
                        customFields = customs.map { p -> CustomFieldEditor(p.first, p.second) },
                    )
                }
            }
        } else {
            _ui.update { it.copy(isLoading = false) }
        }
    }

    fun updateFirstName(v: String) = _ui.update { it.copy(firstName = v) }
    fun updateLastName(v: String) = _ui.update { it.copy(lastName = v) }
    fun updateCompany(v: String) = _ui.update { it.copy(company = v) }
    fun updateMobile(v: String) = _ui.update { it.copy(mobilePhone = v) }
    fun updateLandline(v: String) = _ui.update { it.copy(landlinePhone = v) }
    fun updateNotes(v: String) = _ui.update { it.copy(notes = v) }
    fun updateEmail(v: String) = _ui.update { it.copy(email = v) }
    fun updateBirthday(v: String) = _ui.update { it.copy(birthday = v) }
    fun updateAddress(v: String) = _ui.update { it.copy(address = v) }

    fun onImagePicked(uri: Uri?) {
        _ui.update { it.copy(pendingPhotoUri = uri) }
    }

    fun addCustomField() {
        _ui.update { it.copy(customFields = it.customFields + CustomFieldEditor()) }
    }

    fun updateCustomLabel(index: Int, label: String) {
        _ui.update { s ->
            val list = s.customFields.toMutableList()
            if (index in list.indices) list[index] = list[index].copy(label = label)
            s.copy(customFields = list)
        }
    }

    fun updateCustomValue(index: Int, value: String) {
        _ui.update { s ->
            val list = s.customFields.toMutableList()
            if (index in list.indices) list[index] = list[index].copy(value = value)
            s.copy(customFields = list)
        }
    }

    fun removeCustomField(index: Int) {
        _ui.update { s ->
            val list = s.customFields.toMutableList()
            if (index in list.indices) list.removeAt(index)
            s.copy(customFields = list)
        }
    }

    suspend fun save(): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = _ui.value
            val entity = ContactEntity(
                id = contactId,
                firstName = s.firstName.trim(),
                lastName = s.lastName.trim(),
                company = s.company.trim(),
                mobilePhone = s.mobilePhone.trim(),
                landlinePhone = s.landlinePhone.trim(),
                notes = s.notes.trim(),
                email = s.email.trim(),
                birthday = s.birthday.trim(),
                address = s.address.trim(),
                photoPath = s.existingPhotoPath,
            )
            val pairs = s.customFields.map { it.label.trim() to it.value.trim() }
            repository.saveContact(entity, pairs, s.pendingPhotoUri)
            true
        } catch (_: Exception) {
            false
        }
    }
}

class ContactEditViewModelFactory(
    private val repository: ContactRepository,
    private val contactId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ContactEditViewModel::class.java))
        return ContactEditViewModel(repository, contactId) as T
    }
}
