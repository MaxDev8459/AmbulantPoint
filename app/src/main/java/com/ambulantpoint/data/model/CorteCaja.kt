package com.ambulantpoint.data.model

data class CorteCaja(
    val id: Long = 0,
    val jornadaId: Long,
    val efectivoEsperado: Double,
    val efectivoReal: Double,
    val discrepancia: Double,
    val totalPerdidaMermas: Double,
    val fechaHora: String = ""
)
