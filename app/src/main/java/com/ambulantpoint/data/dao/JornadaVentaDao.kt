package com.ambulantpoint.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.ambulantpoint.data.db.DatabaseContract.JornadaVentaEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.JornadaVenta

class JornadaVentaDao(private val dbHelper: DatabaseHelper) {

    fun insert(jornada: JornadaVenta): Long {
        val db = dbHelper.writableDatabase
        return db.insert(JornadaVentaEntry.TABLE_NAME, null, toContentValues(jornada))
    }

    fun getActiva(): JornadaVenta? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            JornadaVentaEntry.TABLE_NAME,
            null,
            "${JornadaVentaEntry.COL_ACTIVA} = 1",
            null, null, null, null,
            "1"
        )
        return cursor.use { if (it.moveToFirst()) cursorToJornada(it) else null }
    }

    fun findById(id: Long): JornadaVenta? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            JornadaVentaEntry.TABLE_NAME,
            null,
            "${JornadaVentaEntry.COL_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        return cursor.use { if (it.moveToFirst()) cursorToJornada(it) else null }
    }

    fun findAllCerradas(): List<JornadaVenta> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            JornadaVentaEntry.TABLE_NAME,
            null,
            "${JornadaVentaEntry.COL_ACTIVA} = 0",
            null, null, null,
            "${JornadaVentaEntry.COL_FECHA_CIERRE} DESC"
        )
        return cursor.use { c ->
            val result = mutableListOf<JornadaVenta>()
            while (c.moveToNext()) result.add(cursorToJornada(c))
            result
        }
    }

    fun cerrar(id: Long, fechaCierre: String): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(JornadaVentaEntry.COL_FECHA_CIERRE, fechaCierre)
            put(JornadaVentaEntry.COL_ACTIVA, 0)
        }
        return db.update(
            JornadaVentaEntry.TABLE_NAME,
            values,
            "${JornadaVentaEntry.COL_ID} = ?",
            arrayOf(id.toString())
        )
    }

    private fun toContentValues(jornada: JornadaVenta): ContentValues =
        ContentValues().apply {
            put(JornadaVentaEntry.COL_FONDO_CAPITAL, jornada.fondoCapital)
            put(JornadaVentaEntry.COL_ACTIVA, if (jornada.activa) 1 else 0)
            // fecha_inicio usa DEFAULT (datetime now) en BD
        }

    private fun cursorToJornada(cursor: Cursor): JornadaVenta =
        JornadaVenta(
            id           = cursor.getLong(cursor.getColumnIndexOrThrow(JornadaVentaEntry.COL_ID)),
            fechaInicio  = cursor.getString(cursor.getColumnIndexOrThrow(JornadaVentaEntry.COL_FECHA_INICIO)),
            fechaCierre  = cursor.getString(cursor.getColumnIndexOrThrow(JornadaVentaEntry.COL_FECHA_CIERRE)),
            fondoCapital = cursor.getDouble(cursor.getColumnIndexOrThrow(JornadaVentaEntry.COL_FONDO_CAPITAL)),
            activa       = cursor.getInt(cursor.getColumnIndexOrThrow(JornadaVentaEntry.COL_ACTIVA)) == 1
        )
}
