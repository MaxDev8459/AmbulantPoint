package com.ambulantpoint

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.databinding.ActivityMetodosPagoBinding

/**
 * MetodosPagoActivity — Configurar métodos de pago activos.
 *
 * Referencia: Prompt Maestro Sección 6.2
 *
 * REGLAS DE NEGOCIO (autocontenidas, sin dependencia de M2/M3/M4):
 * 1. Siempre debe haber al menos 1 método activo.
 * 2. Al intentar DESACTIVAR cualquier método → AlertDialog de confirmación.
 * 3. Si es el ÚNICO activo → rechazar con AlertDialog de error.
 * 4. ACTIVAR siempre está permitido sin confirmación.
 *
 * PERSISTENCIA: SharedPreferences.
 * Justificación: el estado de los métodos de pago es configuración
 * global de la app, no datos de transacciones. SharedPreferences es
 * el mecanismo idóneo para preferencias simples clave-valor.
 * SQLite sería sobrediseño para 3 booleanos que no necesitan consultas,
 * joins ni historial.
 *
 * CLAVE: "metodos_pago" con campos:
 *   - efectivo_activo   (Boolean, default true)
 *   - tarjeta_activa    (Boolean, default false)
 *   - transferencia_activa (Boolean, default false)
 */
class MetodosPagoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMetodosPagoBinding

    // SharedPreferences para persistir estado de métodos
    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Flag interno para ignorar el listener durante carga inicial
    // y evitar que setChecked() dispare el listener prematuramente
    private var cargandoEstado = false

    companion object {
        private const val PREFS_NAME         = "metodos_pago"
        private const val KEY_EFECTIVO       = "efectivo_activo"
        private const val KEY_TARJETA        = "tarjeta_activa"
        private const val KEY_TRANSFERENCIA  = "transferencia_activa"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMetodosPagoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarToolbar()
        cargarEstado()
        configurarSwitches()
    }

    // ─────────────────────────────────────────────────────────
    // TOOLBAR
    // ─────────────────────────────────────────────────────────

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    // ─────────────────────────────────────────────────────────
    // CARGA Y PERSISTENCIA DE ESTADO
    // ─────────────────────────────────────────────────────────

    /**
     * Lee el estado guardado en SharedPreferences y lo aplica
     * a los switches sin disparar los listeners.
     * Default inicial: solo Efectivo activo.
     */
    private fun cargarEstado() {
        cargandoEstado = true
        binding.switchEfectivo.isChecked      = prefs.getBoolean(KEY_EFECTIVO, true)
        binding.switchTarjeta.isChecked       = prefs.getBoolean(KEY_TARJETA, false)
        binding.switchTransferencia.isChecked = prefs.getBoolean(KEY_TRANSFERENCIA, false)
        cargandoEstado = false
    }

    /**
     * Persiste el estado actual de los 3 switches en SharedPreferences.
     * Se llama después de cada cambio confirmado.
     */
    private fun guardarEstado() {
        prefs.edit()
            .putBoolean(KEY_EFECTIVO, binding.switchEfectivo.isChecked)
            .putBoolean(KEY_TARJETA, binding.switchTarjeta.isChecked)
            .putBoolean(KEY_TRANSFERENCIA, binding.switchTransferencia.isChecked)
            .apply()
    }

    // ─────────────────────────────────────────────────────────
    // CONFIGURACIÓN DE SWITCHES
    // ─────────────────────────────────────────────────────────

    private fun configurarSwitches() {
        binding.switchEfectivo.setOnCheckedChangeListener { _, isChecked ->
            if (cargandoEstado) return@setOnCheckedChangeListener
            manejarCambioSwitch("Efectivo", KEY_EFECTIVO, isChecked) {
                // Si se revierte, restaurar sin disparar listener
                cargandoEstado = true
                binding.switchEfectivo.isChecked = !isChecked
                cargandoEstado = false
            }
        }

        binding.switchTarjeta.setOnCheckedChangeListener { _, isChecked ->
            if (cargandoEstado) return@setOnCheckedChangeListener
            manejarCambioSwitch("Tarjeta", KEY_TARJETA, isChecked) {
                cargandoEstado = true
                binding.switchTarjeta.isChecked = !isChecked
                cargandoEstado = false
            }
        }

        binding.switchTransferencia.setOnCheckedChangeListener { _, isChecked ->
            if (cargandoEstado) return@setOnCheckedChangeListener
            manejarCambioSwitch("Transferencia", KEY_TRANSFERENCIA, isChecked) {
                cargandoEstado = true
                binding.switchTransferencia.isChecked = !isChecked
                cargandoEstado = false
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // LÓGICA DE NEGOCIO — CAMBIO DE SWITCH
    // ─────────────────────────────────────────────────────────

    /**
     * Evalúa si el cambio de un switch es permitido y actúa:
     *
     * ACTIVAR  → siempre permitido, persiste inmediatamente.
     * DESACTIVAR:
     *   - Si es el único activo → rechazar con AlertDialog de error.
     *   - Si hay otro activo   → pedir confirmación con AlertDialog.
     *
     * @param nombre       Nombre legible del método (para mensajes).
     * @param clave        Clave en SharedPreferences.
     * @param nuevoEstado  true = activar, false = desactivar.
     * @param revertir     Lambda que revierte el switch si se cancela.
     */
    private fun manejarCambioSwitch(
        nombre: String,
        clave: String,
        nuevoEstado: Boolean,
        revertir: () -> Unit
    ) {
        if (nuevoEstado) {
            // ACTIVAR — siempre permitido, sin confirmación
            guardarEstado()
            return
        }

        // DESACTIVAR — verificar si es el único activo
        if (contarMetodosActivos() <= 1) {
            // Regla 3: no se puede desactivar el último activo
            revertir()
            mostrarDialogUnicoActivo()
            return
        }

        // Hay otros activos — pedir confirmación (Regla 1)
        revertir() // Revertir visualmente mientras el usuario decide
        mostrarDialogConfirmacionDesactivar(nombre) {
            // Confirmado: aplicar el cambio real
            cargandoEstado = true
            when (clave) {
                KEY_EFECTIVO      -> binding.switchEfectivo.isChecked      = false
                KEY_TARJETA       -> binding.switchTarjeta.isChecked       = false
                KEY_TRANSFERENCIA -> binding.switchTransferencia.isChecked = false
            }
            cargandoEstado = false
            guardarEstado()
        }
    }

    /**
     * Cuenta cuántos switches están actualmente en estado ON.
     * Se usa para la regla "mínimo 1 activo".
     */
    private fun contarMetodosActivos(): Int {
        var count = 0
        if (binding.switchEfectivo.isChecked)      count++
        if (binding.switchTarjeta.isChecked)       count++
        if (binding.switchTransferencia.isChecked) count++
        return count
    }

    // ─────────────────────────────────────────────────────────
    // ALERTDIALOGS
    // ─────────────────────────────────────────────────────────

    /**
     * AlertDialog de confirmación al desactivar un método.
     * Regla 2: "¿Estás seguro que deseas desactivar este método de pago?"
     */
    private fun mostrarDialogConfirmacionDesactivar(
        nombre: String,
        onConfirmado: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("Desactivar método de pago")
            .setMessage("¿Estás seguro que deseas desactivar $nombre como método de pago?")
            .setPositiveButton("Desactivar") { dialog, _ ->
                onConfirmado()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                // El switch ya fue revertido antes de mostrar este dialog
            }
            .setCancelable(false)
            .show()
    }

    /**
     * AlertDialog de rechazo cuando se intenta desactivar el único activo.
     * Regla 3: "¡Debe haber por lo menos 1 método de pago activo!"
     */
    private fun mostrarDialogUnicoActivo() {
        AlertDialog.Builder(this)
            .setTitle("Acción no permitida")
            .setMessage("¡Debe haber por lo menos 1 método de pago activo!")
            .setPositiveButton("Entendido") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}