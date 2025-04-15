package com.example.tareaonline4

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tareaonline4.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPref: SharedPreferences
    private var conversionRate = 1.1 // Valor predeterminado

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "conversion_rate") {
            updateConversionRate()
            calculateConversion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inicializaremos con el valor predeterminado
        conversionRate = 1.1
        binding.tvCurrentRate.text = "Tasa inicial: 1 EUR = $conversionRate USD"
        binding.etEurosyDolares.setText("1.0") // Valor inicial

        // 2. Configuraremos SharedPreferences
        sharedPref = getSharedPreferences("exchange_prefs", Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)

        // 3. Configuraremos UI
        setupUI()

        // 4. Calcularemos automáticamente al iniciar
        calculateConversion()

        // 5. Programaremos la descarga diferida
        DownloadWorker.schedulePeriodicDownload(this)
    }

    private fun setupUI() {
        binding.apply {
            etEurosyDolares.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    calculateConversion()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            radioGroup.setOnCheckedChangeListener { _, _ -> calculateConversion() }
        }
    }

    private fun updateConversionRate() {
        val savedRate = sharedPref.getFloat("conversion_rate", -1f)
        if (savedRate > 0) {
            conversionRate = savedRate.toDouble()
            binding.tvCurrentRate.text = "Tasa actual: 1 EUR = ${"%.4f".format(conversionRate)} USD"
            Log.d("MainActivity", "Tasa actualizada desde SharedPreferences: $conversionRate")
        }
    }

    private fun calculateConversion() {
        try {
            val inputText = binding.etEurosyDolares.text.toString()

            if (inputText.isEmpty()) {
                binding.etResultado.setText("")
                return
            }

            val amount = inputText.toDouble()
            val result = when (binding.radioGroup.checkedRadioButtonId) {
                R.id.radioEurosaDolares -> amount * conversionRate
                R.id.radioDolaresaEuros -> amount / conversionRate
                else -> 0.0
            }

            binding.etResultado.setText("%.2f".format(result))

        } catch (e: NumberFormatException) {
           //control de errores
            if (binding.etEurosyDolares.text?.isNotEmpty() == true) {
                binding.etResultado.setText("Error: Ingrese solo números")
                Toast.makeText(this, "Por favor ingrese un valor numérico válido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}