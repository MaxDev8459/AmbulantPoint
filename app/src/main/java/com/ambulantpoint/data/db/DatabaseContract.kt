package com.ambulantpoint.data.db

/**
 * Contrato central de la base de datos AmbulantPoint.
 *
 * REGLA ARQUITECTÓNICA: Ningún archivo fuera de este contrato debe
 * escribir strings literales de nombres de tabla o columna. Siempre
 * referenciar las constantes definidas aquí.
 *
 * Patrón: cada tabla tiene un objeto interno que agrupa su nombre
 * y sus columnas. El SQL de creación también vive aquí para
 * mantener toda la definición de esquema en un solo lugar.
 */
object DatabaseContract {

    const val DB_NAME    = "ambulantpoint.db"
    const val DB_VERSION = 1

    // =========================================================
    // TABLA: categoria
    // Módulo: M1 — Gestión de Catálogo
    // RF asociado: RF-01, RF-18
    // Soft-delete: activo (1 = activa, 0 = eliminada lógicamente)
    // =========================================================
    object CategoriaEntry {
        const val TABLE_NAME          = "categoria"
        const val COL_ID              = "id"
        const val COL_NOMBRE          = "nombre"
        const val COL_ACTIVO          = "activo"
        const val COL_FECHA_CREACION  = "fecha_creacion"

        /**
         * nombre tiene restricción UNIQUE para evitar duplicados.
         * fecha_creacion usa DEFAULT para no requerir el dato en cada INSERT.
         * activo DEFAULT 1 = toda categoría nace activa.
         */
        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID             INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NOMBRE         TEXT    NOT NULL UNIQUE,
                $COL_ACTIVO         INTEGER NOT NULL DEFAULT 1,
                $COL_FECHA_CREACION TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: producto
    // Módulo: M1 — Gestión de Catálogo
    // RF asociado: RF-02, RF-03, RF-18
    // Soft-delete: activo (1 = activo, 0 = eliminado lógicamente)
    // Nota: imagen_path EXCLUIDA por decisión D4 (sin RF de soporte)
    // Nota: costo_base ELIMINADO por decisión D10
    // =========================================================
    object ProductoEntry {
        const val TABLE_NAME          = "producto"
        const val COL_ID              = "id"
        const val COL_NOMBRE          = "nombre"
        const val COL_CATEGORIA_ID    = "categoria_id"
        const val COL_PRECIO_VENTA    = "precio_venta"
        const val COL_STOCK_GENERAL   = "stock_general"   // D1: nombre canónico confirmado
        const val COL_ACTIVO          = "activo"
        const val COL_FECHA_CREACION  = "fecha_creacion"

        /**
         * precio_venta CHECK > 0: regla de negocio — no se vende a precio cero.
         * stock_general CHECK >= 0: no puede haber stock negativo en bodega.
         * FK categoria_id → categoria(id): integridad referencial.
         * UNIQUE(nombre, categoria_id): mismo nombre en categorías distintas es permitido.
         */
        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID             INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NOMBRE         TEXT    NOT NULL,
                $COL_CATEGORIA_ID   INTEGER NOT NULL,
                $COL_PRECIO_VENTA   REAL    NOT NULL CHECK($COL_PRECIO_VENTA > 0),
                $COL_STOCK_GENERAL  INTEGER NOT NULL DEFAULT 0 CHECK($COL_STOCK_GENERAL >= 0),
                $COL_ACTIVO         INTEGER NOT NULL DEFAULT 1,
                $COL_FECHA_CREACION TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY ($COL_CATEGORIA_ID) REFERENCES ${CategoriaEntry.TABLE_NAME}(${CategoriaEntry.COL_ID}),
                UNIQUE ($COL_NOMBRE, $COL_CATEGORIA_ID)
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: metodo_pago (tabla padre — Class Table Inheritance)
    // Módulo: M2 — Operación de Venta Diaria
    // RF asociado: RF-07, RF-08, RF-09
    // D2: CTI — esta tabla comparte PK con efectivo, tarjeta, transferencia
    // =========================================================

    /** Tabla padre de la jerarquía CTI de métodos de pago. Su PK es compartida con las tablas hija. */
    object MetodoPagoEntry {
        const val TABLE_NAME     = "metodo_pago"
        const val COL_ID         = "id"
        const val COL_TIPO       = "tipo"      // 'EFECTIVO' | 'TARJETA' | 'TRANSFERENCIA'
        const val COL_VENTA_ID   = "venta_id"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIPO     TEXT    NOT NULL CHECK($COL_TIPO IN ('EFECTIVO','TARJETA','TRANSFERENCIA')),
                $COL_VENTA_ID INTEGER NOT NULL,
                FOREIGN KEY ($COL_VENTA_ID) REFERENCES ${VentaEntry.TABLE_NAME}(${VentaEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: efectivo (CTI — hija de metodo_pago)
    // PK compartida con metodo_pago mediante FK + PK simultánea
    // =========================================================

    /** Tabla hija CTI para pagos en efectivo. Su PK referencia [MetodoPagoEntry]. */
    object EfectivoEntry {
        const val TABLE_NAME     = "efectivo"
        const val COL_ID         = "id"
        const val COL_MONTO      = "monto"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID    INTEGER PRIMARY KEY,
                $COL_MONTO REAL    NOT NULL CHECK($COL_MONTO > 0),
                FOREIGN KEY ($COL_ID) REFERENCES ${MetodoPagoEntry.TABLE_NAME}(${MetodoPagoEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: tarjeta (CTI — hija de metodo_pago)
    // =========================================================

    /** Tabla hija CTI para pagos con tarjeta. Incluye comisión y monto neto. */
    object TarjetaEntry {
        const val TABLE_NAME       = "tarjeta"
        const val COL_ID           = "id"
        const val COL_MONTO        = "monto"
        const val COL_COMISION     = "comision"
        const val COL_MONTO_NETO   = "monto_neto"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID         INTEGER PRIMARY KEY,
                $COL_MONTO      REAL    NOT NULL CHECK($COL_MONTO > 0),
                $COL_COMISION   REAL    NOT NULL DEFAULT 0 CHECK($COL_COMISION >= 0),
                $COL_MONTO_NETO REAL    NOT NULL CHECK($COL_MONTO_NETO > 0),
                FOREIGN KEY ($COL_ID) REFERENCES ${MetodoPagoEntry.TABLE_NAME}(${MetodoPagoEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: transferencia (CTI — hija de metodo_pago)
    // =========================================================

    /** Tabla hija CTI para transferencias bancarias. Agrega campo opcional de referencia. */
    object TransferenciaEntry {
        const val TABLE_NAME       = "transferencia"
        const val COL_ID           = "id"
        const val COL_MONTO        = "monto"
        const val COL_COMISION     = "comision"
        const val COL_MONTO_NETO   = "monto_neto"
        const val COL_REFERENCIA   = "referencia"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID         INTEGER PRIMARY KEY,
                $COL_MONTO      REAL    NOT NULL CHECK($COL_MONTO > 0),
                $COL_COMISION   REAL    NOT NULL DEFAULT 0 CHECK($COL_COMISION >= 0),
                $COL_MONTO_NETO REAL    NOT NULL CHECK($COL_MONTO_NETO > 0),
                $COL_REFERENCIA TEXT,
                FOREIGN KEY ($COL_ID) REFERENCES ${MetodoPagoEntry.TABLE_NAME}(${MetodoPagoEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: jornada_venta
    // Módulo: M2 — Operación de Venta Diaria
    // RF asociado: RF-05
    // =========================================================

    /** Representa una sesión de trabajo diaria del vendedor. Solo puede haber una jornada activa a la vez. */
    object JornadaVentaEntry {
        const val TABLE_NAME          = "jornada_venta"
        const val COL_ID              = "id"
        const val COL_FECHA_INICIO    = "fecha_inicio"
        const val COL_FECHA_CIERRE    = "fecha_cierre"
        const val COL_FONDO_CAPITAL   = "fondo_capital"
        const val COL_ACTIVA          = "activa"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID             INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FECHA_INICIO   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                $COL_FECHA_CIERRE   TEXT,
                $COL_FONDO_CAPITAL  REAL    NOT NULL DEFAULT 0 CHECK($COL_FONDO_CAPITAL >= 0),
                $COL_ACTIVA         INTEGER NOT NULL DEFAULT 1
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: carga_diaria
    // Módulo: M2 — Operación de Venta Diaria
    // RF asociado: RF-04
    // Composición con jornada_venta (vive y muere con la jornada)
    // =========================================================

    /** Registro de los productos cargados para una jornada específica. Único por jornada+producto. */
    object CargaDiariaEntry {
        const val TABLE_NAME              = "carga_diaria"
        const val COL_ID                  = "id"
        const val COL_JORNADA_ID          = "jornada_id"
        const val COL_PRODUCTO_ID         = "producto_id"
        const val COL_CANTIDAD_CARGA      = "cantidad_carga"
        const val COL_CANTIDAD_VENDIDA    = "cantidad_vendida"
        const val COL_CANTIDAD_MERMA      = "cantidad_merma"
        const val COL_CANTIDAD_SOBRANTE   = "cantidad_sobrante"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID                 INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_JORNADA_ID         INTEGER NOT NULL,
                $COL_PRODUCTO_ID        INTEGER NOT NULL,
                $COL_CANTIDAD_CARGA     INTEGER NOT NULL DEFAULT 0 CHECK($COL_CANTIDAD_CARGA >= 0),
                $COL_CANTIDAD_VENDIDA   INTEGER NOT NULL DEFAULT 0 CHECK($COL_CANTIDAD_VENDIDA >= 0),
                $COL_CANTIDAD_MERMA     INTEGER NOT NULL DEFAULT 0 CHECK($COL_CANTIDAD_MERMA >= 0),
                $COL_CANTIDAD_SOBRANTE  INTEGER NOT NULL DEFAULT 0 CHECK($COL_CANTIDAD_SOBRANTE >= 0),
                FOREIGN KEY ($COL_JORNADA_ID)  REFERENCES ${JornadaVentaEntry.TABLE_NAME}(${JornadaVentaEntry.COL_ID}),
                FOREIGN KEY ($COL_PRODUCTO_ID) REFERENCES ${ProductoEntry.TABLE_NAME}(${ProductoEntry.COL_ID}),
                UNIQUE ($COL_JORNADA_ID, $COL_PRODUCTO_ID)
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: venta
    // Módulo: M2 — Operación de Venta Diaria
    // RF asociado: RF-06
    // =========================================================

    /** Cabecera de una venta. El detalle de ítems vive en [DetalleVentaEntry]. */
    object VentaEntry {
        const val TABLE_NAME        = "venta"
        const val COL_ID            = "id"
        const val COL_JORNADA_ID    = "jornada_id"
        const val COL_FECHA_HORA    = "fecha_hora"
        const val COL_TOTAL         = "total"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_JORNADA_ID  INTEGER NOT NULL,
                $COL_FECHA_HORA  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                $COL_TOTAL       REAL    NOT NULL CHECK($COL_TOTAL > 0),
                FOREIGN KEY ($COL_JORNADA_ID) REFERENCES ${JornadaVentaEntry.TABLE_NAME}(${JornadaVentaEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: detalle_venta
    // Módulo: M2 — Operación de Venta Diaria
    // RF asociado: RF-06
    // CRÍTICO: precio_unitario es snapshot inmutable del precio
    // al momento de la venta — nunca referenciar precio actual (D8)
    // =========================================================

    /**
     * Línea de ítem de una venta. [COL_PRECIO_UNITARIO] es un snapshot inmutable (D8):
     * representa el precio al momento de la venta, independiente del precio actual del producto.
     */
    object DetalleVentaEntry {
        const val TABLE_NAME          = "detalle_venta"
        const val COL_ID              = "id"
        const val COL_VENTA_ID        = "venta_id"
        const val COL_PRODUCTO_ID     = "producto_id"
        const val COL_CANTIDAD        = "cantidad"
        const val COL_PRECIO_UNITARIO = "precio_unitario"  // snapshot inmutable — D8
        const val COL_SUBTOTAL        = "subtotal"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID              INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_VENTA_ID        INTEGER NOT NULL,
                $COL_PRODUCTO_ID     INTEGER NOT NULL,
                $COL_CANTIDAD        INTEGER NOT NULL CHECK($COL_CANTIDAD > 0),
                $COL_PRECIO_UNITARIO REAL    NOT NULL CHECK($COL_PRECIO_UNITARIO > 0),
                $COL_SUBTOTAL        REAL    NOT NULL CHECK($COL_SUBTOTAL > 0),
                FOREIGN KEY ($COL_VENTA_ID)    REFERENCES ${VentaEntry.TABLE_NAME}(${VentaEntry.COL_ID}),
                FOREIGN KEY ($COL_PRODUCTO_ID) REFERENCES ${ProductoEntry.TABLE_NAME}(${ProductoEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: movimiento_inventario
    // Módulo: M1/M2 — trazabilidad de stock
    // RF asociado: RF-03, RF-04, RF-07 (Mermas)
    // ref_venta_id y ref_carga_id son mutuamente excluyentes
    // D11: precio_perdida = snapshot de precioVenta al momento
    //      de la merma. Solo se popula en tipo='MERMA'.
    //      En todos los demás tipos el valor es 0.0.
    // =========================================================

    /**
     * Auditoría de todos los movimientos de stock. [COL_REF_VENTA_ID] y [COL_REF_CARGA_ID]
     * son mutuamente excluyentes según el tipo. [COL_PRECIO_PERDIDA] solo aplica en `tipo='MERMA'` (D11).
     */
    object MovimientoInventarioEntry {
        const val TABLE_NAME          = "movimiento_inventario"
        const val COL_ID              = "id"
        const val COL_PRODUCTO_ID     = "producto_id"
        const val COL_TIPO            = "tipo"   // 'CARGA' | 'VENTA' | 'MERMA' | 'DEVOLUCION' | 'AJUSTE'
        const val COL_CANTIDAD        = "cantidad"
        const val COL_FECHA_HORA      = "fecha_hora"
        const val COL_REF_VENTA_ID    = "ref_venta_id"
        const val COL_REF_CARGA_ID    = "ref_carga_id"
        const val COL_MOTIVO          = "motivo"
        const val COL_PRECIO_PERDIDA  = "precio_perdida"  // D11: snapshot precio en merma

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID              INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PRODUCTO_ID     INTEGER NOT NULL,
                $COL_TIPO            TEXT    NOT NULL CHECK($COL_TIPO IN ('CARGA','VENTA','MERMA','DEVOLUCION','AJUSTE')),
                $COL_CANTIDAD        INTEGER NOT NULL,
                $COL_FECHA_HORA      TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                $COL_REF_VENTA_ID    INTEGER,
                $COL_REF_CARGA_ID    INTEGER,
                $COL_MOTIVO          TEXT,
                $COL_PRECIO_PERDIDA  REAL    NOT NULL DEFAULT 0.0 CHECK($COL_PRECIO_PERDIDA >= 0),
                FOREIGN KEY ($COL_PRODUCTO_ID)  REFERENCES ${ProductoEntry.TABLE_NAME}(${ProductoEntry.COL_ID}),
                FOREIGN KEY ($COL_REF_VENTA_ID) REFERENCES ${VentaEntry.TABLE_NAME}(${VentaEntry.COL_ID}),
                FOREIGN KEY ($COL_REF_CARGA_ID) REFERENCES ${CargaDiariaEntry.TABLE_NAME}(${CargaDiariaEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    // =========================================================
    // TABLA: corte_caja
    // Módulo: M3 — Cierre y Conciliación
    // RF asociado: RF-10, RF-11, RF-12, RF-13
    // =========================================================

    /**
     * Resumen de cierre de jornada. [COL_TOTAL_PERDIDA_MERMAS] se calcula sumando
     * `precio_perdida * cantidad` de todos los movimientos con `tipo = 'MERMA'` de la jornada.
     */
    object CorteCajaEntry {
        const val TABLE_NAME               = "corte_caja"
        const val COL_ID                   = "id"
        const val COL_JORNADA_ID           = "jornada_id"
        const val COL_EFECTIVO_ESPERADO    = "efectivo_esperado"
        const val COL_EFECTIVO_REAL        = "efectivo_real"
        const val COL_DISCREPANCIA         = "discrepancia"
        const val COL_TOTAL_PERDIDA_MERMAS = "total_perdida_mermas"
        const val COL_FECHA_HORA           = "fecha_hora"

        const val SQL_CREATE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID                   INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_JORNADA_ID           INTEGER NOT NULL UNIQUE,
                $COL_EFECTIVO_ESPERADO    REAL    NOT NULL DEFAULT 0,
                $COL_EFECTIVO_REAL        REAL    NOT NULL DEFAULT 0,
                $COL_DISCREPANCIA         REAL    NOT NULL DEFAULT 0,
                $COL_TOTAL_PERDIDA_MERMAS REAL    NOT NULL DEFAULT 0,
                $COL_FECHA_HORA           TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY ($COL_JORNADA_ID) REFERENCES ${JornadaVentaEntry.TABLE_NAME}(${JornadaVentaEntry.COL_ID})
            )
        """

        const val SQL_DROP = "DROP TABLE IF EXISTS $TABLE_NAME"
    }
}