package id.ios.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class Launcher : Activity() {
    private lateinit var cfg: Cfg
    private var pager: Pager? = null
    private var apps = listOf<App>()
    private var pop: PopupWindow? = null
    private val ex = Executors.newSingleThreadExecutor()
    private val dn: Float get() = resources.displayMetrics.density
    private val sp by lazy { getSharedPreferences("p", 0) }
    private val rcv = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { load() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val f = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_CHANGED)
        f.addDataScheme("package")
        registerReceiver(rcv, f)
        load()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(rcv)
        ex.shutdown()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        pop?.dismiss()
        pager?.goTo(0)
    }

    override fun onBackPressed() {
        if (pop?.isShowing == true) pop?.dismiss() else pager?.goTo(0)
    }

    private fun load() {
        cfg = loadCfg(this)
        val c = cfg
        val px = (c.iconDp * dn).toInt()
        ex.execute {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { App(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm).toString(), renderIcon(it.loadIcon(pm), px, c.n)) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { if (!isDestroyed) { apps = list; build() } }
        }
    }

    private fun build() {
        val c = cfg
        val d = dn
        val dockApps = savedDock().mapNotNull { k -> apps.find { it.key == k } }.take(c.dock)
        val rest = apps.filter { a -> dockApps.none { it.key == a.key } }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.argb((c.dim * 255).toInt(), 0, 0, 0))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        root.addView(col, -1, -1)

        val pg = Pager(this)
        pager = pg
        val pages: List<List<App>> = if (rest.isEmpty()) listOf(emptyList()) else rest.chunked(c.cols * c.rows)
        for (page in pages) {
            val g = Grid(this, c.cols, c.rows)
            for (a in page) g.addView(av(a, c.labels, false))
            g.setOnLongClickListener { homeMenu(); true }
            pg.row.addView(g, LinearLayout.LayoutParams(-1, -1))
        }

        val dots = LinearLayout(this)
        dots.gravity = Gravity.CENTER
        for (i in pages.indices) {
            val v = View(this)
            val o = GradientDrawable()
            o.shape = GradientDrawable.OVAL
            o.setColor(Color.WHITE)
            v.background = o
            val lp = LinearLayout.LayoutParams((7 * d).toInt(), (7 * d).toInt())
            lp.setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0)
            dots.addView(v, lp)
        }
        pg.onPos = { p ->
            val s = Math.round(p)
            for (i in 0 until dots.childCount) dots.getChildAt(i).alpha = if (i == s) 1f else 0.4f
        }
        pg.onPos(0f)

        val dock = Glass(this, c.glassTint, c.glassAlpha, c.strokeAlpha, c.glassR * d)
        dock.gravity = Gravity.CENTER
        dock.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
        for (a in dockApps) dock.addView(av(a, false, true), LinearLayout.LayoutParams(0, -1, 1f))

        col.addView(pg, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(dots, LinearLayout.LayoutParams(-1, (22 * d).toInt()))
        val dl = LinearLayout.LayoutParams(-1, ((c.iconDp + 26) * d).toInt())
        dl.setMargins((14 * d).toInt(), 0, (14 * d).toInt(), (10 * d).toInt())
        col.addView(dock, dl)

        col.setOnApplyWindowInsetsListener { v, i ->
            v.setPadding(0, i.systemWindowInsetTop, 0, i.systemWindowInsetBottom)
            i
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val lp = window.attributes
            lp.blurBehindRadius = c.blur
            window.attributes = lp
            if (c.blur > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        setContentView(root)
        col.requestApplyInsets()
    }

    private fun av(a: App, label: Boolean, inDock: Boolean): View {
        val v = AppView(this, a, cfg, label)
        v.setOnClickListener { launch(a) }
        v.setOnLongClickListener { appMenu(a, inDock); true }
        return v
    }

    private fun launch(a: App) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(a.pkg, a.cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka aplikasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savedDock(): List<String> =
        sp.getString("dock", null)?.split(";")?.filter { it.isNotEmpty() } ?: defDock()

    private fun defDock(): List<String> {
        val ints = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent("android.media.action.IMAGE_CAPTURE")
        )
        val out = ArrayList<String>()
        for (i in ints) {
            val p = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            val a = apps.firstOrNull { it.pkg == p }
            if (a != null && a.key !in out) out.add(a.key)
        }
        for (a in apps) {
            if (out.size >= cfg.dock) break
            if (a.key !in out) out.add(a.key)
        }
        return out.take(cfg.dock)
    }

    private fun toggleDock(a: App, inDock: Boolean) {
        val cur = savedDock().toMutableList()
        if (inDock) {
            cur.remove(a.key)
        } else {
            if (cur.size >= cfg.dock) cur.removeAt(cur.size - 1)
            cur.add(a.key)
        }
        sp.edit().putString("dock", cur.joinToString(";")).apply()
        build()
    }

    private fun appMenu(a: App, inDock: Boolean) {
        menu(listOf<Pair<String, () -> Unit>>(
            (if (inDock) "Lepas dari Dock" else "Taruh di Dock") to { toggleDock(a, inDock) },
            "Info Aplikasi" to {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
            },
            "Hapus Aplikasi" to {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
            }
        ))
    }

    private fun homeMenu() {
        menu(listOf<Pair<String, () -> Unit>>(
            "Impor Tema (.json)" to {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7
                )
            },
            "Reset Tema" to { File(filesDir, "theme.json").delete(); load() },
            "Wallpaper" to {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
            }
        ))
    }

    private fun menu(items: List<Pair<String, () -> Unit>>) {
        pop?.dismiss()
        val d = dn
        val dark = cfg.darkMenu
        val g = Glass(this, if (dark) 0xFF1C1C1E.toInt() else 0xFFF2F2F7.toInt(), 0.92f, 0.35f, 16 * d)
        g.orientation = LinearLayout.VERTICAL
        g.minimumWidth = (230 * d).toInt()
        val p = PopupWindow(g, -2, -2, true)
        p.setBackgroundDrawable(ColorDrawable(0))
        items.forEachIndexed { i, (t, fn) ->
            if (i > 0) {
                val line = View(this)
                line.setBackgroundColor(0x33808080)
                g.addView(line, -1, 1)
            }
            val tv = TextView(this)
            tv.text = t
            tv.textSize = 17f
            tv.setTextColor(if (dark) Color.WHITE else Color.BLACK)
            tv.setPadding((18 * d).toInt(), (13 * d).toInt(), (18 * d).toInt(), (13 * d).toInt())
            tv.setOnClickListener { p.dismiss(); fn() }
            g.addView(tv)
        }
        p.showAtLocation(window.decorView, Gravity.CENTER, 0, 0)
        pop = p
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc != 7 || res != RESULT_OK) return
        val u = data?.data ?: return
        try {
            val bytes = contentResolver.openInputStream(u)!!.use { it.readBytes() }
            if (bytes.size > 200_000) throw Exception("too big")
            val t = String(bytes)
            JSONObject(t)
            File(filesDir, "theme.json").writeText(t)
            load()
        } catch (e: Exception) {
            Toast.makeText(this, "Tema tidak valid", Toast.LENGTH_SHORT).show()
        }
    }
}
