package id.ios.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.json.JSONObject
import java.io.File

/** Konfigurasi tema (dibaca dari theme.json). Semua field opsional. */
class Cfg(j: JSONObject) {
    val cols = j.optInt("columns", 4).coerceIn(3, 6)
    val rows = j.optInt("rows", 6).coerceIn(4, 8)
    val iconDp = j.optInt("iconSizeDp", 60).coerceIn(40, 80)
    val n = j.optDouble("iconSquircle", 5.0).coerceIn(2.0, 12.0)
    val labels = j.optBoolean("showLabels", true)
    val labelSp = j.optDouble("labelSizeSp", 11.0).toFloat()
    val labelColor = col(j.optString("labelColor"), Color.WHITE)
    val glassTint = col(j.optString("glassTint"), Color.WHITE)
    val glassAlpha = j.optDouble("glassAlpha", 0.25).toFloat().coerceIn(0f, 1f)
    val strokeAlpha = j.optDouble("glassStrokeAlpha", 0.6).toFloat().coerceIn(0f, 1f)
    val glassR = j.optInt("glassRadiusDp", 36)
    val dock = j.optInt("dockSize", 4).coerceIn(3, 6)
    val dim = j.optDouble("wallpaperDim", 0.0).toFloat().coerceIn(0f, 0.8f)
    val blur = j.optInt("wallpaperBlur", 0).coerceIn(0, 100)
    val darkMenu = j.optBoolean("darkMenu", false)

    private fun col(s: String, d: Int): Int = try { Color.parseColor(s) } catch (e: Exception) { d }
}

fun loadCfg(c: Context): Cfg = try {
    val f = File(c.filesDir, "theme.json")
    Cfg(if (f.exists()) JSONObject(f.readText()) else JSONObject())
} catch (e: Exception) {
    Cfg(JSONObject())
}

class App(val pkg: String, val cls: String, val label: String, val icon: Bitmap) {
    val key: String get() = "$pkg/$cls"
}

/** Bentuk squircle ala iOS (superellipse). */
fun squirclePath(s: Float, n: Double): Path {
    val p = Path()
    val r = s / 2f
    val steps = 80
    for (i in 0..steps) {
        val t = i * 2 * Math.PI / steps
        val ct = Math.cos(t)
        val st = Math.sin(t)
        val x = r + r * Math.signum(ct) * Math.pow(Math.abs(ct), 2.0 / n)
        val y = r + r * Math.signum(st) * Math.pow(Math.abs(st), 2.0 / n)
        if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
    }
    p.close()
    return p
}

fun renderIcon(d: Drawable, px: Int, n: Double): Bitmap {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
        val o = px / 4
        d.background?.let { it.setBounds(-o, -o, px + o, px + o); it.draw(c) }
        d.foreground?.let { it.setBounds(-o, -o, px + o, px + o); it.draw(c) }
    } else {
        c.drawColor(Color.WHITE)
        val i = (px * 0.12f).toInt()
        d.setBounds(i, i, px - i, px - i)
        d.draw(c)
    }
    val m = Paint(Paint.ANTI_ALIAS_FLAG)
    m.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    c.drawPath(squirclePath(px.toFloat(), n), m)
    return b
}

class AppView(c: Context, private val app: App, private val cfg: Cfg, private val label: Boolean) : View(c) {
    private val d = resources.displayMetrics.density
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cfg.labelColor
        textSize = cfg.labelSp * d
        textAlign = Paint.Align.CENTER
        setShadowLayer(2.5f * d, 0f, d, 0x77000000)
    }
    private var t: CharSequence = ""

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        alpha = if (pressed) 0.6f else 1f
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        t = TextUtils.ellipsize(app.label, TextPaint(tp), w - 6 * d, TextUtils.TruncateAt.END)
    }

    override fun onDraw(cv: Canvas) {
        val sz = cfg.iconDp * d
        val lh = if (label) 18 * d else 0f
        val top = (height - sz - lh) / 2f
        cv.drawBitmap(app.icon, (width - sz) / 2f, top, ip)
        if (label) cv.drawText(t, 0, t.length, width / 2f, top + sz + 14 * d, tp)
    }
}

/** Panel "liquid glass": isi transparan + kilau atas + tepi specular. */
class Glass(c: Context, private val tint: Int, private val a: Float, private val sa: Float, private val r: Float) : LinearLayout(c) {
    private val f = Paint(Paint.ANTI_ALIAS_FLAG)
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init { setWillNotDraw(false) }

    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val rc = RectF(d, d, w - d, h - d)
        val rr = minOf(r, h / 2f)
        f.shader = null
        f.color = (tint and 0xFFFFFF) or ((a * 255).toInt() shl 24)
        cv.drawRoundRect(rc, rr, rr, f)
        f.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0x40FFFFFF, 0x05FFFFFF, 0x20FFFFFF), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(rc, rr, rr, f)
        val hi = ((sa * 255).toInt() shl 24) or 0xFFFFFF
        val lo = ((sa * 70).toInt() shl 24) or 0xFFFFFF
        s.strokeWidth = 1.3f * d
        s.shader = LinearGradient(0f, 0f, w, h, intArrayOf(hi, lo, lo, hi), floatArrayOf(0f, .35f, .65f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(rc, rr, rr, s)
    }
}

class Grid(c: Context, private val cols: Int, private val rows: Int) : ViewGroup(c) {
    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm)
        val h = MeasureSpec.getSize(hm)
        setMeasuredDimension(w, h)
        val cw = MeasureSpec.makeMeasureSpec(w / cols, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(h / rows, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (r - l) / cols
        val ch = (b - t) / rows
        for (i in 0 until childCount) {
            val x = (i % cols) * cw
            val y = (i / cols) * ch
            getChildAt(i).layout(x, y, x + cw, y + ch)
        }
    }
}

class Pager(c: Context) : HorizontalScrollView(c) {
    val row = LinearLayout(c)
    var onPos: (Float) -> Unit = {}
    private var s0 = 0

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = true
        addView(row, FrameLayout.LayoutParams(-2, -1))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        for (i in 0 until row.childCount) row.getChildAt(i).layoutParams = LinearLayout.LayoutParams(w, -1)
    }

    override fun fling(velocityX: Int) {}

    override fun onScrollChanged(l: Int, t: Int, ol: Int, ot: Int) {
        super.onScrollChanged(l, t, ol, ot)
        onPos(l.toFloat() / maxOf(width, 1))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) s0 = scrollX
        val r = super.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            val w = maxOf(width, 1)
            var p = Math.round(s0 / w.toFloat())
            val dx = scrollX - s0
            if (dx > w / 8) p++ else if (dx < -w / 8) p--
            goTo(p)
        }
        return r
    }

    fun goTo(p: Int) {
        val q = p.coerceIn(0, maxOf(row.childCount - 1, 0))
        smoothScrollTo(q * width, 0)
    }
}
