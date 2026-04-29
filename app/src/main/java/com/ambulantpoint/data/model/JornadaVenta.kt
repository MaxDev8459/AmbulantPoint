package com.ambulantpoint.data.model

data class JornadaVenta(
    val id: Long = 0L,
    val fechaInicio: String = "",
    val fechaCierre: String? = null,
    val fondoCapital: Double = 0.0,
    val activa: Boolean = true
)
