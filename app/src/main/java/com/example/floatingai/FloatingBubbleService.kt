package com.example.floatingai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var chatView: View? = null
    private val conversation = mutableListOf<Pair<String, String>>() // role, content
    private val mainHandler = Handler(Looper.getMainLooper())

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

    private fun showChat() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.chat_overlay, null)
        chatView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.6).toInt(),
            windowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM

        val messagesText = view.findViewById<TextView>(R.id.messagesText)
        val scrollView = view.findViewById<ScrollView>(R.id.messagesScroll)
        val input = view.findViewById<EditText>(R.id.chatInput)
        val sendBtn = view.findViewById<ImageView>(R.id.sendButton)
        val closeBtn = view.findViewById<ImageView>(R.id.closeButton)

        fun renderConversation() {
            val sb = StringBuilder()
            for ((role, content) in conversation) {
                val label = if (role == "user") "Toi" else "IA"
                sb.append(label).append(": ").append(content).append("\n\n")
            }
            messagesText.text = sb.toString()
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }

        renderConversation()

        closeBtn.setOnClickListener { closeChat() }

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            conversation.add("user" to text)
            renderConversation()
            input.setText("")

            ApiClient.sendMessage(
                this,
                conversation.toList(),
                onResult = { reply ->
                    conversation.add("assistant" to reply)
                    mainHandler.post { renderConversation() }
                },
                onError = { error ->
                    conversation.add("assistant" to "⚠️ $error")
                    mainHandler.post { renderConversation() }
                }
            )
        }

        windowManager.addView(chatView, params)
    }

    private fun closeChat() {
        chatView?.let { windowManager.removeView(it) }
        chatView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        chatView?.let { windowManager.removeView(it) }
    }
}
