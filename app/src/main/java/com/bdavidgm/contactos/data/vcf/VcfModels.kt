package com.bdavidgm.contactos.data.vcf

/**
 * Representación intermedia de un vCard para importar/exportar.
 */
data class VcfContact(
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val mobilePhone: String = "",
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
    val photoBase64: String? = null,
    val photoType: String? = null,
    /** Ruta relativa dentro de un ZIP exportado por esta app ([X-CONTACTOS-PHOTO]). */
    val sidecarPhotoPath: String? = null,
    val customFields: List<Pair<String, String>> = emptyList(),
)
