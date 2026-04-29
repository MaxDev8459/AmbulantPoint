package com.ambulantpoint.ui.catalogo

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ambulantpoint.data.dao.CategoriaDao
import com.ambulantpoint.data.dao.ProductoDao
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.Producto
import com.ambulantpoint.databinding.ActivityGestionStockBinding
import com.ambulantpoint.databinding.ItemStockProductoBinding
import com.ambulantpoint.service.BusinessRuleException
import com.ambulantpoint.service.CatalogService
import com.ambulantpoint.service.ValidationException

/**
 * GestionStockActivity — Agregar stock manualmente a productos del catálogo.
 *
 * Regla critica: solo se permite INCREMENTAR stock desde esta pantalla.
 * La reduccion de stock ocurre exclusivamente via ventas (M2 CU-02)
 * o mermas (M2 CU-04). Nunca de forma manual.
 *
 * Modulo: M1 — Gestión de Catálogo
 * RF asociado: RF-18 | CU asociado: CU-10
 */
class GestionStockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestionStockBinding
    private lateinit var catalogService: CatalogService
    private lateinit var adapter: StockAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestionStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarServicio()
        configurarToolbar()
        configurarRecycler()
        cargarProductos()
    }

    // ─────────────────────────────────────────────────────────
    // INICIALIZACIÓN
    // ─────────────────────────────────────────────────────────

    private fun inicializarServicio() {
        val dbHelper = DatabaseHelper.getInstance(this)
        catalogService = CatalogService(
            categoriaDao = CategoriaDao(dbHelper),
            productoDao  = ProductoDao(dbHelper)
        )
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun configurarRecycler() {
        adapter = StockAdapter { productoId, cantidad, onExito ->
            try {
                val stockNuevo = catalogService.incrementarStockProducto(productoId, cantidad)
                onExito(stockNuevo)
                Toast.makeText(this, "Stock actualizado: $stockNuevo unidades", Toast.LENGTH_SHORT).show()
            } catch (e: ValidationException) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            } catch (e: BusinessRuleException) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvStock.apply {
            layoutManager = LinearLayoutManager(this@GestionStockActivity)
            adapter = this@GestionStockActivity.adapter
        }
    }

    // ─────────────────────────────────────────────────────────
    // CARGA DE DATOS
    // ─────────────────────────────────────────────────────────

    private fun cargarProductos() {
        val productos = catalogService.getProductosTodosActivos()
        if (productos.isEmpty()) {
            binding.tvSinProductos.visibility = View.VISIBLE
            binding.rvStock.visibility        = View.GONE
        } else {
            binding.tvSinProductos.visibility = View.GONE
            binding.rvStock.visibility        = View.VISIBLE
            adapter.actualizar(productos)
        }
    }

    // ─────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────

    inner class StockAdapter(
        private val onAgregar: (productoId: Long, cantidad: Int, onExito: (stockNuevo: Int) -> Unit) -> Unit
    ) : RecyclerView.Adapter<StockAdapter.ViewHolder>() {

        private val items = mutableListOf<Producto>()

        fun actualizar(nuevos: List<Producto>) {
            items.clear()
            items.addAll(nuevos)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemStockProductoBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class ViewHolder(private val b: ItemStockProductoBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(producto: Producto) {
                b.tvNombreStock.text = producto.nombre
                val categoria = catalogService.getCategoria(producto.categoriaId.toInt())
                b.tvCategoriaStock.text = categoria?.nombre ?: "Sin categoría"
                mostrarStock(producto.stockGeneral)

                b.btnAgregarStock.setOnClickListener {
                    val texto = b.etCantidadAgregar.text?.toString()?.trim() ?: ""
                    val cantidad = texto.toIntOrNull()

                    if (cantidad == null || cantidad <= 0) {
                        Toast.makeText(
                            this@GestionStockActivity,
                            "Ingresa una cantidad mayor a 0.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    onAgregar(producto.id, cantidad) { stockNuevo ->
                        mostrarStock(stockNuevo)
                        b.etCantidadAgregar.setText("")
                        b.etCantidadAgregar.clearFocus()
                        val idx = items.indexOfFirst { it.id == producto.id }
                        if (idx != -1) {
                            items[idx] = producto.copy(stockGeneral = stockNuevo)
                        }
                    }
                }
            }

            private fun mostrarStock(stock: Int) {
                b.tvStockActual.text = "Stock actual: $stock"
            }
        }
    }
}
