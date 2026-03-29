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

class PhotosFragment : Fragment() {

    sealed class MediaItem {
        data class Header(val date: String) : MediaItem()
        data class Media(
            val uri: Uri,
            val isVideo: Boolean,
            var isSelected: Boolean = false
        ) : MediaItem()
    }

    private val items = mutableListOf<MediaItem>()
    private val fullItems = mutableListOf<MediaItem>()
    private var isSelectionMode = false
    private var selectedCount = 0
    private lateinit var adapter: MediaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutSelectionBar: View
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var tvCount: TextView
    private lateinit var layoutCountBar: View
    private lateinit var btnDateFilter: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_photos, container, false)
        recyclerView = view.findViewById(R.id.rvPhotos)
        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar)
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        tvCount = view.findViewById(R.id.tvCount)
        layoutCountBar = view.findViewById(R.id.layoutCountBar)
        btnDateFilter = view.findViewById(R.id.btnDateFilter)

        val layoutManager = GridLayoutManager(requireContext(), 3)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (position < items.size && items[position] is MediaItem.Header) 3 else 1
        }

        recyclerView.layoutManager = layoutManager
        adapter = MediaAdapter(items)
        recyclerView.adapter = adapter

        // Chọn tất cả / Bỏ chọn tất cả
        btnSelectAll.setOnClickListener {
            val allSelected = items.filterIsInstance<MediaItem.Media>().all { it.isSelected }
            items.filterIsInstance<MediaItem.Media>().forEach { it.isSelected = !allSelected }
            selectedCount = if (allSelected) 0 else items.filterIsInstance<MediaItem.Media>().size
            updateSelectionBar()
            adapter.notifyDataSetChanged()
        }

        // Thêm vào yêu thích hàng loạt
        view.findViewById<TextView>(R.id.btnAddFavPhoto).setOnClickListener {
            val selected = items.filterIsInstance<MediaItem.Media>().filter { it.isSelected }.map { it.uri }
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Chưa chọn ảnh nào!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FavoritesManager.addImages(requireContext(), selected)
            Toast.makeText(requireContext(), "Đã thêm ${selected.size} ảnh vào yêu thích", Toast.LENGTH_SHORT).show()
            exitSelectionMode()
        }

        // Xóa ảnh đã chọn
        view.findViewById<TextView>(R.id.btnDeletePhoto).setOnClickListener {
            val selected = items.filterIsInstance<MediaItem.Media>().filter { it.isSelected }.map { it.uri }
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Chưa chọn ảnh nào!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Xóa ảnh")
                .setMessage("Bạn có chắc muốn xóa ${selected.size} ảnh?")
                .setPositiveButton("Xóa") { _, _ -> deleteSelectedPhotos(selected) }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Sửa tên ảnh (chỉ 1 ảnh)
        view.findViewById<TextView>(R.id.btnRenamePhoto).setOnClickListener {
            val selected = items.filterIsInstance<MediaItem.Media>().filter { it.isSelected }
            if (selected.size != 1) {
                Toast.makeText(requireContext(), "Chọn đúng 1 ảnh để sửa tên!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showRenameDialog(selected.first().uri)
        }

        // Hủy chọn
        view.findViewById<TextView>(R.id.btnCancelPhoto).setOnClickListener {
            exitSelectionMode()
        }

        // Lọc theo ngày
        btnDateFilter.setOnClickListener {
            showDatePicker()
        }

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

            // Lọc từ fullItems
            val filtered = mutableListOf<MediaItem>()
            var found = false
            for (item in fullItems) {
                if (item is MediaItem.Header && item.date == label) {
                    found = true
                    filtered.add(item)
                } else if (item is MediaItem.Header && found) {
                    break
                } else if (found && item is MediaItem.Media) {
                    filtered.add(item)
                }
            }
            items.clear()
            items.addAll(filtered)
            adapter.notifyDataSetChanged()
            val photoCount = items.filterIsInstance<MediaItem.Media>().size
            tvCount.text = "$photoCount ảnh ($label)"
            btnDateFilter.text = "Xóa lọc"
            btnDateFilter.setOnClickListener { clearDateFilter() }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun clearDateFilter() {
        items.clear()
        items.addAll(fullItems)
        adapter.notifyDataSetChanged()
        val photoCount = items.filterIsInstance<MediaItem.Media>().size
        tvCount.text = "$photoCount ảnh"
        btnDateFilter.text = "Lọc ngày"
        btnDateFilter.setOnClickListener { showDatePicker() }
    }

    private fun deleteSelectedPhotos(uris: List<Uri>) {
        val resolver = requireContext().contentResolver
        var count = 0
        for (uri in uris) {
            try {
                val rows = resolver.delete(uri, null, null)
                if (rows > 0) count++
            } catch (_: Exception) {}
        }
        Toast.makeText(requireContext(), "Đã xóa $count ảnh", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
        refreshPhotos()
    }

    private fun showRenameDialog(uri: Uri) {
        val et = EditText(requireContext()).apply {
            hint = "Nhập tên mới"
            setPadding(60, 40, 60, 40)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Sửa tên ảnh")
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
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, newName) }
            requireContext().contentResolver.update(uri, values, null, null)
            Toast.makeText(requireContext(), "Đã đổi tên thành $newName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Lỗi đổi tên: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        exitSelectionMode()
        refreshPhotos()
    }

    private fun refreshPhotos() {
        val context = requireContext()
        Thread {
            val loaded = loadPhotos(context)
            recyclerView.post {
                items.clear()
                items.addAll(loaded)
                fullItems.clear()
                fullItems.addAll(loaded)
                adapter.notifyDataSetChanged()
                val photoCount = items.filterIsInstance<MediaItem.Media>().size
                tvCount.text = "$photoCount ảnh"
                layoutCountBar.visibility = View.VISIBLE
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        Thread {
            val loaded = loadPhotos(context)
            recyclerView.post {
                items.clear()
                items.addAll(loaded)
                fullItems.clear()
                fullItems.addAll(loaded)
                adapter.notifyDataSetChanged()
                val photoCount = items.filterIsInstance<MediaItem.Media>().size
                tvCount.text = "$photoCount ảnh"
                layoutCountBar.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun loadPhotos(context: Context): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val mediaMap = mutableMapOf<String, MutableList<MediaItem.Media>>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        try {
            context.contentResolver.query(
                imageCollection, imageProjection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val date = dateFormat.format(Date(cursor.getLong(dateCol) * 1000))
                    val uri = ContentUris.withAppendedId(imageCollection, id)
                    mediaMap.getOrPut(date) { mutableListOf() }
                        .add(MediaItem.Media(uri, false))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sortedDates = mediaMap.keys.sortedByDescending {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it)
        }

        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))
        for (date in sortedDates) {
            val label = when (date) {
                today -> "Hôm nay"
                yesterday -> "Hôm qua"
                else -> date
            }
            result.add(MediaItem.Header(label))
            result.addAll(mediaMap[date]!!)
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
        selectedCount = 0
        items.filterIsInstance<MediaItem.Media>().forEach { it.isSelected = false }
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
        val total = items.filterIsInstance<MediaItem.Media>().size
        tvSelectionCount.text = "Đã chọn $selectedCount/$total ảnh"
        btnSelectAll.text = if (items.filterIsInstance<MediaItem.Media>()
                .all { it.isSelected }) "Bỏ chọn tất cả" else "Chọn tất cả"
    }

    inner class MediaAdapter(private val items: List<MediaItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        val TYPE_HEADER = 0
        val TYPE_MEDIA = 1

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(R.id.tvDateHeader)
        }

        inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgThumb: ImageView = view.findViewById(R.id.imgPhoto)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
            val iconVideo: ImageView = view.findViewById(R.id.iconVideo)
            val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
            val btnHeart: ImageView = view.findViewById(R.id.btnHeart)
        }

        override fun getItemViewType(position: Int) =
            if (items[position] is MediaItem.Header) TYPE_HEADER else TYPE_MEDIA

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                HeaderViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_date_header, parent, false)
                )
            } else {
                MediaViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_media, parent, false)
                )
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is MediaItem.Header -> {
                    (holder as HeaderViewHolder).tvDate.text = item.date
                }
                is MediaItem.Media -> {
                    val h = holder as MediaViewHolder
                    Glide.with(h.itemView)
                        .load(item.uri)
                        .centerCrop()
                        .into(h.imgThumb)

                    h.iconVideo.visibility = View.GONE
                    h.tvDuration.visibility = View.GONE

                    // Checkbox & trái tim
                    h.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                    h.btnHeart.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
                    h.cbSelect.isChecked = item.isSelected

                    // Trái tim
                    val isFav = FavoritesManager.isImageFavorite(requireContext(), item.uri)
                    h.btnHeart.setImageResource(
                        if (isFav) android.R.drawable.btn_star_big_on
                        else android.R.drawable.btn_star_big_off
                    )
                    h.btnHeart.setOnClickListener {
                        val added = FavoritesManager.toggleImage(requireContext(), item.uri)
                        h.btnHeart.setImageResource(
                            if (added) android.R.drawable.btn_star_big_on
                            else android.R.drawable.btn_star_big_off
                        )
                        val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
                        h.btnHeart.startAnimation(pulseAnim)
                        Toast.makeText(
                            requireContext(),
                            if (added) "Đã thêm vào ảnh yêu thích" else "Đã bỏ khỏi ảnh yêu thích",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Bấm thường
                    h.itemView.setOnClickListener {
                        if (isSelectionMode) {
                            item.isSelected = !item.isSelected
                            selectedCount += if (item.isSelected) 1 else -1
                            updateSelectionBar()
                            notifyItemChanged(position)
                        } else {
                            val intent = Intent(requireContext(), PhotoViewActivity::class.java)
                            intent.data = item.uri
                            startActivity(intent)
                        }
                    }

                    // Giữ lâu → vào chế độ chọn
                    h.itemView.setOnLongClickListener {
                        if (!isSelectionMode) {
                            enterSelectionMode()
                            item.isSelected = true
                            selectedCount = 1
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
