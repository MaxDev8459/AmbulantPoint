package com.ambulantpoint.service

// Representa la selección de método de pago al registrar una venta.
// RF-07, RF-08 — un único método por venta (sin pago mixto).
sealed class MetodoPagoInput {
    abstract val monto: Double

    data class Efectivo(override val monto: Double) : MetodoPagoInput()

    data class Tarjeta(
        override val monto: Double,
        val porcentajeComision: Double
    ) : MetodoPagoInput() {
        val comision: Double  get() = monto * porcentajeComision / 100.0
        val montoNeto: Double get() = monto - comision
    }

    data class Transferencia(
        override val monto: Double,
        val porcentajeComision: Double,
        val referencia: String? = null
    ) : MetodoPagoInput() {
        val comision: Double  get() = monto * porcentajeComision / 100.0
        val montoNeto: Double get() = monto - comision
    }
}
