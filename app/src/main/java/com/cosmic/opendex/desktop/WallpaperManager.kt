package com.cosmic.opendex.desktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.cosmic.opendex.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages desktop wallpapers: presets and custom gallery images.
 */
class WallpaperManager(private val context: Context) {

    private val TAG = "OpenDeX:Wallpaper"
    private val prefs = DesktopPreferences(context)
    private val customWallpaperFile = File(context.filesDir, "custom_wallpaper.png")

    data class Preset(val title: String, val drawableRes: Int)

    val presets = listOf(
        Preset("Windows 11 Bloom", R.drawable.wallpaper_windows_bloom),
        Preset("Aurora Borealis", R.drawable.wallpaper_aurora_borealis),
        Preset("Cyberpunk Midnight", R.drawable.wallpaper_cyberpunk),
        Preset("DeX Slate Nebula", R.drawable.wallpaper_dex_dark),
        Preset("Deep Emerald", R.drawable.wallpaper_emerald),
        Preset("Sapphire Abyss", R.drawable.wallpaper_sapphire)
    )

    fun applyWallpaper(imageView: ImageView) {
        val type = prefs.getWallpaperType()
        if (type == DesktopPreferences.WALLPAPER_TYPE_CUSTOM && customWallpaperFile.exists()) {
            val bitmap = decodeSampledBitmapFromFile(customWallpaperFile.absolutePath)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                return
            }
        }

        // Default or preset
        val presetIndex = prefs.getWallpaperPresetIndex().coerceIn(0, presets.size - 1)
        imageView.setImageDrawable(ContextCompat.getDrawable(context, presets[presetIndex].drawableRes))
    }

    fun applyPreset(index: Int, imageView: ImageView) {
        val safeIndex = index.coerceIn(0, presets.size - 1)
        prefs.setWallpaperPreset(safeIndex)
        imageView.setImageDrawable(ContextCompat.getDrawable(context, presets[safeIndex].drawableRes))
    }

    suspend fun saveCustomWallpaper(uri: Uri, imageView: ImageView): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $uri")
                return@withContext false
            }

            FileOutputStream(customWallpaperFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()

            prefs.setCustomWallpaper()

            val bitmap = decodeSampledBitmapFromFile(customWallpaperFile.absolutePath)
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom wallpaper: ${e.message}", e)
            false
        }
    }

    private fun decodeSampledBitmapFromFile(path: String): Bitmap? {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val reqWidth = metrics.widthPixels
            val reqHeight = metrics.heightPixels

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding custom wallpaper bitmap: ${e.message}")
            null
        }
    }
}
