package com.example.floatingai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val providers = arrayOf("gemini", "groq", "anthropic", "openai", "offline")

    private val pickModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importLocalModel(uri)
        }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Sans micro, Jarvis ne pourra pas t'écouter à la voix.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val providerSpinner = findViewById<Spinner>(R.id.providerSpinner)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val modelInput = findViewById<EditText>(R.id.modelInput)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val enableBubbleButton = findViewById<Button>(R.id.enableBubbleButton)
        val offlineSection = findViewById<LinearLayout>(R.id.offlineSection)
        val localModelStatus = findViewById<TextView>(R.id.localModelStatus)
        val importModelButton = findViewById<Button>(R.id.importModelButton)
        val voiceReplySwitch = findViewById<Switch>(R.id.voiceReplySwitch)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        voiceReplySwitch.isChecked = Prefs.isVoiceReplyEnabled(this)
        voiceReplySwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setVoiceReplyEnabled(this, checked)
        }

        providerSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)

        val currentProvider = Prefs.getProvider(this)
        providerSpinner.setSelection(providers.indexOf(currentProvider))
        apiKeyInput.setText(Prefs.getApiKey(this, currentProvider))
        modelInput.setText(Prefs.getModel(this, currentProvider))
        updateProviderFields(currentProvider, apiKeyInput, modelInput, offlineSection)
        refreshLocalModelStatus(localModelStatus)

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long
            ) {
                val p = providers[position]
                apiKeyInput.setText(Prefs.getApiKey(this@MainActivity, p))
                modelInput.setText(Prefs.getModel(this@MainActivity, p))
                updateProviderFields(p, apiKeyInput, modelInput, offlineSection)
                refreshLocalModelStatus(localModelStatus)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        importModelButton.setOnClickListener {
            pickModelLauncher.launch(arrayOf("*/*"))
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

    private fun updateProviderFields(
        provider: String,
        apiKeyInput: EditText,
        modelInput: EditText,
        offlineSection: LinearLayout
    ) {
        val isOffline = provider == "offline"
        apiKeyInput.visibility = if (isOffline) android.view.View.GONE else android.view.View.VISIBLE
        modelInput.visibility = if (isOffline) android.view.View.GONE else android.view.View.VISIBLE
        offlineSection.visibility = if (isOffline) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun refreshLocalModelStatus(statusView: TextView) {
        val name = Prefs.getLocalModelName(this)
        statusView.text = if (name.isNotBlank() && LocalLlmClient.hasLocalModel(this)) {
            "Modèle importé : $name"
        } else {
            "Aucun modèle importé"
        }
    }

    private fun importLocalModel(uri: Uri) {
        Toast.makeText(this, "Import du modèle en cours…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val destination = LocalLlmClient.modelFile(this)
                contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Impossible de lire le fichier sélectionné")

                LocalLlmClient.unload()

                val displayName = queryFileName(uri) ?: destination.name
                Prefs.setLocalModelName(this, displayName)

                runOnUiThread {
                    findViewById<TextView>(R.id.localModelStatus)?.text = "Modèle importé : $displayName"
                    Toast.makeText(this, "Modèle importé avec succès", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Échec de l'import : ${e.message ?: e.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) {
            null
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
