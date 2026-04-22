package com.ambulantpoint

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.databinding.ActivityReporteBinding

/**
 * ReporteActivity — Esqueleto navegable.
 * La lógica de negocio se implementa en la fase de desarrollo del módulo correspondiente.
 * Referencia: Prompt Maestro Sección 8 punto 7.
 */
class ReporteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReporteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}