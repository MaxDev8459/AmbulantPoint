package com.ambulantpoint.data.model

data class CargaDiaria(
    val id: Long = 0L,
    val jornadaId: Long,
    val productoId: Long,
    val cantidadCarga: Int,
    val cantidadVendida: Int = 0,
    val cantidadMerma: Int = 0,
    val cantidadSobrante: Int = 0
) {
    fun hayStock(): Boolean = cantidadSobrante > 0
}
