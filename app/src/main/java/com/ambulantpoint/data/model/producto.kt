package com.ambulantpoint.data.model

/**
 * Modelo de dominio: Producto del catálogo.
 *
 * Decisiones arquitectónicas aplicadas:
 * - D1: Atributo de stock canónico = stockGeneral (mapeado a columna stock_general en BD)
 * - D3: Soft-delete via campo [activo] — nunca eliminar físicamente si tiene historial
 * - D4: imagen_path EXCLUIDA — sin RF de soporte
 * - D10: costoBase ELIMINADO del modelo activo
 *
 * INVARIANTE: stockGeneral >= 0. El DAO garantiza esto mediante
 * transacción atómica con CHECK en SQLite. Nunca decrementar
 * directamente — usar ProductoDao.decrementarStock().
 */
data class Producto(
    val id: Long = 0L,
    val nombre: String,
    val categoriaId: Long,
    val precioVenta: Double,
    val stockGeneral: Int = 0,    // D1: nombre canónico confirmado
    val activo: Boolean = true,   // D3: soft-delete
    val fechaCreacion: String = ""
) {

    /**
     * Indica si el producto tiene unidades disponibles en bodega.
     * Usado por el Slash Filter (M2) para mostrar solo categorías
     * con al menos 1 producto cargable.
     *
     * RF asociado: RF-04 (Carga Diaria)
     */
    fun tieneStockDisponible(): Boolean = stockGeneral > 0
}