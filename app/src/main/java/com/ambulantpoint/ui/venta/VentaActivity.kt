package com.ambulantpoint.ui.venta

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ambulantpoint.R
import com.ambulantpoint.data.dao.CargaDiariaDao
import com.ambulantpoint.data.dao.CategoriaDao
import com.ambulantpoint.data.dao.DetalleVentaDao
import com.ambulantpoint.data.dao.JornadaVentaDao
import com.ambulantpoint.data.dao.MetodoPagoDao
import com.ambulantpoint.data.dao.MovimientoInventarioDao
import com.ambulantpoint.data.dao.ProductoDao
import com.ambulantpoint.data.dao.VentaDao
import com.ambulantpoint.data.dao.CorteCajaDao
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.CargaDiaria
import com.ambulantpoint.data.model.Categoria
import com.ambulantpoint.data.model.Producto
import com.ambulantpoint.databinding.ActivityVentaBinding
import com.ambulantpoint.databinding.ItemProductoVentaBinding
import com.ambulantpoint.service.BusinessRuleException
import com.ambulantpoint.service.MetodoPagoInput
import com.ambulantpoint.service.ValidationException
import com.ambulantpoint.service.VentaService
import com.ambulantpoint.ui.conciliacion.ReporteActivity
import com.google.android.material.button.MaterialButton

/**
 * VentaActivity — CU-02: Filtrar y Realizar Venta + CU-04: Registrar Merma.
 *
 * Slash Filter: botones horizontales por categoría con stock diario > 0.
 * Tap en producto → diálogo de venta (cantidad + método de pago).
 * Menú → Registrar Merma → diálogo de merma.
 *
 * RF asociado: RF-05, RF-06, RF-07, RF-08, RF-09
 */
class VentaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVentaBinding
    private lateinit var ventaService: VentaService
    private lateinit var adapter: ProductoVentaAdapter

    private var jornadaId: Long = -1L
    private var categoriaSeleccionadaId: Long = -1L

    // Estado completo de la jornada: pares (carga, producto)
    private var cargasConProducto: List<Pair<CargaDiaria, Producto>> = emptyList()
    private var categoriasActivas: List<Categoria> = emptyList()

    // Métodos de pago activos (desde SharedPreferences de MetodosPagoActivity)
    private var efectivoActivo      = true
    private var tarjetaActiva       = false
    private var transferenciaActiva = false

    companion object {
        const val EXTRA_JORNADA_ID = "extra_jornada_id"
        private const val PREFS_METODOS = "metodos_pago"
        private const val KEY_EFECTIVO       = "efectivo_activo"
        private const val KEY_TARJETA        = "tarjeta_activa"
        private const val KEY_TRANSFERENCIA  = "transferencia_activa"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVentaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jornadaId = intent.getLongExtra(EXTRA_JORNADA_ID, -1L)
        if (jornadaId == -1L) {
            Toast.makeText(this, "Error: jornada no encontrada.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        inicializarServicio()
        leerMetodosPago()
        configurarToolbar()
        configurarRecycler()
        cargarEstado()
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

    private fun leerMetodosPago() {
        val prefs = getSharedPreferences(PREFS_METODOS, Context.MODE_PRIVATE)
        efectivoActivo      = prefs.getBoolean(KEY_EFECTIVO, true)
        tarjetaActiva       = prefs.getBoolean(KEY_TARJETA, false)
        transferenciaActiva = prefs.getBoolean(KEY_TRANSFERENCIA, false)
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun configurarRecycler() {
        adapter = ProductoVentaAdapter { carga, producto ->
            mostrarDialogVenta(carga, producto)
        }
        binding.recyclerProductosVenta.apply {
            layoutManager = LinearLayoutManager(this@VentaActivity)
            adapter = this@VentaActivity.adapter
        }
    }

    // ─────────────────────────────────────────────────────────
    // MENÚ
    // ─────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_venta, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_registrar_merma -> {
                mostrarDialogMerma()
                true
            }
            R.id.action_cerrar_jornada -> {
                mostrarDialogCierreJornada()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─────────────────────────────────────────────────────────
    // CARGA Y REFRESCO DE ESTADO
    // ─────────────────────────────────────────────────────────

    private fun cargarEstado() {
        cargasConProducto = ventaService.getCargasConProducto(jornadaId)
        categoriasActivas = ventaService.getCategoriasConStockDiario(jornadaId)

        actualizarTotalVentas()
        reconstruirSlashFilter()
    }

    private fun actualizarTotalVentas() {
        val total = ventaService.getTotalVentasByJornada(jornadaId)
        binding.tvTotalVentaDia.text = "${"$%.2f".format(total)}"
    }

    private fun reconstruirSlashFilter() {
        val ll = binding.llSlashFilter
        ll.removeAllViews()

        if (categoriasActivas.isEmpty()) {
            mostrarSinStock()
            return
        }

        // Si la categoría seleccionada ya no tiene stock, seleccionar la primera disponible
        if (categoriasActivas.none { it.id.toLong() == categoriaSeleccionadaId }) {
            categoriaSeleccionadaId = categoriasActivas.first().id.toLong()
        }

        val margen = resources.getDimensionPixelSize(R.dimen.spacing8)
        categoriasActivas.forEach { categoria ->
            val btn = MaterialButton(this).apply {
                text = categoria.nombre
                setOnClickListener {
                    categoriaSeleccionadaId = categoria.id.toLong()
                    reconstruirSlashFilter()
                }
                val esSeleccionada = categoria.id.toLong() == categoriaSeleccionadaId
                if (esSeleccionada) {
                    setBackgroundColor(getColor(R.color.colorBtnIniciarVenta))
                    setTextColor(getColor(R.color.colorOnPrimary))
                } else {
                    setBackgroundColor(getColor(R.color.colorSurface))
                    setTextColor(getColor(R.color.colorPrimary))
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, margen, 0) }
                layoutParams = params
            }
            ll.addView(btn)
        }

        actualizarListaProductos()
    }

    private fun actualizarListaProductos() {
        val filtrados = cargasConProducto.filter { (carga, producto) ->
            producto.categoriaId == categoriaSeleccionadaId && carga.hayStock()
        }

        val haySinStock = filtrados.isEmpty()
        binding.tvSinStockDiario.visibility       = if (haySinStock) View.VISIBLE else View.GONE
        binding.recyclerProductosVenta.visibility = if (haySinStock) View.GONE else View.VISIBLE

        if (!haySinStock) adapter.actualizar(filtrados)
    }

    private fun mostrarSinStock() {
        binding.tvSinStockDiario.text = "Todo el stock del día ha sido vendido o dado de baja."
        binding.tvSinStockDiario.visibility       = View.VISIBLE
        binding.recyclerProductosVenta.visibility = View.GONE
    }

    // ─────────────────────────────────────────────────────────
    // DIÁLOGO DE VENTA (CU-02)
    // ─────────────────────────────────────────────────────────

    private fun mostrarDialogVenta(carga: CargaDiaria, producto: Producto) {
        val vista = layoutInflater.inflate(android.R.layout.simple_list_item_2, null, false)

        // Construcción manual del diálogo para controlar cantidad y método de pago
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(R.dimen.screenContentPadding)
            setPadding(p, p, p, p)
        }

        // -- Precio referencia
        val tvPrecio = TextView(this).apply {
            text = "Precio unitario: ${"$%.2f".format(producto.precioVenta)}"
            textSize = 14f
            setTextColor(getColor(R.color.colorOnSurface))
        }
        contenedor.addView(tvPrecio)

        // -- Cantidad
        val tvLabelCantidad = TextView(this).apply {
            text = "Cantidad (máx. ${carga.cantidadSobrante}):"
            textSize = 14f
            setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing12), 0, 0)
            setTextColor(getColor(R.color.colorOnBackground))
        }
        contenedor.addView(tvLabelCantidad)

        val etCantidad = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
            textSize = 16f
        }
        contenedor.addView(etCantidad)

        // -- Método de pago
        val tvLabelMetodo = TextView(this).apply {
            text = "Método de pago:"
            textSize = 14f
            setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing12), 0, 0)
            setTextColor(getColor(R.color.colorOnBackground))
        }
        contenedor.addView(tvLabelMetodo)

        val radioGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        var idSeleccionadoDefault = View.generateViewId()

        if (efectivoActivo) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = "Efectivo"
                isChecked = true
                idSeleccionadoDefault = id
            }
            radioGroup.addView(rb)
        }
        if (tarjetaActiva) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = "Tarjeta"
                if (!efectivoActivo) { isChecked = true; idSeleccionadoDefault = id }
            }
            radioGroup.addView(rb)
        }
        if (transferenciaActiva) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = "Transferencia"
                if (!efectivoActivo && !tarjetaActiva) { isChecked = true; idSeleccionadoDefault = id }
            }
            radioGroup.addView(rb)
        }
        contenedor.addView(radioGroup)

        // -- Campo comisión (visible solo cuando tarjeta/transferencia)
        val tvLabelComision = TextView(this).apply {
            text = "Comisión (%):"
            textSize = 14f
            setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing8), 0, 0)
            setTextColor(getColor(R.color.colorOnSurface))
            visibility = View.GONE
        }
        contenedor.addView(tvLabelComision)

        val etComision = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0.0")
            textSize = 14f
            visibility = View.GONE
        }
        contenedor.addView(etComision)

        radioGroup.setOnCheckedChangeListener { _, _ ->
            val textoSeleccionado = radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)?.text?.toString() ?: ""
            val esComisionable = textoSeleccionado == "Tarjeta" || textoSeleccionado == "Transferencia"
            tvLabelComision.visibility = if (esComisionable) View.VISIBLE else View.GONE
            etComision.visibility      = if (esComisionable) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle(producto.nombre)
            .setView(contenedor)
            .setPositiveButton("Registrar") { dialog, _ ->
                procesarVenta(carga, producto, etCantidad, radioGroup, etComision)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun procesarVenta(
        carga: CargaDiaria,
        producto: Producto,
        etCantidad: EditText,
        radioGroup: RadioGroup,
        etComision: EditText
    ) {
        val cantidad = etCantidad.text.toString().toIntOrNull()
        if (cantidad == null || cantidad <= 0) {
            Toast.makeText(this, "Ingresa una cantidad válida.", Toast.LENGTH_SHORT).show()
            return
        }

        val textoMetodo = radioGroup.findViewById<RadioButton>(
            radioGroup.checkedRadioButtonId
        )?.text?.toString() ?: ""

        val monto = producto.precioVenta * cantidad
        val pct   = etComision.text.toString().toDoubleOrNull() ?: 0.0

        val metodoPago: MetodoPagoInput = when (textoMetodo) {
            "Tarjeta"       -> MetodoPagoInput.Tarjeta(monto, pct)
            "Transferencia" -> MetodoPagoInput.Transferencia(monto, pct)
            else            -> MetodoPagoInput.Efectivo(monto)
        }

        try {
            ventaService.registrarVenta(jornadaId, producto.id, cantidad, metodoPago)
            val totalStr = "${"$%.2f".format(monto)}"
            Toast.makeText(this, "Venta registrada: $totalStr", Toast.LENGTH_SHORT).show()
            cargarEstado()
        } catch (e: ValidationException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        } catch (e: BusinessRuleException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────
    // CIERRE DE JORNADA (CU-05 — RF-10, RF-11, RF-13)
    // ─────────────────────────────────────────────────────────

    private fun mostrarDialogCierreJornada() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Jornada")
            .setMessage(
                "¿Seguro que deseas cerrar la jornada del día?\n\n" +
                "Los productos sobrantes serán devueltos al inventario.\n" +
                "Esta acción no se puede deshacer."
            )
            .setPositiveButton("Sí, cerrar") { _, _ -> mostrarDialogEfectivoReal() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogEfectivoReal() {
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(R.dimen.screenContentPadding)
            setPadding(p, p, p, p)
        }
        val tvLabel = TextView(this).apply {
            text = "Ingresa el efectivo real contado en caja:"
            textSize = 14f
            setTextColor(getColor(R.color.colorOnBackground))
        }
        contenedor.addView(tvLabel)

        val etEfectivo = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0.00")
            textSize = 16f
        }
        contenedor.addView(etEfectivo)

        AlertDialog.Builder(this)
            .setTitle("Efectivo en Caja")
            .setView(contenedor)
            .setPositiveButton("Confirmar Cierre") { _, _ ->
                val efectivoReal = etEfectivo.text.toString().toDoubleOrNull()
                if (efectivoReal == null || efectivoReal < 0) {
                    Toast.makeText(this, "Ingresa un monto válido.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ejecutarCierreJornada(efectivoReal)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarCierreJornada(efectivoReal: Double) {
        try {
            ventaService.cerrarJornada(jornadaId, efectivoReal)
            startActivity(
                Intent(this, ReporteActivity::class.java).apply {
                    putExtra(ReporteActivity.EXTRA_JORNADA_ID, jornadaId)
                }
            )
            finish()
        } catch (e: ValidationException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        } catch (e: BusinessRuleException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────
    // DIÁLOGO DE MERMA (CU-04)
    // ─────────────────────────────────────────────────────────

    private fun mostrarDialogMerma() {
        val cargasConStock = cargasConProducto.filter { it.first.hayStock() }
        if (cargasConStock.isEmpty()) {
            Toast.makeText(this, "No hay productos con stock disponible para registrar merma.", Toast.LENGTH_SHORT).show()
            return
        }

        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(R.dimen.screenContentPadding)
            setPadding(p, p, p, p)
        }

        // -- Selector de producto
        val tvLabelProducto = TextView(this).apply {
            text = "Producto dañado:"
            textSize = 14f
            setTextColor(getColor(R.color.colorOnBackground))
        }
        contenedor.addView(tvLabelProducto)

        val nombresProductos = cargasConStock.map { it.second.nombre }
        val spinnerProducto  = android.widget.Spinner(this)
        val spinnerAdapter   = ArrayAdapter(this, android.R.layout.simple_spinner_item, nombresProductos)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProducto.adapter = spinnerAdapter
        contenedor.addView(spinnerProducto)

        // -- Cantidad
        val tvLabelCantidad = TextView(this).apply {
            text = "Cantidad:"
            textSize = 14f
            setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing12), 0, 0)
            setTextColor(getColor(R.color.colorOnBackground))
        }
        contenedor.addView(tvLabelCantidad)

        val etCantidad = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
            textSize = 14f
        }
        contenedor.addView(etCantidad)

        // -- Motivo (opcional)
        val tvLabelMotivo = TextView(this).apply {
            text = "Motivo (opcional):"
            textSize = 14f
            setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing12), 0, 0)
            setTextColor(getColor(R.color.colorOnSurface))
        }
        contenedor.addView(tvLabelMotivo)

        val etMotivo = EditText(this).apply {
            hint = "Ej: producto caído, golpeado..."
            textSize = 14f
        }
        contenedor.addView(etMotivo)

        AlertDialog.Builder(this)
            .setTitle("Registrar Merma")
            .setView(contenedor)
            .setPositiveButton("Registrar") { dialog, _ ->
                val indice   = spinnerProducto.selectedItemPosition
                val (carga, producto) = cargasConStock[indice]
                val cantidad = etCantidad.text.toString().toIntOrNull()
                val motivo   = etMotivo.text.toString().trim().ifBlank { null }

                if (cantidad == null || cantidad <= 0) {
                    Toast.makeText(this, "Ingresa una cantidad válida.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    ventaService.registrarMerma(jornadaId, producto.id, cantidad, motivo)
                    Toast.makeText(this, "Merma registrada: $cantidad ud(s) de ${producto.nombre}.", Toast.LENGTH_SHORT).show()
                    cargarEstado()
                } catch (e: ValidationException) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                } catch (e: BusinessRuleException) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ─────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────

    inner class ProductoVentaAdapter(
        private val onTap: (CargaDiaria, Producto) -> Unit
    ) : RecyclerView.Adapter<ProductoVentaAdapter.ViewHolder>() {

        private val items = mutableListOf<Pair<CargaDiaria, Producto>>()

        fun actualizar(nuevos: List<Pair<CargaDiaria, Producto>>) {
            items.clear()
            items.addAll(nuevos)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemProductoVentaBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position].first, items[position].second)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(private val b: ItemProductoVentaBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(carga: CargaDiaria, producto: Producto) {
                b.tvNombreVenta.text      = producto.nombre
                b.tvPrecioVenta.text      = "${"$%.2f".format(producto.precioVenta)}"
                b.tvStockDiarioVenta.text = carga.cantidadSobrante.toString()
                b.root.setOnClickListener { onTap(carga, producto) }
            }
        }
    }
}
