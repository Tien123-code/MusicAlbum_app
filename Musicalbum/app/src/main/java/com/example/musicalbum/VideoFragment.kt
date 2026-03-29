package com.example.musicalbum

import android.app.DatePickerDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class VideoFragment : Fragment() {

    sealed class VideoItem {
        data class Header(val date: String, val count: String) : VideoItem()
        data class Video(
            val uri: Uri,
            val duration: Long,
            var isSelected: Boolean = false
        ) : VideoItem()
    }

    private val items = mutableListOf<VideoItem>()
    private val fullItems = mutableListOf<VideoItem>()
    private var isSelectionMode = false
    private var selectedCount = 0

    private lateinit var adapter: VideoAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var layoutSelectionBar: View
    private lateinit var tvCount: TextView
    private lateinit var layoutCountBar: View
    private lateinit var btnDateFilter: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_video, container, false)

        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)
        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        tvCount = view.findViewById(R.id.tvCount)
        layoutCountBar = view.findViewById(R.id.layoutCountBar)
        btnDateFilter = view.findViewById(R.id.btnDateFilter)

        recyclerView = view.findViewById(R.id.rvVideos)
        val layoutManager = GridLayoutManager(requireContext(), 3)

        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (position < items.size && items[position] is VideoItem.Header) 3 else 1
        }
        recyclerView.layoutManager = layoutManager

        adapter = VideoAdapter(items)
        recyclerView.adapter = adapter

        // Chọn tất cả / Bỏ chọn tất cả
        btnSelectAll.setOnClickListener {
            val allSelected = items.filterIsInstance<VideoItem.Video>()
                .all { it.isSelected }
            items.filterIsInstance<VideoItem.Video>()
                .forEach { it.isSelected = !allSelected }
            selectedCount = if (allSelected) 0
            else items.filterIsInstance<VideoItem.Video>().size
            updateSelectionBar()
            adapter.notifyDataSetChanged()
        }

        // Thêm vào yêu thích hàng loạt
        view.findViewById<TextView>(R.id.btnAddFavVideo).setOnClickListener {
            val selected = items.filterIsInstance<VideoItem.Video>()
                .filter { it.isSelected }.map { it.uri }
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(),
                    "Chưa chọn video nào!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FavoritesManager.addVideos(requireContext(), selected)
            Toast.makeText(requireContext(),
                "Đã thêm ${selected.size} video vào yêu thích ",
                Toast.LENGTH_SHORT).show()
            exitSelectionMode()
        }

        // Xóa video đã chọn
        view.findViewById<TextView>(R.id.btnDeleteVideo).setOnClickListener {
            val selected = items.filterIsInstance<VideoItem.Video>().filter { it.isSelected }.map { it.uri }
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Chưa chọn video nào!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Xóa video")
                .setMessage("Bạn có chắc muốn xóa ${selected.size} video?")
                .setPositiveButton("Xóa") { _, _ -> deleteSelectedVideos(selected) }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Sửa tên video (chỉ 1)
        view.findViewById<TextView>(R.id.btnRenameVideo).setOnClickListener {
            val selected = items.filterIsInstance<VideoItem.Video>().filter { it.isSelected }
            if (selected.size != 1) {
                Toast.makeText(requireContext(), "Chọn đúng 1 video để sửa tên!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showRenameDialog(selected.first().uri)
        }

        // Hủy chọn
        view.findViewById<TextView>(R.id.btnCancelVideo).setOnClickListener {
            exitSelectionMode()
        }

        // Lọc theo ngày
        btnDateFilter.setOnClickListener { showDatePicker() }

        return view
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val picked = Calendar.getInstance().apply { set(year, month, day) }
            val pickedStr = dateFormat.format(picked.time)

            val today = dateFormat.format(Date())
            val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))
            val label = when (pickedStr) {
                today -> "Hôm nay"
                yesterday -> "Hôm qua"
                else -> pickedStr
            }

            val filtered = mutableListOf<VideoItem>()
            var found = false
            for (item in fullItems) {
                if (item is VideoItem.Header && item.date == label) {
                    found = true
                    filtered.add(item)
                } else if (item is VideoItem.Header && found) {
                    break
                } else if (found && item is VideoItem.Video) {
                    filtered.add(item)
                }
            }
            items.clear()
            items.addAll(filtered)
            adapter.notifyDataSetChanged()
            val videoCount = items.filterIsInstance<VideoItem.Video>().size
            tvCount.text = "$videoCount video ($label)"
            btnDateFilter.text = "Xóa lọc"
            btnDateFilter.setOnClickListener { clearDateFilter() }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun clearDateFilter() {
        items.clear()
        items.addAll(fullItems)
        adapter.notifyDataSetChanged()
        val videoCount = items.filterIsInstance<VideoItem.Video>().size
        tvCount.text = "$videoCount video"
        btnDateFilter.text = "Lọc ngày"
        btnDateFilter.setOnClickListener { showDatePicker() }
    }

    private fun deleteSelectedVideos(uris: List<Uri>) {
        val resolver = requireContext().contentResolver
        var count = 0
        for (uri in uris) {
            try {
                val rows = resolver.delete(uri, null, null)
                if (rows > 0) count++
            } catch (_: Exception) {}
        }
        Toast.makeText(requireContext(), "Đã xóa $count video", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
        refreshVideos()
    }

    private fun showRenameDialog(uri: Uri) {
        val et = EditText(requireContext()).apply {
            hint = "Nhập tên mới"
            setPadding(60, 40, 60, 40)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Sửa tên video")
            .setView(et)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "Tên không được trống!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                renameMedia(uri, newName)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun renameMedia(uri: Uri, newName: String) {
        try {
            val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, newName) }
            requireContext().contentResolver.update(uri, values, null, null)
            Toast.makeText(requireContext(), "Đã đổi tên thành $newName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Lỗi đổi tên: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        exitSelectionMode()
        refreshVideos()
    }

    private fun refreshVideos() {
        val context = requireContext()
        Thread {
            val loaded = loadVideos(context)
            recyclerView.post {
                items.clear()
                items.addAll(loaded)
                fullItems.clear()
                fullItems.addAll(loaded)
                adapter.notifyDataSetChanged()
                val videoCount = items.filterIsInstance<VideoItem.Video>().size
                tvCount.text = "$videoCount video"
                layoutCountBar.visibility = View.VISIBLE
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        Thread {
            val loaded = loadVideos(context)
            recyclerView.post {
                items.clear()
                items.addAll(loaded)
                fullItems.clear()
                fullItems.addAll(loaded)
                adapter.notifyDataSetChanged()
                val videoCount = items.filterIsInstance<VideoItem.Video>().size
                tvCount.text = "$videoCount video"
                layoutCountBar.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun loadVideos(context: Context): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val dateFormat  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val videoMap    = mutableMapOf<String, MutableList<VideoItem.Video>>()
        val collection  = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
            ),
            null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val uri  = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                val date = dateFormat.format(Date(cursor.getLong(dateCol) * 1000))
                videoMap.getOrPut(date) { mutableListOf() }
                    .add(VideoItem.Video(uri, cursor.getLong(durCol)))
            }
        }

        val today     = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))

        videoMap.keys
            .sortedByDescending {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it)
            }
            .forEach { date ->
                val list  = videoMap[date]!!
                val label = when (date) {
                    today     -> "Hôm nay"
                    yesterday -> "Hôm qua"
                    else      -> date
                }
                result.add(VideoItem.Header(label, "${list.size} video"))
                result.addAll(list)
            }
        return result
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        layoutSelectionBar.visibility = View.VISIBLE
        val slideDown = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.item_fall_down)
        layoutSelectionBar.startAnimation(slideDown)
        updateSelectionBar()
        adapter.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedCount   = 0
        items.filterIsInstance<VideoItem.Video>().forEach { it.isSelected = false }
        val fadeOut = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out)
        fadeOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(a: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
            override fun onAnimationEnd(a: android.view.animation.Animation?) {
                layoutSelectionBar.visibility = View.GONE
            }
        })
        layoutSelectionBar.startAnimation(fadeOut)
        adapter.notifyDataSetChanged()
    }

    private fun updateSelectionBar() {
        val total = items.filterIsInstance<VideoItem.Video>().size
        tvSelectionCount.text = "Đã chọn $selectedCount/$total video"
        btnSelectAll.text = if (items.filterIsInstance<VideoItem.Video>()
                .all { it.isSelected }) "Bỏ chọn tất cả" else "Chọn tất cả"
    }

    private fun formatDuration(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / 1000) / 60
        return "%d:%02d".format(m, s)
    }

    inner class VideoAdapter(private val items: List<VideoItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_VIDEO  = 1

        inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate  : TextView = view.findViewById(R.id.tvDateHeader)
            val tvCount : TextView = view.findViewById(R.id.tvDateCount)
        }

        inner class VideoVH(view: View) : RecyclerView.ViewHolder(view) {
            val imgThumb  : ImageView = view.findViewById(R.id.imgPhoto)
            val tvDuration: TextView  = view.findViewById(R.id.tvDuration)
            val iconVideo : ImageView = view.findViewById(R.id.iconVideo)
            val cbSelect  : CheckBox  = view.findViewById(R.id.cbSelect)
            val btnHeart  : ImageView = view.findViewById(R.id.btnHeart)
        }

        override fun getItemViewType(position: Int) =
            if (items[position] is VideoItem.Header) TYPE_HEADER else TYPE_VIDEO

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            if (viewType == TYPE_HEADER) {
                HeaderVH(LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false))
            } else {
                VideoVH(LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_media, parent, false))
            }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is VideoItem.Header -> {
                    val h = holder as HeaderVH
                    h.tvDate.text  = item.date
                    h.tvCount.text = item.count
                }
                is VideoItem.Video -> {
                    val h = holder as VideoVH

                    Glide.with(h.itemView).load(item.uri).centerCrop().into(h.imgThumb)
                    h.iconVideo.visibility  = View.VISIBLE
                    h.tvDuration.visibility = View.VISIBLE
                    h.tvDuration.text       = formatDuration(item.duration)

                    // Checkbox & trái tim
                    h.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                    h.btnHeart.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
                    h.cbSelect.isChecked  = item.isSelected

                    // Trái tim
                    val isFav = FavoritesManager.isVideoFavorite(requireContext(), item.uri)
                    h.btnHeart.setImageResource(
                        if (isFav) android.R.drawable.btn_star_big_on
                        else android.R.drawable.btn_star_big_off
                    )
                    h.btnHeart.setOnClickListener {
                        val added = FavoritesManager.toggleVideo(requireContext(), item.uri)
                        h.btnHeart.setImageResource(
                            if (added) android.R.drawable.btn_star_big_on
                            else android.R.drawable.btn_star_big_off
                        )
                        val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
                        h.btnHeart.startAnimation(pulseAnim)
                        Toast.makeText(
                            requireContext(),
                            if (added) "Đã thêm vào video yêu thích ❤️"
                            else "Đã bỏ khỏi video yêu thích",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Bấm thường
                    h.itemView.setOnClickListener {
                        if (isSelectionMode) {
                            item.isSelected = !item.isSelected
                            selectedCount  += if (item.isSelected) 1 else -1
                            updateSelectionBar()
                            notifyItemChanged(position)
                        } else {
                            val intent = Intent(requireContext(), VideoPlayerActivity::class.java)
                            intent.data = item.uri
                            startActivity(intent)
                        }
                    }

                    // Giữ lâu → vào chế độ chọn
                    h.itemView.setOnLongClickListener {
                        if (!isSelectionMode) {
                            enterSelectionMode()
                            item.isSelected = true
                            selectedCount   = 1
                            updateSelectionBar()
                            notifyItemChanged(position)
                        }
                        true
                    }
                }
            }
        }
    }
}