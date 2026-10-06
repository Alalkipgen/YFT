package com.alal.yft.thumbnail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A blank picture; Robolectric's legacy graphics can't make one through `ImageBitmap()`. */
internal fun testPicture(width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
