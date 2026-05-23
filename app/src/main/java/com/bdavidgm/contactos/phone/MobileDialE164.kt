package com.bdavidgm.contactos.phone

/** Solo dígitos (para código de país y número nacional). */
fun digitsOnlyPhone(s: String): String = s.filter { it.isDigit() }

/** Quita ceros iniciales típicos del número nacional (p. ej. 0 local). */
fun stripLeadingNationalZeros(nationalDigits: String): String {
    var s = nationalDigits
    while (s.startsWith('0') && s.length > 1) {
        s = s.substring(1)
    }
    return s
}

/**
 * Concatena código de país (solo dígitos) + número nacional (solo dígitos, sin ceros iniciales de más).
 * Devuelve null si falta código o el nacional queda vacío tras normalizar.
 */
fun buildMobileE164Digits(dialCode: String, nationalPhone: String): String? {
    val cc = digitsOnlyPhone(dialCode)
    val nat = stripLeadingNationalZeros(digitsOnlyPhone(nationalPhone))
    if (cc.isEmpty() || nat.isEmpty()) return null
    return cc + nat
}
