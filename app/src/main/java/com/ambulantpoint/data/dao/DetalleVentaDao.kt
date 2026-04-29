package com.ambulantpoint.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.ambulantpoint.data.db.DatabaseContract.DetalleVentaEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.DetalleVenta

class DetalleVentaDao(private val dbHelper: DatabaseHelper) {

    fun insert(detalle: DetalleVenta): Long {
        val db = dbHelper.writableDatabase
        return db.insert(DetalleVentaEntry.TABLE_NAME, null, toContentValues(detalle))
    }

    fun getByVenta(ventaId: Long): List<DetalleVenta> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DetalleVentaEntry.TABLE_NAME,
            null,
            "${DetalleVentaEntry.COL_VENTA_ID} = ?",
            arrayOf(ventaId.toString()),
            null, null, null
        )
        return cursor.use { buildList(it) }
    }

    private fun toContentValues(detalle: DetalleVenta): ContentValues =
        ContentValues().apply {
            put(DetalleVentaEntry.COL_VENTA_ID,        detalle.ventaId)
            put(DetalleVentaEntry.COL_PRODUCTO_ID,     detalle.productoId)
            put(DetalleVentaEntry.COL_CANTIDAD,        detalle.cantidad)
            put(DetalleVentaEntry.COL_PRECIO_UNITARIO, detalle.precioUnitario)
            put(DetalleVentaEntry.COL_SUBTOTAL,        detalle.subtotal)
        }

    private fun cursorToDetalle(cursor: Cursor): DetalleVenta =
        DetalleVenta(
            id             = cursor.getLong(cursor.getColumnIndexOrThrow(DetalleVentaEntry.COL_ID)),
            ventaId        = cursor.getLong(cursor.getColumnIndexOrThrow(DetalleVentaEntry.COL_VENTA_ID)),
            productoId     = cursor.getLong(cursor.getColumnIndexOrThrow(DetalleVentaEntry.COL_PRODUCTO_ID)),
            cantidad       = cursor.getInt(cursor.getColumnIndexOrThrow(DetalleVentaEntry.COL_CANTIDAD)),
            precioUnitario = cursor.getDouble(cursor.getColumnIndexOrThrow(DetalleVentaEntry.COL_PRECIO_UNITARIO)),
            subtotal       = cursor.getDouble(cursor.getColumnIndexOrThrow(DetalleVentaEntry.COL_SUBTOTAL))
        )

    private fun buildList(cursor: Cursor): List<DetalleVenta> {
        val result = mutableListOf<DetalleVenta>()
        while (cursor.moveToNext()) result.add(cursorToDetalle(cursor))
        return result
    }
}
