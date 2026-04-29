package com.ambulantpoint.service

import com.ambulantpoint.data.dao.CargaDiariaDao
import com.ambulantpoint.data.dao.CategoriaDao
import com.ambulantpoint.data.dao.CorteCajaDao
import com.ambulantpoint.data.dao.DetalleVentaDao
import com.ambulantpoint.data.dao.JornadaVentaDao
import com.ambulantpoint.data.dao.MetodoPagoDao
import com.ambulantpoint.data.dao.MovimientoInventarioDao
import com.ambulantpoint.data.dao.ProductoDao
import com.ambulantpoint.data.dao.VentaDao
import com.ambulantpoint.data.db.DatabaseContract.ProductoEntry
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.CargaDiaria
import com.ambulantpoint.data.model.Categoria
import com.ambulantpoint.data.model.CorteCaja
import com.ambulantpoint.data.model.DetalleVenta
import com.ambulantpoint.data.model.JornadaVenta
import com.ambulantpoint.data.model.MovimientoInventario
import com.ambulantpoint.data.model.Producto
import com.ambulantpoint.data.model.Venta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VentaService(
    private val dbHelper: DatabaseHelper,
    private val jornadaVentaDao: JornadaVentaDao,
    private val cargaDiariaDao: CargaDiariaDao,
    private val ventaDao: VentaDao,
    private val detalleVentaDao: DetalleVentaDao,
    private val movimientoInventarioDao: MovimientoInventarioDao,
    private val metodoPagoDao: MetodoPagoDao,
    private val productoDao: ProductoDao,
    private val categoriaDao: CategoriaDao,
    private val corteCajaDao: CorteCajaDao
) {

    // ─────────────────────────────────────────────────────────
    // CONSULTAS DE ESTADO
    // ─────────────────────────────────────────────────────────

    fun getJornadaActiva(): JornadaVenta? = jornadaVentaDao.getActiva()

    // Productos cargables: activos y con stock general > 0 (para CU-01)
    fun getProductosParaCarga(): List<Producto> =
        productoDao.findAll(soloActivos = true).filter { it.stockGeneral > 0 }

    // Pares (CargaDiaria, Producto) de la jornada (para CU-02 y Slash Filter)
    fun getCargasConProducto(jornadaId: Long): List<Pair<CargaDiaria, Producto>> {
        val cargas = cargaDiariaDao.getByJornada(jornadaId)
        return cargas.mapNotNull { carga ->
            val producto = productoDao.findById(carga.productoId)
            if (producto != null) Pair(carga, producto) else null
        }
    }

    // Categorías que aún tienen stock diario > 0 (para el Slash Filter)
    fun getCategoriasConStockDiario(jornadaId: Long): List<Categoria> {
        val cargasActivas = cargaDiariaDao.getByJornada(jornadaId).filter { it.hayStock() }
        val categoriaIds = cargasActivas.mapNotNull { carga ->
            productoDao.findById(carga.productoId)?.categoriaId?.toInt()
        }.distinct()
        return categoriaIds.mapNotNull { categoriaDao.findById(it) }
    }

    // ─────────────────────────────────────────────────────────
    // CU-01: INICIAR JORNADA
    // ─────────────────────────────────────────────────────────

    // seleccion: Map<productoId, cantidadACarga>. Se ignoran entradas con cantidad <= 0.
    // Retorna el id de la JornadaVenta creada.
    // Toda la operación es atómica: si algo falla, ningún cambio persiste.
    fun iniciarJornada(fondoCapital: Double, seleccion: Map<Long, Int>): Long {
        val seleccionFiltrada = seleccion.filter { it.value > 0 }

        if (seleccionFiltrada.isEmpty())
            throw ValidationException("Selecciona al menos 1 producto con cantidad mayor a 0.")

        if (jornadaVentaDao.getActiva() != null)
            throw BusinessRuleException("Ya existe una jornada activa. Ciérrala antes de iniciar una nueva.")

        for ((productoId, cantidad) in seleccionFiltrada) {
            val producto = productoDao.findById(productoId)
                ?: throw BusinessRuleException("Producto no encontrado (id=$productoId).")
            if (producto.stockGeneral < cantidad)
                throw BusinessRuleException(
                    "Stock insuficiente para \"${producto.nombre}\". " +
                    "Disponible: ${producto.stockGeneral}, solicitado: $cantidad."
                )
        }

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val jornadaId = jornadaVentaDao.insert(JornadaVenta(fondoCapital = fondoCapital))
            if (jornadaId == -1L) throw Exception("Error al crear la jornada en BD.")

            for ((productoId, cantidad) in seleccionFiltrada) {
                val cargaId = cargaDiariaDao.insert(
                    CargaDiaria(
                        jornadaId        = jornadaId,
                        productoId       = productoId,
                        cantidadCarga    = cantidad,
                        cantidadSobrante = cantidad
                    )
                )
                // Decremento directo de stock_general — evita transacción anidada de ProductoDao
                db.execSQL(
                    "UPDATE ${ProductoEntry.TABLE_NAME} " +
                    "SET ${ProductoEntry.COL_STOCK_GENERAL} = ${ProductoEntry.COL_STOCK_GENERAL} - ? " +
                    "WHERE ${ProductoEntry.COL_ID} = ?",
                    arrayOf(cantidad, productoId)
                )
                movimientoInventarioDao.insert(
                    MovimientoInventario(
                        productoId = productoId,
                        tipo       = MovimientoInventario.TIPO_CARGA,
                        cantidad   = cantidad,
                        refCargaId = cargaId
                    )
                )
            }

            db.setTransactionSuccessful()
            return jornadaId
        } finally {
            db.endTransaction()
        }
    }

    // ─────────────────────────────────────────────────────────
    // CU-02: REGISTRAR VENTA
    // ─────────────────────────────────────────────────────────

    // precio_unitario se captura como snapshot del precio actual del producto (D8).
    // Retorna el id de la Venta creada.
    fun registrarVenta(
        jornadaId: Long,
        productoId: Long,
        cantidad: Int,
        metodoPago: MetodoPagoInput
    ): Long {
        val carga = cargaDiariaDao.getByJornadaYProducto(jornadaId, productoId)
            ?: throw BusinessRuleException("El producto no está en la carga de hoy.")

        if (cantidad <= 0)
            throw ValidationException("La cantidad debe ser mayor a 0.")

        if (carga.cantidadSobrante < cantidad)
            throw BusinessRuleException(
                "Stock diario insuficiente. Disponible: ${carga.cantidadSobrante}."
            )

        val producto = productoDao.findById(productoId)
            ?: throw BusinessRuleException("Producto no encontrado.")

        val precioUnitario = producto.precioVenta
        val subtotal       = precioUnitario * cantidad
        val total          = metodoPago.monto

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val ventaId = ventaDao.insert(Venta(jornadaId = jornadaId, total = total))

            detalleVentaDao.insert(
                DetalleVenta(
                    ventaId        = ventaId,
                    productoId     = productoId,
                    cantidad       = cantidad,
                    precioUnitario = precioUnitario,
                    subtotal       = subtotal
                )
            )

            val tipo = when (metodoPago) {
                is MetodoPagoInput.Efectivo      -> "EFECTIVO"
                is MetodoPagoInput.Tarjeta       -> "TARJETA"
                is MetodoPagoInput.Transferencia -> "TRANSFERENCIA"
            }
            val metodoId = metodoPagoDao.insertMetodoPago(ventaId, tipo)
            when (metodoPago) {
                is MetodoPagoInput.Efectivo ->
                    metodoPagoDao.insertEfectivo(metodoId, metodoPago.monto)
                is MetodoPagoInput.Tarjeta ->
                    metodoPagoDao.insertTarjeta(metodoId, metodoPago.monto, metodoPago.comision, metodoPago.montoNeto)
                is MetodoPagoInput.Transferencia ->
                    metodoPagoDao.insertTransferencia(metodoId, metodoPago.monto, metodoPago.comision, metodoPago.montoNeto, metodoPago.referencia)
            }

            val nuevoSobrante = carga.cantidadSobrante - cantidad
            cargaDiariaDao.incrementarVendida(carga.id, cantidad)
            cargaDiariaDao.actualizarSobrante(carga.id, nuevoSobrante)

            movimientoInventarioDao.insert(
                MovimientoInventario(
                    productoId = productoId,
                    tipo       = MovimientoInventario.TIPO_VENTA,
                    cantidad   = cantidad,
                    refVentaId = ventaId
                )
            )

            db.setTransactionSuccessful()
            return ventaId
        } finally {
            db.endTransaction()
        }
    }

    // ─────────────────────────────────────────────────────────
    // CU-04: REGISTRAR MERMA
    // ─────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────
    // CU-05: CERRAR JORNADA (RF-10, RF-11, RF-13) + CU-06 (RF-12)
    // ─────────────────────────────────────────────────────────

    // Retorna el jornadaId cerrado. Toda la operación es atómica.
    fun cerrarJornada(jornadaId: Long, efectivoReal: Double): Long {
        val jornada = jornadaVentaDao.findById(jornadaId)
            ?: throw BusinessRuleException("Jornada no encontrada (id=$jornadaId).")
        if (!jornada.activa)
            throw BusinessRuleException("La jornada ya fue cerrada.")
        if (efectivoReal < 0)
            throw ValidationException("El efectivo real no puede ser negativo.")

        val cargas             = cargaDiariaDao.getByJornada(jornadaId)
        val efectivoEsperado   = metodoPagoDao.getSumEfectivoByJornada(jornadaId)
        val mermas             = movimientoInventarioDao.getMermasByJornada(jornadaId)
        val totalPerdidaMermas = mermas.sumOf { it.precioPerdida * it.cantidad }
        val discrepancia       = efectivoReal - efectivoEsperado
        val fechaCierre        = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // RF-13: Retornar sobrantes a stock_general
            for (carga in cargas) {
                if (carga.cantidadSobrante > 0) {
                    db.execSQL(
                        "UPDATE ${ProductoEntry.TABLE_NAME} " +
                        "SET ${ProductoEntry.COL_STOCK_GENERAL} = ${ProductoEntry.COL_STOCK_GENERAL} + ? " +
                        "WHERE ${ProductoEntry.COL_ID} = ?",
                        arrayOf(carga.cantidadSobrante, carga.productoId)
                    )
                    movimientoInventarioDao.insert(
                        MovimientoInventario(
                            productoId = carga.productoId,
                            tipo       = MovimientoInventario.TIPO_DEVOLUCION,
                            cantidad   = carga.cantidadSobrante,
                            refCargaId = carga.id
                        )
                    )
                }
            }

            // CU-06: Crear CorteCaja (RF-12)
            val corteId = corteCajaDao.insert(
                CorteCaja(
                    jornadaId          = jornadaId,
                    efectivoEsperado   = efectivoEsperado,
                    efectivoReal       = efectivoReal,
                    discrepancia       = discrepancia,
                    totalPerdidaMermas = totalPerdidaMermas
                )
            )
            if (corteId == -1L) throw Exception("Error al generar el corte de caja.")

            jornadaVentaDao.cerrar(jornadaId, fechaCierre)
            db.setTransactionSuccessful()
            return jornadaId
        } finally {
            db.endTransaction()
        }
    }

    // ─────────────────────────────────────────────────────────
    // CONSULTAS PARA REPORTES (fachadas sobre DAOs)
    // ─────────────────────────────────────────────────────────

    fun getJornadaById(jornadaId: Long): JornadaVenta?    = jornadaVentaDao.findById(jornadaId)
    fun getJornadasCerradas(): List<JornadaVenta>          = jornadaVentaDao.findAllCerradas()
    fun getCorteCajaByJornada(jornadaId: Long): CorteCaja? = corteCajaDao.findByJornada(jornadaId)
    fun getTotalVentasByJornada(jornadaId: Long): Double   = ventaDao.getSumByJornada(jornadaId)
    fun getCountVentasByJornada(jornadaId: Long): Int      = ventaDao.getCountByJornada(jornadaId)
    fun getSumEfectivoByJornada(jornadaId: Long): Double   = metodoPagoDao.getSumEfectivoByJornada(jornadaId)
    fun getSumTarjetaByJornada(jornadaId: Long): Double    = metodoPagoDao.getSumTarjetaByJornada(jornadaId)
    fun getSumTransferenciaByJornada(jornadaId: Long): Double = metodoPagoDao.getSumTransferenciaByJornada(jornadaId)

    // precio_perdida = snapshot de precioVenta al momento de la merma (D11).
    fun registrarMerma(jornadaId: Long, productoId: Long, cantidad: Int, motivo: String?) {
        if (cantidad <= 0)
            throw ValidationException("La cantidad de merma debe ser mayor a 0.")

        val carga = cargaDiariaDao.getByJornadaYProducto(jornadaId, productoId)
            ?: throw BusinessRuleException("El producto no está en la carga de hoy.")

        if (carga.cantidadSobrante < cantidad)
            throw BusinessRuleException(
                "No hay suficiente stock disponible. Disponible: ${carga.cantidadSobrante}."
            )

        val producto = productoDao.findById(productoId)
            ?: throw BusinessRuleException("Producto no encontrado.")

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val nuevoSobrante = carga.cantidadSobrante - cantidad
            cargaDiariaDao.incrementarMerma(carga.id, cantidad)
            cargaDiariaDao.actualizarSobrante(carga.id, nuevoSobrante)

            movimientoInventarioDao.insert(
                MovimientoInventario(
                    productoId    = productoId,
                    tipo          = MovimientoInventario.TIPO_MERMA,
                    cantidad      = cantidad,
                    refCargaId    = carga.id,
                    motivo        = motivo,
                    precioPerdida = producto.precioVenta
                )
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
