package com.example.musicalbum

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.Serializable

class MusicPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SONG_URI = "song_uri"
        const val EXTRA_SONG_TITLE = "song_title"
        const val EXTRA_SONG_ARTIST = "song_artist"
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_CURRENT_INDEX = "current_index"
        private const val MUSIC_EDIT_PREFS = "music_edit_prefs"
    }

    private lateinit var imgAlbumArt: ImageView
    private lateinit var imgVinylDisc: ImageView
    private lateinit var imgBlurBackground: ImageView
    private lateinit var tvSongTitle: TextView
    private lateinit var tvArtistName: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnFavorite: ImageButton

    private var musicService: MusicService? = null
    private var serviceBound = false
    private var isPlaying = false
    private var isShuffle = false
    private var repeatMode = 0 // 0=off, 1=all, 2=one

    private var rotateAnimation: RotateAnimation? = null
    private lateinit var visualizer: AudioVisualizerView
    private var currentSongUri: Uri? = null
    private var sleepTimerMinutes = 0
    private var sleepTimerHandler: Handler? = null

    private val playlist = mutableListOf<SongInfo>()
    private var currentIndex = 0
    private var pendingPlayIndex: Int = -1 // Store song to play when service connects

    data class SongInfo(
        val uriString: String,
        val title: String,
        val artist: String
    ) : Serializable {
        val uri: Uri
            get() = Uri.parse(uriString)
    }


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as MusicService.MusicBinder).getService()
            serviceBound = true
            musicService?.onCompletionListener = {
                runOnUiThread {
                    when (repeatMode) {
                        2 -> playSong(currentIndex)
                        else -> playNext()
                    }
                }
            }
            // Setup visualizer when service is connected
            try {
                val audioSessionId = musicService?.getAudioSessionId() ?: 0
                if (audioSessionId != 0) {
                    visualizer.setPlayer(audioSessionId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Play pending song if exists
            if (pendingPlayIndex >= 0) {
                val index = pendingPlayIndex
                pendingPlayIndex = -1
                playSongInternal(index)
            } else {
                updatePlaybackState()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }

    private val seekHandler = Handler(Looper.getMainLooper())
    private val seekRunnable = object : Runnable {
        override fun run() {
            if (serviceBound && musicService != null) {
                val pos = musicService!!.getCurrentPosition()
                seekBar.progress = pos
                tvCurrentTime.text = formatDuration(pos.toLong())
            }
            seekHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
        setContentView(R.layout.activity_music_player)

        initViews()
        setupListeners()
        loadSongFromIntent()
        bindMusicService()
        animateEntrance()
    }

    private fun initViews() {
        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        imgVinylDisc = findViewById(R.id.imgVinylDisc)
        imgBlurBackground = findViewById(R.id.imgBlurBackground)
        tvSongTitle = findViewById(R.id.tvSongTitle)
        tvArtistName = findViewById(R.id.tvArtistName)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnFavorite = findViewById(R.id.btnFavorite)

        visualizer = findViewById(R.id.visualizer)

        // Setup rotating vinyl disc
        setupRotatingDisc()
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnPlayPause.setOnClickListener {
            val anim = AnimationUtils.loadAnimation(this, R.anim.scale_press)
            btnPlayPause.startAnimation(anim)
            togglePlayPause()
        }
        btnPrevious.setOnClickListener {
            val anim = AnimationUtils.loadAnimation(this, R.anim.scale_press)
            btnPrevious.startAnimation(anim)
            playPrevious()
        }
        btnNext.setOnClickListener {
            val anim = AnimationUtils.loadAnimation(this, R.anim.scale_press)
            btnNext.startAnimation(anim)
            playNext()
        }

        btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            updateShuffleButton()
            Toast.makeText(this, if (isShuffle) "Phát ngẫu nhiên: BẬT" else "Phát ngẫu nhiên: TẮT", Toast.LENGTH_SHORT).show()
        }

        btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateRepeatButton()
        }

        btnFavorite.setOnClickListener {
            currentSongUri?.let { uri ->
                val added = FavoritesManager.toggleMusic(this, uri)
                updateFavoriteButton()
                val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
                btnFavorite.startAnimation(pulseAnim)
                Toast.makeText(this, if (added) "Đã thêm vào yêu thích ❤️" else "Đã bỏ khỏi yêu thích", Toast.LENGTH_SHORT).show()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                    tvCurrentTime.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Lyrics button
        findViewById<ImageButton>(R.id.btnLyrics).setOnClickListener {
            showLyricsDialog()
        }

        // Equalizer button
        findViewById<ImageButton>(R.id.btnEqualizer).setOnClickListener {
            showEqualizerDialog()
        }

        // Sleep timer button
        findViewById<ImageButton>(R.id.btnSleepTimer).setOnClickListener {
            showSleepTimerDialog()
        }

        // Queue button
        findViewById<ImageButton>(R.id.btnQueue).setOnClickListener {
            showQueueDialog()
        }

        // Menu button
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            showMenuDialog()
        }
    }

    private fun loadSongFromIntent() {
        val uri = intent.getStringExtra(EXTRA_SONG_URI)?.let { Uri.parse(it) }
        val title = intent.getStringExtra(EXTRA_SONG_TITLE) ?: "Unknown"
        val artist = intent.getStringExtra(EXTRA_SONG_ARTIST) ?: "Unknown Artist"
        val indexFromIntent = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
        
        // Try to get playlist from intent first
        @Suppress("UNCHECKED_CAST")
        val playlistFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_PLAYLIST, ArrayList::class.java) as? ArrayList<SongInfo>
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_PLAYLIST) as? ArrayList<SongInfo>
        }
        
        playlist.clear()
        if (playlistFromIntent != null && playlistFromIntent.isNotEmpty()) {
            playlist.addAll(playlistFromIntent)
        } else {
            // Fallback to loading from MediaStore
            playlist.addAll(loadPlaylistFromMediaStore())
        }

        if (uri != null) {
            currentSongUri = uri
            currentIndex = playlist.indexOfFirst { it.uri == uri }
            if (currentIndex == -1) {
                currentIndex = indexFromIntent.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
            }
            displaySongInfo(uri, title, artist)
            playSong(currentIndex)
        }
    }

    private fun loadPlaylistFromMediaStore(): List<SongInfo> {
        val result = mutableListOf<SongInfo>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val songUri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                val songTitle = cursor.getString(titleCol) ?: "Unknown"
                val songArtist = cursor.getString(artistCol) ?: "Unknown Artist"
                result.add(SongInfo(songUri.toString(), songTitle, songArtist))
            }
        }
        return result
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun displaySongInfo(uri: Uri, title: String, artist: String) {
        // Crossfade title and artist
        tvSongTitle.animate().alpha(0f).setDuration(150).withEndAction {
            tvSongTitle.text = title
            tvSongTitle.isSelected = true
            tvSongTitle.animate().alpha(1f).setDuration(250).start()
        }.start()
        tvArtistName.animate().alpha(0f).setDuration(150).withEndAction {
            tvArtistName.text = artist
            tvArtistName.animate().alpha(1f).setDuration(250).start()
        }.start()

        // Crossfade album art
        imgAlbumArt.animate().alpha(0.3f).setDuration(200).withEndAction {
            currentSongUri = uri
            loadAlbumArt(uri)
            imgAlbumArt.animate().alpha(1f).setDuration(400).start()
        }.start()

        updateFavoriteButton()
    }

    private fun loadAlbumArt(uri: Uri) {
        Thread {
            try {
                val customCoverUri = getCustomCoverForSong(uri)
                if (customCoverUri != null) {
                    val customBitmap = decodeBitmapFromUri(customCoverUri)
                    if (customBitmap != null) {
                        runOnUiThread {
                            imgAlbumArt.setImageBitmap(customBitmap)
                            imgVinylDisc.setImageBitmap(createVinylBitmap(customBitmap))
                            setBlurBackground(customBitmap)
                        }
                        return@Thread
                    }
                }
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val art = retriever.embeddedPicture
                val bitmap = if (art != null) {
                    BitmapFactory.decodeByteArray(art, 0, art.size)
                } else {
                    null
                }
                retriever.release()

                runOnUiThread {
                    if (bitmap != null) {
                        imgAlbumArt.setImageBitmap(bitmap)
                        imgVinylDisc.setImageBitmap(createVinylBitmap(bitmap))
                        setBlurBackground(bitmap)
                    } else {
                        imgAlbumArt.setImageResource(android.R.drawable.ic_menu_gallery)
                        imgVinylDisc.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    imgAlbumArt.setImageResource(android.R.drawable.ic_menu_gallery)
                    imgVinylDisc.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }.start()
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return null
                    BitmapFactory.decodeFile(path)
                }
                else -> {
                    contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun setBlurBackground(source: Bitmap) {
        Thread {
            try {
                // Simple fast blur by scaling down and back up
                val smallWidth = 50
                val smallHeight = (smallWidth * source.height.toFloat() / source.width).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
                val blurred = Bitmap.createScaledBitmap(small, source.width / 4, source.height / 4, true)
                runOnUiThread {
                    imgBlurBackground.setImageBitmap(blurred)
                    imgBlurBackground.alpha = 0f
                    imgBlurBackground.animate().alpha(0.35f).setDuration(600).start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun createVinylBitmap(source: Bitmap): Bitmap {
        return source
    }

    private fun animateEntrance() {
        val albumContainer = findViewById<View>(R.id.albumArtContainer)
        val controlsLayout = findViewById<View>(R.id.controlsLayout)

        // Album art: scale bounce from center
        albumContainer.alpha = 0f
        albumContainer.scaleX = 0.6f
        albumContainer.scaleY = 0.6f
        albumContainer.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(500)
            .setStartDelay(200)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()

        // Song title: slide from left
        tvSongTitle.alpha = 0f
        tvSongTitle.translationX = -200f
        tvSongTitle.animate()
            .alpha(1f).translationX(0f)
            .setDuration(400)
            .setStartDelay(400)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Artist: slide from left with delay
        tvArtistName.alpha = 0f
        tvArtistName.translationX = -200f
        tvArtistName.animate()
            .alpha(1f).translationX(0f)
            .setDuration(400)
            .setStartDelay(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Controls: slide up from bottom
        controlsLayout.alpha = 0f
        controlsLayout.translationY = 150f
        controlsLayout.animate()
            .alpha(1f).translationY(0f)
            .setDuration(450)
            .setStartDelay(550)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // SeekBar: fade in
        seekBar.alpha = 0f
        seekBar.animate()
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(600)
            .start()

        // Favorite button: pop in
        btnFavorite.alpha = 0f
        btnFavorite.scaleX = 0f
        btnFavorite.scaleY = 0f
        btnFavorite.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(350)
            .setStartDelay(650)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()

        // Visualizer: fade in
        visualizer.alpha = 0f
        visualizer.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(700)
            .start()
    }

    private fun setupRotatingDisc() {
        rotateAnimation = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation?.duration = 10000
        rotateAnimation?.repeatCount = Animation.INFINITE
        rotateAnimation?.interpolator = LinearInterpolator()
    }

    private fun startRotating() {
        imgVinylDisc.startAnimation(rotateAnimation)
        imgAlbumArt.startAnimation(rotateAnimation)
        visualizer.startAnimation()
    }

    private fun stopRotating() {
        imgVinylDisc.clearAnimation()
        imgAlbumArt.clearAnimation()
        visualizer.stopAnimation()
    }

    private fun togglePlayPause() {
        if (!serviceBound || musicService == null) return
        
        if (musicService!!.isPlaying()) {
            musicService!!.pause()
            isPlaying = false
        } else {
            musicService!!.resume()
            isPlaying = true
        }
        updatePlayPauseButton()
        updatePlaybackState()
    }

    private fun playSong(index: Int) {
        if (index < 0 || index >= playlist.size) return
        
        if (!serviceBound || musicService == null) {
            // Store for later when service connects
            pendingPlayIndex = index
            return
        }
        
        playSongInternal(index)
    }
    
    private fun playSongInternal(index: Int) {
        if (index < 0 || index >= playlist.size) return
        currentIndex = index
        val song = playlist[currentIndex]
        displaySongInfo(song.uri, song.title, song.artist)
        musicService?.play(song.uri, song.title, song.artist)
        isPlaying = true
        updatePlaybackState()
        
        // Re-setup visualizer
        try {
            val audioSessionId = musicService?.getAudioSessionId() ?: 0
            if (audioSessionId != 0) {
                visualizer.setPlayer(audioSessionId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playNext() {
        if (playlist.isEmpty()) return
        if (isShuffle) {
            playSong((0 until playlist.size).random())
        } else {
            val nextIndex = if (currentIndex < playlist.size - 1) currentIndex + 1
            else if (repeatMode == 1) 0 else currentIndex
            playSong(nextIndex)
        }
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return
        val prevIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playSong(prevIndex)
    }

    private fun updatePlayPauseButton() {
        btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun updateShuffleButton() {
        btnShuffle.alpha = if (isShuffle) 1.0f else 0.5f
    }

    private fun updateRepeatButton() {
        when (repeatMode) {
            0 -> {
                btnRepeat.setImageResource(android.R.drawable.ic_menu_revert)
                btnRepeat.alpha = 0.5f
                Toast.makeText(this, "Lặp lại: TẮT", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                btnRepeat.setImageResource(android.R.drawable.ic_menu_revert)
                btnRepeat.alpha = 1.0f
                Toast.makeText(this, "Lặp lại: TẤT CẢ", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                btnRepeat.setImageResource(android.R.drawable.ic_menu_revert)
                btnRepeat.alpha = 1.0f
                Toast.makeText(this, "Lặp lại: MỘT BÀI", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFavoriteButton() {
        currentSongUri?.let { uri ->
            val isFav = FavoritesManager.isMusicFavorite(this, uri)
            btnFavorite.setImageResource(if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
        }
    }

    private fun updatePlaybackState() {
        if (serviceBound && musicService != null) {
            isPlaying = musicService!!.isPlaying()
            updatePlayPauseButton()
            
            val duration = musicService!!.getDuration()
            if (duration > 0) {
                seekBar.max = duration
                tvTotalTime.text = formatDuration(duration.toLong())
            }
            
            if (isPlaying) {
                startRotating()
                seekHandler.post(seekRunnable)
            } else {
                stopRotating()
                seekHandler.removeCallbacks(seekRunnable)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // Dialog methods...
    private fun showLyricsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Lời bài hát")
            .setMessage("Tính năng lời bài hát đang được phát triển.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showEqualizerDialog() {
        val options = arrayOf("Bass Boost", "Treble Boost", "Vocal Boost", "Flat", "Rock", "Pop", "Jazz", "Classical")
        AlertDialog.Builder(this)
            .setTitle("Equalizer")
            .setItems(options) { _, which ->
                Toast.makeText(this, "Đã chọn: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf("Tắt", "15 phút", "30 phút", "60 phút", "Tùy chỉnh")
        AlertDialog.Builder(this)
            .setTitle("Hẹn giờ tắt")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cancelSleepTimer()
                    1 -> startSleepTimer(15)
                    2 -> startSleepTimer(30)
                    3 -> startSleepTimer(60)
                    4 -> { /* Custom input */ }
                }
            }
            .show()
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimerMinutes = minutes
        sleepTimerHandler?.removeCallbacksAndMessages(null)
        sleepTimerHandler = Handler(Looper.getMainLooper())
        sleepTimerHandler?.postDelayed({
            if (isPlaying) togglePlayPause()
            Toast.makeText(this, "Hẹn giờ: Đã dừng phát nhạc", Toast.LENGTH_LONG).show()
        }, minutes * 60 * 1000L)
        Toast.makeText(this, "Đã hẹn giờ tắt sau $minutes phút", Toast.LENGTH_SHORT).show()
    }

    private fun cancelSleepTimer() {
        sleepTimerHandler?.removeCallbacksAndMessages(null)
        Toast.makeText(this, "Đã hủy hẹn giờ tắt", Toast.LENGTH_SHORT).show()
    }

    private fun showQueueDialog() {
        if (playlist.isEmpty()) {
            Toast.makeText(this, "Danh sách phát trống", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Danh sách phát (${playlist.size} bài)")
            .setNegativeButton("Đóng", null)
            .create()

        val recyclerView = RecyclerView(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = QueueAdapter(playlist, currentIndex) { index ->
            playSong(index)
            dialog.dismiss()
        }
        recyclerView.setPadding(32, 32, 32, 32)

        dialog.setView(recyclerView)
        dialog.show()
    }

    private fun showMenuDialog() {
        val options = arrayOf("Chia sẻ", "Thông tin bài hát", "Đặt làm nhạc chuông", "Xóa khỏi thiết bị")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareSong()
                    1 -> showSongInfo()
                    2 -> Toast.makeText(this, "Tính năng đang phát triển", Toast.LENGTH_SHORT).show()
                    3 -> deleteSong()
                }
            }
            .show()
    }

    private fun shareSong() {
        currentSongUri?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ bài hát"))
        }
    }

    private fun showSongInfo() {
        currentSongUri?.let { uri ->
            val info = getSongDetailedInfo(uri)
            AlertDialog.Builder(this)
                .setTitle("Thông tin bài hát")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun deleteSong() {
        currentSongUri?.let { uri ->
            AlertDialog.Builder(this)
                .setTitle("Xóa bài hát")
                .setMessage("Bạn có chắc muốn xóa bài hát này khỏi thiết bị?")
                .setPositiveButton("Xóa") { _, _ ->
                    try {
                        val rows = contentResolver.delete(uri, null, null)
                        if (rows > 0) {
                            Toast.makeText(this, "Đã xóa bài hát", Toast.LENGTH_SHORT).show()
                            playNext()
                        } else {
                            Toast.makeText(this, "Không thể xóa", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun getSongDetailedInfo(uri: Uri): String {
        val info = StringBuilder()
        try {
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATA
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    info.append("Tên: ${cursor.getString(0) ?: "Unknown"}\n")
                    info.append("Nghệ sĩ: ${cursor.getString(1) ?: "Unknown"}\n")
                    info.append("Album: ${cursor.getString(2) ?: "Unknown"}\n")
                    info.append("Thời lượng: ${formatDuration(cursor.getLong(3))}\n")
                    info.append("Kích thước: ${cursor.getLong(4) / 1024 / 1024} MB\n")
                    info.append("Đường dẫn: ${cursor.getString(5)}")
                }
            }
        } catch (e: Exception) {
            info.append("Không thể lấy thông tin")
        }
        return info.toString()
    }

    private fun getCustomCoverForSong(uri: Uri): Uri? {
        val prefs = getSharedPreferences(MUSIC_EDIT_PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(uri.toString(), null) ?: return null
        return if (value.startsWith("/")) {
            Uri.fromFile(java.io.File(value))
        } else {
            Uri.parse(value)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }

    override fun onDestroy() {
        super.onDestroy()
        seekHandler.removeCallbacks(seekRunnable)
        sleepTimerHandler?.removeCallbacksAndMessages(null)
        visualizer.release()
        if (serviceBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            serviceBound = false
        }
    }

    inner class QueueAdapter(
        private val songs: List<SongInfo>,
        private val current: Int,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<QueueAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = TextView(view.context).apply {
                textSize = 16f
                setPadding(16, 16, 16, 16)
                setTextColor(android.graphics.Color.WHITE)
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.widget.LinearLayout(parent.context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = songs[position]
            holder.tvTitle.text = "${position + 1}. ${song.title} - ${song.artist}"
            holder.tvTitle.setTextColor(
                if (position == current) android.graphics.Color.parseColor("#6C63FF")
                else android.graphics.Color.parseColor("#B0B0C0")
            )
            holder.itemView.setOnClickListener { onClick(position) }
            (holder.itemView as android.widget.LinearLayout).removeAllViews()
            (holder.itemView as android.widget.LinearLayout).addView(holder.tvTitle)
        }

        override fun getItemCount() = songs.size
    }
}
