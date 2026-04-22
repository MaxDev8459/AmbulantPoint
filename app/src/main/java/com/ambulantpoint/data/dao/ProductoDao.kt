package com.ambulantpoint.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.ambulantpoint.data.db.DatabaseContract.ProductoEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.Producto

/**
 * DAO para la entidad Producto.
 *
 * Responsabilidades:
 * - Persistencia CRUD de productos en SQLite
 * - Gestión atómica de stock (incrementar/decrementar)
 * - Consultas filtradas para Slash Filter (M2)
 * - Soft-delete y hard-delete según historial (D3)
 *
 * REGLA: Este DAO nunca lanza excepciones de negocio.
 * Toda validación de reglas de negocio vive en CatalogService.
 * El DAO solo lanza excepciones de persistencia (SQLiteException).
 *
 * Decisiones aplicadas:
 * - D1: stock_general como nombre canónico de columna
 * - D3: Soft-delete via campo activo
 */
class ProductoDao(private val dbHelper: DatabaseHelper) {

    // ─────────────────────────────────────────────────────────
    // INSERCIÓN
    // ─────────────────────────────────────────────────────────

    /**
     * Inserta un nuevo producto en la BD.
     * @return ID generado, o -1 si falló el INSERT.
     * RF asociado: RF-02, RF-18
     */
    fun insert(producto: Producto): Long {
        val db = dbHelper.writableDatabase
        return db.insert(ProductoEntry.TABLE_NAME, null, toContentValues(producto))
    }

    // ─────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────

    /**
     * Busca un producto por su ID.
     * @return Producto si existe, null si no.
     */
    fun findById(id: Long): Producto? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ProductoEntry.TABLE_NAME,
            null,
            "${ProductoEntry.COL_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToProducto(it) else null
        }
    }

    /**
     * Retorna todos los productos, opcionalmente filtrados por estado activo.
     * @param soloActivos true = solo productos con activo=1
     * RF asociado: RF-18
     */
    fun findAll(soloActivos: Boolean = false): List<Producto> {
        val db = dbHelper.readableDatabase
        val selection = if (soloActivos) "${ProductoEntry.COL_ACTIVO} = 1" else null
        val cursor = db.query(
            ProductoEntry.TABLE_NAME,
            null,
            selection,
            null, null, null,
            "${ProductoEntry.COL_NOMBRE} ASC"
        )
        return cursor.use { buildList(it) }
    }

    /**
     * Retorna los productos de una categoría específica.
     * @param categoriaId ID de la categoría padre
     * @param soloActivos si true, filtra solo activos
     * RF asociado: RF-01, RF-02
     */
    fun findByCategoria(categoriaId: Long, soloActivos: Boolean = false): List<Producto> {
        val db = dbHelper.readableDatabase
        val selection = buildString {
            append("${ProductoEntry.COL_CATEGORIA_ID} = ?")
            if (soloActivos) append(" AND ${ProductoEntry.COL_ACTIVO} = 1")
        }
        val cursor = db.query(
            ProductoEntry.TABLE_NAME,
            null,
            selection,
            arrayOf(categoriaId.toString()),
            null, null,
            "${ProductoEntry.COL_NOMBRE} ASC"
        )
        return cursor.use { buildList(it) }
    }

    /**
     * Retorna productos de una categoría que tienen stock_general > 0.
     * Usado exclusivamente por el Slash Filter (M2) para mostrar
     * solo categorías cargables en la jornada.
     *
     * RF asociado: RF-04 (Carga Diaria — Slash Filter)
     */
    fun findByCategoriaConStock(categoriaId: Long): List<Producto> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ProductoEntry.TABLE_NAME,
            null,
            "${ProductoEntry.COL_CATEGORIA_ID} = ? " +
                    "AND ${ProductoEntry.COL_ACTIVO} = 1 " +
                    "AND ${ProductoEntry.COL_STOCK_GENERAL} > 0",
            arrayOf(categoriaId.toString()),
            null, null,
            "${ProductoEntry.COL_NOMBRE} ASC"
        )
        return cursor.use { buildList(it) }
    }

    /**
     * Verifica si existe un producto con ese nombre dentro de la misma categoría.
     * Usado por CatalogService para validar unicidad antes de INSERT/UPDATE.
     * RF asociado: RF-18 (nombre no duplicado en misma categoría)
     */
    fun existsByNombreEnCategoria(nombre: String, categoriaId: Long, excludeId: Long = -1L): Boolean {
        val db = dbHelper.readableDatabase
        val selection = buildString {
            append("${ProductoEntry.COL_NOMBRE} = ? ")
            append("AND ${ProductoEntry.COL_CATEGORIA_ID} = ?")
            if (excludeId != -1L) append(" AND ${ProductoEntry.COL_ID} != ?")
        }
        val args = if (excludeId != -1L) {
            arrayOf(nombre.trim(), categoriaId.toString(), excludeId.toString())
        } else {
            arrayOf(nombre.trim(), categoriaId.toString())
        }
        val cursor = db.query(
            ProductoEntry.TABLE_NAME,
            arrayOf(ProductoEntry.COL_ID),
            selection, args,
            null, null, null
        )
        return cursor.use { it.count > 0 }
    }

    /**
     * Cuenta los productos (activos o todos) de una categoría.
     * Usado por CatalogService para decidir si una categoría
     * puede ser eliminada físicamente.
     */
    fun countByCategoria(categoriaId: Long, soloActivos: Boolean = false): Int {
        val db = dbHelper.readableDatabase
        val selection = buildString {
            append("${ProductoEntry.COL_CATEGORIA_ID} = ?")
            if (soloActivos) append(" AND ${ProductoEntry.COL_ACTIVO} = 1")
        }
        val cursor = db.query(
            ProductoEntry.TABLE_NAME,
            arrayOf("COUNT(*)"),
            selection,
            arrayOf(categoriaId.toString()),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    // ─────────────────────────────────────────────────────────
    // ACTUALIZACIÓN
    // ─────────────────────────────────────────────────────────

    /**
     * Actualiza todos los campos editables de un producto existente.
     * NO actualiza stock_general — usar incrementarStock/decrementarStock.
     * NO actualiza fecha_creacion — inmutable.
     * @return número de filas afectadas (0 = no encontrado, 1 = éxito)
     * RF asociado: RF-18
     */
    fun update(producto: Producto): Int {
        val db = dbHelper.writableDatabase
        // D10: costo_base ELIMINADO — ContentValues construido sin él
        val values = ContentValues().apply {
            put(ProductoEntry.COL_NOMBRE, producto.nombre.trim())
            put(ProductoEntry.COL_CATEGORIA_ID, producto.categoriaId)
            put(ProductoEntry.COL_PRECIO_VENTA, producto.precioVenta)
            put(ProductoEntry.COL_ACTIVO, if (producto.activo) 1 else 0)
            // stock_general NO se actualiza aquí — solo via incrementarStock/decrementarStock
        }
        return db.update(
            ProductoEntry.TABLE_NAME,
            values,
            "${ProductoEntry.COL_ID} = ?",
            arrayOf(producto.id.toString())
        )
    }

    // ─────────────────────────────────────────────────────────
    // GESTIÓN DE STOCK (transacciones atómicas)
    // ─────────────────────────────────────────────────────────

    /**
     * Incrementa stock_general en [cantidad] unidades de forma atómica.
     * Usado al registrar una carga de producción (RF-03).
     *
     * @return stock_general resultante, o -1 si el producto no existe.
     * RF asociado: RF-03 (Carga de Stock General)
     */
    fun incrementarStock(productoId: Long, cantidad: Int): Int {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            db.execSQL(
                "UPDATE ${ProductoEntry.TABLE_NAME} " +
                        "SET ${ProductoEntry.COL_STOCK_GENERAL} = " +
                        "${ProductoEntry.COL_STOCK_GENERAL} + ? " +
                        "WHERE ${ProductoEntry.COL_ID} = ?",
                arrayOf(cantidad, productoId)
            )
            db.setTransactionSuccessful()
            // Retornar el stock resultante
            findById(productoId)?.stockGeneral ?: -1
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Decrementa stock_general en [cantidad] unidades de forma atómica.
     * Incluye edge case: rechaza el decremento si provocaría stock negativo.
     *
     * @return stock_general resultante tras el decremento,
     *         o -1 si el producto no existe,
     *         o -2 si el stock actual es insuficiente (edge case).
     * RF asociado: RF-04 (Carga Diaria), RF-07 (Mermas)
     */
    fun decrementarStock(productoId: Long, cantidad: Int): Int {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val producto = findById(productoId) ?: return -1

            // Edge case: stock insuficiente — rechazar sin modificar BD
            if (producto.stockGeneral < cantidad) return -2

            db.execSQL(
                "UPDATE ${ProductoEntry.TABLE_NAME} " +
                        "SET ${ProductoEntry.COL_STOCK_GENERAL} = " +
                        "${ProductoEntry.COL_STOCK_GENERAL} - ? " +
                        "WHERE ${ProductoEntry.COL_ID} = ? " +
                        "AND ${ProductoEntry.COL_STOCK_GENERAL} >= ?",
                arrayOf(cantidad, productoId, cantidad)
            )
            db.setTransactionSuccessful()
            // Retornar el stock resultante
            findById(productoId)?.stockGeneral ?: -1
        } finally {
            db.endTransaction()
        }
    }

    // ─────────────────────────────────────────────────────────
    // ELIMINACIÓN
    // ─────────────────────────────────────────────────────────

    /**
     * Eliminación lógica: marca activo = 0.
     * Usar cuando el producto tiene historial de ventas (D3).
     * El producto deja de aparecer en catálogo pero sus datos
     * históricos se conservan íntegros.
     * @return número de filas afectadas
     * RF asociado: RF-18
     */
    fun softDelete(productoId: Long): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ProductoEntry.COL_ACTIVO, 0)
        }
        return db.update(
            ProductoEntry.TABLE_NAME,
            values,
            "${ProductoEntry.COL_ID} = ?",
            arrayOf(productoId.toString())
        )
    }

    /**
     * Eliminación física: borra el registro completamente.
     * Solo usar cuando el producto NO tiene historial de ventas (D3).
     * CatalogService es responsable de verificar el historial antes
     * de invocar este método.
     * @return número de filas afectadas
     * RF asociado: RF-18
     */
    fun hardDelete(productoId: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(
            ProductoEntry.TABLE_NAME,
            "${ProductoEntry.COL_ID} = ?",
            arrayOf(productoId.toString())
        )
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────

    /**
     * Construye el ContentValues para INSERT.
     * D10: costo_base ELIMINADO — no aparece en este método.
     */
    private fun toContentValues(producto: Producto): ContentValues =
        ContentValues().apply {
            put(ProductoEntry.COL_NOMBRE, producto.nombre.trim())
            put(ProductoEntry.COL_CATEGORIA_ID, producto.categoriaId)
            put(ProductoEntry.COL_PRECIO_VENTA, producto.precioVenta)
            put(ProductoEntry.COL_STOCK_GENERAL, producto.stockGeneral)
            put(ProductoEntry.COL_ACTIVO, if (producto.activo) 1 else 0)
            // fecha_creacion: omitida — usa DEFAULT (datetime('now','localtime')) en BD
        }

    /**
     * Mapea una fila del Cursor al modelo Producto.
     * El cursor debe estar posicionado en la fila deseada antes de llamar.
     */
    private fun cursorToProducto(cursor: Cursor): Producto =
        Producto(
            id            = cursor.getLong(cursor.getColumnIndexOrThrow(ProductoEntry.COL_ID)),
            nombre        = cursor.getString(cursor.getColumnIndexOrThrow(ProductoEntry.COL_NOMBRE)),
            categoriaId   = cursor.getLong(cursor.getColumnIndexOrThrow(ProductoEntry.COL_CATEGORIA_ID)),
            precioVenta   = cursor.getDouble(cursor.getColumnIndexOrThrow(ProductoEntry.COL_PRECIO_VENTA)),
            stockGeneral  = cursor.getInt(cursor.getColumnIndexOrThrow(ProductoEntry.COL_STOCK_GENERAL)),
            activo        = cursor.getInt(cursor.getColumnIndexOrThrow(ProductoEntry.COL_ACTIVO)) == 1,
            fechaCreacion = cursor.getString(cursor.getColumnIndexOrThrow(ProductoEntry.COL_FECHA_CREACION))
        )

    /**
     * Itera el cursor completo y construye la lista de productos.
     * Usa cursor.use{} para garantizar cierre automático.
     */
    private fun buildList(cursor: Cursor): List<Producto> {
        val result = mutableListOf<Producto>()
        while (cursor.moveToNext()) {
            result.add(cursorToProducto(cursor))
        }
        return result
    }
}
