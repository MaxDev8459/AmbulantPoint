package com.ambulantpoint

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ambulantpoint.databinding.ActivityNotificacionesBinding

/**
 * NotificacionesActivity — Esqueleto navegable.
 * La lógica de negocio se implementa en la fase de desarrollo del módulo correspondiente.
 * Referencia: Prompt Maestro Sección 8 punto 7.
 */
class NotificacionesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificacionesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificacionesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}