package com.bdavidgm.contactos.data.vcf

/**
 * Resultado de exportar contactos: texto vCard y, opcionalmente, imágenes en archivos aparte (ZIP).
 */
data class VcfExportResult(
    val vcfText: String,
    /** Rutas relativas dentro del ZIP → bytes (solo en modo sidecar). */
    val sidecarPhotos: List<Pair<String, ByteArray>> = emptyList(),
)
