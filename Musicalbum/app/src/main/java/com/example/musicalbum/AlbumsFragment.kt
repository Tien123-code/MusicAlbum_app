package com.example.musicalbum

import android.content.ContentUris
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

class AlbumsFragment : Fragment() {

    data class Album(
        val id: Long,
        val name: String,
        val imageCount: Int,
        val videoCount: Int,
        val coverUri: Uri?,
        val type: AlbumType // ✅ Phân loại album
    )

    enum class AlbumType {
        ALL,    // Album ảnh + video
        VIDEO   // Chỉ có video
    }

    private val albums = mutableListOf<Album>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_albums, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvAlbums)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        loadAlbums()
        recyclerView.adapter = AlbumAdapter(albums)
        return view
    }

    private fun loadAlbums() {
        data class AlbumData(
            val name: String,
            var imageCount: Int = 0,
            var videoCount: Int = 0,
            val coverUri: Uri?
        )

        val albumMap = mutableMapOf<Long, AlbumData>()

        // ── Load ảnh ──────────────────────────────────────
        val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        requireContext().contentResolver.query(
            imageCollection,
            arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media._ID
            ),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val bucketIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idCol        = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val bucketId  = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val uri = ContentUris.withAppendedId(imageCollection, cursor.getLong(idCol))
                if (albumMap.containsKey(bucketId)) {
                    albumMap[bucketId] = albumMap[bucketId]!!.copy(
                        imageCount = albumMap[bucketId]!!.imageCount + 1
                    )
                } else {
                    albumMap[bucketId] = AlbumData(bucketName, 1, 0, uri)
                }
            }
        }

        // ── Load video ────────────────────────────────────
        // Dùng bucketId âm để tránh trùng với bucket ảnh
        val videoAlbumMap = mutableMapOf<Long, AlbumData>()
        val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        requireContext().contentResolver.query(
            videoCollection,
            arrayOf(
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media._ID
            ),
            null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val bucketIdCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val idCol         = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val bucketId   = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val uri = ContentUris.withAppendedId(videoCollection, cursor.getLong(idCol))

                // Nếu bucket này đã có trong albumMap (ảnh) → thêm videoCount
                if (albumMap.containsKey(bucketId)) {
                    albumMap[bucketId] = albumMap[bucketId]!!.copy(
                        videoCount = albumMap[bucketId]!!.videoCount + 1
                    )
                } else {
                    // Bucket chỉ có video → thêm vào videoAlbumMap riêng
                    if (videoAlbumMap.containsKey(bucketId)) {
                        videoAlbumMap[bucketId] = videoAlbumMap[bucketId]!!.copy(
                            videoCount = videoAlbumMap[bucketId]!!.videoCount + 1
                        )
                    } else {
                        videoAlbumMap[bucketId] = AlbumData(bucketName, 0, 1, uri)
                    }
                }
            }
        }

        // ── Gộp kết quả ──────────────────────────────────
        // Album có ảnh (có thể kèm video)
        albums.addAll(albumMap.map { (id, data) ->
            Album(id, data.name, data.imageCount, data.videoCount, data.coverUri, AlbumType.ALL)
        }.sortedByDescending { it.imageCount + it.videoCount })

        // Album chỉ có video
        albums.addAll(videoAlbumMap.map { (id, data) ->
            Album(id, data.name, 0, data.videoCount, data.coverUri, AlbumType.VIDEO)
        }.sortedByDescending { it.videoCount })
    }

    inner class AlbumAdapter(private val albums: List<Album>) :
        RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

        inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgCover  : ImageView = view.findViewById(R.id.imgAlbumCover)
            val tvName    : TextView  = view.findViewById(R.id.tvAlbumName)
            val tvCount   : TextView  = view.findViewById(R.id.tvAlbumCount)
            val iconVideo : ImageView = view.findViewById(R.id.iconVideoAlbum)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AlbumViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_album, parent, false)
            )

        override fun getItemCount() = albums.size

        override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
            val album = holder.run { albums[position] }

            holder.tvName.text = album.name

            // Hiện số lượng
            val parts = mutableListOf<String>()
            if (album.imageCount > 0) parts.add("${album.imageCount} ảnh")
            if (album.videoCount > 0) parts.add("${album.videoCount} video")
            holder.tvCount.text = parts.joinToString(" · ")

            // Icon video nếu album chỉ có video
            holder.iconVideo.visibility =
                if (album.type == AlbumType.VIDEO) View.VISIBLE else View.GONE

            Glide.with(holder.itemView)
                .load(album.coverUri)
                .centerCrop()
                .into(holder.imgCover)

            holder.itemView.setOnClickListener {
                val fragment = AlbumDetailFragment.newInstance(
                    album.id,
                    album.name,
                    album.type == AlbumType.VIDEO // ✅ Truyền loại album
                )
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_up, R.anim.fade_out,
                        R.anim.fade_in, R.anim.slide_down
                    )
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
                requireActivity().findViewById<View>(R.id.fragmentContainer)
                    .visibility = View.VISIBLE
            }
        }
    }
}