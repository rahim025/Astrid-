package com.example.floatingai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val providers = arrayOf("gemini", "groq", "anthropic", "openai")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val providerSpinner = findViewById<Spinner>(R.id.providerSpinner)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val modelInput = findViewById<EditText>(R.id.modelInput)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val enableBubbleButton = findViewById<Button>(R.id.enableBubbleButton)

        providerSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)

        val currentProvider = Prefs.getProvider(this)
        providerSpinner.setSelection(providers.indexOf(currentProvider))
        apiKeyInput.setText(Prefs.getApiKey(this, currentProvider))
        modelInput.setText(Prefs.getModel(this, currentProvider))

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long
            ) {
                val p = providers[position]
                apiKeyInput.setText(Prefs.getApiKey(this@MainActivity, p))
                modelInput.setText(Prefs.getModel(this@MainActivity, p))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        saveButton.setOnClickListener {
            val provider = providers[providerSpinner.selectedItemPosition]
            Prefs.setProvider(this, provider)
            Prefs.setApiKey(this, provider, apiKeyInput.text.toString().trim())
            Prefs.setModel(this, provider, modelInput.text.toString().trim())
            Toast.makeText(this, "Paramètres enregistrés", Toast.LENGTH_SHORT).show()
        }

        enableBubbleButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Autorise l'affichage par-dessus les autres applis, puis reviens appuyer à nouveau sur ce bouton",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                startBubbleService()
            }
        }
    }

    private fun startBubbleService() {
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Bulle activée ! Tu peux quitter l'appli.", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
