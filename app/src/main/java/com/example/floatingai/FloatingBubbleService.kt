package com.example.floatingai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private data class ChatMessage(
        val role: String,          // "user" ou "assistant"
        val content: String,
        val time: Long = System.currentTimeMillis(),
        val isError: Boolean = false
    )

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var chatView: View? = null
    private val messages = mutableListOf<ChatMessage>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var isWaiting = false
    private var messagesContainer: LinearLayout? = null
    private var messagesScroll: ScrollView? = null
    private var statusText: TextView? = null
    private var sendButton: ImageView? = null
    private var typingView: TextView? = null
    private var typingStep = 0

    private val typingRunnable = object : Runnable {
        override fun run() {
            typingView?.text = "Astrid écrit" + ".".repeat(typingStep % 3 + 1)
            typingStep++
            mainHandler.postDelayed(this, 400)
        }
    }

    companion object {
        const val CHANNEL_ID = "floating_ai_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Astrid", NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Astrid est active")
            .setContentText("Appuie sur la bulle pour discuter")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    private fun windowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.bubble_layout, null)
        bubbleView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        val bubbleIcon = view.findViewById<ImageView>(R.id.bubbleIcon)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubbleIcon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) toggleChat()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    private fun toggleChat() {
        if (chatView != null) closeChat() else showChat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showChat() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.chat_overlay, null)
        chatView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.68).toInt(),
            windowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        messagesContainer = view.findViewById(R.id.messagesContainer)
        messagesScroll = view.findViewById(R.id.messagesScroll)
        statusText = view.findViewById(R.id.statusText)
        sendButton = view.findViewById(R.id.sendButton)
        val input = view.findViewById<EditText>(R.id.chatInput)
        val closeBtn = view.findViewById<ImageView>(R.id.closeButton)

        closeBtn.setOnClickListener { closeChat() }

        sendButton?.setOnClickListener {
            if (isWaiting) return@setOnClickListener
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            input.setText("")
            messages.add(ChatMessage("user", text))
            requestReply()
        }

        setWaiting(isWaiting)
        renderMessages()

        view.alpha = 0f
        view.translationY = dp(40).toFloat()
        windowManager.addView(chatView, params)
        view.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun closeChat() {
        val view = chatView ?: return
        chatView = null
        mainHandler.removeCallbacks(typingRunnable)
        typingView = null
        messagesContainer = null
        messagesScroll = null
        statusText = null
        sendButton = null
        view.animate()
            .alpha(0f)
            .translationY(dp(40).toFloat())
            .setDuration(180)
            .withEndAction {
                try { windowManager.removeView(view) } catch (_: Exception) {}
            }
            .start()
    }

    // ---------- Envoi / réponse ----------

    private fun requestReply() {
        setWaiting(true)
        renderMessages()

        val history = messages.filter { !it.isError }.map { it.role to it.content }

        ApiClient.sendMessage(
            this,
            history,
            onResult = { reply ->
                mainHandler.post {
                    messages.add(ChatMessage("assistant", reply))
                    setWaiting(false)
                    renderMessages()
                }
            },
            onError = { error ->
                mainHandler.post {
                    messages.add(ChatMessage("assistant", friendlyError(error), isError = true))
                    setWaiting(false)
                    renderMessages()
                }
            }
        )
    }

    private fun setWaiting(waiting: Boolean) {
        isWaiting = waiting
        statusText?.text = if (waiting) "En train d'écrire…" else "En ligne"
        sendButton?.alpha = if (waiting) 0.5f else 1f
    }

    private fun friendlyError(raw: String): String {
        val code = Regex("\\((\\d{3})\\)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            raw.startsWith("Aucune clé API") -> raw
            raw.startsWith("Erreur réseau") ->
                "Connexion impossible. Vérifiez votre accès à internet."
            code == 401 || code == 403 ->
                "Clé API refusée. Vérifiez-la dans les paramètres de l'application."
            code == 404 ->
                "Le modèle sélectionné est introuvable. Modifiez-le dans les paramètres de l'application."
            code == 429 ->
                "Trop de demandes en peu de temps. Patientez un instant."
            code != null && code >= 500 ->
                "Le service est momentanément indisponible."
            else -> "Une erreur est survenue."
        }
    }

    // ---------- Affichage des messages ----------

    private fun renderMessages() {
        val container = messagesContainer ?: return
        mainHandler.removeCallbacks(typingRunnable)
        typingView = null
        container.removeAllViews()

        // Message d'accueil (affichage uniquement, non envoyé à l'API)
        addMessageView(
            container,
            ChatMessage(
                "assistant",
                "Bonjour, je suis Astrid, votre assistante. Comment puis-je vous aider ?",
                time = 0L
            ),
            showTime = false
        )

        for (message in messages) addMessageView(container, message)

        if (isWaiting) {
            typingStep = 0
            typingView = addMessageView(
                container,
                ChatMessage("assistant", "Astrid écrit."),
                showTime = false
            )
            mainHandler.post(typingRunnable)
        }

        messagesScroll?.post { messagesScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addMessageView(
        container: LinearLayout,
        message: ChatMessage,
        showTime: Boolean = true
    ): TextView {
        val row = LayoutInflater.from(this).inflate(R.layout.item_message, container, false)
        val bubble = row.findViewById<TextView>(R.id.messageText)
        val timeText = row.findViewById<TextView>(R.id.messageTime)

        val isUser = message.role == "user"
        val gravity = if (isUser) Gravity.END else Gravity.START
        (bubble.layoutParams as LinearLayout.LayoutParams).gravity = gravity
        (timeText.layoutParams as LinearLayout.LayoutParams).gravity = gravity
        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()

        when {
            isUser -> {
                bubble.setBackgroundResource(R.drawable.bubble_user)
                bubble.setTextColor(Color.WHITE)
            }
            message.isError -> {
                bubble.setBackgroundResource(R.drawable.bubble_error)
                bubble.setTextColor(ContextCompat.getColor(this, R.color.astrid_error_text))
            }
            else -> {
                bubble.setBackgroundResource(R.drawable.bubble_assistant)
                bubble.setTextColor(ContextCompat.getColor(this, R.color.astrid_text))
            }
        }
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10))

        bubble.text = if (isUser || message.isError) message.content else formatText(message.content)

        if (message.isError) {
            timeText.text = "Touchez pour réessayer"
            bubble.setOnClickListener {
                if (isWaiting) return@setOnClickListener
                messages.remove(message)
                requestReply()
            }
        } else if (showTime) {
            timeText.text = timeFormat.format(Date(message.time))
            bubble.setOnLongClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Astrid", message.content))
                Toast.makeText(this, "Message copié", Toast.LENGTH_SHORT).show()
                true
            }
        } else {
            timeText.visibility = View.GONE
        }

        container.addView(row)
        return bubble
    }

    /** Mise en forme légère : **gras**, listes à puces, titres # simplifiés. */
    private fun formatText(raw: String): CharSequence {
        val cleaned = raw.trim().lines().joinToString("\n") { line ->
            line
                .replace(Regex("^\\s*[-*]\\s+"), "• ")
                .replace(Regex("^#{1,6}\\s+"), "")
        }
        val builder = SpannableStringBuilder()
        var last = 0
        for (match in Regex("\\*\\*(.+?)\\*\\*").findAll(cleaned)) {
            builder.append(cleaned.substring(last, match.range.first))
            val start = builder.length
            builder.append(match.groupValues[1])
            builder.setSpan(
                StyleSpan(Typeface.BOLD), start, builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            last = match.range.last + 1
        }
        builder.append(cleaned.substring(last))
        return builder
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(typingRunnable)
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        chatView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }
}
