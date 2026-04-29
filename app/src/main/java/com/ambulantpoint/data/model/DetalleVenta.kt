package com.ambulantpoint.data.model

// precio_unitario es snapshot inmutable capturado al momento de la venta (D8).
// Nunca recalcular desde Producto.precioVenta posterior.
data class DetalleVenta(
    val id: Long = 0L,
    val ventaId: Long,
    val productoId: Long,
    val cantidad: Int,
    val precioUnitario: Double,
    val subtotal: Double
)
