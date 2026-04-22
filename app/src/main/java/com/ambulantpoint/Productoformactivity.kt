package com.ambulantpoint.ui.catalogo

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.data.dao.CategoriaDao
import com.ambulantpoint.data.dao.ProductoDao
import com.ambulantpoint.data.db.DatabaseHelper
import com.ambulantpoint.data.model.Categoria
import com.ambulantpoint.databinding.ActivityProductoFormBinding
import com.ambulantpoint.service.BusinessRuleException
import com.ambulantpoint.service.CatalogService
import com.ambulantpoint.service.ValidationException
import com.google.android.material.textfield.TextInputEditText

/**
 * ProductoFormActivity — Crear o editar un producto del catálogo.
 *
 * MODO CREACIÓN: Intent sin extras → todos los campos visibles.
 * MODO EDICIÓN:  Intent con EXTRA_PRODUCTO_ID → stock oculto,
 *                campos precargados con datos del producto existente.
 *
 * Módulo: M1 — Gestión de Catálogo
 * RF asociado: RF-02, RF-18 | CU asociado: CU-10
 */
class ProductoFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductoFormBinding
    private lateinit var catalogService: CatalogService

    private val categorias = mutableListOf<Categoria>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    // ID del producto a editar. null = modo creación.
    private var productoId: Long? = null

    companion object {
        const val EXTRA_PRODUCTO_ID = "extra_producto_id"
    }

    /**
     * Inicializa el binding, detecta el modo (creación/edición) desde el Intent,
     * configura el servicio, toolbar, campos y botones.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductoFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productoId = intent.getLongExtra(EXTRA_PRODUCTO_ID, -1L)
            .takeIf { it != -1L }

        inicializarServicio()
        configurarToolbar()
        configurarModo()
        cargarCategorias()
        configurarBotones()
    }

    // ─────────────────────────────────────────────────────────
    // INICIALIZACIÓN
    // ─────────────────────────────────────────────────────────

    /** Crea el [CatalogService] con sus DAOs usando el singleton [DatabaseHelper]. */
    private fun inicializarServicio() {
        val dbHelper = DatabaseHelper.getInstance(this)
        catalogService = CatalogService(
            categoriaDao = CategoriaDao(dbHelper),
            productoDao  = ProductoDao(dbHelper)
        )
    }

    /** Configura la toolbar con retroceso y título dinámico según el modo activo. */
    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        // Título según modo
        binding.toolbar.title = if (productoId == null) "Nuevo Producto" else "Editar Producto"
    }

    /**
     * Aplica el modo correcto según si viene productoId o no.
     * EDICIÓN: oculta el contenedor de stock y precarga datos.
     * CREACIÓN: todos los campos visibles, sin precarga.
     */
    private fun configurarModo() {
        val id = productoId
        if (id == null) {
            // Modo creación — stock visible
            binding.containerStock.visibility = View.VISIBLE
        } else {
            // Modo edición — stock oculto
            binding.containerStock.visibility = View.GONE
            precargarDatosProducto(id)
        }
    }

    /**
     * Carga los datos del producto existente en los campos del formulario.
     * Se llama solo en modo edición, antes de cargarCategorias().
     */
    private fun precargarDatosProducto(id: Long) {
        val producto = catalogService.getProducto(id)
        if (producto == null) {
            Toast.makeText(this, "Producto no encontrado.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.etNombre.setText(producto.nombre)
        binding.etPrecioVenta.setText(producto.precioVenta.toString())
        // La categoría se seleccionará en cargarCategorias() usando categoriaId
    }

    // ─────────────────────────────────────────────────────────
    // SPINNER DE CATEGORÍAS
    // ─────────────────────────────────────────────────────────

    /**
     * Carga las categorías activas en el spinner.
     * Si el adapter ya existe, actualiza sus datos en lugar de recrearlo.
     * @param seleccionarId ID de la categoría a seleccionar automáticamente tras la carga.
     *                      Si es null, se selecciona la categoría actual del producto (modo edición).
     */
    private fun cargarCategorias(seleccionarId: Int? = null) {
        val cargadas = catalogService.getCategoriasActivas()
        categorias.clear()
        categorias.addAll(cargadas)

        val nombres = categorias.map { it.nombre }

        if (!::spinnerAdapter.isInitialized) {
            spinnerAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                nombres.toMutableList()
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerCategoria.adapter = it
            }
        } else {
            spinnerAdapter.clear()
            spinnerAdapter.addAll(nombres)
            spinnerAdapter.notifyDataSetChanged()
        }

        // En modo edición: seleccionar la categoría actual del producto
        val idASeleccionar = seleccionarId ?: run {
            val id = productoId ?: return@run null
            catalogService.getProducto(id)?.categoriaId?.toInt()
        }

        if (idASeleccionar != null) {
            val index = categorias.indexOfFirst { it.id == idASeleccionar }
            if (index >= 0) binding.spinnerCategoria.setSelection(index)
        }

        if (categorias.isEmpty()) {
            binding.spinnerCategoria.isEnabled = false
            mostrarErrorCategoria("No hay categorías. Crea una con el botón '+ Nueva'.")
        } else {
            binding.spinnerCategoria.isEnabled = true
            ocultarErrorCategoria()
        }
    }

    // ─────────────────────────────────────────────────────────
    // BOTONES
    // ─────────────────────────────────────────────────────────

    /** Asigna listeners al botón "Nueva Categoría" y al botón "Guardar". */
    private fun configurarBotones() {
        binding.btnNuevaCategoria.setOnClickListener { mostrarDialogNuevaCategoria() }
        binding.btnGuardar.setOnClickListener { intentarGuardar() }
    }

    // ─────────────────────────────────────────────────────────
    // ALERTDIALOG — NUEVA CATEGORÍA
    // ─────────────────────────────────────────────────────────

    /** Muestra un AlertDialog con un campo de texto para ingresar el nombre de la nueva categoría. */
    private fun mostrarDialogNuevaCategoria() {
        val editText = TextInputEditText(this).apply {
            hint = "Nombre de la categoría"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(48, 32, 48, 16)
        }

        AlertDialog.Builder(this)
            .setTitle("Nueva Categoría")
            .setView(editText)
            .setPositiveButton("Crear") { dialog, _ ->
                crearCategoria(editText.text.toString().trim())
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /**
     * Crea la categoría a través de [CatalogService] y recarga el spinner
     * seleccionando automáticamente la nueva categoría.
     */
    private fun crearCategoria(nombre: String) {
        try {
            val nuevoId = catalogService.createCategoria(nombre).toInt()
            Toast.makeText(this, "Categoría \"$nombre\" creada", Toast.LENGTH_SHORT).show()
            cargarCategorias(seleccionarId = nuevoId)
        } catch (e: ValidationException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        } catch (e: BusinessRuleException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────
    // GUARDAR — CREACIÓN O EDICIÓN
    // ─────────────────────────────────────────────────────────

    /**
     * Lee y valida los campos del formulario, luego invoca create o update según el modo.
     * Retorna RESULT_OK a [GestionProductosActivity] al finalizar correctamente.
     */
    private fun intentarGuardar() {
        limpiarErrores()

        val nombre      = binding.etNombre.text.toString()
        val precioTexto = binding.etPrecioVenta.text.toString()
        val posicion    = binding.spinnerCategoria.selectedItemPosition

        if (categorias.isEmpty() || posicion < 0) {
            mostrarErrorCategoria("Selecciona o crea una categoría.")
            return
        }
        val categoriaSeleccionada = categorias[posicion]

        val precioVenta = precioTexto.toDoubleOrNull()
        if (precioVenta == null) {
            mostrarErrorPrecio("Ingresa un precio válido (ej: 10.50).")
            return
        }

        try {
            val id = productoId
            if (id == null) {
                // ── MODO CREACIÓN ──────────────────────────────
                val stockTexto   = binding.etStockInicial.text.toString()
                val stockInicial = if (stockTexto.isBlank()) 0
                else stockTexto.toIntOrNull()
                if (stockInicial == null) {
                    mostrarErrorStock("Ingresa una cantidad entera válida (ej: 10).")
                    return
                }
                catalogService.createProducto(
                    nombre       = nombre,
                    categoriaId  = categoriaSeleccionada.id,
                    precioVenta  = precioVenta,
                    stockInicial = stockInicial
                )
                Toast.makeText(this, "Producto creado correctamente", Toast.LENGTH_SHORT).show()
            } else {
                // ── MODO EDICIÓN ───────────────────────────────
                catalogService.updateProducto(
                    productoId  = id,
                    nombre      = nombre,
                    categoriaId = categoriaSeleccionada.id,
                    precioVenta = precioVenta
                )
                Toast.makeText(this, "Producto actualizado correctamente", Toast.LENGTH_SHORT).show()
            }
            // Indicar a GestionProductosActivity que recargue la lista
            setResult(RESULT_OK)
            finish()
        } catch (e: ValidationException) {
            manejarExcepcionValidacion(e.message ?: "Error de validación.")
        } catch (e: BusinessRuleException) {
            manejarExcepcionNegocio(e.message ?: "Error de negocio.")
        }
    }

    // ─────────────────────────────────────────────────────────
    // MANEJO DE ERRORES POR CAMPO
    // ─────────────────────────────────────────────────────────

    /** Mapea el mensaje de [ValidationException] al campo de error correspondiente. */
    private fun manejarExcepcionValidacion(mensaje: String) {
        when {
            mensaje.contains("nombre", ignoreCase = true)  -> mostrarErrorNombre(mensaje)
            mensaje.contains("precio", ignoreCase = true)  -> mostrarErrorPrecio(mensaje)
            mensaje.contains("stock",  ignoreCase = true)  -> mostrarErrorStock(mensaje)
            else -> Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    /** Mapea el mensaje de [BusinessRuleException] al campo de error correspondiente. */
    private fun manejarExcepcionNegocio(mensaje: String) {
        when {
            mensaje.contains("nombre",    ignoreCase = true) -> mostrarErrorNombre(mensaje)
            mensaje.contains("categoría", ignoreCase = true) -> mostrarErrorCategoria(mensaje)
            else -> Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    /** Muestra el error bajo el campo Nombre y le da foco al campo. */
    private fun mostrarErrorNombre(m: String)    { binding.tvErrorNombre.text    = m; binding.tvErrorNombre.visibility    = View.VISIBLE; binding.etNombre.requestFocus() }
    /** Muestra el error bajo el spinner de Categoría. */
    private fun mostrarErrorCategoria(m: String) { binding.tvErrorCategoria.text = m; binding.tvErrorCategoria.visibility = View.VISIBLE }
    /** Muestra el error bajo el campo Precio y le da foco al campo. */
    private fun mostrarErrorPrecio(m: String)    { binding.tvErrorPrecio.text    = m; binding.tvErrorPrecio.visibility    = View.VISIBLE; binding.etPrecioVenta.requestFocus() }
    /** Muestra el error bajo el campo Stock y le da foco al campo. */
    private fun mostrarErrorStock(m: String)     { binding.tvErrorStock.text     = m; binding.tvErrorStock.visibility     = View.VISIBLE; binding.etStockInicial.requestFocus() }
    /** Oculta el texto de error del spinner de Categoría. */
    private fun ocultarErrorCategoria()          { binding.tvErrorCategoria.visibility = View.GONE }

    /** Oculta todos los textos de error del formulario antes de una nueva validación. */
    private fun limpiarErrores() {
        binding.tvErrorNombre.visibility    = View.GONE
        binding.tvErrorCategoria.visibility = View.GONE
        binding.tvErrorPrecio.visibility    = View.GONE
        binding.tvErrorStock.visibility     = View.GONE
    }
}