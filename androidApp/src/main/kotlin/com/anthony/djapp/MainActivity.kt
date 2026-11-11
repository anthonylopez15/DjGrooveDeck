package com.anthony.djapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.anthony.djapp.ui.DjApp

class MainActivity : ComponentActivity() {
    private lateinit var session: DjSession.Session
    private lateinit var library: AndroidLibrary

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) library.onFolderPicked(uri)
    }

    // sem esta permissão (Android 13+) a música continua em segundo plano, mas a notificação não aparece
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // mixer, decks e faixas carregadas ficam na sessão do processo: a tela só se liga a eles
        session = DjSession.get(this)
        library = AndroidLibrary(applicationContext) { folderPicker.launch(null) }

        setContent { DjApp(session.controller, library) }
        askNotificationPermissionOnce()
    }

    override fun onStart() {
        super.onStart()
        DjSession.activityVisible = true
        session.controller.foreground = true
    }

    override fun onResume() {
        super.onResume()
        // voltar dos recentes sem que a tela tenha parado (sem onStart): a tela está de novo à vista.
        // A saída de áudio não é ligada aqui: o play a liga (DjController.playbackGate).
        DjSession.activityVisible = true
    }

    /**
     * Chamado quando o usuário sai de propósito (início, recentes), com o app ainda em primeiro plano:
     * é o momento em que o Android deixa iniciar o serviço sem restrição. O onStop fica de reserva
     * (tela desligando, por exemplo).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (session.anyPlaying) {
            DjSession.activityVisible = false // o serviço não pode achar que o app segue na tela
            PlaybackService.start(this)
        }
    }

    override fun onStop() {
        DjSession.activityVisible = false
        session.controller.foreground = false
        session.save()
        // Tocando: o serviço mantém o app vivo e o áudio ligado. Parado: desliga a saída de áudio.
        if (!session.anyPlaying || !PlaybackService.start(this)) session.output.stop()
        super.onStop()
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
