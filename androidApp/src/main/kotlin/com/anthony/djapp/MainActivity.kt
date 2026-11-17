package com.anthony.djapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import com.anthony.djapp.data.AndroidAudioSystem
import com.anthony.djapp.data.AndroidTrackRepository
import com.anthony.djapp.data.MixSession
import com.anthony.djapp.presentation.DjViewModel
import com.anthony.djapp.ui.DjApp
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    // peças do processo, entregues pelo Koin (a tela só se liga a elas)
    private val mix: MixSession by inject()
    private val library: AndroidTrackRepository by inject()
    private val audioSystem: AndroidAudioSystem by inject()
    private val screen: ScreenVisibility by inject()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) library.onFolderPicked(uri)
    }

    // sem esta permissão (Android 13+) a música continua em segundo plano, mas a notificação não aparece
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // a biblioteca vive no processo; esta tela só empresta o seletor de pastas enquanto existe
        library.folderPicker = picker

        setContent {
            // o ViewModel sobrevive à recriação da tela; a sessão de áudio, ao processo inteiro
            DjApp(koinViewModel<DjViewModel>(), library)
        }
        askNotificationPermissionOnce()
    }

    override fun onStart() {
        super.onStart()
        screen.visible = true
        mix.setForeground(true)
    }

    override fun onResume() {
        super.onResume()
        // voltar dos recentes sem que a tela tenha parado (sem onStart): a tela está de novo à vista.
        // A saída de áudio não é ligada aqui: o play a liga (DjController.playbackGate).
        screen.visible = true
    }

    /**
     * Chamado quando o usuário sai de propósito (início, recentes), com o app ainda em primeiro plano:
     * é o momento em que o Android deixa iniciar o serviço sem restrição. O onStop fica de reserva
     * (tela desligando, por exemplo).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (mix.anyPlaying) {
            screen.visible = false // o serviço não pode achar que o app segue na tela
            PlaybackService.start(this)
        }
    }

    override fun onStop() {
        screen.visible = false
        mix.setForeground(false)
        mix.save()
        // Tocando: o serviço mantém o app vivo e o áudio ligado. Parado: desliga a saída de áudio.
        if (!mix.anyPlaying || !PlaybackService.start(this)) audioSystem.stopOutput()
        super.onStop()
    }

    /** Seletor de pastas desta tela, emprestado à biblioteca enquanto ela existe. */
    private val picker: () -> Unit = { folderPicker.launch(null) }

    override fun onDestroy() {
        // devolve o seletor só se ainda é o desta tela (a nova pode já ter registrado o dela)
        if (library.folderPicker === picker) library.folderPicker = null
        super.onDestroy()
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit { putBoolean("asked_notifications", true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
