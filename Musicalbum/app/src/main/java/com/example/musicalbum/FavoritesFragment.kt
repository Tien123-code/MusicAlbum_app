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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class FavoritesFragment : Fragment() {

    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_MUSIC = "music"

        fun newInstance(type: String) = FavoritesFragment().apply {
            arguments = Bundle().apply { putString("type", type) }
        }
    }

    private val type by lazy { arguments?.getString("type") ?: TYPE_IMAGE }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_favorites, container, false)

        // Toolbar
        val title = when (type) {
            TYPE_IMAGE -> "Ảnh yêu thích"
            TYPE_VIDEO -> "Video yêu thích"
            else       -> "Nhạc yêu thích"
        }
        view.findViewById<TextView>(R.id.tvFavTitle).text = title
        view.findViewById<ImageView>(R.id.btnFavBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvFavorites)

        when (type) {
            TYPE_IMAGE -> setupImageFavorites(recyclerView)
            TYPE_VIDEO -> setupVideoFavorites(recyclerView)
            TYPE_MUSIC -> setupMusicFavorites(recyclerView)
        }

        return view
    }

    // ── Ảnh yêu thích ────────────────────────────────────
    private fun setupImageFavorites(recyclerView: RecyclerView) {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        val uris = FavoritesManager.getFavoriteImages(requireContext())
            .map { Uri.parse(it) }
        recyclerView.adapter = ImageFavAdapter(uris.toMutableList())
    }

    inner class ImageFavAdapter(private val list: MutableList<Uri>) :
        RecyclerView.Adapter<ImageFavAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val img    : ImageView = view.findViewById(R.id.imgPhoto)
            val btnHeart: ImageView = view.findViewById(R.id.btnHeart)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media, parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = list[position]
            Glide.with(holder.itemView).load(uri).centerCrop().into(holder.img)

            // Item entrance animation
            val anim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.scale_bounce)
            anim.startOffset = (position % 12 * 40).toLong()
            holder.itemView.startAnimation(anim)

            holder.btnHeart.setImageResource(android.R.drawable.btn_star_big_on)
            holder.btnHeart.setOnClickListener {
                holder.btnHeart.setImageResource(android.R.drawable.btn_star_big_off)
                val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
                holder.btnHeart.startAnimation(pulseAnim)
                FavoritesManager.toggleImage(requireContext(), uri)
                
                holder.itemView.animate().alpha(0f).translationX(-holder.itemView.width.toFloat())
                    .setDuration(300).withEndAction {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        list.removeAt(pos)
                        notifyItemRemoved(pos)
                    }
                }.start()
                
                Toast.makeText(requireContext(), "Đã bỏ khỏi ảnh yêu thích", Toast.LENGTH_SHORT).show()
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(requireContext(), PhotoViewActivity::class.java)
                intent.data = uri
                intent.putStringArrayListExtra(
                    PhotoViewActivity.EXTRA_PHOTO_URIS,
                    ArrayList(list.map { it.toString() })
                )
                startActivity(intent)
            }
        }
    }

    // ── Video yêu thích ───────────────────────────────────
    private fun setupVideoFavorites(recyclerView: RecyclerView) {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        val uris = FavoritesManager.getFavoriteVideos(requireContext())
            .map { Uri.parse(it) }
        recyclerView.adapter = VideoFavAdapter(uris.toMutableList())
    }

    inner class VideoFavAdapter(private val list: MutableList<Uri>) :
        RecyclerView.Adapter<VideoFavAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val img    : ImageView = view.findViewById(R.id.imgPhoto)
            val btnHeart: ImageView = view.findViewById(R.id.btnHeart)
            val iconVideo: ImageView = view.findViewById(R.id.iconVideo)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media, parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = list[position]
            Glide.with(holder.itemView).load(uri).centerCrop().into(holder.img)
            holder.iconVideo.visibility = View.VISIBLE

            // Item entrance animation
            val anim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.scale_bounce)
            anim.startOffset = (position % 12 * 40).toLong()
            holder.itemView.startAnimation(anim)

            holder.btnHeart.setImageResource(android.R.drawable.btn_star_big_on)
            holder.btnHeart.setOnClickListener {
                holder.btnHeart.setImageResource(android.R.drawable.btn_star_big_off)
                val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
                holder.btnHeart.startAnimation(pulseAnim)
                FavoritesManager.toggleVideo(requireContext(), uri)
                
                holder.itemView.animate().alpha(0f).translationX(-holder.itemView.width.toFloat())
                    .setDuration(300).withEndAction {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        list.removeAt(pos)
                        notifyItemRemoved(pos)
                    }
                }.start()
                
                Toast.makeText(requireContext(), "Đã bỏ khỏi video yêu thích", Toast.LENGTH_SHORT).show()
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(requireContext(), VideoPlayerActivity::class.java)
                intent.data = uri
                startActivity(intent)
            }
        }
    }

    // ── Nhạc yêu thích ───────────────────────────────────
    private fun setupMusicFavorites(recyclerView: RecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val uris = FavoritesManager.getFavoriteMusic(requireContext())
            .map { Uri.parse(it) }
        
        val musicDataList = mutableListOf<MusicData>()
        for (uri in uris) {
            getMusicInfo(uri)?.let { musicDataList.add(it) }
        }
        
        recyclerView.adapter = MusicFavAdapter(musicDataList)
    }

    data class MusicData(val uri: Uri, val title: String, val artist: String)

    private fun getMusicInfo(uri: Uri): MusicData? {
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        return try {
            requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val title = cursor.getString(0)
                    val artist = cursor.getString(1)
                    MusicData(uri, title, artist)
                } else {
                    MusicData(uri, "Không rõ tên", "Không rõ ca sĩ")
                }
            }
        } catch (e: Exception) {
            MusicData(uri, uri.lastPathSegment ?: "Bài hát", "Ngoại vi")
        }
    }

    inner class MusicFavAdapter(private val list: MutableList<MusicData>) :
        RecyclerView.Adapter<MusicFavAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle : TextView  = view.findViewById(R.id.tvSongTitle)
            val tvArtist: TextView  = view.findViewById(R.id.tvSongArtist)
            val tvIndex : TextView  = view.findViewById(R.id.tvSongIndex)
            val btnHeart: ImageView = view.findViewById(R.id.btnHeartSong)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song_fav, parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val data = list[position]
            holder.tvIndex.text  = (position + 1).toString()
            holder.tvTitle.text  = data.title
            holder.tvArtist.text = data.artist

            // Item entrance animation
            val anim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.item_slide_in_right)
            anim.startOffset = (position % 10 * 50).toLong()
            holder.itemView.startAnimation(anim)

            holder.btnHeart.setImageResource(android.R.drawable.btn_star_big_on)
            holder.btnHeart.setOnClickListener {
                holder.btnHeart.setImageResource(android.R.drawable.btn_star_big_off)
                val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
                holder.btnHeart.startAnimation(pulseAnim)
                FavoritesManager.toggleMusic(requireContext(), data.uri)
                
                holder.itemView.animate().alpha(0f).translationX(-holder.itemView.width.toFloat())
                    .setDuration(300).withEndAction {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        list.removeAt(pos)
                        notifyItemRemoved(pos)
                        notifyItemRangeChanged(pos, list.size)
                    }
                }.start()

                Toast.makeText(requireContext(), "Đã bỏ khỏi nhạc yêu thích", Toast.LENGTH_SHORT).show()
            }

            holder.itemView.setOnClickListener {
                val playlistForIntent = ArrayList(list.map {
                    MusicPlayerActivity.SongInfo(it.uri.toString(), it.title, it.artist)
                })

                val intent = Intent(requireContext(), MusicPlayerActivity::class.java).apply {
                    putExtra(MusicPlayerActivity.EXTRA_SONG_URI, data.uri.toString())
                    putExtra(MusicPlayerActivity.EXTRA_SONG_TITLE, data.title)
                    putExtra(MusicPlayerActivity.EXTRA_SONG_ARTIST, data.artist)
                    putExtra(MusicPlayerActivity.EXTRA_CURRENT_INDEX, position)
                    putExtra(MusicPlayerActivity.EXTRA_PLAYLIST, playlistForIntent)
                }
                startActivity(intent)
            }
        }
    }
}