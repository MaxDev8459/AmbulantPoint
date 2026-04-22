package com.ambulantpoint.data.model

/**
 * Modelo de dominio — Categoria.
 *
 * PATRÓN: data class de Kotlin.
 * Genera automáticamente: equals(), hashCode(), toString(), copy().
 * No tiene dependencias de Android framework — es un POJO puro,
 * testeable sin emulador.
 *
 * SOFT-DELETE: el campo activo controla visibilidad lógica.
 * Una categoría con activo=false NO aparece en el catálogo ni
 * en el Slash Filter, pero sus datos históricos se preservan.
 *
 * REGLA DE NEGOCIO (ver CU-10):
 * No se puede eliminar físicamente una categoría si tiene
 * productos asociados — esa validación vive en CatalogService,
 * no aquí. El modelo no conoce reglas de negocio.
 */
data class Categoria(

    /**
     * id=0 indica entidad no persistida aún (valor por defecto).
     * SQLite asigna el ID real en el INSERT mediante AUTOINCREMENT.
     * Después del insert, el DAO devuelve el objeto con el ID real.
     */
    val id: Int = 0,

    /**
     * Nombre único por diseño de BD (UNIQUE constraint).
     * La validación de duplicado se hace en CatalogService antes
     * del INSERT para dar un mensaje de error claro al usuario.
     */
    val nombre: String,

    /**
     * true  = categoría visible y operativa.
     * false = eliminada lógicamente (soft-delete, decisión D3).
     * Default true: toda categoría nace activa.
     */
    val activo: Boolean = true,

    /**
     * Timestamp almacenado como String en formato ISO 8601.
     * SQLite no tiene tipo DATE nativo — usamos TEXT con
     * datetime('now','localtime') como DEFAULT en la BD.
     * Aquí lo recibimos ya como String desde el Cursor.
     */
    val fechaCreacion: String = ""
)