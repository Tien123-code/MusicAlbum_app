package com.example.musicalbum

import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.app.Activity
import android.app.RecoverableSecurityException
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MusicFragment : Fragment() {

    companion object {
        private const val MUSIC_EDIT_PREFS = "music_edit_prefs"
    }

    data class Song(
        val id: Long,
        val title: String,
        val artist: String,
        val uri: android.net.Uri,
        val duration: Long,
        var isSelected: Boolean = false
    )

    private val songs = mutableListOf<Song>()
    private val fullSongsList = mutableListOf<Song>()
    private var mediaPlayer: MediaPlayer? = null
    private var currentIndex = -1
    private var isPlaying = false
    private var musicService: MusicService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as MusicService.MusicBinder).getService()
            serviceBound = true
            musicService?.onCompletionListener = {
                view?.post {
                    when {
                        repeatMode == 2 -> playSong(currentIndex)
                        else -> playNext()
                    }
                }
            }
            syncMiniPlayerWithService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }
    private var isSelectionMode = false
    private var selectedCount = 0
    private var isShuffle = false
    private var repeatMode = 0  // 0=off, 1=repeat all, 2=repeat one

    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var adapter: SongAdapter
    private lateinit var etSearch: android.widget.EditText
    private lateinit var layoutSelectionBar: View
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var tvCount: TextView
    private var pendingMusicEdit: PendingMusicEdit? = null
    private lateinit var imgMiniPlayerArt: ImageView
    
    private var editingCoverUri: Uri? = null
    private var coverPreviewView: ImageView? = null

    data class PendingMusicEdit(
        val songUri: Uri,
        val newTitle: String,
        val newArtist: String,
        val coverUri: Uri?
    )

    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            editingCoverUri = uri
            coverPreviewView?.let { updateCoverPreview(it, uri) }
        }
    }

    private val editPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingMusicEdit ?: return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            applyMusicEdits(pending, false)
        } else {
            Toast.makeText(requireContext(), "Bạn cần cấp quyền để sửa nhạc", Toast.LENGTH_SHORT).show()
        }
        pendingMusicEdit = null
    }

    private val seekHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val seekRunnable = object : Runnable {
        override fun run() {
            if (serviceBound && musicService != null) {
                val pos = musicService!!.getCurrentPosition()
                seekBar.progress = pos
                tvCurrentTime.text = formatDuration(pos.toLong())
            } else {
                mediaPlayer?.let {
                    seekBar.progress = it.currentPosition
                    tvCurrentTime.text = formatDuration(it.currentPosition.toLong())
                }
            }
            seekHandler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_music, container, false)

        etSearch         = view.findViewById(R.id.etSearchMusic)
        tvTitle          = view.findViewById(R.id.tvNowTitle)
        tvArtist         = view.findViewById(R.id.tvNowArtist)
        tvCurrentTime    = view.findViewById(R.id.tvCurrentTime)
        tvTotalTime      = view.findViewById(R.id.tvTotalTime)
        btnPlayPause     = view.findViewById(R.id.btnPlayPause)
        seekBar          = view.findViewById(R.id.seekBar)
        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar)
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)
        tvCount = view.findViewById(R.id.tvCount)

        imgMiniPlayerArt = view.findViewById(R.id.imgMiniPlayerArt)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvSongs)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SongAdapter(songs) { index -> playSong(index) }
        recyclerView.adapter = adapter

        val context = requireContext()
        Thread {
            val loaded = loadSongs(context)
            recyclerView.post {
                songs.clear()
                songs.addAll(loaded)
                fullSongsList.clear()
                fullSongsList.addAll(songs)
                adapter.notifyDataSetChanged()
                tvCount.text = "${songs.size} bài hát"
                tvCount.visibility = View.VISIBLE
            }
        }.start()

        // Nút phát nhạc
        btnPlayPause.setOnClickListener {
            val scaleAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.scale_press)
            btnPlayPause.startAnimation(scaleAnim)
            togglePlayPause()
        }
        view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            if (currentIndex > 0) playSong(currentIndex - 1)
        }
        view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            playNext()
        }

        // Shuffle
        btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            btnShuffle.imageTintList = android.content.res.ColorStateList.valueOf(
                if (isShuffle) android.graphics.Color.parseColor("#6C63FF")
                else android.graphics.Color.parseColor("#6C6C80")
            )
            Toast.makeText(requireContext(),
                if (isShuffle) "Phát ngẫu nhiên: BẬT" else "Phát ngẫu nhiên: TẮT",
                Toast.LENGTH_SHORT).show()
        }

        // Repeat
        btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            when (repeatMode) {
                0 -> {
                    btnRepeat.imageTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#6C6C80"))
                    Toast.makeText(requireContext(), "Lặp lại: TẮT", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    btnRepeat.imageTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#6C63FF"))
                    Toast.makeText(requireContext(), "Lặp lại: TẤT CẢ", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    btnRepeat.imageTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#FF6584"))
                    Toast.makeText(requireContext(), "Lặp lại: MỘT BÀI", Toast.LENGTH_SHORT).show()
                }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (serviceBound && musicService != null) {
                        musicService!!.seekTo(progress)
                    } else {
                        mediaPlayer?.seekTo(progress)
                    }
                    tvCurrentTime.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Tìm kiếm
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMusic(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // ✅ Chọn tất cả
        view.findViewById<TextView>(R.id.btnSelectAll).setOnClickListener {
            val allSelected = songs.all { it.isSelected }
            songs.forEach { it.isSelected = !allSelected }
            selectedCount = if (allSelected) 0 else songs.size
            updateSelectionBar()
            adapter.notifyDataSetChanged()
        }

        // ✅ Thêm vào yêu thích hàng loạt
        view.findViewById<TextView>(R.id.btnAddFavMusic).setOnClickListener {
            val selected = songs.filter { it.isSelected }
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(),
                    "Chưa chọn bài nào!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FavoritesManager.addMusic(requireContext(), selected.map { it.uri })
            Toast.makeText(requireContext(),
                "Đã thêm ${selected.size} bài vào nhạc yêu thích ❤️",
                Toast.LENGTH_SHORT).show()
            exitSelectionMode()
        }

        // Xóa nhạc đã chọn
        view.findViewById<TextView>(R.id.btnDeleteMusic).setOnClickListener {
            val selected = songs.filter { it.isSelected }.map { it.uri }
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Chưa chọn bài nào!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Xóa nhạc")
                .setMessage("Bạn có chắc muốn xóa ${selected.size} bài?")
                .setPositiveButton("Xóa") { _, _ -> deleteSelectedMusic(selected) }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Sửa tên nhạc (chỉ 1 bài)
        view.findViewById<TextView>(R.id.btnRenameMusic).setOnClickListener {
            val selected = songs.filter { it.isSelected }
            if (selected.size != 1) {
                Toast.makeText(requireContext(), "Chọn đúng 1 bài để sửa tên!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showEditMusicDialog(selected.first())
        }

        // ✅ Hủy chọn
        view.findViewById<TextView>(R.id.btnCancelMusic).setOnClickListener {
            exitSelectionMode()
        }

        // Sort button
        val btnSort = view.findViewById<TextView>(R.id.btnSortMusic)
        btnSort.visibility = View.VISIBLE
        btnSort.setOnClickListener { showSortDialog() }

        if (!serviceBound) {
            val serviceIntent = Intent(requireContext(), MusicService::class.java)
            requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        return view
    }

    private var currentSortMode = 0 // 0=title, 1=artist, 2=duration, 3=date(id)

    private fun showSortDialog() {
        val options = arrayOf("Theo tên (A-Z)", "Theo ca sĩ (A-Z)", "Theo thời lượng", "Mới nhất")
        AlertDialog.Builder(requireContext())
            .setTitle("Sắp xếp")
            .setSingleChoiceItems(options, currentSortMode) { dialog, which ->
                currentSortMode = which
                applySortMode()
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun applySortMode() {
        val sorted = when (currentSortMode) {
            0 -> fullSongsList.sortedBy { it.title.lowercase() }
            1 -> fullSongsList.sortedBy { it.artist.lowercase() }
            2 -> fullSongsList.sortedBy { it.duration }
            3 -> fullSongsList.sortedByDescending { it.id }
            else -> fullSongsList
        }
        songs.clear()
        songs.addAll(sorted)
        adapter.notifyDataSetChanged()
        val labels = arrayOf("Tên ▾", "Ca sĩ ▾", "Thời lượng ▾", "Mới nhất ▾")
        view?.findViewById<TextView>(R.id.btnSortMusic)?.text = labels.getOrElse(currentSortMode) { "Sắp xếp ▾" }
    }

    private fun deleteSelectedMusic(uris: List<Uri>) {
        val resolver = requireContext().contentResolver
        var count = 0
        for (uri in uris) {
            try {
                val rows = resolver.delete(uri, null, null)
                if (rows > 0) count++
            } catch (_: Exception) {}
        }
        Toast.makeText(requireContext(), "Đã xóa $count bài", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
        refreshSongs()
    }

    private fun showEditMusicDialog(song: Song) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val etTitle = EditText(requireContext()).apply {
            hint = "Tên bài hát"
            setText(song.title)
        }
        val etArtist = EditText(requireContext()).apply {
            hint = "Ca sĩ"
            setText(song.artist)
        }
        val imgCover = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                420
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val btnChooseCover = Button(requireContext()).apply {
            text = "Chọn ảnh nền"
        }
        val btnClearCover = Button(requireContext()).apply {
            text = "Xóa ảnh nền"
        }
        editingCoverUri = getCustomCoverForSong(song.uri)
        coverPreviewView = imgCover
        updateCoverPreview(imgCover, editingCoverUri)
        btnChooseCover.setOnClickListener {
            pickCoverLauncher.launch("image/*")
        }
        btnClearCover.setOnClickListener {
            editingCoverUri = null
            updateCoverPreview(imgCover, null)
        }
        container.addView(etTitle)
        container.addView(etArtist)
        container.addView(imgCover)
        container.addView(btnChooseCover)
        container.addView(btnClearCover)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Chỉnh sửa nhạc")
            .setView(container)
            .setPositiveButton("Lưu") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newArtist = etArtist.text.toString().trim()
                if (newTitle.isEmpty()) {
                    Toast.makeText(requireContext(), "Tên bài hát không được trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val artistValue = if (newArtist.isEmpty()) "Unknown Artist" else newArtist
                applyMusicEdits(
                    PendingMusicEdit(song.uri, newTitle, artistValue, editingCoverUri),
                    true
                )
            }
            .setNegativeButton("Hủy", null)
            .create()
        dialog.setOnDismissListener {
            coverPreviewView = null
            editingCoverUri = null
        }
        dialog.show()
    }

    private fun applyMusicEdits(edit: PendingMusicEdit, requestPermissionIfNeeded: Boolean) {
        try {
            val currentDisplayName = getCurrentDisplayName(edit.songUri)
            val updatedDisplayName = buildDisplayName(edit.newTitle, currentDisplayName)
            
            // For Android Q+, we need to use the proper update method
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, edit.newTitle)
                put(MediaStore.Audio.Media.ARTIST, edit.newArtist)
                put(MediaStore.Audio.Media.DISPLAY_NAME, updatedDisplayName)
            }
            
            val rows = requireContext().contentResolver.update(edit.songUri, values, null, null)
            
            if (rows > 0) {
                saveCustomCoverForSong(edit.songUri, edit.coverUri)
                Toast.makeText(requireContext(), "Đã cập nhật bài hát", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                refreshSongs()
            } else {
                // On Android 10+, might need to use alternative method
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    applyMusicEditsAndroidQ(edit)
                } else {
                    Toast.makeText(requireContext(), "Không thể cập nhật bài hát", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requestPermissionIfNeeded) {
                val recoverable = e as? RecoverableSecurityException
                if (recoverable != null) {
                    pendingMusicEdit = edit
                    val intentSender = recoverable.userAction.actionIntent.intentSender
                    editPermissionLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    return
                }
            }
            Toast.makeText(requireContext(), "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun applyMusicEditsAndroidQ(edit: PendingMusicEdit) {
        try {
            // For Android 10+, try updating with IS_PENDING flag
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, edit.newTitle)
                put(MediaStore.Audio.Media.ARTIST, edit.newArtist)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            
            val rows = requireContext().contentResolver.update(edit.songUri, values, null, null)
            
            if (rows > 0) {
                // Clear pending flag
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                requireContext().contentResolver.update(edit.songUri, values, null, null)
                
                saveCustomCoverForSong(edit.songUri, edit.coverUri)
                Toast.makeText(requireContext(), "Đã cập nhật bài hát", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                refreshSongs()
            } else {
                Toast.makeText(requireContext(), "Không thể cập nhật - cần quyền MANAGE_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCoverPreview(imageView: ImageView, uri: Uri?) {
        if (uri == null) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }
        runCatching {
            imageView.setImageURI(uri)
        }.onFailure {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun getCurrentDisplayName(songUri: Uri): String {
        val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
        requireContext().contentResolver.query(songUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0) ?: ""
            }
        }
        return ""
    }

    private fun buildDisplayName(newTitle: String, currentDisplayName: String): String {
        val extension = currentDisplayName.substringAfterLast(".", "")
        if (extension.isEmpty()) return newTitle
        if (newTitle.endsWith(".$extension", true)) return newTitle
        return "$newTitle.$extension"
    }

    private fun saveCustomCoverForSong(songUri: Uri, coverUri: Uri?) {
        val prefs = requireContext().getSharedPreferences(MUSIC_EDIT_PREFS, Context.MODE_PRIVATE)
        val key = songUri.toString()
        prefs.edit().apply {
            val oldValue = prefs.getString(key, null)
            if (coverUri == null) {
                oldValue?.let { deleteInternalCoverIfNeeded(it) }
                remove(key)
            } else {
                val persistedPath = copyCoverToInternalStorage(coverUri, songUri)
                if (persistedPath != null) {
                    if (oldValue != null && oldValue != persistedPath) {
                        deleteInternalCoverIfNeeded(oldValue)
                    }
                    putString(key, persistedPath)
                } else {
                    putString(key, coverUri.toString())
                }
            }
        }.apply()
    }

    private fun getCustomCoverForSong(songUri: Uri): Uri? {
        val prefs = requireContext().getSharedPreferences(MUSIC_EDIT_PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(songUri.toString(), null) ?: return null
        return if (value.startsWith("/")) {
            Uri.fromFile(java.io.File(value))
        } else {
            Uri.parse(value)
        }
    }

    private fun copyCoverToInternalStorage(sourceUri: Uri, songUri: Uri): String? {
        return try {
            val coversDir = java.io.File(requireContext().filesDir, "music_covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val fileName = "${songUri.toString().hashCode()}_cover.jpg"
            val outFile = java.io.File(coversDir, fileName)
            requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteInternalCoverIfNeeded(value: String) {
        if (!value.startsWith("/")) return
        runCatching {
            val file = java.io.File(value)
            if (file.exists()) file.delete()
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): android.graphics.Bitmap? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return null
                    android.graphics.BitmapFactory.decodeFile(path)
                }
                else -> {
                    requireContext().contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshSongs() {
        val context = requireContext()
        val rv = view?.findViewById<RecyclerView>(R.id.rvSongs) ?: return
        Thread {
            val loaded = loadSongs(context)
            rv.post {
                songs.clear()
                songs.addAll(loaded)
                fullSongsList.clear()
                fullSongsList.addAll(loaded)
                adapter.notifyDataSetChanged()
                tvCount.text = "${songs.size} bài hát"
                tvCount.visibility = View.VISIBLE
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        syncMiniPlayerWithService()
        adapter.notifyDataSetChanged()
    }

    private fun syncMiniPlayerWithService() {
        val svc = musicService ?: return
        val playing = serviceBound && svc.isPlaying()
        isPlaying = playing
        btnPlayPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )

        val duration = svc.getDuration()
        if (duration > 0) {
            seekBar.max = duration
            tvTotalTime.text = formatDuration(duration.toLong())
            seekHandler.post(seekRunnable)
        } else {
            seekHandler.removeCallbacks(seekRunnable)
        }

        val currentUri = svc.getCurrentSongUri()
        if (currentUri != null) {
            tvTitle.text = svc.getCurrentTitle()
            tvArtist.text = svc.getCurrentArtist()
            loadAlbumArtAsync(currentUri, imgMiniPlayerArt)

            val idx = songs.indexOfFirst { it.uri == currentUri }
            if (idx >= 0) {
                currentIndex = idx
                adapter.setCurrentIndex(idx)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun filterMusic(query: String) {
        val currentSong = if (currentIndex in songs.indices) songs[currentIndex] else null
        val filtered = if (query.isEmpty()) fullSongsList
        else fullSongsList.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
        }
        songs.clear()
        songs.addAll(filtered)
        // Cập nhật lại currentIndex theo danh sách mới
        currentIndex = if (currentSong != null) songs.indexOfFirst { it.id == currentSong.id } else -1
        adapter.setCurrentIndex(currentIndex)
        adapter.notifyDataSetChanged()
    }

    private fun loadSongs(context: android.content.Context): List<Song> {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id  = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                result.add(Song(
                    id,
                    cursor.getString(titleCol),
                    cursor.getString(artistCol),
                    uri,
                    cursor.getLong(durationCol)
                ))
            }
        }
        return result
    }

    private fun playSong(index: Int) {
        if (isSelectionMode) return
        currentIndex = index
        val song = songs[index]

        // Update mini player info and album art
        tvTitle.text = song.title
        tvArtist.text = song.artist
        loadAlbumArtAsync(song.uri, imgMiniPlayerArt)
        adapter.setCurrentIndex(index)

        // Mở MusicPlayerActivity với toàn bộ playlist
        val playlistForIntent = ArrayList(songs.map { 
            MusicPlayerActivity.SongInfo(it.uri.toString(), it.title, it.artist) 
        })
        
        val intent = Intent(requireContext(), MusicPlayerActivity::class.java).apply {
            putExtra(MusicPlayerActivity.EXTRA_SONG_URI, song.uri.toString())
            putExtra(MusicPlayerActivity.EXTRA_SONG_TITLE, song.title)
            putExtra(MusicPlayerActivity.EXTRA_SONG_ARTIST, song.artist)
            putExtra(MusicPlayerActivity.EXTRA_CURRENT_INDEX, index)
            putExtra(MusicPlayerActivity.EXTRA_PLAYLIST, playlistForIntent)
        }
        startActivity(intent)
    }

    private fun playNext() {
        if (songs.isEmpty()) return
        if (isShuffle) {
            val next = (0 until songs.size).random()
            playSong(next)
        } else {
            if (currentIndex < songs.size - 1) {
                playSong(currentIndex + 1)
            } else if (repeatMode == 1) {
                playSong(0)
            }
        }
    }

    private fun togglePlayPause() {
        val svc = musicService
        if (svc != null && serviceBound) {
            if (svc.isPlaying()) {
                svc.pause()
                isPlaying = false
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                seekHandler.removeCallbacks(seekRunnable)
            } else {
                svc.resume()
                isPlaying = true
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                seekHandler.post(seekRunnable)
            }
        }
    }

    // Vào chế độ chọn nhiều
    private fun enterSelectionMode() {
        isSelectionMode = true
        layoutSelectionBar.visibility = View.VISIBLE
        val slideDown = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.item_fall_down)
        layoutSelectionBar.startAnimation(slideDown)
        updateSelectionBar()
        adapter.notifyDataSetChanged()
    }

    // ✅ Thoát chế độ chọn nhiều
    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedCount = 0
        songs.forEach { it.isSelected = false }
        val slideUp = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out)
        slideUp.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(a: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
            override fun onAnimationEnd(a: android.view.animation.Animation?) {
                layoutSelectionBar.visibility = View.GONE
            }
        })
        layoutSelectionBar.startAnimation(slideUp)
        adapter.notifyDataSetChanged()
    }

    private fun updateSelectionBar() {
        tvSelectionCount.text = "Đã chọn $selectedCount/${songs.size} bài"
        view?.findViewById<TextView>(R.id.btnSelectAll)?.text =
            if (songs.all { it.isSelected }) "Bỏ chọn tất cả" else "Chọn tất cả"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        seekHandler.removeCallbacks(seekRunnable)
        if (serviceBound) {
            try { requireContext().unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
        }
    }

    inner class SongAdapter(
        private val songs: List<Song>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

        private var currentPlaying = -1

        inner class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle   : TextView  = view.findViewById(R.id.tvSongTitle)
            val tvArtist  : TextView  = view.findViewById(R.id.tvSongArtist)
            val tvIndex   : TextView  = view.findViewById(R.id.tvSongIndex)
            val tvDuration: TextView  = view.findViewById(R.id.tvSongDuration)
            val btnHeart  : ImageView = view.findViewById(R.id.btnHeartSong)
            val cbSelect  : CheckBox  = view.findViewById(R.id.cbSelectSong)
            val imgAlbumArt: ImageView = view.findViewById(R.id.imgAlbumArt)
            val imgNowPlaying: ImageView = view.findViewById(R.id.imgNowPlaying)
        }

        fun setCurrentIndex(index: Int) {
            val old = currentPlaying
            currentPlaying = index
            if (old >= 0 && old < itemCount) notifyItemChanged(old)
            if (index >= 0 && index < itemCount) notifyItemChanged(index)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SongViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_song, parent, false)
            )

        override fun getItemCount() = songs.size

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            val song = songs[position]
            holder.tvTitle.text    = song.title
            holder.tvArtist.text   = song.artist
            holder.tvDuration.text = formatDuration(song.duration)

            // Item entrance animation
            animateItem(holder.itemView, position)

            // Load album art
            loadAlbumArtAsync(song.uri, holder.imgAlbumArt)

            // Now Playing indicator with pulse animation
            val isCurrentSong = position == currentPlaying && !isSelectionMode
            if (isCurrentSong) {
                holder.imgNowPlaying.visibility = View.VISIBLE
                val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(
                    holder.itemView.context, R.anim.now_playing_pulse
                )
                holder.imgNowPlaying.startAnimation(pulseAnim)
            } else {
                holder.imgNowPlaying.clearAnimation()
                holder.imgNowPlaying.visibility = View.GONE
            }

            // Số thứ tự / đang phát
            holder.tvIndex.text = when {
                isSelectionMode          -> ""
                position == currentPlaying -> ""
                else                     -> "${position + 1}"
            }
            holder.tvIndex.visibility = if (isSelectionMode || position != currentPlaying) View.VISIBLE else View.GONE

            holder.tvTitle.setTextColor(
                if (position == currentPlaying && !isSelectionMode)
                    android.graphics.Color.parseColor("#6C63FF")
                else android.graphics.Color.parseColor("#FFFFFF")
            )

            // ✅ Checkbox chọn nhiều
            holder.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            holder.cbSelect.isChecked  = song.isSelected

            // ✅ Trái tim yêu thích
            holder.btnHeart.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            val isFav = FavoritesManager.isMusicFavorite(requireContext(), song.uri)
            holder.btnHeart.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            holder.btnHeart.setOnClickListener {
                val added = FavoritesManager.toggleMusic(requireContext(), song.uri)
                holder.btnHeart.setImageResource(
                    if (added) android.R.drawable.btn_star_big_on
                    else android.R.drawable.btn_star_big_off
                )
                // Pulse animation on heart toggle
                val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
                holder.btnHeart.startAnimation(pulseAnim)
                Toast.makeText(
                    requireContext(),
                    if (added) "Đã thêm vào nhạc yêu thích ❤️"
                    else "Đã bỏ khỏi nhạc yêu thích",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // ✅ Bấm thường
            holder.itemView.setOnClickListener {
                if (isSelectionMode) {
                    song.isSelected = !song.isSelected
                    selectedCount += if (song.isSelected) 1 else -1
                    updateSelectionBar()
                    notifyItemChanged(position)
                } else {
                    onClick(position)
                }
            }

            // ✅ Giữ lâu → vào chế độ chọn
            holder.itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode()
                    song.isSelected = true
                    selectedCount = 1
                    updateSelectionBar()
                    notifyItemChanged(position)
                }
                true
            }
        }

        private var lastAnimatedPosition = -1

        private fun animateItem(view: View, position: Int) {
            if (position > lastAnimatedPosition) {
                val anim = android.view.animation.AnimationUtils.loadAnimation(
                    view.context, R.anim.item_slide_in_right
                )
                anim.startOffset = (position % 10 * 30).toLong()
                view.startAnimation(anim)
                lastAnimatedPosition = position
            }
        }
    }

    private fun loadAlbumArtAsync(uri: Uri, imageView: ImageView) {
        val targetTag = uri.toString()
        imageView.tag = targetTag
        Thread {
            try {
                val customCover = getCustomCoverForSong(uri)
                if (customCover != null) {
                    val customBitmap = decodeBitmapFromUri(customCover)
                    if (customBitmap != null) {
                        requireActivity().runOnUiThread {
                            if (imageView.tag == targetTag) {
                                imageView.setImageBitmap(customBitmap)
                            }
                        }
                        return@Thread
                    }
                }

                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(requireContext(), uri)
                val art = retriever.embeddedPicture
                val bitmap = if (art != null) {
                    android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                } else {
                    null
                }
                retriever.release()

                requireActivity().runOnUiThread {
                    if (imageView.tag != targetTag) return@runOnUiThread
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    if (imageView.tag == targetTag) {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
        }.start()
    }
}
