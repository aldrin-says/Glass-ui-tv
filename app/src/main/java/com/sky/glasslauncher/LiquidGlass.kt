package com.sky.glasslauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider

private fun argb(a: Int, rgb: Int = 0xFFFFFF): Int = (a shl 24) or rgb

/**
 * The colourful wallpaper. It is generated once as a small smooth bitmap, used as the
 * window background, and also sampled by every glass surface as its "backdrop".
 */
object Wallpaper {
    private var bmp: Bitmap? = null

    fun bitmap(): Bitmap {
        bmp?.let { return it }
        val w = 640
        val h = 360
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)

        val base = Paint(Paint.ANTI_ALIAS_FLAG)
        base.shader = LinearGradient(
            0f, 0f, w * 0.45f, h.toFloat(),
            intArrayOf(argb(255, 0x070A1F), argb(255, 0x140B30), argb(255, 0x0A1F3D)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), base)

        fun blob(cx: Float, cy: Float, r: Float, rgb: Int, alpha: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            p.shader = RadialGradient(
                cx * w, cy * h, r * w,
                argb(alpha, rgb), argb(0, rgb), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }

        blob(0.12f, 0.18f, 0.42f, 0xFF4F9A, 170) // pink
        blob(0.55f, 0.00f, 0.45f, 0x3D7BFF, 170) // blue
        blob(0.95f, 0.30f, 0.38f, 0xFF9A3D, 140) // amber
        blob(0.22f, 1.00f, 0.45f, 0x2DE2C6, 150) // teal
        blob(0.78f, 1.00f, 0.50f, 0x8F5BFF, 170) // violet
        blob(0.50f, 0.55f, 0.30f, 0x4A6CFF, 70)  // soft centre glow

        bmp = b
        return b
    }
}

/**
 * Liquid-glass surface:
 *  - samples the wallpaper behind the view, slightly magnified (lens) and brightened
 *  - frosted tint, glossy specular highlight, soft bottom shading
 *  - bright edge light that fades across the surface, like glass catching light
 *  - when focused: stronger glass and a solid white ring
 */
class LiquidGlassDrawable(
    private val host: View,
    private val radiusDp: Float
) : Drawable() {

    private val density = host.resources.displayMetrics.density
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = argb(255)
    }

    private val clip = Path()
    private val rf = RectF()
    private val rf2 = RectF()
    private val src = Rect()
    private val loc = IntArray(2)

    private var radius = 0f
    private var focused = false

    private var frostIdle: Shader? = null
    private var frostFocus: Shader? = null
    private var spec: Shader? = null
    private var shade: Shader? = null
    private var rimShader: Shader? = null

    init {
        val cm = ColorMatrix().apply { setSaturation(1.6f) }
        val lift = ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, 18f,
                0f, 1.25f, 0f, 0f, 18f,
                0f, 0f, 1.25f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(lift)
        backdrop.colorFilter = ColorMatrixColorFilter(cm)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val f = state.contains(android.R.attr.state_focused)
        if (f != focused) {
            focused = f
            invalidateSelf()
            return true
        }
        return false
    }

    override fun onBoundsChange(b: Rect) {
        rf.set(b)
        radius = minOf(radiusDp * density, b.width() / 2f, b.height() / 2f)
        clip.reset()
        clip.addRoundRect(rf, radius, radius, Path.Direction.CW)

        val l = b.left.toFloat()
        val t = b.top.toFloat()
        val r = b.right.toFloat()
        val bt = b.bottom.toFloat()
        val w = b.width().toFloat()
        val h = b.height().toFloat()

        frostIdle = LinearGradient(0f, t, 0f, bt, argb(0x3C), argb(0x12), Shader.TileMode.CLAMP)
        frostFocus = LinearGradient(0f, t, 0f, bt, argb(0x70), argb(0x30), Shader.TileMode.CLAMP)
        spec = RadialGradient(l + w * 0.22f, t - h * 0.10f, w * 0.85f, argb(0x5A), argb(0), Shader.TileMode.CLAMP)
        shade = LinearGradient(0f, t + h * 0.55f, 0f, bt, argb(0, 0), argb(0x2A, 0), Shader.TileMode.CLAMP)
        rimShader = LinearGradient(
            l, t, r, bt,
            intArrayOf(argb(0xF2), argb(0x22), argb(0x99)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        canvas.save()
        canvas.clipPath(clip)

        // 1) backdrop: the wallpaper behind this surface, magnified a little like a lens
        val wp = Wallpaper.bitmap()
        val root = host.rootView
        val rw = root.width
        val rh = root.height
        if (rw > 0 && rh > 0) {
            host.getLocationInWindow(loc)
            val sx = wp.width / rw.toFloat()
            val sy = wp.height / rh.toFloat()
            val lens = 1.18f
            val cx = (loc[0] + b.exactCenterX()) * sx
            val cy = (loc[1] + b.exactCenterY()) * sy
            val hw = b.width() * sx / lens / 2f
            val hh = b.height() * sy / lens / 2f
            src.set((cx - hw).toInt(), (cy - hh).toInt(), (cx + hw).toInt(), (cy + hh).toInt())
            if (src.intersect(0, 0, wp.width, wp.height) && !src.isEmpty) {
                canvas.drawBitmap(wp, src, b, backdrop)
            }
        }

        // 2) frost tint, specular gloss, bottom depth
        fill.shader = if (focused) frostFocus else frostIdle
        canvas.drawRect(rf, fill)
        fill.shader = spec
        canvas.drawRect(rf, fill)
        fill.shader = shade
        canvas.drawRect(rf, fill)
        fill.shader = null
        canvas.restore()

        // 3) edge light
        val sw = 1.5f * density
        rim.shader = rimShader
        rim.strokeWidth = sw
        rf2.set(rf)
        rf2.inset(sw / 2f, sw / 2f)
        canvas.drawRoundRect(rf2, radius - sw / 2f, radius - sw / 2f, rim)

        // 4) focus ring (solid white)
        if (focused) {
            val rw2 = 3f * density
            ring.strokeWidth = rw2
            rf2.set(rf)
            rf2.inset(rw2 / 2f, rw2 / 2f)
            canvas.drawRoundRect(rf2, radius - rw2 / 2f, radius - rw2 / 2f, ring)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Glossy sheen drawn over the app artwork so banners also look like they sit under glass.
 */
class GlassSheenDrawable(
    private val insetDp: Float,
    private val radiusDp: Float,
    density: Float
) : Drawable() {

    private val inset = insetDp * density
    private val radius = radiusDp * density
    private val density = density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rf = RectF()
    private val path = Path()
    private var top: Shader? = null
    private var spec: Shader? = null
    private var edgeShader: Shader? = null

    override fun onBoundsChange(b: Rect) {
        rf.set(b)
        rf.inset(inset, inset)
        path.reset()
        path.addRoundRect(rf, radius, radius, Path.Direction.CW)
        top = LinearGradient(
            0f, rf.top, 0f, rf.top + rf.height() * 0.5f,
            argb(0x30), argb(0), Shader.TileMode.CLAMP
        )
        spec = RadialGradient(
            rf.left + rf.width() * 0.2f, rf.top - rf.height() * 0.15f, rf.width() * 0.75f,
            argb(0x3A), argb(0), Shader.TileMode.CLAMP
        )
        edgeShader = LinearGradient(
            rf.left, rf.top, rf.right, rf.bottom,
            intArrayOf(argb(0xCC), argb(0x10), argb(0x66)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.save()
        canvas.clipPath(path)
        paint.shader = top
        canvas.drawRect(rf, paint)
        paint.shader = spec
        canvas.drawRect(rf, paint)
        paint.shader = null
        canvas.restore()

        val sw = 1.2f * density
        edge.shader = edgeShader
        edge.strokeWidth = sw
        val r2 = RectF(rf)
        r2.inset(sw / 2f, sw / 2f)
        canvas.drawRoundRect(r2, radius - sw / 2f, radius - sw / 2f, edge)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Soft floating shadow under a glass surface (drawn only outside its outline). */
fun glassShadow(v: View, radiusDp: Float, elevationDp: Float) {
    val d = v.resources.displayMetrics.density
    v.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val r = minOf(radiusDp * d, view.width / 2f, view.height / 2f)
            outline.setRoundRect(0, 0, view.width, view.height, r)
            outline.setAlpha(0.55f)
        }
    }
    v.elevation = elevationDp * d
}
