package com.bdavidgm.contactos.phone

data class PhoneCountry(
    val dialCodeDigits: String,
    val label: String,
)

object PhoneCountries {
    const val DEFAULT_DIAL_CODE = "53"

    private val OTHERS: List<PhoneCountry> = listOf(
        PhoneCountry("49", "Alemania (+49)"),
        PhoneCountry("54", "Argentina (+54)"),
        PhoneCountry("61", "Australia (+61)"),
        PhoneCountry("591", "Bolivia (+591)"),
        PhoneCountry("55", "Brasil (+55)"),
        PhoneCountry("56", "Chile (+56)"),
        PhoneCountry("86", "China (+86)"),
        PhoneCountry("57", "Colombia (+57)"),
        PhoneCountry("506", "Costa Rica (+506)"),
        PhoneCountry("593", "Ecuador (+593)"),
        PhoneCountry("503", "El Salvador (+503)"),
        PhoneCountry("34", "España (+34)"),
        PhoneCountry("1", "Estados Unidos (+1)"),
        PhoneCountry("33", "Francia (+33)"),
        PhoneCountry("502", "Guatemala (+502)"),
        PhoneCountry("504", "Honduras (+504)"),
        PhoneCountry("91", "India (+91)"),
        PhoneCountry("39", "Italia (+39)"),
        PhoneCountry("81", "Japón (+81)"),
        PhoneCountry("52", "México (+52)"),
        PhoneCountry("505", "Nicaragua (+505)"),
        PhoneCountry("64", "Nueva Zelanda (+64)"),
        PhoneCountry("507", "Panamá (+507)"),
        PhoneCountry("595", "Paraguay (+595)"),
        PhoneCountry("51", "Perú (+51)"),
        PhoneCountry("351", "Portugal (+351)"),
        PhoneCountry("44", "Reino Unido (+44)"),
        PhoneCountry("82", "Rep. de Corea (+82)"),
        PhoneCountry("7", "Rusia (+7)"),
        PhoneCountry("598", "Uruguay (+598)"),
        PhoneCountry("58", "Venezuela (+58)"),
    ).sortedBy { it.label }

    /** Cuba primero; el resto por nombre. */
    val ALL: List<PhoneCountry> =
        listOf(PhoneCountry(DEFAULT_DIAL_CODE, "Cuba (+53)")) + OTHERS

    /** Si el código guardado no está en la lista, se añade “Otro” al inicio. */
    fun optionsForStoredDialCode(storedDial: String): List<PhoneCountry> {
        val d = digitsOnlyPhone(storedDial).ifEmpty { DEFAULT_DIAL_CODE }
        if (ALL.any { it.dialCodeDigits == d }) return ALL
        return listOf(PhoneCountry(d, "Otro (+$d)")) + ALL
    }
}
