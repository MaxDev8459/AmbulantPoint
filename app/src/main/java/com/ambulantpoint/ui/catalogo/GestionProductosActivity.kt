package com.ambulantpoint.ui.catalogo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ambulantpoint.R
import com.ambulantpoint.data.dao.CategoriaDao
import com.ambulantpoint.data.dao.ProductoDao
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.Producto
import com.ambulantpoint.databinding.ActivityGestionProductosBinding
import com.ambulantpoint.databinding.ItemProductoBinding
import com.ambulantpoint.service.BusinessRuleException
import com.ambulantpoint.service.CatalogService

/**
 * GestionProductosActivity — Lista, edita y elimina productos del catálogo.
 *
 * Muestra todos los productos con activo=true (con stock o sin stock).
 * No muestra productos con activo=false (eliminados lógicamente).
 *
 * Flujo:
 * - Tocar fila     → ProductoFormActivity en modo EDICIÓN
 * - Tocar papelera → AlertDialog confirmación → deleteProducto()
 * - Tocar FAB      → ProductoFormActivity en modo CREACIÓN
 *
 * Módulo: M1 — Gestión de Catálogo
 * RF asociado: RF-02, RF-18 | CU asociado: CU-10
 */
class GestionProductosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestionProductosBinding
    private lateinit var catalogService: CatalogService
    private lateinit var adapter: ProductoAdapter

    // Launcher que recarga la lista cuando ProductoFormActivity retorna RESULT_OK
    private val formLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) cargarProductos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestionProductosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarServicio()
        configurarToolbar()
        configurarRecycler()
        configurarFab()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_gestion_productos, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_gestionar_stock) {
            startActivity(Intent(this, GestionStockActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        cargarProductos()
    }

    private fun configurarRecycler() {
        adapter = ProductoAdapter(
            onEditar   = { producto -> abrirEdicion(producto) },
            onEliminar = { producto -> confirmarEliminacion(producto) }
        )
        binding.recyclerProductos.apply {
            layoutManager = LinearLayoutManager(this@GestionProductosActivity)
            adapter = this@GestionProductosActivity.adapter
        }
    }

    private fun configurarFab() {
        binding.fabNuevoProducto.setOnClickListener {
            formLauncher.launch(
                Intent(this, ProductoFormActivity::class.java)
                // Sin EXTRA_PRODUCTO_ID → modo creación
            )
        }
    }

    // ─────────────────────────────────────────────────────────
    // CARGA DE DATOS
    // ─────────────────────────────────────────────────────────

    private fun cargarProductos() {
        val productos = catalogService.getProductosTodosActivos()

        adapter.actualizar(productos)

        val hayProductos = productos.isNotEmpty()
        binding.recyclerProductos.visibility = if (hayProductos) View.VISIBLE else View.GONE
        binding.tvEmpty.visibility           = if (hayProductos) View.GONE    else View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────
    // ACCIONES
    // ─────────────────────────────────────────────────────────

    private fun abrirEdicion(producto: Producto) {
        val intent = Intent(this, ProductoFormActivity::class.java).apply {
            putExtra(ProductoFormActivity.EXTRA_PRODUCTO_ID, producto.id)
        }
        formLauncher.launch(intent)
    }

    private fun confirmarEliminacion(producto: Producto) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Eliminar \"${producto.nombre}\"? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { dialog, _ ->
                try {
                    catalogService.deleteProducto(producto.id)
                    cargarProductos()
                } catch (e: BusinessRuleException) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("No se puede eliminar")
                        .setMessage(e.message)
                        .setPositiveButton("Entendido", null)
                        .show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ─────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────

    inner class ProductoAdapter(
        private val onEditar: (Producto) -> Unit,
        private val onEliminar: (Producto) -> Unit
    ) : RecyclerView.Adapter<ProductoAdapter.ViewHolder>() {

        private val items = mutableListOf<Producto>()

        fun actualizar(nuevos: List<Producto>) {
            items.clear()
            items.addAll(nuevos)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemProductoBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(private val b: ItemProductoBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(producto: Producto) {
                b.tvNombreProducto.text = producto.nombre
                b.tvPrecioProducto.text = "$%.2f".format(producto.precioVenta)
                b.tvStockProducto.text  = "Stock: ${producto.stockGeneral}"

                val categoria = catalogService.getCategoria(producto.categoriaId.toInt())
                b.tvCategoriaProducto.text = categoria?.nombre ?: "Sin categoría"

                b.tvStockProducto.setTextColor(
                    if (producto.stockGeneral == 0)
                        getColor(R.color.colorWarning)
                    else
                        getColor(R.color.colorOnSurface)
                )

                b.root.setOnClickListener { onEditar(producto) }
                b.btnEliminarProducto.setOnClickListener { onEliminar(producto) }
            }
        }
    }
}
