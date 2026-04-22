package com.ambulantpoint

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.databinding.ActivityDashboardBinding

/**
 * DashboardActivity — Esqueleto navegable.
 * La lógica de negocio se implementa en la fase de desarrollo del módulo correspondiente.
 * Referencia: Prompt Maestro Sección 8 punto 7.
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}