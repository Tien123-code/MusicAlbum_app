package com.example.musicalbum

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import java.text.SimpleDateFormat
import java.util.*

class PhotoViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_URIS = "extra_photo_uris"
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnFav: ImageView
    private lateinit var btnDelete: ImageView
    private lateinit var btnSlideshow: ImageView
    private val photoList = mutableListOf<Uri>()
    private lateinit var adapter: PhotoPagerAdapter
    private var isSlideshowRunning = false
    private val slideshowHandler = Handler(Looper.getMainLooper())
    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (viewPager.currentItem < photoList.size - 1) {
                viewPager.setCurrentItem(viewPager.currentItem + 1, true)
                slideshowHandler.postDelayed(this, 3000)
            } else {
                stopSlideshow()
            }
        }
    }

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            removeCurrentPhotoFromList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        setContentView(R.layout.activity_photo_view)

        viewPager = findViewById(R.id.viewPagerPhotos)
        tvDate = findViewById(R.id.tvPhotoDate)
        tvTime = findViewById(R.id.tvPhotoTime)
        btnFav = findViewById(R.id.btnFavPhoto)
        btnDelete = findViewById(R.id.btnDeletePhoto)
        btnSlideshow = findViewById(R.id.btnSlideshow)

        val currentUri = intent.data ?: return

        // Nếu có danh sách URI truyền vào (từ Favorites), dùng danh sách đó
        val extraUris = intent.getStringArrayListExtra(EXTRA_PHOTO_URIS)
        if (extraUris != null) {
            photoList.clear()
            photoList.addAll(extraUris.map { Uri.parse(it) })
        } else {
            loadAllPhotos(currentUri)
        }

        adapter = PhotoPagerAdapter(photoList)
        viewPager.adapter = adapter
        
        val startIndex = photoList.indexOf(currentUri)
        if (startIndex != -1) {
            viewPager.setCurrentItem(startIndex, false)
            updateInfo(currentUri)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position >= 0 && position < photoList.size) {
                    updateInfo(photoList[position])
                }
            }
        })

        // Smooth depth page transition for photo swiping
        viewPager.setPageTransformer { page, position ->
            val absPos = kotlin.math.abs(position)
            page.alpha = 1f - absPos * 0.4f
            page.scaleX = 1f - absPos * 0.15f
            page.scaleY = 1f - absPos * 0.15f
        }

        btnFav.setOnClickListener {
            val uri = photoList[viewPager.currentItem]
            val isFav = FavoritesManager.toggleImage(this, uri)
            btnFav.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on 
                else android.R.drawable.btn_star_big_off
            )
            val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)
            btnFav.startAnimation(pulseAnim)
            Toast.makeText(this, if (isFav) "Đã thêm vào yêu thích ❤️" else "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show()
        }

        btnDelete.setOnClickListener {
            confirmDelete()
        }

        btnSlideshow.setOnClickListener {
            if (isSlideshowRunning) stopSlideshow() else startSlideshow()
        }
    }

    private fun startSlideshow() {
        isSlideshowRunning = true
        btnSlideshow.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this, "Slideshow bắt đầu", Toast.LENGTH_SHORT).show()
        slideshowHandler.postDelayed(slideshowRunnable, 3000)
    }

    private fun stopSlideshow() {
        isSlideshowRunning = false
        btnSlideshow.setImageResource(android.R.drawable.ic_media_play)
        slideshowHandler.removeCallbacks(slideshowRunnable)
    }

    private fun loadAllPhotos(selectedUri: Uri) {
        photoList.clear()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                photoList.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
            }
        }
    }

    private fun updateInfo(uri: Uri) {
        val projection = arrayOf(MediaStore.Images.Media.DATE_ADDED)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dateAdded = cursor.getLong(0) * 1000
                val date = Date(dateAdded)
                tvDate.text = SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault()).format(date)
                tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            }
        }
        
        val isFav = FavoritesManager.isImageFavorite(this, uri)
        btnFav.setImageResource(
            if (isFav) android.R.drawable.btn_star_big_on 
            else android.R.drawable.btn_star_big_off
        )
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Xóa ảnh")
            .setMessage("Bạn có chắc chắn muốn xóa ảnh này vĩnh viễn khỏi thiết bị không?")
            .setPositiveButton("Xóa") { _, _ -> deletePhoto() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun deletePhoto() {
        val uri = photoList[viewPager.currentItem]
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } else {
            // Android 10 (API 29) và cũ hơn
            try {
                contentResolver.delete(uri, null, null)
                removeCurrentPhotoFromList()
            } catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val recoverableSecurityException = securityException as? RecoverableSecurityException
                        ?: throw securityException
                    val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                    deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } else {
                    throw securityException
                }
            }
        }
    }

    private fun removeCurrentPhotoFromList() {
        val index = viewPager.currentItem
        if (index != -1 && index < photoList.size) {
            photoList.removeAt(index)
            adapter.notifyItemRemoved(index)
            if (photoList.isEmpty()) {
                finish()
            } else {
                val nextIndex = if (index >= photoList.size) photoList.size - 1 else index
                viewPager.setCurrentItem(nextIndex, false)
                // Cần delay một chút để ViewPager cập nhật trang mới
                viewPager.post {
                    if (nextIndex < photoList.size) {
                        updateInfo(photoList[nextIndex])
                    }
                }
            }
            Toast.makeText(this, "Đã xóa ảnh khỏi thiết bị", Toast.LENGTH_SHORT).show()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    inner class PhotoPagerAdapter(private val uris: List<Uri>) : 
        RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoView: PhotoView = view.findViewById(R.id.photoView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viewpager_photo, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            Glide.with(holder.itemView)
                .load(uris[position])
                .into(holder.photoView)
        }

        override fun getItemCount() = uris.size
    }
}