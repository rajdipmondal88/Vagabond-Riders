package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Foreground Service for Vagabond Riders Music Engine.
 * Manages background playback, audio focus, wake lock, and system MediaSession
 * to guarantee native lock-screen controls, notification scrubber, and headphone actions.
 */
class VRMediaPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val TAG = "VRMediaService"
        const val CHANNEL_ID = "vr_music_playback_channel"
        const val NOTIFICATION_ID = 4004

        const val ACTION_PLAY = "com.example.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.media.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.example.media.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.example.media.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.media.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.media.ACTION_STOP"
        const val ACTION_SEEK_FORWARD = "com.example.media.ACTION_SEEK_FORWARD"
        const val ACTION_SEEK_BACKWARD = "com.example.media.ACTION_SEEK_BACKWARD"
        const val ACTION_SEEK_TO = "com.example.media.ACTION_SEEK_TO"
        const val EXTRA_POSITION_MS = "extra_position_ms"

        fun startService(context: Context) {
            val intent = Intent(context, VRMediaPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAudioFocusGranted = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null
    private var currentArtworkBitmap: Bitmap? = null
    private var lastArtworkUrl: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "Audio becoming noisy (headphones unplugged), pausing playback")
                pause()
            }
        }
    }
    private var isNoisyReceiverRegistered = false

    inner class LocalBinder : Binder() {
        fun getService(): VRMediaPlaybackService = this@VRMediaPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VRMediaPlaybackService onCreate")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initMediaSession()
        registerNoisyReceiver()
        VRMusicManager.bindService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        intent?.action?.let { action ->
            Log.d(TAG, "onStartCommand action: $action")
            when (action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_TOGGLE -> togglePlayPause()
                ACTION_NEXT -> skipToNext()
                ACTION_PREVIOUS -> skipToPrevious()
                ACTION_SEEK_FORWARD -> seekRelative(10000L)
                ACTION_SEEK_BACKWARD -> seekRelative(-10000L)
                ACTION_SEEK_TO -> {
                    val pos = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                    seekTo(pos)
                }
                ACTION_STOP -> stopPlayback()
            }
        }
        return START_NOT_STICKY
    }

    private fun initMediaSession() {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            mediaButtonIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSessionCompat(this, "VRMediaSession").apply {
            setMediaButtonReceiver(pendingIntent)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    this@VRMediaPlaybackService.play()
                }

                override fun onPause() {
                    this@VRMediaPlaybackService.pause()
                }

                override fun onSkipToNext() {
                    this@VRMediaPlaybackService.skipToNext()
                }

                override fun onSkipToPrevious() {
                    this@VRMediaPlaybackService.skipToPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    this@VRMediaPlaybackService.seekTo(pos)
                }

                override fun onFastForward() {
                    this@VRMediaPlaybackService.seekRelative(10000L)
                }

                override fun onRewind() {
                    this@VRMediaPlaybackService.seekRelative(-10000L)
                }

                override fun onStop() {
                    this@VRMediaPlaybackService.stopPlayback()
                }

                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    when (action) {
                        ACTION_SEEK_FORWARD -> seekRelative(10000L)
                        ACTION_SEEK_BACKWARD -> seekRelative(-10000L)
                    }
                }
            })
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
    }

    fun prepareAndPlay(track: VRTrack) {
        serviceScope.launch {
            try {
                VRMusicManager.updatePlaybackState {
                    it.copy(
                        currentTrack = track,
                        isBuffering = true,
                        errorMessage = null,
                        positionMs = 0L,
                        durationMs = track.durationMs
                    )
                }

                requestAudioFocusInternal()
                startForegroundNotification()

                // Prepare Media Player
                releaseMediaPlayer()
                val player = MediaPlayer().apply {
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                }

                val uriString = track.getPlaybackUri()
                if (uriString.startsWith("/") || uriString.startsWith("file://")) {
                    player.setDataSource(applicationContext, Uri.fromFile(File(uriString.removePrefix("file://"))))
                } else {
                    player.setDataSource(uriString)
                }

                player.setOnPreparedListener { mp ->
                    val duration = mp.duration.toLong().coerceAtLeast(track.durationMs)
                    VRMusicManager.updatePlaybackState {
                        it.copy(
                            isBuffering = false,
                            isPlaying = true,
                            durationMs = duration,
                            positionMs = 0L
                        )
                    }
                    mp.start()
                    updatePlaybackStateCompat(PlaybackStateCompat.STATE_PLAYING, 0L)
                    loadArtworkAndMetadata(track, duration)
                    startProgressTracker()
                    updateForegroundNotification()
                }

                player.setOnCompletionListener {
                    handleTrackCompletion()
                }

                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    VRMusicManager.updatePlaybackState {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Playback error ($what, $extra)"
                        )
                    }
                    updatePlaybackStateCompat(PlaybackStateCompat.STATE_ERROR, 0L)
                    updateForegroundNotification()
                    true
                }

                player.setOnBufferingUpdateListener { _, percent ->
                    // Buffering update
                }

                mediaPlayer = player
                player.prepareAsync()

                // Load artwork for metadata
                loadArtworkAndMetadata(track, track.durationMs)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start playback for track: ${track.title}", e)
                VRMusicManager.updatePlaybackState {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = e.message ?: "Failed to play audio"
                    )
                }
                updateForegroundNotification()
            }
        }
    }

    fun play() {
        val player = mediaPlayer
        if (player != null && !player.isPlaying) {
            requestAudioFocusInternal()
            player.start()
            val currentPos = player.currentPosition.toLong()
            VRMusicManager.updatePlaybackState { it.copy(isPlaying = true, positionMs = currentPos) }
            updatePlaybackStateCompat(PlaybackStateCompat.STATE_PLAYING, currentPos)
            startProgressTracker()
            startForegroundNotification()
            updateForegroundNotification()
        } else if (player == null) {
            val current = VRMusicManager.playbackState.value.currentTrack
            if (current != null) {
                prepareAndPlay(current)
            }
        }
    }

    fun pause() {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            player.pause()
            val currentPos = player.currentPosition.toLong()
            VRMusicManager.updatePlaybackState { it.copy(isPlaying = false, positionMs = currentPos) }
            updatePlaybackStateCompat(PlaybackStateCompat.STATE_PAUSED, currentPos)
            stopProgressTracker()
            updateForegroundNotification()
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun skipToNext() {
        VRMusicManager.nextTrack()
    }

    fun skipToPrevious() {
        val currentPos = mediaPlayer?.currentPosition ?: 0
        if (currentPos > 3000) {
            seekTo(0L)
        } else {
            VRMusicManager.previousTrack()
        }
    }

    fun seekTo(posMs: Long) {
        val player = mediaPlayer ?: return
        val safePos = posMs.coerceIn(0L, player.duration.toLong())
        player.seekTo(safePos.toInt())
        VRMusicManager.updatePlaybackState { it.copy(positionMs = safePos) }
        val state = if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        updatePlaybackStateCompat(state, safePos)
        updateForegroundNotification()
    }

    fun seekRelative(deltaMs: Long) {
        val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
        seekTo(currentPos + deltaMs)
    }

    fun stopPlayback() {
        stopProgressTracker()
        releaseMediaPlayer()
        VRMusicManager.updatePlaybackState {
            it.copy(isPlaying = false, isBuffering = false, positionMs = 0L)
        }
        updatePlaybackStateCompat(PlaybackStateCompat.STATE_STOPPED, 0L)
        abandonAudioFocusInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleTrackCompletion() {
        val state = VRMusicManager.playbackState.value
        when (state.repeatMode) {
            VRRepeatMode.ONE -> {
                state.currentTrack?.let { prepareAndPlay(it) }
            }
            VRRepeatMode.ALL -> {
                skipToNext()
            }
            VRRepeatMode.OFF -> {
                if (state.hasNext) {
                    skipToNext()
                } else {
                    stopProgressTracker()
                    VRMusicManager.updatePlaybackState {
                        it.copy(isPlaying = false, positionMs = it.durationMs)
                    }
                    updatePlaybackStateCompat(PlaybackStateCompat.STATE_PAUSED, state.durationMs)
                    updateForegroundNotification()
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = serviceScope.launch {
            while (isActive) {
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        val current = player.currentPosition.toLong()
                        val total = player.duration.toLong()
                        VRMusicManager.updatePlaybackState {
                            it.copy(positionMs = current, durationMs = total.coerceAtLeast(it.durationMs))
                        }
                    }
                } catch (_: Exception) {}
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun updatePlaybackStateCompat(state: Int, positionMs: Long) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_STOP

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun loadArtworkAndMetadata(track: VRTrack, durationMs: Long) {
        serviceScope.launch {
            val artwork = if (!track.artworkUrl.isNullOrBlank() && track.artworkUrl != lastArtworkUrl) {
                withContext(Dispatchers.IO) {
                    try {
                        val req = Request.Builder().url(track.artworkUrl).build()
                        val res = okHttpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            res.body?.byteStream()?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            } else {
                currentArtworkBitmap
            } ?: generateFallbackArtwork(track.title)

            currentArtworkBitmap = artwork
            lastArtworkUrl = track.artworkUrl

            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
                .build()

            mediaSession.setMetadata(metadata)
            updateForegroundNotification()
        }
    }

    private fun generateFallbackArtwork(title: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size / 2f,
                size / 2f,
                size / 1.4f,
                intArrayOf(0xFF1E293B.toInt(), 0xFF0F172A.toInt(), 0xFF020617.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Accent Circle
        paint.shader = null
        paint.color = 0xFFEA580C.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 12f
        canvas.drawCircle(size / 2f, size / 2f, size / 3.2f, paint)

        // Center Monogram / Text
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 100f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true

        val initial = if (title.isNotBlank()) title.take(2).uppercase() else "VR"
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initial, size / 2f, yPos, paint)

        return bitmap
    }

    private fun startForegroundNotification() {
        val notification = buildMediaNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateForegroundNotification() {
        val notification = buildMediaNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildMediaNotification(): Notification {
        val state = VRMusicManager.playbackState.value
        val track = state.currentTrack ?: VRTrack(title = "Vagabond Riders Audio", streamUrl = "")
        val isPlaying = state.isPlaying

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_MUSIC_PLAYER", true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification Action Intents
        val prevPendingIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, VRMediaPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rewindPendingIntent = PendingIntent.getService(
            this,
            103,
            Intent(this, VRMediaPlaybackService::class.java).apply { action = ACTION_SEEK_BACKWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val togglePendingIntent = PendingIntent.getService(
            this,
            104,
            Intent(this, VRMediaPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardPendingIntent = PendingIntent.getService(
            this,
            105,
            Intent(this, VRMediaPlaybackService::class.java).apply { action = ACTION_SEEK_FORWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextPendingIntent = PendingIntent.getService(
            this,
            106,
            Intent(this, VRMediaPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            107,
            Intent(this, VRMediaPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val toggleTitle = if (isPlaying) "Pause" else "Play"

        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPendingIntent)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(currentArtworkBitmap ?: generateFallbackArtwork(track.title))
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.album}")
            .setSubText("Vagabond Music")
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)
            // Actions: 0 = Prev, 1 = Play/Pause, 2 = Next, 3 = Rewind 10s, 4 = Forward 10s
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(toggleIcon, toggleTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_media_rew, "-10s", rewindPendingIntent)
            .addAction(android.R.drawable.ic_media_ff, "+10s", forwardPendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vagabond Riders Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background playback and lock-screen media controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestAudioFocusInternal(): Boolean {
        if (isAudioFocusGranted) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        isAudioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return isAudioFocusGranted
    }

    private fun abandonAudioFocusInternal() {
        if (!isAudioFocusGranted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        isAudioFocusGranted = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently, pausing")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily, pausing")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost ducking, lowering volume")
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained, restoring volume and resuming")
                mediaPlayer?.setVolume(1.0f, 1.0f)
                play()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(noisyReceiver, filter)
            isNoisyReceiverRegistered = true
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VRMediaPlaybackService onDestroy")
        stopProgressTracker()
        releaseMediaPlayer()
        abandonAudioFocusInternal()
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {}
            isNoisyReceiverRegistered = false
        }
        mediaSession.isActive = false
        mediaSession.release()
        VRMusicManager.unbindService()
    }
}
