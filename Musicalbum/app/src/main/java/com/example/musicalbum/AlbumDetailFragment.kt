package com.example.musicalbum

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class AlbumDetailFragment : Fragment() {

    companion object {
        fun newInstance(albumId: Long, albumName: String, isVideoOnly: Boolean = false) =
            AlbumDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong("albumId", albumId)
                    putString("albumName", albumName)
                    putBoolean("isVideoOnly", isVideoOnly) // ✅ Lúc này isVideoOnly đã có
                }
            }
    }
    sealed class MediaItem {
        data class Header(val date: String) : MediaItem()
        data class Media(
            val uri: Uri,
            val isVideo: Boolean,
            val duration: Long = 0
        ) : MediaItem()
    }

    private val items = mutableListOf<MediaItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_album_detail, container, false)
// Trong onCreateView thêm:
        val isVideoOnly = arguments?.getBoolean("isVideoOnly") ?: false
        val albumId = arguments?.getLong("albumId") ?: return view
        val albumName = arguments?.getString("albumName") ?: ""

        view.findViewById<TextView>(R.id.tvAlbumDetailTitle).text = albumName
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvAlbumDetail)
        val layoutManager = GridLayoutManager(requireContext(), 3)

        // Header chiếm full width
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (position < items.size && items[position] is MediaItem.Header) 3 else 1
        }

        recyclerView.layoutManager = layoutManager
        loadMedia(albumId, isVideoOnly)
        recyclerView.adapter = DetailAdapter(items)
        return view
    }

    // Sửa hàm loadMedia thêm tham số:
    private fun loadMedia(albumId: Long, isVideoOnly: Boolean) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val mediaMap = mutableMapOf<String, MutableList<MediaItem.Media>>()

        // Chỉ load ảnh nếu KHÔNG phải album video thuần
        if (!isVideoOnly) {
            val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            requireContext().contentResolver.query(
                imageCollection,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED
                ),
                "${MediaStore.Images.Media.BUCKET_ID} = ?",
                arrayOf(albumId.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val uri  = ContentUris.withAppendedId(imageCollection, cursor.getLong(idCol))
                    val date = dateFormat.format(Date(cursor.getLong(dateCol) * 1000))
                    mediaMap.getOrPut(date) { mutableListOf() }
                        .add(MediaItem.Media(uri, false))
                }
            }
        }

        // Luôn load video
        val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        requireContext().contentResolver.query(
            videoCollection,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
            ),
            "${MediaStore.Video.Media.BUCKET_ID} = ?",
            arrayOf(albumId.toString()),
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val uri  = ContentUris.withAppendedId(videoCollection, cursor.getLong(idCol))
                val date = dateFormat.format(Date(cursor.getLong(dateCol) * 1000))
                mediaMap.getOrPut(date) { mutableListOf() }
                    .add(MediaItem.Media(uri, true, cursor.getLong(durCol)))
            }
        }

        // Sắp xếp ngày mới nhất lên đầu
        val today     = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))

        mediaMap.keys
            .sortedByDescending {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it)
            }
            .forEach { date ->
                val label = when (date) {
                    today     -> "Hôm nay"
                    yesterday -> "Hôm qua"
                    else      -> date
                }
                items.add(MediaItem.Header(label))
                items.addAll(mediaMap[date]!!)
            }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }

    inner class DetailAdapter(private val items: List<MediaItem>) :
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
                        .inflate(R.layout.item_photo, parent, false)
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

                    if (item.isVideo) {
                        h.iconVideo.visibility = View.VISIBLE
                        h.tvDuration.visibility = View.VISIBLE
                        h.tvDuration.text = formatDuration(item.duration)
                    } else {
                        h.iconVideo.visibility = View.GONE
                        h.tvDuration.visibility = View.GONE
                    }

                    h.itemView.setOnClickListener {
                        if (item.isVideo) {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(item.uri, "video/*")
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivity(intent)
                        } else {
                            val intent = Intent(requireContext(), PhotoViewActivity::class.java)
                            intent.data = item.uri
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}