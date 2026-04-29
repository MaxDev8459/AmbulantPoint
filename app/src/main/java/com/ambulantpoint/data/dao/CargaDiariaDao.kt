package com.ambulantpoint.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.ambulantpoint.data.db.DatabaseContract.CargaDiariaEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.CargaDiaria

class CargaDiariaDao(private val dbHelper: DatabaseHelper) {

    fun insert(carga: CargaDiaria): Long {
        val db = dbHelper.writableDatabase
        return db.insert(CargaDiariaEntry.TABLE_NAME, null, toContentValues(carga))
    }

    fun getByJornada(jornadaId: Long): List<CargaDiaria> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CargaDiariaEntry.TABLE_NAME,
            null,
            "${CargaDiariaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString()),
            null, null, null
        )
        return cursor.use { buildList(it) }
    }

    fun getByJornadaYProducto(jornadaId: Long, productoId: Long): CargaDiaria? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CargaDiariaEntry.TABLE_NAME,
            null,
            "${CargaDiariaEntry.COL_JORNADA_ID} = ? AND ${CargaDiariaEntry.COL_PRODUCTO_ID} = ?",
            arrayOf(jornadaId.toString(), productoId.toString()),
            null, null, null
        )
        return cursor.use { if (it.moveToFirst()) cursorToCarga(it) else null }
    }

    fun incrementarVendida(id: Long, cantidad: Int): Int {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE ${CargaDiariaEntry.TABLE_NAME} " +
            "SET ${CargaDiariaEntry.COL_CANTIDAD_VENDIDA} = ${CargaDiariaEntry.COL_CANTIDAD_VENDIDA} + ? " +
            "WHERE ${CargaDiariaEntry.COL_ID} = ?",
            arrayOf(cantidad, id)
        )
        return 1
    }

    fun incrementarMerma(id: Long, cantidad: Int): Int {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE ${CargaDiariaEntry.TABLE_NAME} " +
            "SET ${CargaDiariaEntry.COL_CANTIDAD_MERMA} = ${CargaDiariaEntry.COL_CANTIDAD_MERMA} + ? " +
            "WHERE ${CargaDiariaEntry.COL_ID} = ?",
            arrayOf(cantidad, id)
        )
        return 1
    }

    fun actualizarSobrante(id: Long, sobrante: Int): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(CargaDiariaEntry.COL_CANTIDAD_SOBRANTE, sobrante)
        }
        return db.update(
            CargaDiariaEntry.TABLE_NAME,
            values,
            "${CargaDiariaEntry.COL_ID} = ?",
            arrayOf(id.toString())
        )
    }

    private fun toContentValues(carga: CargaDiaria): ContentValues =
        ContentValues().apply {
            put(CargaDiariaEntry.COL_JORNADA_ID,        carga.jornadaId)
            put(CargaDiariaEntry.COL_PRODUCTO_ID,       carga.productoId)
            put(CargaDiariaEntry.COL_CANTIDAD_CARGA,    carga.cantidadCarga)
            put(CargaDiariaEntry.COL_CANTIDAD_VENDIDA,  carga.cantidadVendida)
            put(CargaDiariaEntry.COL_CANTIDAD_MERMA,    carga.cantidadMerma)
            put(CargaDiariaEntry.COL_CANTIDAD_SOBRANTE, carga.cantidadSobrante)
        }

    private fun cursorToCarga(cursor: Cursor): CargaDiaria =
        CargaDiaria(
            id               = cursor.getLong(cursor.getColumnIndexOrThrow(CargaDiariaEntry.COL_ID)),
            jornadaId        = cursor.getLong(cursor.getColumnIndexOrThrow(CargaDiariaEntry.COL_JORNADA_ID)),
            productoId       = cursor.getLong(cursor.getColumnIndexOrThrow(CargaDiariaEntry.COL_PRODUCTO_ID)),
            cantidadCarga    = cursor.getInt(cursor.getColumnIndexOrThrow(CargaDiariaEntry.COL_CANTIDAD_CARGA)),
            cantidadVendida  = cursor.getInt(cursor.getColumnIndexOrThrow(CargaDiariaEntry.COL_CANTIDAD_VENDIDA)),
            cantidadMerma    = cursor.getInt(cursor.getColumnIndexOrThrow(CargaDiariaEntry.COL_CANTIDAD_MERMA)),
            cantidadSobrante = cursor.getInt(cursor.getColumnIndexOrThrow(CargaDiariaEntry.COL_CANTIDAD_SOBRANTE))
        )

    private fun buildList(cursor: Cursor): List<CargaDiaria> {
        val result = mutableListOf<CargaDiaria>()
        while (cursor.moveToNext()) result.add(cursorToCarga(cursor))
        return result
    }
}
