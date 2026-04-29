package com.ambulantpoint.data.model

data class Venta(
    val id: Long = 0L,
    val jornadaId: Long,
    val fechaHora: String = "",
    val total: Double
)
