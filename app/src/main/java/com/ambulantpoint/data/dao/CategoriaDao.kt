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
    // Retorna el ID asignado por SQLite, o -1 si falló.
    // El objeto devuelto incluye el ID real post-insert.
    // =========================================================
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
    // Retorna null si no existe — el llamador decide qué hacer.
    // Nunca lanzar excepción por "no encontrado".
    // =========================================================
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
    // FIND ALL (activas e inactivas)
    // Para pantallas de administración que muestran todo el historial.
    // =========================================================
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
    // Principal query del Slash Filter: findByActivo(true)
    // retorna solo categorías visibles con productos disponibles.
    // =========================================================
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
    // Retorna número de filas afectadas (0 = no encontrado, 1 = OK).
    // Solo actualiza nombre y activo — fechaCreacion es inmutable.
    // =========================================================
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
    // SOFT DELETE (decisión D3)
    // Nunca elimina físicamente — solo marca activo=false.
    // La eliminación física solo ocurre si el producto no tiene
    // historial, y esa decisión la toma CatalogService, no este DAO.
    // =========================================================
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
    // Solo para casos donde NO existe historial asociado.
    // CatalogService decide cuándo llamar esto vs softDelete.
    // =========================================================
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
    // Usado por CatalogService para validar duplicados antes
    // de insertar — evita depender del mensaje de error de SQLite.
    // excludeId: permite verificar unicidad al editar (excluir
    // el propio registro del chequeo).
    // =========================================================
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
    // Usado por CatalogService para decidir si se puede
    // eliminar una categoría (no debe tener productos activos).
    // Query a tabla producto desde CategoriaDao es aceptable
    // para esta validación puntual de integridad referencial.
    // =========================================================
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