package com.codesrahul.exclusivetv

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

object LogoUtil {

    private const val FALLBACK_SIZE_DP = 60
    
    // Cache for fallback icons to prevent redundant allocations during scrolling
    private val iconCache = android.util.LruCache<String, Drawable>(26) // Cache for ~26 letters

    fun loadLogo(context: Context, imageView: ImageView, url: String?, name: String?) {
        val letter = if (!name.isNullOrEmpty()) name.substring(0, 1).uppercase() else "?"
        val appLogoFallback = ContextCompat.getDrawable(context, R.drawable.logo_exclusive)
        
        if (url.isNullOrBlank()) {
            // Use app logo if available, otherwise use letter tile
            imageView.setImageDrawable(appLogoFallback ?: getOrCreateFallback(context, letter))
            return
        }

        Glide.with(context)
            .load(url)
            .centerInside()
            .transition(DrawableTransitionOptions.withCrossFade())
            .error(appLogoFallback ?: getOrCreateFallback(context, letter))
            .into(imageView)
    }

    private fun getOrCreateFallback(context: Context, letter: String): Drawable {
        return iconCache.get(letter) ?: createLetterTile(context, letter).also {
            iconCache.put(letter, it)
        }
    }

    private fun createLetterTile(context: Context, letter: String): Drawable {
        val size = Utils.dpToPx(FALLBACK_SIZE_DP)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background from drawable
        val background = ContextCompat.getDrawable(context, R.drawable.logo_fallback)
        background?.setBounds(0, 0, size, size)
        background?.draw(canvas)
        
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * 0.5f
        }

        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)

        canvas.drawText(letter, xPos, yPos, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
