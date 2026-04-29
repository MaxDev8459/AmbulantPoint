package com.ambulantpoint.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.ambulantpoint.data.db.DatabaseContract.VentaEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.Venta

class VentaDao(private val dbHelper: DatabaseHelper) {

    fun insert(venta: Venta): Long {
        val db = dbHelper.writableDatabase
        return db.insert(VentaEntry.TABLE_NAME, null, toContentValues(venta))
    }

    fun getByJornada(jornadaId: Long): List<Venta> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            VentaEntry.TABLE_NAME,
            null,
            "${VentaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString()),
            null, null,
            "${VentaEntry.COL_FECHA_HORA} DESC"
        )
        return cursor.use { buildList(it) }
    }

    private fun toContentValues(venta: Venta): ContentValues =
        ContentValues().apply {
            put(VentaEntry.COL_JORNADA_ID, venta.jornadaId)
            put(VentaEntry.COL_TOTAL,      venta.total)
            // fecha_hora usa DEFAULT (datetime now) en BD
        }

    private fun cursorToVenta(cursor: Cursor): Venta =
        Venta(
            id        = cursor.getLong(cursor.getColumnIndexOrThrow(VentaEntry.COL_ID)),
            jornadaId = cursor.getLong(cursor.getColumnIndexOrThrow(VentaEntry.COL_JORNADA_ID)),
            fechaHora = cursor.getString(cursor.getColumnIndexOrThrow(VentaEntry.COL_FECHA_HORA)),
            total     = cursor.getDouble(cursor.getColumnIndexOrThrow(VentaEntry.COL_TOTAL))
        )

    fun getSumByJornada(jornadaId: Long): Double {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM(${VentaEntry.COL_TOTAL}), 0) " +
            "FROM ${VentaEntry.TABLE_NAME} WHERE ${VentaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getCountByJornada(jornadaId: Long): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${VentaEntry.TABLE_NAME} WHERE ${VentaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun buildList(cursor: Cursor): List<Venta> {
        val result = mutableListOf<Venta>()
        while (cursor.moveToNext()) result.add(cursorToVenta(cursor))
        return result
    }
}
