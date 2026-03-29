package com.example.musicalbum

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_STOP = "action_stop"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentTitle = ""
    private var currentArtist = ""
    private var currentSongUri: Uri? = null
    var onCompletionListener: (() -> Unit)? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val uriStr = intent.getStringExtra(EXTRA_URI)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Đang phát"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "---"
                if (uriStr != null) {
                    play(Uri.parse(uriStr), title, artist)
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> {
                stopPlayback()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun play(uri: Uri, title: String, artist: String) {
        currentSongUri = uri
        currentTitle = title
        currentArtist = artist
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, uri)
        mediaPlayer?.start()
        mediaPlayer?.setOnCompletionListener {
            onCompletionListener?.invoke()
        }
        if (canShowNotification()) {
            runCatching {
                startForeground(NOTIFICATION_ID, buildNotification(title, artist, true))
            }
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        updateNotification(false)
    }

    fun resume() {
        mediaPlayer?.start()
        updateNotification(true)
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getAudioSessionId(): Int {
        return mediaPlayer?.audioSessionId ?: 0
    }

    fun getCurrentSongUri(): Uri? = currentSongUri

    fun getCurrentTitle(): String = currentTitle

    fun getCurrentArtist(): String = currentArtist

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun updateNotification(playing: Boolean) {
        if (!canShowNotification()) return
        val nm = getSystemService(NotificationManager::class.java)
        runCatching {
            nm.notify(NOTIFICATION_ID, buildNotification(currentTitle, currentArtist, playing))
        }
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpen)
            .setOngoing(isPlaying)
            .addAction(
                Notification.Action.Builder(
                    null, "Dừng", pendingStop
                ).build()
            )
            .build()
    }

    private fun canShowNotification(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phát nhạc",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kênh thông báo phát nhạc nền"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
