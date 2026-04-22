package com.ambulantpoint

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.databinding.ActivityMainBinding
import com.ambulantpoint.ui.catalogo.GestionProductosActivity

/**
 * MainActivity — Pantalla de bienvenida y menú principal.
 *
 * Responsabilidad exclusiva: navegar a cada módulo al tocar
 * el botón correspondiente del grid. No contiene lógica de negocio.
 *
 * Referencia: Prompt Maestro Sección 6.1 y 6.2
 *
 * NOTA: No se usa ViewBinding complejo ni ViewModel en esta fase
 * (instrucción Prompt Maestro Sección 8 punto 9 — solo findViewById
 * y lógica directa en Activity). Sin embargo, se usa el binding
 * generado automáticamente por Android Studio para evitar
 * findViewById repetitivo, que es equivalente y más seguro.
 *
 * Activities destino:
 * - ProductoFormActivity  → M1 (Nuevo Producto)
 * - IniciarVentaActivity  → M2 (Iniciar Venta del Día)
 * - DashboardActivity     → M4 (Dashboard)
 * - ReporteActivity       → M3 (Generar Reporte)
 * - NotificacionesActivity → M4 (Notificaciones y Predicciones)
 * - MetodosPagoActivity   → Configuración
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Inicializa el binding, la toolbar y los botones de navegación. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarToolbar()
        configurarBotones()
    }

    // ─────────────────────────────────────────────────────────
    // TOOLBAR
    // ─────────────────────────────────────────────────────────

    /** Configura la toolbar sin botón de retroceso (es la pantalla raíz). */
    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        // En MainActivity no mostramos botón "atrás"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    // ─────────────────────────────────────────────────────────
    // BOTONES DEL MENÚ
    // Cada botón lanza un Intent explícito a su Activity destino.
    // ─────────────────────────────────────────────────────────

    /** Asigna listeners a cada botón del grid para navegar al módulo correspondiente. */
    private fun configurarBotones() {

        // ── M1: Gestión de Productos ─────────────────────────
        binding.btnNuevoProducto.setOnClickListener {
            startActivity(Intent(this, GestionProductosActivity::class.java))
        }

        // ── M2: Iniciar Venta del Día ────────────────────────
        binding.btnIniciarVenta.setOnClickListener {
            startActivity(Intent(this, IniciarVentaActivity::class.java))
        }

        // ── M4: Dashboard ────────────────────────────────────
        binding.btnDashboard.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        // ── M3: Generar Reporte ──────────────────────────────
        binding.btnReporte.setOnClickListener {
            startActivity(Intent(this, ReporteActivity::class.java))
        }

        // ── M4: Notificaciones y Predicciones ───────────────
        binding.btnNotificaciones.setOnClickListener {
            startActivity(Intent(this, NotificacionesActivity::class.java))
        }

        // ── Configuración: Métodos de Pago ───────────────────
        binding.btnMetodosPago.setOnClickListener {
            startActivity(Intent(this, MetodosPagoActivity::class.java))
        }
    }
}