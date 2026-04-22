package com.ambulantpoint.service

import com.ambulantpoint.data.dao.CategoriaDao
import com.ambulantpoint.data.dao.ProductoDao
import com.ambulantpoint.data.model.Categoria
import com.ambulantpoint.data.model.Producto

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPCIONES DE DOMINIO
// ─────────────────────────────────────────────────────────────────────────────

/** Violación de formato o rango de un campo. */
class ValidationException(message: String) : Exception(message)

/** Violación de una regla de negocio del dominio. */
class BusinessRuleException(message: String) : Exception(message)

// ─────────────────────────────────────────────────────────────────────────────
// CATALOG SERVICE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Capa de servicio para M1 — Gestión de Catálogo.
 *
 * Orquesta CategoriaDao y ProductoDao. Es el único punto de entrada
 * para operaciones de catálogo desde la capa de presentación.
 *
 * TIPOS DE ID (estado actual de los modelos):
 * - Categoria.id  → Int  (alineado con CategoriaDao actual)
 * - Producto.id   → Long (alineado con ProductoDao actual)
 *
 * Las conversiones categoriaId.toLong() son puentes necesarios donde
 * ProductoDao espera Long pero el modelo Categoria usa Int.
 *
 * Decisiones aplicadas:
 * - D3: Soft-delete scope = solo Categoria y Producto
 * - RF-18: Gestión de Catálogo de Productos
 */
class CatalogService(
    private val categoriaDao: CategoriaDao,
    private val productoDao: ProductoDao
) {

    // =========================================================================
    // SECCIÓN: CATEGORÍAS
    // RF asociado: RF-01, RF-18
    // Categoria.id = Int — consistente con CategoriaDao
    // =========================================================================

    /**
     * Crea una nueva categoría validando unicidad de nombre.
     *
     * @return ID generado por SQLite (Long).
     * @throws ValidationException si el nombre es vacío o excede 50 chars.
     * @throws BusinessRuleException si ya existe una categoría con ese nombre.
     */
    @Throws(ValidationException::class, BusinessRuleException::class)
    fun createCategoria(nombre: String): Long {
        val nombreLimpio = nombre.trim()

        if (nombreLimpio.isBlank()) {
            throw ValidationException("El nombre de la categoría no puede estar vacío.")
        }
        if (nombreLimpio.length > 50) {
            throw ValidationException("El nombre de la categoría no puede exceder 50 caracteres.")
        }
        if (categoriaDao.existsByNombre(nombreLimpio)) {
            throw BusinessRuleException("Ya existe una categoría con el nombre \"$nombreLimpio\".")
        }

        val nueva = Categoria(nombre = nombreLimpio)
        val id = categoriaDao.insert(nueva)

        if (id == -1L) {
            throw BusinessRuleException("No se pudo crear la categoría. Intenta de nuevo.")
        }
        return id
    }

    /**
     * Actualiza el nombre de una categoría existente.
     *
     * @param categoriaId Int — tipo del campo id en Categoria.kt.
     * @throws ValidationException si el nombre es vacío o excede 50 chars.
     * @throws BusinessRuleException si la categoría no existe o el nombre está duplicado.
     */
    @Throws(ValidationException::class, BusinessRuleException::class)
    fun updateCategoria(categoriaId: Int, nuevoNombre: String) {
        val nombreLimpio = nuevoNombre.trim()

        if (nombreLimpio.isBlank()) {
            throw ValidationException("El nombre de la categoría no puede estar vacío.")
        }
        if (nombreLimpio.length > 50) {
            throw ValidationException("El nombre de la categoría no puede exceder 50 caracteres.")
        }

        val existente = categoriaDao.findById(categoriaId)
            ?: throw BusinessRuleException("La categoría con ID $categoriaId no existe.")

        if (!existente.nombre.equals(nombreLimpio, ignoreCase = true)) {
            if (categoriaDao.existsByNombre(nombreLimpio)) {
                throw BusinessRuleException("Ya existe una categoría con el nombre \"$nombreLimpio\".")
            }
        }

        categoriaDao.update(existente.copy(nombre = nombreLimpio))
    }

    /**
     * Elimina una categoría según CU-10 Flujo Alterno A3:
     * - Con productos con historial → rechazar, pedir reasignación.
     * - Con productos sin historial → eliminar productos + categoría físicamente.
     * - Sin productos → eliminar físicamente.
     *
     * @param categoriaId Int — tipo del campo id en Categoria.kt.
     * @throws BusinessRuleException si tiene productos con historial de ventas.
     */
    @Throws(BusinessRuleException::class)
    fun deleteCategoria(categoriaId: Int) {
        categoriaDao.findById(categoriaId)
            ?: throw BusinessRuleException("La categoría con ID $categoriaId no existe.")

        val totalProductos = productoDao.countByCategoria(
            categoriaId.toLong(), soloActivos = false
        )

        if (totalProductos > 0) {
            val productosConHistorial = productoDao
                .findByCategoria(categoriaId.toLong(), soloActivos = false)
                .filter { tieneHistorialVentas(it.id) }

            if (productosConHistorial.isNotEmpty()) {
                throw BusinessRuleException(
                    "No se puede eliminar la categoría porque tiene " +
                            "${productosConHistorial.size} producto(s) con historial de ventas. " +
                            "Reasígnalos a otra categoría primero."
                )
            }

            // Sin historial: eliminar productos físicamente antes que la categoría
            productoDao.findByCategoria(categoriaId.toLong(), soloActivos = false)
                .forEach { productoDao.hardDelete(it.id) }
        }

        categoriaDao.hardDelete(categoriaId)
    }

    /** @return Categoria o null si no existe. */
    fun getCategoria(categoriaId: Int): Categoria? =
        categoriaDao.findById(categoriaId)

    /**
     * Retorna todas las categorías activas ordenadas por nombre.
     * RF asociado: RF-01
     */
    fun getCategoriasActivas(): List<Categoria> =
        categoriaDao.findByActivo(true)

    /** Retorna todas las categorías (activas e inactivas). */
    fun getTodasCategorias(): List<Categoria> =
        categoriaDao.findAll()

    // =========================================================================
    // SECCIÓN: PRODUCTOS
    // RF asociado: RF-02, RF-03, RF-18
    // Producto.id = Long — consistente con ProductoDao
    // =========================================================================

    /**
     * Crea un nuevo producto en el catálogo.
     *
     * @param categoriaId Int — tipo del campo id en Categoria.kt.
     * @return ID generado del nuevo producto (Long).
     * @throws ValidationException por campos inválidos.
     * @throws BusinessRuleException por reglas de negocio violadas.
     *
     * D10: costoBase ELIMINADO.
     * RF asociado: RF-02, RF-18
     */
    @Throws(ValidationException::class, BusinessRuleException::class)
    fun createProducto(
        nombre: String,
        categoriaId: Int,
        precioVenta: Double,
        stockInicial: Int = 0
    ): Long {
        val nombreLimpio = nombre.trim()

        if (nombreLimpio.isBlank()) {
            throw ValidationException("El nombre del producto no puede estar vacío.")
        }
        if (nombreLimpio.length > 100) {
            throw ValidationException("El nombre del producto no puede exceder 100 caracteres.")
        }
        if (precioVenta <= 0.0) {
            throw ValidationException("El precio de venta debe ser mayor a \$0.00.")
        }
        if (stockInicial < 0) {
            throw ValidationException("El stock inicial no puede ser negativo.")
        }

        val categoria = categoriaDao.findById(categoriaId)
            ?: throw BusinessRuleException("La categoría seleccionada no existe.")

        if (!categoria.activo) {
            throw BusinessRuleException(
                "No se puede agregar un producto a la categoría \"${categoria.nombre}\" " +
                        "porque está inactiva."
            )
        }

        if (productoDao.existsByNombreEnCategoria(nombreLimpio, categoriaId.toLong())) {
            throw BusinessRuleException(
                "Ya existe un producto con el nombre \"$nombreLimpio\" " +
                        "en la categoría \"${categoria.nombre}\"."
            )
        }

        val nuevo = Producto(
            nombre       = nombreLimpio,
            categoriaId  = categoriaId.toLong(),
            precioVenta  = precioVenta,
            stockGeneral = stockInicial,
            activo       = true
        )

        val id = productoDao.insert(nuevo)
        if (id == -1L) {
            throw BusinessRuleException("No se pudo crear el producto. Intenta de nuevo.")
        }
        return id
    }

    /**
     * Actualiza los campos editables de un producto existente.
     *
     * @param productoId Long — tipo del campo id en Producto.kt.
     * @param categoriaId Int — tipo del campo id en Categoria.kt.
     * @throws ValidationException por campos inválidos.
     * @throws BusinessRuleException si no existe o nombre duplicado.
     *
     * D10: costoBase ELIMINADO.
     * RF asociado: RF-18
     */
    @Throws(ValidationException::class, BusinessRuleException::class)
    fun updateProducto(
        productoId: Long,
        nombre: String,
        categoriaId: Int,
        precioVenta: Double
    ) {
        val nombreLimpio = nombre.trim()

        if (nombreLimpio.isBlank()) {
            throw ValidationException("El nombre del producto no puede estar vacío.")
        }
        if (nombreLimpio.length > 100) {
            throw ValidationException("El nombre del producto no puede exceder 100 caracteres.")
        }
        if (precioVenta <= 0.0) {
            throw ValidationException("El precio de venta debe ser mayor a \$0.00.")
        }

        val productoExistente = productoDao.findById(productoId)
            ?: throw BusinessRuleException("El producto con ID $productoId no existe.")

        val categoria = categoriaDao.findById(categoriaId)
            ?: throw BusinessRuleException("La categoría seleccionada no existe.")

        if (!categoria.activo) {
            throw BusinessRuleException(
                "No se puede mover el producto a la categoría \"${categoria.nombre}\" " +
                        "porque está inactiva."
            )
        }

        val nombreCambio    = !productoExistente.nombre.equals(nombreLimpio, ignoreCase = true)
        val categoriaCambio = productoExistente.categoriaId != categoriaId.toLong()

        if (nombreCambio || categoriaCambio) {
            if (productoDao.existsByNombreEnCategoria(
                    nombreLimpio, categoriaId.toLong(), excludeId = productoId
                )
            ) {
                throw BusinessRuleException(
                    "Ya existe un producto con el nombre \"$nombreLimpio\" " +
                            "en la categoría \"${categoria.nombre}\"."
                )
            }
        }

        productoDao.update(
            productoExistente.copy(
                nombre      = nombreLimpio,
                categoriaId = categoriaId.toLong(),
                precioVenta = precioVenta
            )
        )
    }

    /**
     * Elimina un producto. Soft si tiene historial, hard si no (D3).
     *
     * @param productoId Long — tipo del campo id en Producto.kt.
     * RF asociado: RF-18 (CU-10 Flujo Alterno A2)
     */
    @Throws(BusinessRuleException::class)
    fun deleteProducto(productoId: Long) {
        productoDao.findById(productoId)
            ?: throw BusinessRuleException("El producto con ID $productoId no existe.")

        if (tieneHistorialVentas(productoId)) {
            productoDao.softDelete(productoId)
        } else {
            productoDao.hardDelete(productoId)
        }
    }

    /** @return Producto o null si no existe. */
    fun getProducto(productoId: Long): Producto? =
        productoDao.findById(productoId)

    /**
     * Retorna todos los productos con activo=true, sin filtrar por stock.
     * Usado por GestionProductosActivity — muestra catálogo completo.
     * Excluye únicamente los eliminados lógicamente (activo=false).
     * RF asociado: RF-18
     */
    fun getProductosTodosActivos(): List<Producto> =
        productoDao.findAll(soloActivos = true)

    /**
     * Retorna todos los productos activos de una categoría.
     * @param categoriaId Int — tipo del campo id en Categoria.kt.
     * RF asociado: RF-02
     */
    fun getProductosPorCategoria(categoriaId: Int): List<Producto> =
        productoDao.findByCategoria(categoriaId.toLong(), soloActivos = true)

    /**
     * Retorna productos con stock_general > 0 de una categoría.
     * Exclusivo para el Slash Filter de M2.
     * @param categoriaId Int — tipo del campo id en Categoria.kt.
     * RF asociado: RF-04
     */
    fun getProductosConStockPorCategoria(categoriaId: Int): List<Producto> =
        productoDao.findByCategoriaConStock(categoriaId.toLong())

    // =========================================================================
    // SECCIÓN: GESTIÓN DE STOCK
    // RF asociado: RF-03
    // =========================================================================

    /**
     * Incrementa stock_general de un producto (carga de bodega).
     *
     * @param productoId Long.
     * @param cantidad Debe ser > 0.
     * @return Stock resultante.
     * @throws ValidationException si cantidad <= 0.
     * @throws BusinessRuleException si el producto no existe.
     */
    @Throws(ValidationException::class, BusinessRuleException::class)
    fun incrementarStockProducto(productoId: Long, cantidad: Int): Int {
        if (cantidad <= 0) {
            throw ValidationException("La cantidad a agregar debe ser mayor a 0.")
        }
        productoDao.findById(productoId)
            ?: throw BusinessRuleException("El producto con ID $productoId no existe.")

        val resultado = productoDao.incrementarStock(productoId, cantidad)
        if (resultado == -1) {
            throw BusinessRuleException("No se pudo actualizar el stock. Intenta de nuevo.")
        }
        return resultado
    }

    /**
     * Decrementa stock_general de un producto.
     * Validación doble: servicio (primera línea) + DAO (atómica).
     *
     * @param productoId Long.
     * @param cantidad Debe ser > 0.
     * @return Stock resultante.
     * @throws ValidationException si cantidad <= 0.
     * @throws BusinessRuleException si no existe o stock insuficiente.
     *
     * RF asociado: RF-04 (Carga Diaria), RF-07 (Mermas)
     */
    @Throws(ValidationException::class, BusinessRuleException::class)
    fun decrementarStockProducto(productoId: Long, cantidad: Int): Int {
        if (cantidad <= 0) {
            throw ValidationException("La cantidad a decrementar debe ser mayor a 0.")
        }

        val producto = productoDao.findById(productoId)
            ?: throw BusinessRuleException("El producto con ID $productoId no existe.")

        if (producto.stockGeneral < cantidad) {
            throw BusinessRuleException(
                "Stock insuficiente para \"${producto.nombre}\". " +
                        "Disponible: ${producto.stockGeneral}, solicitado: $cantidad."
            )
        }

        return when (val resultado = productoDao.decrementarStock(productoId, cantidad)) {
            -1   -> throw BusinessRuleException("El producto con ID $productoId no existe.")
            -2   -> throw BusinessRuleException(
                "Stock insuficiente para \"${producto.nombre}\". " +
                        "Disponible: ${producto.stockGeneral}, solicitado: $cantidad."
            )
            else -> resultado
        }
    }

    // =========================================================================
    // HELPERS PRIVADOS
    // =========================================================================

    /**
     * Verifica si un producto tiene registros en detalle_venta.
     *
     * TODO M2: return detalleVentaDao.countByProducto(productoId) > 0
     * Stub temporal — retorna false para no bloquear desarrollo de M1.
     * RF asociado: RF-18 (decisión soft/hard delete)
     */
    private fun tieneHistorialVentas(productoId: Long): Boolean {
        // TODO M2: implementar con DetalleVentaDao
        return false
    }
}
