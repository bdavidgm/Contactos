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
    val birthday: String = "",
    val address: String = "",
    val photoBase64: String? = null,
    val photoType: String? = null,
    val customFields: List<Pair<String, String>> = emptyList(),
)
