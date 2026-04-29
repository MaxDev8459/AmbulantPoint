package com.ambulantpoint.ui.venta

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.databinding.ActivityMetodosPagoBinding

/**
 * MetodosPagoActivity — Configurar métodos de pago activos.
 *
 * REGLAS DE NEGOCIO (autocontenidas, sin dependencia de M2/M3/M4):
 * 1. Siempre debe haber al menos 1 método activo.
 * 2. Al intentar DESACTIVAR cualquier método → AlertDialog de confirmación.
 * 3. Si es el ÚNICO activo → rechazar con AlertDialog de error.
 * 4. ACTIVAR siempre está permitido sin confirmación.
 *
 * PERSISTENCIA: SharedPreferences.
 * CU asociado: CU-03
 */
class MetodosPagoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMetodosPagoBinding

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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

    private fun cargarEstado() {
        cargandoEstado = true
        binding.switchEfectivo.isChecked      = prefs.getBoolean(KEY_EFECTIVO, true)
        binding.switchTarjeta.isChecked       = prefs.getBoolean(KEY_TARJETA, false)
        binding.switchTransferencia.isChecked = prefs.getBoolean(KEY_TRANSFERENCIA, false)
        cargandoEstado = false
    }

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

    private fun manejarCambioSwitch(
        nombre: String,
        clave: String,
        nuevoEstado: Boolean,
        revertir: () -> Unit
    ) {
        if (nuevoEstado) {
            guardarEstado()
            return
        }

        if (contarMetodosActivos() <= 1) {
            revertir()
            mostrarDialogUnicoActivo()
            return
        }

        revertir()
        mostrarDialogConfirmacionDesactivar(nombre) {
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
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun mostrarDialogUnicoActivo() {
        AlertDialog.Builder(this)
            .setTitle("Acción no permitida")
            .setMessage("¡Debe haber por lo menos 1 método de pago activo!")
            .setPositiveButton("Entendido") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
