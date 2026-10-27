package com.sky.glasslauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private class AppEntry(val pkg: String, val label: String, val info: ResolveInfo)

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var appsRow: RecyclerView
    private lateinit var gamesRow: RecyclerView
    private lateinit var appsAdapter: TileAdapter
    private lateinit var gamesAdapter: TileAdapter
    private var focusedOnce = false

    private val pm: PackageManager get() = packageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE)

        appsRow = findViewById(R.id.appsRow)
        gamesRow = findViewById(R.id.gamesRow)
        appsRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        gamesRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        appsAdapter = TileAdapter(::onTileClick, ::onTileLongClick)
        gamesAdapter = TileAdapter(::onTileClick, ::onTileLongClick)
        appsRow.adapter = appsAdapter
        gamesRow.adapter = gamesAdapter

        val google = findViewById<TextView>(R.id.btnGoogle)
        val search = findViewById<View>(R.id.btnSearch)
        val settings = findViewById<View>(R.id.btnSettings)

        // Multi-colour "G" for the Google button
        google.post {
            google.paint.shader = LinearGradient(
                0f, 0f, google.paint.measureText("G"), 0f,
                intArrayOf(
                    Color.parseColor("#4285F4"),
                    Color.parseColor("#EA4335"),
                    Color.parseColor("#FBBC05"),
                    Color.parseColor("#34A853")
                ),
                null, Shader.TileMode.CLAMP
            )
            google.invalidate()
        }

        Ui.focusScale(google)
        Ui.focusScale(search)
        Ui.focusScale(settings)
        google.setOnClickListener { openGoogle() }
        search.setOnClickListener { openSearch() }
        settings.setOnClickListener { openSettings() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (!focusedOnce) {
            focusedOnce = true
            focusFirst()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while already on the launcher: jump back to the first tile.
        if (intent?.action == Intent.ACTION_MAIN) focusFirst()
    }

    @Deprecated("Launcher stays on the home screen")
    override fun onBackPressed() {
        // A launcher never closes.
    }

    // ---------------------------------------------------------------- data

    private fun loadApps(): List<AppEntry> {
        val seen = LinkedHashMap<String, AppEntry>()
        for (cat in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val p = ri.activityInfo.packageName
                if (p == packageName || seen.containsKey(p)) continue
                seen[p] = AppEntry(p, ri.loadLabel(pm).toString(), ri)
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private fun savedPins(): MutableList<String>? =
        prefs.getString("pins", null)?.split(",")?.filter { it.isNotBlank() }?.toMutableList()

    private fun savePins(list: List<String>) {
        prefs.edit().putString("pins", list.joinToString(",")).apply()
    }

    /** First run: IPTV, YouTube, Netflix, Play Store (+ Play Games if present). */
    private fun getPins(byPkg: Map<String, AppEntry>): MutableList<String> {
        savedPins()?.let { return it }
        val list = mutableListOf("iptv")
        val groups = listOf(
            listOf("com.google.android.youtube.tv", "com.google.android.youtube"),
            listOf("com.netflix.ninja", "com.netflix.mediaclient"),
            listOf("com.android.vending"),
            listOf("com.google.android.play.games")
        )
        for (g in groups) g.firstOrNull { byPkg.containsKey(it) }?.let { list.add(it) }
        savePins(list)
        return list
    }

    private fun iptvPackage(apps: List<AppEntry>): String? {
        val saved = prefs.getString("iptv_pkg", null)
        if (saved != null && apps.any { it.pkg == saved }) return saved
        val found = apps.firstOrNull {
            it.label.contains("iptv", ignoreCase = true) || it.pkg.contains("iptv", ignoreCase = true)
        }
        if (found != null) {
            prefs.edit().putString("iptv_pkg", found.pkg).apply()
            return found.pkg
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun isGame(ai: ApplicationInfo): Boolean {
        if ((ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ai.category == ApplicationInfo.CATEGORY_GAME
    }

    private fun tileFor(kind: Kind, e: AppEntry, label: String): Tile {
        val banner = try {
            e.info.activityInfo.loadBanner(pm)
        } catch (_: Exception) {
            null
        }
        return Tile(kind, e.pkg, label, banner ?: e.info.loadIcon(pm), banner != null)
    }

    private fun refresh() {
        val apps = loadApps()
        val byPkg = apps.associateBy { it.pkg }
        val pins = getPins(byPkg)
        val iptvPkg = iptvPackage(apps)

        // Apps row
        val appTiles = ArrayList<Tile>()
        for (key in pins) {
            if (key == "iptv") {
                val e = iptvPkg?.let { byPkg[it] }
                appTiles.add(
                    if (e != null) tileFor(Kind.IPTV, e, e.label)
                    else Tile(Kind.IPTV, null, getString(R.string.iptv), getDrawable(R.drawable.ic_tv), false)
                )
            } else {
                val e = byPkg[key] ?: continue
                appTiles.add(tileFor(Kind.APP, e, e.label))
            }
        }
        appTiles.add(Tile(Kind.ADD, null, getString(R.string.add_app), getDrawable(R.drawable.ic_add), false))
        appsAdapter.submit(appTiles)

        // Games row (lower screen): every installed game, then a Play Store shortcut
        val pinned = pins.toSet()
        val gameTiles = ArrayList<Tile>()
        for (e in apps) {
            if (e.pkg in pinned) continue
            if (isGame(e.info.activityInfo.applicationInfo)) gameTiles.add(tileFor(Kind.GAME, e, e.label))
        }
        gameTiles.add(
            Tile(Kind.MORE_GAMES, null, getString(R.string.more_games), getDrawable(R.drawable.ic_gamepad), false)
        )
        gamesAdapter.submit(gameTiles)
    }

    private fun focusFirst() {
        appsRow.post {
            appsRow.scrollToPosition(0)
            appsRow.post {
                val first = appsRow.findViewHolderForAdapterPosition(0)?.itemView
                if (first != null) first.requestFocus() else appsRow.requestFocus()
            }
        }
    }

    private fun focusAppsAt(pos: Int) {
        appsRow.post {
            appsRow.scrollToPosition(pos)
            appsRow.post { appsRow.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() }
        }
    }

    // ------------------------------------------------------------- actions

    private fun onTileClick(t: Tile) {
        when (t.kind) {
            Kind.APP, Kind.GAME -> launch(t.pkg)
            Kind.IPTV -> if (t.pkg != null) launch(t.pkg) else chooseIptv()
            Kind.ADD -> pickApp(getString(R.string.add_app_title)) { addPin(it) }
            Kind.MORE_GAMES -> launch("com.android.vending")
        }
    }

    private fun onTileLongClick(t: Tile) {
        when (t.kind) {
            Kind.APP -> AlertDialog.Builder(this, R.style.GlassDialog)
                .setTitle(t.label)
                .setMessage(R.string.remove_message)
                .setPositiveButton(R.string.remove) { _, _ -> removePin(t.pkg) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            Kind.IPTV -> chooseIptv()
            else -> Unit
        }
    }

    private fun chooseIptv() {
        pickApp(getString(R.string.choose_iptv)) { pkg ->
            prefs.edit().putString("iptv_pkg", pkg).apply()
            refresh()
        }
    }

    private fun addPin(pkg: String) {
        val pins = savedPins() ?: mutableListOf("iptv")
        if (pkg in pins) {
            Toast.makeText(this, R.string.already_added, Toast.LENGTH_SHORT).show()
            return
        }
        pins.add(pkg)
        savePins(pins)
        refresh()
        // new tile sits just before the "+" tile
        focusAppsAt(pins.indexOf(pkg).coerceAtLeast(0))
    }

    private fun removePin(pkg: String?) {
        if (pkg == null) return
        val pins = savedPins() ?: return
        val idx = pins.indexOf(pkg)
        pins.remove(pkg)
        savePins(pins)
        refresh()
        focusAppsAt(idx.coerceAtLeast(0))
    }

    private fun pickApp(title: String, onPick: (String) -> Unit) {
        val apps = loadApps()
        val adapter = object : ArrayAdapter<AppEntry>(this, 0, apps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_pick, parent, false)
                val e = getItem(position)!!
                v.findViewById<ImageView>(R.id.pickIcon).setImageDrawable(e.info.loadIcon(pm))
                v.findViewById<TextView>(R.id.pickLabel).text = e.label
                return v
            }
        }
        AlertDialog.Builder(this, R.style.GlassDialog)
            .setTitle(title)
            .setAdapter(adapter) { _, which -> onPick(apps[which].pkg) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launch(pkg: String?) {
        if (pkg == null) return
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (i == null) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
            return
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(i)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFirst(vararg intents: Intent?): Boolean {
        for (i in intents) {
            if (i == null) continue
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(i)
                return true
            } catch (_: Exception) {
                // try the next one
            }
        }
        return false
    }

    private fun openGoogle() {
        val ok = startFirst(
            Intent(Intent.ACTION_VOICE_COMMAND),
            pm.getLeanbackLaunchIntentForPackage("com.google.android.katniss"),
            pm.getLaunchIntentForPackage("com.google.android.googlequicksearchbox"),
            Intent(Intent.ACTION_ASSIST)
        )
        if (!ok) Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }

    private fun openSearch() {
        val ok = startFirst(
            Intent("android.search.action.GLOBAL_SEARCH"),
            pm.getLeanbackLaunchIntentForPackage("com.google.android.katniss"),
            Intent(Intent.ACTION_WEB_SEARCH)
        )
        if (!ok) Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        val ok = startFirst(Intent(Settings.ACTION_SETTINGS))
        if (!ok) Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }
}
