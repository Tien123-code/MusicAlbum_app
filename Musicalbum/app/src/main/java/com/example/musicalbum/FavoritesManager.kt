package com.example.musicalbum

import android.content.Context
import android.net.Uri

object FavoritesManager {

    private const val PREF_NAME = "favorites"
    private const val KEY_IMAGES = "fav_images"
    private const val KEY_VIDEOS = "fav_videos"
    private const val KEY_MUSIC  = "fav_music"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Lấy danh sách ──────────────────────────────────
    fun getFavoriteImages(context: Context): MutableSet<String> =
        getPrefs(context).getStringSet(KEY_IMAGES, mutableSetOf())!!.toMutableSet()

    fun getFavoriteVideos(context: Context): MutableSet<String> =
        getPrefs(context).getStringSet(KEY_VIDEOS, mutableSetOf())!!.toMutableSet()

    fun getFavoriteMusic(context: Context): MutableSet<String> =
        getPrefs(context).getStringSet(KEY_MUSIC, mutableSetOf())!!.toMutableSet()

    // ── Kiểm tra ───────────────────────────────────────
    fun isImageFavorite(context: Context, uri: Uri) =
        getFavoriteImages(context).contains(uri.toString())

    fun isVideoFavorite(context: Context, uri: Uri) =
        getFavoriteVideos(context).contains(uri.toString())

    fun isMusicFavorite(context: Context, uri: Uri) =
        getFavoriteMusic(context).contains(uri.toString())

    // ── Toggle 1 item ──────────────────────────────────
    fun toggleImage(context: Context, uri: Uri): Boolean {
        val set = getFavoriteImages(context)
        val added = if (set.contains(uri.toString())) {
            set.remove(uri.toString()); false
        } else {
            set.add(uri.toString()); true
        }
        getPrefs(context).edit().putStringSet(KEY_IMAGES, set).apply()
        return added
    }

    fun toggleVideo(context: Context, uri: Uri): Boolean {
        val set = getFavoriteVideos(context)
        val added = if (set.contains(uri.toString())) {
            set.remove(uri.toString()); false
        } else {
            set.add(uri.toString()); true
        }
        getPrefs(context).edit().putStringSet(KEY_VIDEOS, set).apply()
        return added
    }

    fun toggleMusic(context: Context, uri: Uri): Boolean {
        val set = getFavoriteMusic(context)
        val added = if (set.contains(uri.toString())) {
            set.remove(uri.toString()); false
        } else {
            set.add(uri.toString()); true
        }
        getPrefs(context).edit().putStringSet(KEY_MUSIC, set).apply()
        return added
    }

    // ── Thêm nhiều cùng lúc ────────────────────────────
    fun addImages(context: Context, uris: List<Uri>) {
        val set = getFavoriteImages(context)
        uris.forEach { set.add(it.toString()) }
        getPrefs(context).edit().putStringSet(KEY_IMAGES, set).apply()
    }

    fun addVideos(context: Context, uris: List<Uri>) {
        val set = getFavoriteVideos(context)
        uris.forEach { set.add(it.toString()) }
        getPrefs(context).edit().putStringSet(KEY_VIDEOS, set).apply()
    }

    fun addMusic(context: Context, uris: List<Uri>) {
        val set = getFavoriteMusic(context)
        uris.forEach { set.add(it.toString()) }
        getPrefs(context).edit().putStringSet(KEY_MUSIC, set).apply()
    }
}