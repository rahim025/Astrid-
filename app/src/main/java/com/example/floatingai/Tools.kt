package com.example.floatingai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract

/**
 * Jarvis peut déclencher de vraies actions sur le téléphone. Le modèle termine sa
 * réponse par une balise au format [[ACTION:type|param1|param2]] (voir Identity.kt).
 * ToolExecutor la repère, l'exécute, puis la retire du texte affiché/parlé.
 *
 * Choix de conception : chaque action ouvre l'app système correspondante (composeur
 * SMS, numéroteur, calendrier, horloge) plutôt que d'agir en silence. Aucune permission
 * dangereuse (SMS, appels, calendrier) n'est donc nécessaire — l'utilisateur garde la
 * main pour confirmer l'envoi ou l'enregistrement final.
 */
object ToolExecutor {

    private val actionRegex = Regex("""\[\[ACTION:([a-z_]+)((?:\|[^\[\]]*)*)]]""", RegexOption.IGNORE_CASE)

    data class ParsedReply(val displayText: String, val actionResult: String?)

    /** Repère une éventuelle balise d'action dans la réponse brute du modèle, l'exécute,
     * et renvoie le texte nettoyé (sans la balise) + un message de confirmation. */
    fun process(context: Context, rawReply: String): ParsedReply {
        val match = actionRegex.find(rawReply) ?: return ParsedReply(rawReply.trim(), null)

        val type = match.groupValues[1].lowercase()
        val params = match.groupValues[2].split("|").drop(1).map { it.trim() }
        val cleanText = rawReply.replace(match.value, "").trim()

        val result = try {
            execute(context, type, params)
        } catch (e: Exception) {
            "Action impossible (${e.message ?: e.javaClass.simpleName})."
        }

        return ParsedReply(cleanText, result)
    }

    private fun execute(context: Context, type: String, params: List<String>): String = when (type) {
        "open_app" -> openApp(context, params.getOrElse(0) { "" })
        "web_search" -> webSearch(context, params.getOrElse(0) { "" })
        "call" -> call(context, params.getOrElse(0) { "" })
        "sms" -> sms(context, params.getOrElse(0) { "" }, params.getOrElse(1) { "" })
        "alarm" -> alarm(context, params.getOrElse(0) { "" }, params.getOrElse(1) { "Réveil" })
        "timer" -> timer(context, params.getOrElse(0) { "60" }, params.getOrElse(1) { "Minuteur" })
        "flashlight" -> flashlight(context, params.getOrElse(0) { "on" })
        "reminder" -> reminder(context, params.getOrElse(0) { "" }, params.getOrElse(1) { "" })
        else -> "Action inconnue : $type"
    }

    private fun newTaskIntent(intent: Intent): Intent =
        intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    private fun openApp(context: Context, name: String): String {
        if (name.isBlank()) return "Nom d'application manquant."
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val target = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(name, ignoreCase = true)
        }
        val launchIntent = target?.let { pm.getLaunchIntentForPackage(it.packageName) }
        return if (launchIntent != null && target != null) {
            context.startActivity(newTaskIntent(launchIntent))
            "${pm.getApplicationLabel(target)} ouverte."
        } else {
            "Application « $name » introuvable sur ce téléphone."
        }
    }

    private fun webSearch(context: Context, query: String): String {
        if (query.isBlank()) return "Requête de recherche manquante."
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        context.startActivity(newTaskIntent(Intent(Intent.ACTION_VIEW, uri)))
        return "Recherche « $query » ouverte dans le navigateur."
    }

    private fun call(context: Context, number: String): String {
        if (number.isBlank()) return "Numéro manquant."
        context.startActivity(newTaskIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))))
        return "Numéroteur ouvert pour $number."
    }

    private fun sms(context: Context, number: String, message: String): String {
        if (number.isBlank()) return "Numéro manquant."
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
        }
        context.startActivity(newTaskIntent(intent))
        return "SMS prêt pour $number — confirme l'envoi depuis l'app Messages."
    }

    private fun alarm(context: Context, time: String, label: String): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
            ?: return "Heure invalide pour le réveil (attendu HH:MM)."
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        context.startActivity(newTaskIntent(intent))
        return "Réveil proposé pour ${"%02d".format(hour)}:${"%02d".format(minute)}."
    }

    private fun timer(context: Context, seconds: String, label: String): String {
        val secs = seconds.toIntOrNull() ?: return "Durée de minuteur invalide."
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, secs)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        context.startActivity(newTaskIntent(intent))
        return "Minuteur de $secs secondes lancé."
    }

    private fun flashlight(context: Context, state: String): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Pas de flash disponible sur cet appareil."
        val on = state.equals("on", ignoreCase = true)
        cameraManager.setTorchMode(cameraId, on)
        return if (on) "Lampe torche allumée." else "Lampe torche éteinte."
    }

    private fun reminder(context: Context, title: String, whenText: String): String {
        if (title.isBlank()) return "Titre du rappel manquant."
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            if (whenText.isNotBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, whenText)
            }
        }
        context.startActivity(newTaskIntent(intent))
        return "Rappel « $title » prêt à être enregistré dans le calendrier."
    }
}
