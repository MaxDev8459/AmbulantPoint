package com.ambulantpoint.data.model

// precio_perdida solo se popula en tipo=MERMA (snapshot D11). En todos los demás tipos es 0.0.
// ref_venta_id y ref_carga_id son mutuamente excluyentes (D-R5).
data class MovimientoInventario(
    val id: Long = 0L,
    val productoId: Long,
    val tipo: String,
    val cantidad: Int,
    val fechaHora: String = "",
    val refVentaId: Long? = null,
    val refCargaId: Long? = null,
    val motivo: String? = null,
    val precioPerdida: Double = 0.0
) {
    companion object {
        const val TIPO_CARGA      = "CARGA"
        const val TIPO_VENTA      = "VENTA"
        const val TIPO_MERMA      = "MERMA"
        const val TIPO_DEVOLUCION = "DEVOLUCION"
        const val TIPO_AJUSTE     = "AJUSTE"
    }
}
