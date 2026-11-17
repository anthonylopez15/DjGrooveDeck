package com.anthony.djapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anthony.djapp.presentation.DjViewModel
import com.anthony.djapp.ui.DjApp

class MainActivity : ComponentActivity() {
    private lateinit var session: DjSession.Session

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) session.library.onFolderPicked(uri)
    }

    // sem esta permissão (Android 13+) a música continua em segundo plano, mas a notificação não aparece
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // mixer, decks e faixas carregadas ficam na sessão do processo: a tela só se liga a eles
        session = DjSession.get(this)
        // a biblioteca vive no processo; esta tela só empresta o seletor de pastas enquanto existe
        session.library.folderPicker = picker

        setContent {
            // o ViewModel sobrevive à recriação da tela; a sessão de áudio, ao processo inteiro
            val viewModel = viewModel { DjViewModel(session.mix, session.library) }
            DjApp(viewModel, session.library)
        }
        askNotificationPermissionOnce()
    }

    override fun onStart() {
        super.onStart()
        DjSession.activityVisible = true
        session.mix.setForeground(true)
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
        session.mix.setForeground(false)
        session.save()
        // Tocando: o serviço mantém o app vivo e o áudio ligado. Parado: desliga a saída de áudio.
        if (!session.anyPlaying || !PlaybackService.start(this)) session.stopOutput()
        super.onStop()
    }

    /** Seletor de pastas desta tela, emprestado à biblioteca enquanto ela existe. */
    private val picker: () -> Unit = { folderPicker.launch(null) }

    override fun onDestroy() {
        // devolve o seletor só se ainda é o desta tela (a nova pode já ter registrado o dela)
        if (session.library.folderPicker === picker) session.library.folderPicker = null
        super.onDestroy()
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
