package com.anthony.djapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Serviço em primeiro plano que mantém a música tocando quando o app sai da tela.
 *
 * Ele não é dono do áudio (isso é da [DjSession]): serve para o Android não matar o processo e para
 * mostrar a notificação, com atalho para abrir o app e para pausar tudo. Ele se encerra sozinho
 * quando o app volta à tela ou quando nenhum deck está tocando; nesse segundo caso também para a
 * saída de áudio, para não gastar bateria à toa.
 */
class PlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    /** Id do último pedido de start: stopSelf(id) é ignorado pelo sistema se chegou outro start depois. */
    private var lastStartId = 0

    /**
     * Decidido na hora de parar: o serviço parou porque o app voltou à tela (o áudio segue com ele).
     * Não dá para decidir no onDestroy, que roda depois, quando o app pode já ter saído de novo.
     */
    private var keepAudioOnDestroy = false

    private var ticks = 0

    private val watcher = object : Runnable {
        override fun run() {
            val session = DjSession.get(this@PlaybackService)
            when {
                DjSession.activityVisible -> {
                    keepAudioOnDestroy = true
                    finish()
                }
                !session.anyPlaying -> finish()
                else -> {
                    // tocando em segundo plano: guarda a posição de tempos em tempos, caso o processo morra
                    if (++ticks % SAVE_EVERY_TICKS == 0) session.save()
                    handler.postDelayed(this, WATCH_INTERVAL_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reprodução", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        keepAudioOnDestroy = false // um novo pedido: o serviço volta a cuidar do áudio
        if (intent?.action == ACTION_PAUSE_ALL) DjSession.get(this).pauseAll()

        // precisa vir logo: quem chamou startForegroundService() espera isto em poucos segundos
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        handler.removeCallbacks(watcher)
        handler.post(watcher)
        return START_NOT_STICKY
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // o app foi tirado da lista de recentes: a música para
        keepAudioOnDestroy = false
        finish()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watcher)
        if (!keepAudioOnDestroy) {
            // encerrado com o app fora da tela (nada tocando ou app removido): para tudo, para que a
            // música não volte a tocar sozinha quando o app for aberto de novo
            val session = DjSession.get(this)
            session.pauseAll() // também guarda o estado
            session.output.stop()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val pause = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("GrooveDeck DJ")
            .setContentText("Música tocando · toque para abrir")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Pausar",
                    pause,
                ).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.anthony.djapp.PAUSE_ALL"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val WATCH_INTERVAL_MS = 400L
        private const val SAVE_EVERY_TICKS = 12 // ~5 s

        /** Inicia o serviço (chamado quando o app sai da tela com música tocando). false se o sistema recusar. */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, PlaybackService::class.java))
            true
        } catch (e: Exception) {
            false
        }
    }
}
