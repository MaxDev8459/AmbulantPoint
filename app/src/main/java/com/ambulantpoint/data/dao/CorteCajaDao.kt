package com.ambulantpoint.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.ambulantpoint.data.db.DatabaseContract.CorteCajaEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.CorteCaja

class CorteCajaDao(private val dbHelper: DatabaseHelper) {

    fun insert(corte: CorteCaja): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(CorteCajaEntry.COL_JORNADA_ID,           corte.jornadaId)
            put(CorteCajaEntry.COL_EFECTIVO_ESPERADO,    corte.efectivoEsperado)
            put(CorteCajaEntry.COL_EFECTIVO_REAL,        corte.efectivoReal)
            put(CorteCajaEntry.COL_DISCREPANCIA,         corte.discrepancia)
            put(CorteCajaEntry.COL_TOTAL_PERDIDA_MERMAS, corte.totalPerdidaMermas)
            // fecha_hora usa DEFAULT en BD
        }
        return db.insert(CorteCajaEntry.TABLE_NAME, null, values)
    }

    fun findByJornada(jornadaId: Long): CorteCaja? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            CorteCajaEntry.TABLE_NAME,
            null,
            "${CorteCajaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString()),
            null, null, null
        )
        return cursor.use { if (it.moveToFirst()) fromCursor(it) else null }
    }

    private fun fromCursor(c: Cursor): CorteCaja =
        CorteCaja(
            id                 = c.getLong(c.getColumnIndexOrThrow(CorteCajaEntry.COL_ID)),
            jornadaId          = c.getLong(c.getColumnIndexOrThrow(CorteCajaEntry.COL_JORNADA_ID)),
            efectivoEsperado   = c.getDouble(c.getColumnIndexOrThrow(CorteCajaEntry.COL_EFECTIVO_ESPERADO)),
            efectivoReal       = c.getDouble(c.getColumnIndexOrThrow(CorteCajaEntry.COL_EFECTIVO_REAL)),
            discrepancia       = c.getDouble(c.getColumnIndexOrThrow(CorteCajaEntry.COL_DISCREPANCIA)),
            totalPerdidaMermas = c.getDouble(c.getColumnIndexOrThrow(CorteCajaEntry.COL_TOTAL_PERDIDA_MERMAS)),
            fechaHora          = c.getString(c.getColumnIndexOrThrow(CorteCajaEntry.COL_FECHA_HORA))
        )
}
