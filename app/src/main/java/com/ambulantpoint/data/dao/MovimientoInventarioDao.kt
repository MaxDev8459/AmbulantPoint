package com.ambulantpoint.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.ambulantpoint.data.db.DatabaseContract.MovimientoInventarioEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.MovimientoInventario

class MovimientoInventarioDao(private val dbHelper: DatabaseHelper) {

    fun insert(mov: MovimientoInventario): Long {
        val db = dbHelper.writableDatabase
        return db.insert(MovimientoInventarioEntry.TABLE_NAME, null, toContentValues(mov))
    }

    // Obtiene todas las mermas de una jornada via carga_diaria (para CorteCaja)
    fun getMermasByJornada(jornadaId: Long): List<MovimientoInventario> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT mi.* FROM ${MovimientoInventarioEntry.TABLE_NAME} mi " +
            "JOIN carga_diaria cd ON mi.${MovimientoInventarioEntry.COL_REF_CARGA_ID} = cd.id " +
            "WHERE cd.jornada_id = ? AND mi.${MovimientoInventarioEntry.COL_TIPO} = 'MERMA'",
            arrayOf(jornadaId.toString())
        )
        return cursor.use { buildList(it) }
    }

    fun getByProducto(productoId: Long): List<MovimientoInventario> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            MovimientoInventarioEntry.TABLE_NAME,
            null,
            "${MovimientoInventarioEntry.COL_PRODUCTO_ID} = ?",
            arrayOf(productoId.toString()),
            null, null,
            "${MovimientoInventarioEntry.COL_FECHA_HORA} DESC"
        )
        return cursor.use { buildList(it) }
    }

    private fun toContentValues(mov: MovimientoInventario): ContentValues =
        ContentValues().apply {
            put(MovimientoInventarioEntry.COL_PRODUCTO_ID,    mov.productoId)
            put(MovimientoInventarioEntry.COL_TIPO,           mov.tipo)
            put(MovimientoInventarioEntry.COL_CANTIDAD,       mov.cantidad)
            put(MovimientoInventarioEntry.COL_PRECIO_PERDIDA, mov.precioPerdida)
            mov.refVentaId?.let { put(MovimientoInventarioEntry.COL_REF_VENTA_ID, it) }
            mov.refCargaId?.let { put(MovimientoInventarioEntry.COL_REF_CARGA_ID, it) }
            mov.motivo?.let     { put(MovimientoInventarioEntry.COL_MOTIVO, it) }
            // fecha_hora usa DEFAULT en BD
        }

    private fun cursorToMov(cursor: Cursor): MovimientoInventario {
        val refVentaIdx = cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_REF_VENTA_ID)
        val refCargaIdx = cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_REF_CARGA_ID)
        val motivoIdx   = cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_MOTIVO)
        return MovimientoInventario(
            id            = cursor.getLong(cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_ID)),
            productoId    = cursor.getLong(cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_PRODUCTO_ID)),
            tipo          = cursor.getString(cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_TIPO)),
            cantidad      = cursor.getInt(cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_CANTIDAD)),
            fechaHora     = cursor.getString(cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_FECHA_HORA)),
            refVentaId    = if (cursor.isNull(refVentaIdx)) null else cursor.getLong(refVentaIdx),
            refCargaId    = if (cursor.isNull(refCargaIdx)) null else cursor.getLong(refCargaIdx),
            motivo        = if (cursor.isNull(motivoIdx)) null else cursor.getString(motivoIdx),
            precioPerdida = cursor.getDouble(cursor.getColumnIndexOrThrow(MovimientoInventarioEntry.COL_PRECIO_PERDIDA))
        )
    }

    private fun buildList(cursor: Cursor): List<MovimientoInventario> {
        val result = mutableListOf<MovimientoInventario>()
        while (cursor.moveToNext()) result.add(cursorToMov(cursor))
        return result
    }
}
