package com.ambulantpoint

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.databinding.ActivityIniciarVentaBinding

/**
 * IniciarVentaActivity — Esqueleto navegable.
 * La lógica de negocio se implementa en la fase de desarrollo del módulo correspondiente.
 * Referencia: Prompt Maestro Sección 8 punto 7.
 */
class IniciarVentaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIniciarVentaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIniciarVentaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}