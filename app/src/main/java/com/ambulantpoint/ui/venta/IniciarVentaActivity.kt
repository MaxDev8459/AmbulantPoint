package com.ambulantpoint.ui.venta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.ambulantpoint.data.model.Producto
import com.ambulantpoint.databinding.ActivityIniciarVentaBinding
import com.ambulantpoint.databinding.ItemProductoCargaBinding
import com.ambulantpoint.service.BusinessRuleException
import com.ambulantpoint.service.ValidationException
import com.ambulantpoint.service.VentaService

/**
 * IniciarVentaActivity — CU-01: Cargar Venta Diaria.
 *
 * Flujo:
 * - Si ya existe jornada activa → redirige a VentaActivity directamente.
 * - Usuario selecciona productos con cantidad > 0 e ingresa fondo de caja.
 * - Al confirmar: VentaService.iniciarJornada() → navega a VentaActivity.
 *
 * RF asociado: RF-01, RF-02, RF-03, RF-04
 */
class IniciarVentaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIniciarVentaBinding
    private lateinit var ventaService: VentaService
    private lateinit var adapter: ProductoCargaAdapter

    // Map mutable: productoId → cantidad seleccionada por el usuario
    private val seleccion = mutableMapOf<Long, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIniciarVentaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarServicio()
        configurarToolbar()

        val jornadaActiva = ventaService.getJornadaActiva()
        if (jornadaActiva != null) {
            navegarAVenta(jornadaActiva.id)
            return
        }

        configurarRecycler()
        configurarBotonIniciar()
        cargarProductos()
    }

    // ─────────────────────────────────────────────────────────
    // INICIALIZACIÓN
    // ─────────────────────────────────────────────────────────

    private fun inicializarServicio() {
        val dbHelper = DatabaseHelper.getInstance(this)
        ventaService = VentaService(
            dbHelper                 = dbHelper,
            jornadaVentaDao          = JornadaVentaDao(dbHelper),
            cargaDiariaDao           = CargaDiariaDao(dbHelper),
            ventaDao                 = VentaDao(dbHelper),
            detalleVentaDao          = DetalleVentaDao(dbHelper),
            movimientoInventarioDao  = MovimientoInventarioDao(dbHelper),
            metodoPagoDao            = MetodoPagoDao(dbHelper),
            productoDao              = ProductoDao(dbHelper),
            categoriaDao             = CategoriaDao(dbHelper),
            corteCajaDao             = CorteCajaDao(dbHelper)
        )
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun configurarRecycler() {
        adapter = ProductoCargaAdapter { productoId, cantidad ->
            if (cantidad > 0) seleccion[productoId] = cantidad
            else seleccion.remove(productoId)
        }
        binding.recyclerProductosCarga.apply {
            layoutManager = LinearLayoutManager(this@IniciarVentaActivity)
            adapter = this@IniciarVentaActivity.adapter
        }
    }

    private fun configurarBotonIniciar() {
        binding.btnIniciarJornada.setOnClickListener { intentarIniciarJornada() }
    }

    // ─────────────────────────────────────────────────────────
    // CARGA DE DATOS
    // ─────────────────────────────────────────────────────────

    private fun cargarProductos() {
        val productos = ventaService.getProductosParaCarga()
        val hayProductos = productos.isNotEmpty()

        binding.tvSinStock.visibility            = if (hayProductos) View.GONE else View.VISIBLE
        binding.recyclerProductosCarga.visibility = if (hayProductos) View.VISIBLE else View.GONE
        binding.btnIniciarJornada.visibility     = if (hayProductos) View.VISIBLE else View.GONE

        if (hayProductos) adapter.actualizar(productos)
    }

    // ─────────────────────────────────────────────────────────
    // ACCIÓN: INICIAR JORNADA
    // ─────────────────────────────────────────────────────────

    private fun intentarIniciarJornada() {
        val fondoTexto = binding.etFondoCaja.text.toString().trim()
        val fondoCaja  = if (fondoTexto.isBlank()) 0.0 else fondoTexto.toDoubleOrNull()

        if (fondoCaja == null) {
            Toast.makeText(this, "Ingresa un monto de fondo válido (ej: 150.00).", Toast.LENGTH_SHORT).show()
            binding.etFondoCaja.requestFocus()
            return
        }

        if (fondoCaja < 0) {
            Toast.makeText(this, "El fondo de caja no puede ser negativo.", Toast.LENGTH_SHORT).show()
            return
        }

        // Flujo alterno A2: fondo = $0 → advertencia, permite continuar
        if (fondoCaja == 0.0) {
            mostrarAdvertenciaFondoCero { confirmarIniciar(fondoCaja) }
            return
        }

        confirmarIniciar(fondoCaja)
    }

    private fun mostrarAdvertenciaFondoCero(onConfirmado: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sin fondo de caja")
            .setMessage("Estás iniciando la jornada con $0.00 de fondo. ¿Deseas continuar?")
            .setPositiveButton("Continuar") { _, _ -> onConfirmado() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarIniciar(fondoCaja: Double) {
        try {
            val jornadaId = ventaService.iniciarJornada(fondoCaja, seleccion)
            Toast.makeText(this, "Jornada iniciada.", Toast.LENGTH_SHORT).show()
            navegarAVenta(jornadaId)
        } catch (e: ValidationException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        } catch (e: BusinessRuleException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun navegarAVenta(jornadaId: Long) {
        startActivity(
            Intent(this, VentaActivity::class.java).apply {
                putExtra(VentaActivity.EXTRA_JORNADA_ID, jornadaId)
            }
        )
        finish()
    }

    // ─────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────

    inner class ProductoCargaAdapter(
        private val onCantidadCambiada: (productoId: Long, cantidad: Int) -> Unit
    ) : RecyclerView.Adapter<ProductoCargaAdapter.ViewHolder>() {

        private val items = mutableListOf<Producto>()
        private val cantidades = mutableMapOf<Long, Int>()

        fun actualizar(nuevos: List<Producto>) {
            items.clear()
            items.addAll(nuevos)
            nuevos.forEach { cantidades[it.id] = 0 }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemProductoCargaBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(private val b: ItemProductoCargaBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(producto: Producto) {
                b.tvNombreCarga.text            = producto.nombre
                b.tvCategoriaPrecioCarga.text   = "$%.2f".format(producto.precioVenta)
                b.tvStockDisponibleCarga.text   = "Disponible: ${producto.stockGeneral}"

                val cantidadActual = cantidades[producto.id] ?: 0
                b.tvCantidadCarga.text = cantidadActual.toString()

                b.btnMenos.setOnClickListener {
                    val actual = cantidades[producto.id] ?: 0
                    if (actual > 0) {
                        val nuevo = actual - 1
                        cantidades[producto.id] = nuevo
                        b.tvCantidadCarga.text = nuevo.toString()
                        onCantidadCambiada(producto.id, nuevo)
                    }
                }

                b.btnMas.setOnClickListener {
                    val actual = cantidades[producto.id] ?: 0
                    if (actual < producto.stockGeneral) {
                        val nuevo = actual + 1
                        cantidades[producto.id] = nuevo
                        b.tvCantidadCarga.text = nuevo.toString()
                        onCantidadCambiada(producto.id, nuevo)
                    } else {
                        Toast.makeText(
                            this@IniciarVentaActivity,
                            "Máximo disponible: ${producto.stockGeneral}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                b.btnCargarTodo.setOnClickListener {
                    val stockTotal = producto.stockGeneral
                    cantidades[producto.id] = stockTotal
                    b.tvCantidadCarga.text = stockTotal.toString()
                    onCantidadCambiada(producto.id, stockTotal)
                }
            }
        }
    }
}
