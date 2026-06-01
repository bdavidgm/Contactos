package com.bdavidgm.contactos.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.contactos.data.local.ContactEntity
import com.bdavidgm.contactos.data.repo.ContactRepository
import com.bdavidgm.contactos.phone.PhoneCountries
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
    val mobileDialCode: String = PhoneCountries.DEFAULT_DIAL_CODE,
    val mobilePhone: String = "",
    val landlineDialCode: String = PhoneCountries.DEFAULT_DIAL_CODE,
    val landlinePhone: String = "",
    val notes: String = "",
    val email: String = "",
    val url: String = "",
    val socialFacebook: String = "",
    val socialInstagram: String = "",
    val socialTelegram: String = "",
    val socialX: String = "",
    val socialDiscord: String = "",
    val socialLinkedIn: String = "",
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
                        mobileDialCode = c.mobileDialCode.trim().ifBlank { PhoneCountries.DEFAULT_DIAL_CODE },
                        mobilePhone = c.mobilePhone,
                        landlineDialCode = c.landlineDialCode.trim().ifBlank { PhoneCountries.DEFAULT_DIAL_CODE },
                        landlinePhone = c.landlinePhone,
                        notes = c.notes,
                        email = c.email,
                        url = c.url,
                        socialFacebook = c.socialFacebook,
                        socialInstagram = c.socialInstagram,
                        socialTelegram = c.socialTelegram,
                        socialX = c.socialX,
                        socialDiscord = c.socialDiscord,
                        socialLinkedIn = c.socialLinkedIn,
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
    fun updateMobileDialCode(code: String) {
        val digits = code.filter { it.isDigit() }.ifEmpty { PhoneCountries.DEFAULT_DIAL_CODE }
        _ui.update { it.copy(mobileDialCode = digits) }
    }

    fun updateMobile(v: String) = _ui.update { it.copy(mobilePhone = v) }
    fun updateLandlineDialCode(code: String) {
        val digits = code.filter { it.isDigit() }.ifEmpty { PhoneCountries.DEFAULT_DIAL_CODE }
        _ui.update { it.copy(landlineDialCode = digits) }
    }

    fun updateLandline(v: String) = _ui.update { it.copy(landlinePhone = v) }
    fun updateNotes(v: String) = _ui.update { it.copy(notes = v) }
    fun updateEmail(v: String) = _ui.update { it.copy(email = v) }
    fun updateUrl(v: String) = _ui.update { it.copy(url = v) }
    fun updateSocialFacebook(v: String) = _ui.update { it.copy(socialFacebook = v) }
    fun updateSocialInstagram(v: String) = _ui.update { it.copy(socialInstagram = v) }
    fun updateSocialTelegram(v: String) = _ui.update { it.copy(socialTelegram = v) }
    fun updateSocialX(v: String) = _ui.update { it.copy(socialX = v) }
    fun updateSocialDiscord(v: String) = _ui.update { it.copy(socialDiscord = v) }
    fun updateSocialLinkedIn(v: String) = _ui.update { it.copy(socialLinkedIn = v) }
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
                mobileDialCode = s.mobileDialCode.trim().filter { it.isDigit() }
                    .ifEmpty { PhoneCountries.DEFAULT_DIAL_CODE },
                mobilePhone = s.mobilePhone.trim(),
                landlineDialCode = s.landlineDialCode.trim().filter { it.isDigit() }
                    .ifEmpty { PhoneCountries.DEFAULT_DIAL_CODE },
                landlinePhone = s.landlinePhone.trim(),
                notes = s.notes.trim(),
                email = s.email.trim(),
                url = s.url.trim(),
                socialFacebook = s.socialFacebook.trim(),
                socialInstagram = s.socialInstagram.trim(),
                socialTelegram = s.socialTelegram.trim(),
                socialX = s.socialX.trim(),
                socialDiscord = s.socialDiscord.trim(),
                socialLinkedIn = s.socialLinkedIn.trim(),
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
