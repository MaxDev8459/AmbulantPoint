package com.ambulantpoint.ui.conciliacion

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ambulantpoint.R
import com.ambulantpoint.data.dao.CargaDiariaDao
import com.ambulantpoint.data.dao.CategoriaDao
import com.ambulantpoint.data.dao.CorteCajaDao
import com.ambulantpoint.data.dao.DetalleVentaDao
import com.ambulantpoint.data.dao.JornadaVentaDao
import com.ambulantpoint.data.dao.MetodoPagoDao
import com.ambulantpoint.data.dao.MovimientoInventarioDao
import com.ambulantpoint.data.dao.ProductoDao
import com.ambulantpoint.data.dao.VentaDao
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.JornadaVenta
import com.ambulantpoint.databinding.ActivityReporteBinding
import com.ambulantpoint.databinding.ItemJornadaHistorialBinding
import com.ambulantpoint.service.VentaService
import kotlin.math.abs

/**
 * ReporteActivity — CU-05 (detalle de cierre) + CU-11 (historial de jornadas).
 *
 * Modos:
 * - Con EXTRA_JORNADA_ID → muestra scrollDetalle con el reporte de esa jornada.
 * - Sin EXTRA_JORNADA_ID → muestra llHistorial con la lista de jornadas cerradas.
 *
 * RF asociado: RF-10, RF-11, RF-12, RF-19
 */
class ReporteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReporteBinding
    private lateinit var ventaService: VentaService
    private lateinit var adapter: JornadaHistorialAdapter

    companion object {
        const val EXTRA_JORNADA_ID = "extra_jornada_id_reporte"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarServicio()
        configurarToolbar()
        configurarRecycler()

        val jornadaId = intent.getLongExtra(EXTRA_JORNADA_ID, -1L)
        if (jornadaId != -1L) {
            mostrarDetalle(jornadaId)
        } else {
            mostrarHistorial()
        }
    }

    // ─────────────────────────────────────────────────────────
    // INICIALIZACIÓN
    // ─────────────────────────────────────────────────────────

    private fun inicializarServicio() {
        val dbHelper = DatabaseHelper.getInstance(this)
        ventaService = VentaService(
            dbHelper                = dbHelper,
            jornadaVentaDao         = JornadaVentaDao(dbHelper),
            cargaDiariaDao          = CargaDiariaDao(dbHelper),
            ventaDao                = VentaDao(dbHelper),
            detalleVentaDao         = DetalleVentaDao(dbHelper),
            movimientoInventarioDao = MovimientoInventarioDao(dbHelper),
            metodoPagoDao           = MetodoPagoDao(dbHelper),
            productoDao             = ProductoDao(dbHelper),
            categoriaDao            = CategoriaDao(dbHelper),
            corteCajaDao            = CorteCajaDao(dbHelper)
        )
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun configurarRecycler() {
        adapter = JornadaHistorialAdapter { jornadaId -> mostrarDetalle(jornadaId) }
        binding.rvHistorial.apply {
            layoutManager = LinearLayoutManager(this@ReporteActivity)
            adapter = this@ReporteActivity.adapter
        }
    }

    // ─────────────────────────────────────────────────────────
    // VISTA HISTORIAL
    // ─────────────────────────────────────────────────────────

    private fun mostrarHistorial() {
        binding.scrollDetalle.visibility = View.GONE
        binding.llHistorial.visibility   = View.VISIBLE

        val jornadas = ventaService.getJornadasCerradas()
        if (jornadas.isEmpty()) {
            binding.tvSinJornadas.visibility = View.VISIBLE
            binding.rvHistorial.visibility   = View.GONE
        } else {
            binding.tvSinJornadas.visibility = View.GONE
            binding.rvHistorial.visibility   = View.VISIBLE
            val resumenes = jornadas.map { jornada ->
                JornadaResumen(
                    jornada        = jornada,
                    totalVentas    = ventaService.getTotalVentasByJornada(jornada.id),
                    cantidadVentas = ventaService.getCountVentasByJornada(jornada.id)
                )
            }
            adapter.actualizar(resumenes)
        }
    }

    // ─────────────────────────────────────────────────────────
    // VISTA DETALLE
    // ─────────────────────────────────────────────────────────

    private fun mostrarDetalle(jornadaId: Long) {
        val jornada = ventaService.getJornadaById(jornadaId)
        val corte   = ventaService.getCorteCajaByJornada(jornadaId)

        if (jornada == null || corte == null) {
            mostrarHistorial()
            return
        }

        binding.llHistorial.visibility   = View.GONE
        binding.scrollDetalle.visibility = View.VISIBLE

        // Jornada
        binding.tvFechaInicio.text  = "Inicio: ${jornada.fechaInicio}"
        binding.tvFechaCierre.text  = "Cierre: ${jornada.fechaCierre ?: "-"}"
        binding.tvFondoInicial.text = "Fondo inicial: ${"$%.2f".format(jornada.fondoCapital)}"

        // Ventas
        val totalVentas      = ventaService.getTotalVentasByJornada(jornadaId)
        val cantidadVentas   = ventaService.getCountVentasByJornada(jornadaId)
        val sumEfectivo      = ventaService.getSumEfectivoByJornada(jornadaId)
        val sumTarjeta       = ventaService.getSumTarjetaByJornada(jornadaId)
        val sumTransferencia = ventaService.getSumTransferenciaByJornada(jornadaId)

        binding.tvTotalVentas.text         = "Total vendido: ${"$%.2f".format(totalVentas)}"
        binding.tvCantidadVentas.text      = "Número de ventas: $cantidadVentas"
        binding.tvEfectivoVentas.text      = "Efectivo: ${"$%.2f".format(sumEfectivo)}"
        binding.tvTarjetaVentas.text       = "Tarjeta: ${"$%.2f".format(sumTarjeta)}"
        binding.tvTransferenciaVentas.text = "Transferencia: ${"$%.2f".format(sumTransferencia)}"

        // Conciliación
        binding.tvEfectivoEsperado.text = "Efectivo esperado: ${"$%.2f".format(corte.efectivoEsperado)}"
        binding.tvEfectivoReal.text     = "Efectivo real contado: ${"$%.2f".format(corte.efectivoReal)}"
        val discStr = "$%.2f".format(abs(corte.discrepancia))
        binding.tvDiscrepancia.text = when {
            corte.discrepancia > 0.005  -> "Sobrante: +$$discStr"
            corte.discrepancia < -0.005 -> "Faltante: -$$discStr"
            else                        -> "Sin discrepancia"
        }
        binding.tvDiscrepancia.setTextColor(
            when {
                corte.discrepancia < -0.005 -> getColor(android.R.color.holo_red_dark)
                corte.discrepancia > 0.005  -> getColor(android.R.color.holo_green_dark)
                else                        -> getColor(R.color.colorOnBackground)
            }
        )

        // Mermas
        binding.tvTotalMermas.text = "Impacto financiero: ${"$%.2f".format(corte.totalPerdidaMermas)}"

        binding.btnVerHistorial.setOnClickListener { mostrarHistorial() }
    }

    // ─────────────────────────────────────────────────────────
    // DATA CLASS LOCAL
    // ─────────────────────────────────────────────────────────

    data class JornadaResumen(
        val jornada: JornadaVenta,
        val totalVentas: Double,
        val cantidadVentas: Int
    )

    // ─────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────

    inner class JornadaHistorialAdapter(
        private val onTap: (jornadaId: Long) -> Unit
    ) : RecyclerView.Adapter<JornadaHistorialAdapter.ViewHolder>() {

        private val items = mutableListOf<JornadaResumen>()

        fun actualizar(nuevos: List<JornadaResumen>) {
            items.clear()
            items.addAll(nuevos)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemJornadaHistorialBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class ViewHolder(private val b: ItemJornadaHistorialBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(resumen: JornadaResumen) {
                b.tvFechaJornada.text         = resumen.jornada.fechaInicio.take(10)
                b.tvResumenVentasJornada.text  = "${resumen.cantidadVentas} venta(s)"
                b.tvTotalJornada.text          = "${"$%.2f".format(resumen.totalVentas)}"
                b.root.setOnClickListener { onTap(resumen.jornada.id) }
            }
        }
    }
}
