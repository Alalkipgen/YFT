package com.alal.yft.core.media.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(): ExoPlayer = ExoPlayer.Builder(context).build()
}
