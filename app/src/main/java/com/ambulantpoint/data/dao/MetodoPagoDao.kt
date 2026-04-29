package com.ambulantpoint.data.dao

import android.content.ContentValues
import com.ambulantpoint.data.db.DatabaseContract.MetodoPagoEntry
import com.ambulantpoint.data.db.DatabaseContract.EfectivoEntry
import com.ambulantpoint.data.db.DatabaseContract.TarjetaEntry
import com.ambulantpoint.data.db.DatabaseContract.TransferenciaEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.db.DatabaseContract.VentaEntry

// DAO para la Class Table Inheritance de MetodoPago.
// Cada venta tiene UN registro en metodo_pago (padre) y UNO en la tabla hija correspondiente.
// ref: DatabaseContract CTI design, D-R3
class MetodoPagoDao(private val dbHelper: DatabaseHelper) {

    fun insertMetodoPago(ventaId: Long, tipo: String): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(MetodoPagoEntry.COL_VENTA_ID, ventaId)
            put(MetodoPagoEntry.COL_TIPO, tipo)
        }
        return db.insert(MetodoPagoEntry.TABLE_NAME, null, values)
    }

    fun insertEfectivo(metodoId: Long, monto: Double): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(EfectivoEntry.COL_ID,    metodoId)
            put(EfectivoEntry.COL_MONTO, monto)
        }
        return db.insert(EfectivoEntry.TABLE_NAME, null, values)
    }

    fun insertTarjeta(metodoId: Long, monto: Double, comision: Double, montoNeto: Double): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TarjetaEntry.COL_ID,         metodoId)
            put(TarjetaEntry.COL_MONTO,      monto)
            put(TarjetaEntry.COL_COMISION,   comision)
            put(TarjetaEntry.COL_MONTO_NETO, montoNeto)
        }
        return db.insert(TarjetaEntry.TABLE_NAME, null, values)
    }

    fun insertTransferencia(
        metodoId: Long,
        monto: Double,
        comision: Double,
        montoNeto: Double,
        referencia: String?
    ): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TransferenciaEntry.COL_ID,         metodoId)
            put(TransferenciaEntry.COL_MONTO,      monto)
            put(TransferenciaEntry.COL_COMISION,   comision)
            put(TransferenciaEntry.COL_MONTO_NETO, montoNeto)
            referencia?.let { put(TransferenciaEntry.COL_REFERENCIA, it) }
        }
        return db.insert(TransferenciaEntry.TABLE_NAME, null, values)
    }

    fun getSumEfectivoByJornada(jornadaId: Long): Double {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM(e.${EfectivoEntry.COL_MONTO}), 0) " +
            "FROM ${EfectivoEntry.TABLE_NAME} e " +
            "JOIN ${MetodoPagoEntry.TABLE_NAME} mp ON e.${EfectivoEntry.COL_ID} = mp.${MetodoPagoEntry.COL_ID} " +
            "JOIN ${VentaEntry.TABLE_NAME} v ON mp.${MetodoPagoEntry.COL_VENTA_ID} = v.${VentaEntry.COL_ID} " +
            "WHERE v.${VentaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getSumTarjetaByJornada(jornadaId: Long): Double {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM(t.${TarjetaEntry.COL_MONTO}), 0) " +
            "FROM ${TarjetaEntry.TABLE_NAME} t " +
            "JOIN ${MetodoPagoEntry.TABLE_NAME} mp ON t.${TarjetaEntry.COL_ID} = mp.${MetodoPagoEntry.COL_ID} " +
            "JOIN ${VentaEntry.TABLE_NAME} v ON mp.${MetodoPagoEntry.COL_VENTA_ID} = v.${VentaEntry.COL_ID} " +
            "WHERE v.${VentaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getSumTransferenciaByJornada(jornadaId: Long): Double {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM(tr.${TransferenciaEntry.COL_MONTO}), 0) " +
            "FROM ${TransferenciaEntry.TABLE_NAME} tr " +
            "JOIN ${MetodoPagoEntry.TABLE_NAME} mp ON tr.${TransferenciaEntry.COL_ID} = mp.${MetodoPagoEntry.COL_ID} " +
            "JOIN ${VentaEntry.TABLE_NAME} v ON mp.${MetodoPagoEntry.COL_VENTA_ID} = v.${VentaEntry.COL_ID} " +
            "WHERE v.${VentaEntry.COL_JORNADA_ID} = ?",
            arrayOf(jornadaId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }
}
