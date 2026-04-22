package com.ambulantpoint.data.dao


import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.ambulantpoint.data.db.DatabaseContract.CategoriaEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.Categoria

/**
 * CategoriaDao — acceso a datos exclusivo para la tabla categoria.
 *
 * RESPONSABILIDAD ÚNICA: traducir entre objetos Categoria (dominio)
 * y filas SQLite (persistencia). No contiene lógica de negocio.
 *
 * PATRÓN: recibe DatabaseHelper en constructor para permitir
 * inyección en tests. Nunca instancia el Helper internamente.
 *
 * TRANSACCIONALIDAD: cada operación de escritura usa transacción
 * explícita con rollback en caso de error (decisión arquitectónica
 * confirmada: commit inmediato post-operación).
 *
 * USO:
 *   val dao = CategoriaDao(DatabaseHelper.getInstance(context))
 */
class CategoriaDao(private val dbHelper: DatabaseHelper) {

    // =========================================================
    // INSERT
    // =========================================================

    /**
     * Inserta una nueva categoría en la BD dentro de una transacción explícita.
     * @return ID asignado por SQLite, o -1 si el INSERT falló.
     */
    fun insert(categoria: Categoria): Long {
        val db = dbHelper.writableDatabase
        var newId = -1L

        db.beginTransaction()
        try {
            val values = toContentValues(categoria)
            newId = db.insertOrThrow(CategoriaEntry.TABLE_NAME, null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return newId
    }

    // =========================================================
    // FIND BY ID
    // =========================================================

    /**
     * Busca una categoría por su ID primario.
     * @return [Categoria] si existe, null si no — el llamador decide cómo manejarlo.
     */
    fun findById(id: Int): Categoria? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CategoriaEntry.TABLE_NAME,
            null, // null = todas las columnas
            "${CategoriaEntry.COL_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )

        return cursor.use { c ->
            if (c.moveToFirst()) fromCursor(c) else null
        }
        // cursor.use{} cierra automáticamente el cursor al terminar,
        // incluso si se lanza una excepción — equivalente a try-finally
    }

    // =========================================================
    // FIND ALL
    // =========================================================

    /**
     * Retorna todas las categorías (activas e inactivas) ordenadas alfabéticamente.
     * Útil para pantallas de administración que muestran el historial completo.
     */
    fun findAll(): List<Categoria> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CategoriaEntry.TABLE_NAME,
            null,
            null, null, null, null,
            "${CategoriaEntry.COL_NOMBRE} ASC" // orden alfabético
        )

        return cursor.use { c -> cursorToList(c) }
    }

    // =========================================================
    // FIND BY ACTIVO
    // =========================================================

    /**
     * Retorna categorías filtradas por estado activo, ordenadas alfabéticamente.
     * La llamada `findByActivo(true)` es la query principal del catálogo visible.
     * @param activo true = solo activas, false = solo inactivas (soft-deleted).
     */
    fun findByActivo(activo: Boolean): List<Categoria> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CategoriaEntry.TABLE_NAME,
            null,
            "${CategoriaEntry.COL_ACTIVO} = ?",
            arrayOf(if (activo) "1" else "0"),
            null, null,
            "${CategoriaEntry.COL_NOMBRE} ASC"
        )

        return cursor.use { c -> cursorToList(c) }
    }

    // =========================================================
    // UPDATE
    // =========================================================

    /**
     * Actualiza el nombre y estado activo de una categoría existente.
     * [Categoria.fechaCreacion] es inmutable y no se modifica.
     * @return número de filas afectadas (0 = no encontrado, 1 = éxito).
     */
    fun update(categoria: Categoria): Int {
        val db = dbHelper.writableDatabase
        var rowsAffected = 0

        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(CategoriaEntry.COL_NOMBRE, categoria.nombre)
                put(CategoriaEntry.COL_ACTIVO, if (categoria.activo) 1 else 0)
                // fechaCreacion NO se actualiza — es registro histórico
            }
            rowsAffected = db.update(
                CategoriaEntry.TABLE_NAME,
                values,
                "${CategoriaEntry.COL_ID} = ?",
                arrayOf(categoria.id.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return rowsAffected
    }

    // =========================================================
    // SOFT DELETE
    // =========================================================

    /**
     * Eliminación lógica: marca `activo = 0` sin borrar el registro (decisión D3).
     * La categoría deja de ser visible en el catálogo pero conserva su historial.
     * @return número de filas afectadas.
     */
    fun softDelete(id: Int): Int {
        val db = dbHelper.writableDatabase
        var rowsAffected = 0

        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(CategoriaEntry.COL_ACTIVO, 0)
            }
            rowsAffected = db.update(
                CategoriaEntry.TABLE_NAME,
                values,
                "${CategoriaEntry.COL_ID} = ?",
                arrayOf(id.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return rowsAffected
    }

    // =========================================================
    // HARD DELETE
    // =========================================================

    /**
     * Eliminación física del registro. Solo llamar cuando no existe historial asociado.
     * [CatalogService] es responsable de verificar antes de invocar este método.
     * @return número de filas eliminadas.
     */
    fun hardDelete(id: Int): Int {
        val db = dbHelper.writableDatabase
        var rowsAffected = 0

        db.beginTransaction()
        try {
            rowsAffected = db.delete(
                CategoriaEntry.TABLE_NAME,
                "${CategoriaEntry.COL_ID} = ?",
                arrayOf(id.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return rowsAffected
    }

    // =========================================================
    // EXISTS BY NOMBRE
    // =========================================================

    /**
     * Verifica si ya existe una categoría con el mismo nombre (case-sensitive en BD).
     * @param nombre nombre a verificar (se compara sin trim interno — hacerlo antes de llamar).
     * @param excludeId ID a excluir del chequeo, usado al editar para no colisionar consigo mismo.
     * @return true si existe un duplicado.
     */
    fun existsByNombre(nombre: String, excludeId: Int = -1): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CategoriaEntry.TABLE_NAME,
            arrayOf(CategoriaEntry.COL_ID),
            "${CategoriaEntry.COL_NOMBRE} = ? AND ${CategoriaEntry.COL_ID} != ?",
            arrayOf(nombre.trim(), excludeId.toString()),
            null, null, null
        )

        return cursor.use { c -> c.count > 0 }
    }

    // =========================================================
    // COUNT PRODUCTOS ACTIVOS
    // =========================================================

    /**
     * Cuenta los productos activos asociados a una categoría.
     * Usado por [com.ambulantpoint.service.CatalogService] para decidir si la categoría
     * puede eliminarse. La consulta a tabla `producto` desde este DAO es aceptable
     * por ser una validación puntual de integridad referencial.
     * @return cantidad de productos con `activo = 1` en la categoría.
     */
    fun countProductosActivos(categoriaId: Int): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT COUNT(*) 
            FROM producto 
            WHERE categoria_id = ? AND activo = 1
            """.trimIndent(),
            arrayOf(categoriaId.toString())
        )

        return cursor.use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    // =========================================================
    // HELPERS PRIVADOS
    // Conversión entre Cursor → Categoria y Categoria → ContentValues.
    // Privados: solo este DAO conoce el mapeo de columnas.
    // =========================================================

    /**
     * Construye un objeto Categoria desde la fila actual del Cursor.
     * PRECONDICIÓN: el cursor debe estar posicionado (moveToFirst/Next ya llamado).
     */
    private fun fromCursor(cursor: Cursor): Categoria {
        return Categoria(
            id           = cursor.getInt(cursor.getColumnIndexOrThrow(CategoriaEntry.COL_ID)),
            nombre       = cursor.getString(cursor.getColumnIndexOrThrow(CategoriaEntry.COL_NOMBRE)),
            activo       = cursor.getInt(cursor.getColumnIndexOrThrow(CategoriaEntry.COL_ACTIVO)) == 1,
            fechaCreacion = cursor.getString(cursor.getColumnIndexOrThrow(CategoriaEntry.COL_FECHA_CREACION))
        )
    }

    /**
     * Convierte lista completa de Cursor a List<Categoria>.
     * Itera desde la posición actual hasta el final.
     */
    private fun cursorToList(cursor: Cursor): List<Categoria> {
        val list = mutableListOf<Categoria>()
        while (cursor.moveToNext()) {
            list.add(fromCursor(cursor))
        }
        return list
    }

    /**
     * Construye ContentValues desde un objeto Categoria.
     * NO incluye id (lo asigna SQLite) ni fechaCreacion (DEFAULT en BD).
     */
    private fun toContentValues(categoria: Categoria): ContentValues {
        return ContentValues().apply {
            put(CategoriaEntry.COL_NOMBRE, categoria.nombre.trim())
            put(CategoriaEntry.COL_ACTIVO, if (categoria.activo) 1 else 0)
            // id: omitido — AUTOINCREMENT
            // fechaCreacion: omitido — DEFAULT (datetime('now','localtime'))
        }
    }
}