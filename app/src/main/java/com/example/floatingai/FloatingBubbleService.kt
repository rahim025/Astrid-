package com.example.floatingai

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
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
import kotlin.random.Random

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

    // ---------- Visage kawaii ----------
    private var bubbleContainer: FrameLayout? = null
    private var bubbleIcon: ImageView? = null
    private var headerAvatar: ImageView? = null
    private var isSmiling = false
    private var idleBounceAnimator: ObjectAnimator? = null

    private val blinkRunnable = object : Runnable {
        override fun run() {
            applyFace(R.drawable.kawaii_face_blink)
            mainHandler.postDelayed({ applyFace(currentFaceDrawable()) }, 130)
            mainHandler.postDelayed(this, 2600L + Random.nextLong(2600))
        }
    }

    private val smileRunnable = object : Runnable {
        override fun run() {
            isSmiling = true
            applyFace(R.drawable.kawaii_face_smile)
            mainHandler.postDelayed({
                isSmiling = false
                applyFace(currentFaceDrawable())
            }, 1500)
            mainHandler.postDelayed(this, 3500L + Random.nextLong(3500))
        }
    }

    private val sparkleRunnable = object : Runnable {
        override fun run() {
            if (chatView == null) spawnSparkle()
            mainHandler.postDelayed(this, 2200)
        }
    }

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
        mainHandler.postDelayed(blinkRunnable, 2600L + Random.nextLong(2600))
        mainHandler.postDelayed(smileRunnable, 3500L + Random.nextLong(3500))
        mainHandler.postDelayed(sparkleRunnable, 2200)
    }

    // ---------- Animation du visage kawaii ----------

    private fun currentFaceDrawable(): Int =
        if (isSmiling) R.drawable.kawaii_face_smile else R.drawable.kawaii_face

    private fun applyFace(resId: Int) {
        bubbleIcon?.setImageResource(resId)
        headerAvatar?.setImageResource(resId)
    }

    private fun dpF(value: Int): Float = value * resources.displayMetrics.density

    private fun startIdleBounce(view: ImageView) {
        idleBounceAnimator?.cancel()
        val anim = ObjectAnimator.ofFloat(view, "translationY", 0f, -dpF(7), 0f)
        anim.duration = 2600
        anim.repeatCount = ObjectAnimator.INFINITE
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.start()
        idleBounceAnimator = anim
    }

    /** Petit "squish" façon gelée au tapotement, puis ouvre le chat. */
    private fun playSquishThenToggle(view: ImageView) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.12f, 0.9f, 1.03f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.82f, 1.18f, 0.95f, 1f)
        val set = AnimatorSet()
        set.playTogether(scaleX, scaleY)
        set.duration = 550
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                toggleChat()
            }
        })
        set.start()
    }

    /** Étincelle ambiante (étoile ou coeur) autour de la bulle, comme dans la version web. */
    private fun spawnSparkle() {
        val container = bubbleContainer ?: return
        val isHeart = Random.nextFloat() > 0.55f
        val sparkle = ImageView(this)
        sparkle.setImageResource(if (isHeart) R.drawable.ic_kawaii_heart else R.drawable.ic_kawaii_star)

        val size = dp(14)
        val params = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        val angle = Random.nextDouble(0.0, Math.PI * 2)
        val radius = dpF(34) + Random.nextFloat() * dpF(12)
        params.leftMargin = (Math.cos(angle) * radius).toInt()
        params.topMargin = (Math.sin(angle) * radius).toInt()
        sparkle.layoutParams = params
        sparkle.alpha = 0f
        sparkle.scaleX = 0.3f
        sparkle.scaleY = 0.3f
        container.addView(sparkle)

        sparkle.animate()
            .alpha(1f)
            .scaleX(1f).scaleY(1f)
            .translationYBy(-dpF(18))
            .rotationBy(90f)
            .setDuration(700)
            .withEndAction {
                sparkle.animate()
                    .alpha(0f)
                    .scaleX(0.7f).scaleY(0.7f)
                    .setDuration(700)
                    .withEndAction { container.removeView(sparkle) }
                    .start()
            }
            .start()
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

        bubbleContainer = view.findViewById(R.id.bubbleContainer)
        val bubbleIcon = view.findViewById<ImageView>(R.id.bubbleIcon)
        this.bubbleIcon = bubbleIcon
        bubbleIcon.setImageResource(currentFaceDrawable())
        startIdleBounce(bubbleIcon)

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
                    if (!isDragging) playSquishThenToggle(bubbleIcon)
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
        headerAvatar = view.findViewById(R.id.headerAvatar)
        headerAvatar?.setImageResource(currentFaceDrawable())
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

        setStatusIdle()
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
        headerAvatar = null
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

    private fun isOfflineMode(): Boolean = Prefs.getProvider(this) == "offline"

    private fun setStatusIdle() {
        statusText?.text = if (isOfflineMode()) "Hors ligne (modèle local)" else "En ligne"
    }

    private fun setWaiting(waiting: Boolean) {
        isWaiting = waiting
        statusText?.text = if (waiting) "En train d'écrire…" else {
            if (isOfflineMode()) "Hors ligne (modèle local)" else "En ligne"
        }
        sendButton?.alpha = if (waiting) 0.5f else 1f
    }

    private fun friendlyError(raw: String): String {
        val code = Regex("\\((\\d{3})\\)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            raw.startsWith("Aucune clé API") -> raw
            raw.startsWith("Aucun modèle hors ligne") -> raw
            raw.startsWith("Erreur du modèle local") -> raw
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
                "Bonjour, je suis Astrid, l'assistante personnelle de Rahim Batchabi. Comment puis-je vous aider ?",
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
        mainHandler.removeCallbacks(blinkRunnable)
        mainHandler.removeCallbacks(smileRunnable)
        mainHandler.removeCallbacks(sparkleRunnable)
        idleBounceAnimator?.cancel()
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        chatView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }
}
