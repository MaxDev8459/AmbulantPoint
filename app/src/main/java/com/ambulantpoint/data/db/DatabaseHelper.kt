package com.ambulantpoint.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ambulantpoint.data.db.DatabaseContract.DB_NAME
import com.ambulantpoint.data.db.DatabaseContract.DB_VERSION
import com.ambulantpoint.data.db.DatabaseContract.CategoriaEntry
import com.ambulantpoint.data.db.DatabaseContract.ProductoEntry
import com.ambulantpoint.data.db.DatabaseContract.MetodoPagoEntry
import com.ambulantpoint.data.db.DatabaseContract.EfectivoEntry
import com.ambulantpoint.data.db.DatabaseContract.TarjetaEntry
import com.ambulantpoint.data.db.DatabaseContract.TransferenciaEntry
import com.ambulantpoint.data.db.DatabaseContract.JornadaVentaEntry
import com.ambulantpoint.data.db.DatabaseContract.CargaDiariaEntry
import com.ambulantpoint.data.db.DatabaseContract.VentaEntry
import com.ambulantpoint.data.db.DatabaseContract.DetalleVentaEntry
import com.ambulantpoint.data.db.DatabaseContract.MovimientoInventarioEntry
import com.ambulantpoint.data.db.DatabaseContract.CorteCajaEntry

/**
 * DatabaseHelper — punto de entrada único a la base de datos SQLite.
 *
 * PATRÓN: Singleton. Solo debe existir UNA instancia en toda la app.
 * Si se crean múltiples instancias, SQLite puede lanzar errores de
 * concurrencia o bloquear el archivo de BD.
 *
 * USO DESDE CUALQUIER DAO:
 *   val db = DatabaseHelper.getInstance(context)
 *   val readable = db.readableDatabase
 *   val writable = db.writableDatabase
 *
 * MIGRACIONES: onUpgrade se implementará cuando DB_VERSION incremente.
 * Por ahora destruye y recrea — válido en fase de desarrollo.
 */
class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext, // applicationContext evita memory leaks
        DB_NAME,
        null,
        DB_VERSION
    ) {

    // =========================================================
    // SINGLETON
    // companion object es el equivalente Kotlin de static en Java.
    // @Volatile garantiza visibilidad entre hilos.
    // =========================================================
    companion object {
        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            // Double-checked locking: eficiente y thread-safe
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }

    // =========================================================
    // onCreate — se ejecuta UNA SOLA VEZ cuando la BD no existe
    // Orden de creación CRÍTICO: respetar dependencias de FK
    //   1. Tablas independientes primero (sin FK entrante)
    //   2. Tablas dependientes después
    // =========================================================
    override fun onCreate(db: SQLiteDatabase) {

        // FK support debe activarse por conexión en SQLite Android
        db.execSQL("PRAGMA foreign_keys = ON")

        // --- Tablas base (sin dependencias) ---
        db.execSQL(CategoriaEntry.SQL_CREATE)
        db.execSQL(JornadaVentaEntry.SQL_CREATE)

        // --- Tablas que dependen de las base ---
        db.execSQL(ProductoEntry.SQL_CREATE)         // → categoria
        db.execSQL(CargaDiariaEntry.SQL_CREATE)      // → jornada_venta, producto
        db.execSQL(VentaEntry.SQL_CREATE)            // → jornada_venta
        db.execSQL(MetodoPagoEntry.SQL_CREATE)       // → (venta_id, se FK en M2)
        db.execSQL(EfectivoEntry.SQL_CREATE)         // → metodo_pago (CTI)
        db.execSQL(TarjetaEntry.SQL_CREATE)          // → metodo_pago (CTI)
        db.execSQL(TransferenciaEntry.SQL_CREATE)    // → metodo_pago (CTI)
        db.execSQL(DetalleVentaEntry.SQL_CREATE)     // → venta, producto
        db.execSQL(MovimientoInventarioEntry.SQL_CREATE) // → producto, venta, carga_diaria
        db.execSQL(CorteCajaEntry.SQL_CREATE)        // → jornada_venta
    }

    // =========================================================
    // onUpgrade — se ejecuta cuando DB_VERSION incrementa
    // FASE DESARROLLO: drop + recrear es aceptable.
    // FASE PRODUCCIÓN: implementar migraciones incrementales.
    // Orden de DROP es inverso al de CREATE (respetar FKs)
    // =========================================================
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA foreign_keys = OFF")

        // Drop en orden inverso a dependencias
        db.execSQL(CorteCajaEntry.SQL_DROP)
        db.execSQL(MovimientoInventarioEntry.SQL_DROP)
        db.execSQL(DetalleVentaEntry.SQL_DROP)
        db.execSQL(TransferenciaEntry.SQL_DROP)
        db.execSQL(TarjetaEntry.SQL_DROP)
        db.execSQL(EfectivoEntry.SQL_DROP)
        db.execSQL(MetodoPagoEntry.SQL_DROP)
        db.execSQL(VentaEntry.SQL_DROP)
        db.execSQL(CargaDiariaEntry.SQL_DROP)
        db.execSQL(ProductoEntry.SQL_DROP)
        db.execSQL(JornadaVentaEntry.SQL_DROP)
        db.execSQL(CategoriaEntry.SQL_DROP)

        db.execSQL("PRAGMA foreign_keys = ON")
        onCreate(db)
    }

    // =========================================================
    // onOpen — se ejecuta CADA VEZ que se abre la BD
    // FK support debe reactivarse en cada conexión (limitación SQLite Android)
    // =========================================================
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}