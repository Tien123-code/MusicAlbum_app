package com.example.musicalbum

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongInfo(
    val uri: Uri,
    val title: String,
    val artist: String
) : Parcelable
