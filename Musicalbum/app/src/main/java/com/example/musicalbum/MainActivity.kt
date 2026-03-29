package com.example.musicalbum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasPermissions()) {
            checkManageStoragePermission()
            setupTabs()
        } else {
            requestNeededPermissions()
        }

        // ✅ Nút 3 gạch → menu yêu thích
        findViewById<ImageView>(R.id.btnMenu).setOnClickListener { view ->
            showFavoritesMenu(view)
        }

        // Back stack listener
        supportFragmentManager.addOnBackStackChangedListener {
            val container = findViewById<View>(R.id.fragmentContainer)
            container.visibility = if (supportFragmentManager.backStackEntryCount > 0)
                View.VISIBLE else View.GONE
        }

        // Xử lý nút Back thay cho onBackPressed() deprecated
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showFavoritesMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "❤️ Ảnh yêu thích")
        popup.menu.add(0, 2, 1, "❤️ Video yêu thích")
        popup.menu.add(0, 3, 2, "❤️ Nhạc yêu thích")

        popup.setOnMenuItemClickListener { item ->
            val type = when (item.itemId) {
                1 -> FavoritesFragment.TYPE_IMAGE
                2 -> FavoritesFragment.TYPE_VIDEO
                else -> FavoritesFragment.TYPE_MUSIC
            }
            openFavorites(type)
            true
        }
        popup.show()
    }

    private fun openFavorites(type: String) {
        val fragment = FavoritesFragment.newInstance(type)
        val container = findViewById<View>(R.id.fragmentContainer)
        container.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_up, R.anim.fade_out,
                R.anim.fade_in, R.anim.slide_down
            )
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun requestNeededPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkManageStoragePermission()
                setupTabs()
            } else {
                Toast.makeText(
                    this,
                    "Ứng dụng cần quyền truy cập bộ nhớ để hiển thị ảnh, video và nhạc.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> PhotosFragment()
                    1 -> AlbumsFragment()
                    2 -> VideoFragment()
                    else -> MusicFragment()
                }
            }
        }
        viewPager.adapter = adapter

        // Smooth depth page transition
        viewPager.setPageTransformer { page, position ->
            page.apply {
                val absPos = kotlin.math.abs(position)
                alpha = 1f - absPos * 0.3f
                scaleX = 1f - absPos * 0.1f
                scaleY = 1f - absPos * 0.1f
                translationX = -position * width * 0.05f
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Ảnh"
                1 -> "Album"
                2 -> "Video"
                else -> "Nhạc"
            }
        }.attach()
    }

}
