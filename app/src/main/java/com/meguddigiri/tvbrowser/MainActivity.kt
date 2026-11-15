package com.meguddigiri.tvbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.util.Locale

private const val HOME_URL = "https://www.google.com"
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private data class BrowserTab(val web: WebView, val privateMode: Boolean, var title: String = "New tab", var url: String = HOME_URL)

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var address: EditText
    private lateinit var browser: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private val tabs = mutableListOf<BrowserTab>()
    private val bookmarks = mutableListOf<Pair<String, String>>()
    private val history = mutableListOf<Pair<String, String>>()
    private var desktopMode = true
    private var textZoom = 100
    private var activeIndex = 0
    private val bg = Color.rgb(8, 13, 24)
    private val panel = Color.rgb(20, 30, 48)
    private val accent = Color.rgb(102, 217, 239)
    private val prefs by lazy { getSharedPreferences("tv_browser", Context.MODE_PRIVATE) }
    private val active: BrowserTab get() = tabs[activeIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        readBookmarks()
        buildUi()
        openTab(false)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 12, 23))
            setPadding(dp(24), dp(16), dp(24), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(20), 0)
        }
        brand.addView(TextView(this).apply {
            text = "NOVA"; textSize = 23f; setTextColor(Color.rgb(111, 220, 255))
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = 0.12f
        })
        brand.addView(TextView(this).apply {
            text = "TV BROWSER"; textSize = 9f; setTextColor(Color.rgb(139, 157, 183)); letterSpacing = 0.18f
        })
        header.addView(brand, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)))

        address = EditText(this).apply {
            hint = "Search Google or enter a website"
            textSize = 16f
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(139, 157, 183))
            setSelectAllOnFocus(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(18), 0, dp(18), 0)
            background = rounded(Color.rgb(22, 32, 50), dp(16), Color.rgb(48, 69, 96))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        header.addView(address, LinearLayout.LayoutParams(0, dp(54), 1f))
        header.addView(button("MIC") { voiceSearch() }, LinearLayout.LayoutParams(dp(76), dp(50)).apply { leftMargin = dp(10) })
        header.addView(button("GO  →", true) { loadAddress() }, LinearLayout.LayoutParams(dp(92), dp(50)).apply { leftMargin = dp(8) })
        root.addView(header)

        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(5))
        }
        root.addView(tabStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addAction(label: String, width: Int = 104, action: () -> Unit) {
            actions.addView(button(label, false, action), LinearLayout.LayoutParams(dp(width), dp(44)).apply {
                rightMargin = dp(7); topMargin = dp(12); bottomMargin = dp(12)
            })
        }
        addAction("＋  NEW TAB", 116) { openTab(false) }
        addAction("▣  TABS", 94) { tabsDialog() }
        addAction("☆  BOOKMARK", 128) { saveBookmark() }
        addAction("★  SAVED", 100) { bookmarksDialog() }
        addAction("↓  DOWNLOADS", 132) { downloadsDialog() }
        addAction("◷  HISTORY", 104) { historyDialog() }
        addAction("⚙  SETTINGS", 112) { settingsDialog() }
        addAction("⌕  FIND", 84) { findOnPage() }
        addAction("↗  SHARE", 90) { sharePage() }
        addAction("◉  PRIVATE", 112) { openTab(true) }
        addAction("⌂  HOME", 94) { homePage() }
        addAction("←", 64) { goBack() }
        addAction("→", 64) { if (active.web.canGoForward()) active.web.goForward() }
        addAction("↻  RELOAD", 104) { active.web.reload() }
        scroll.addView(actions)
        root.addView(scroll)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(94, 211, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(22, 32, 50))
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        browser = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 19, 32))
            background = rounded(Color.rgb(12, 19, 32), dp(18), Color.rgb(35, 51, 74))
            clipToOutline = true
        }
        val browserParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        browserParams.topMargin = dp(4)
        root.addView(browser, browserParams)
        status = TextView(this).apply {
            text = "READY  •  Use your remote to browse"
            textSize = 11f
            setTextColor(Color.rgb(139, 157, 183))
            letterSpacing = 0.06f
            setPadding(dp(4), dp(9), dp(4), 0)
        }
        root.addView(status)
        setContentView(root)

        address.setOnEditorActionListener { _, id, event ->
            if (id == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                loadAddress(); true
            } else false
        }
        address.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_UP) {
                active.web.requestFocus(); true
            } else false
        }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), stroke)
        }

    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        letterSpacing = 0.02f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setTextColor(if (primary) Color.rgb(5, 18, 31) else Color.rgb(224, 236, 250))
        background = rounded(
            if (primary) Color.rgb(105, 218, 255) else Color.rgb(22, 34, 53),
            dp(13),
            if (primary) Color.rgb(105, 218, 255) else Color.rgb(48, 68, 94)
        )
        elevation = dp(2).toFloat()
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(8), 0, dp(8), 0)
        setOnFocusChangeListener { view, focused ->
            view.scaleX = if (focused) 1.045f else 1f
            view.scaleY = if (focused) 1.045f else 1f
            view.elevation = if (focused) dp(8).toFloat() else dp(2).toFloat()
            background = rounded(
                if (primary) Color.rgb(105, 218, 255) else if (focused) Color.rgb(39, 61, 88) else Color.rgb(22, 34, 53),
                dp(13),
                if (focused) Color.rgb(105, 218, 255) else Color.rgb(48, 68, 94)
            )
        }
        setOnClickListener { action() }
    }

    private fun createWeb(privateMode: Boolean): WebView {
        val web = WebView(this)
        web.setBackgroundColor(Color.WHITE)
        web.settings.javaScriptEnabled = true
        web.settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(this)
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.settings.domStorageEnabled = !privateMode
        web.settings.loadsImagesAutomatically = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.setSupportZoom(true)
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        web.settings.useWideViewPort = desktopMode
        web.settings.loadWithOverviewMode = desktopMode
        web.settings.textZoom = textZoom
        web.settings.saveFormData = !privateMode
        web.settings.cacheMode = if (privateMode) android.webkit.WebSettings.LOAD_NO_CACHE else android.webkit.WebSettings.LOAD_DEFAULT
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            override fun onPageFinished(view: WebView?, url: String?) {
                val tab = tabs.firstOrNull { it.web === web }
                if (tab != null) {
                    tab.url = url ?: tab.url
                    if (!privateMode && !tab.url.startsWith("data:") && tab.url != "about:blank") addHistory(tab.title, tab.url)
                    address.setText(if (tab.url.startsWith("data:")) "" else tab.url)
                }
                status.text = if (privateMode) "Private tab • app history not saved" else "Ready"
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, value: Int) {
                progress.progress = value
                progress.visibility = if (value in 1..99) View.VISIBLE else View.GONE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                tabs.firstOrNull { it.web === web }?.title = title ?: "Web page"
            }
        }
        web.setDownloadListener(DownloadListener { url, agent, disposition, mime, _ ->
            download(url, agent, disposition, mime)
        })
        return web
    }

    private fun openTab(privateMode: Boolean) {
        val web = createWeb(privateMode)
        tabs.add(BrowserTab(web, privateMode))
        activeIndex = tabs.lastIndex
        showActive()
        homePage()
        status.text = if (privateMode) "Private tab opened" else "New tab opened"
    }

    private fun showActive() {
        if (tabs.isEmpty()) return
        renderTabStrip()
        browser.removeAllViews()
        browser.addView(active.web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        address.setText(if (active.url.startsWith("data:") || active.url == HOME_URL) "" else active.url)
        status.text = "Tab ${activeIndex + 1} of ${tabs.size}" + if (active.privateMode) " • Private" else ""
    }

    private fun renderTabStrip() {
        tabStrip.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            val label = (if (index == activeIndex) "● " else "") + (tab.title.take(18).ifBlank { "New Tab" }) + "  ×"
            val chip = button(label, index == activeIndex) { closeTab(index) }
            chip.setOnLongClickListener {
                activeIndex = index
                showActive()
                true
            }
            tabStrip.addView(chip, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                rightMargin = dp(6)
            })
        }
        tabStrip.addView(button("+", false) { openTab(false) }, LinearLayout.LayoutParams(dp(48), dp(38)))
    }

    private fun loadAddress() {
        val raw = address.text.toString().trim()
        if (raw.isEmpty()) return
        val target = if (raw.contains("://")) raw else if (raw.contains(".") && !raw.contains(" ")) "https://$raw"
            else "https://www.google.com/search?q=" + Uri.encode(raw)
        active.url = target
        active.web.loadUrl(target)
        active.web.requestFocus()
    }

    private fun homePage() {
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
            *{box-sizing:border-box}body{margin:0;min-height:100vh;background:#202124;color:#e8eaed;font-family:Arial,sans-serif;text-align:center}
            .top{height:54px;display:flex;justify-content:flex-end;align-items:center;gap:24px;padding:0 34px;color:#e8eaed;font-size:14px}
            .main{padding-top:9vh;margin:auto;width:90%;max-width:1100px}.logo{font-size:76px;letter-spacing:-5px;font-weight:500;margin:35px 0 30px;color:#f1f3f4}
            .search{height:58px;max-width:700px;margin:0 auto 44px;background:#f1f3f4;border-radius:32px;display:flex;align-items:center;padding:0 24px;gap:15px}
            .search input{border:0;outline:0;background:transparent;color:#202124;font-size:20px;width:100%}.search button{border:0;background:transparent;font-size:22px;color:#4285f4}
            .grid{display:flex;justify-content:center;gap:20px;flex-wrap:wrap}.tile{width:150px;min-height:126px;padding:18px 8px;color:#e8eaed;text-decoration:none;border-radius:14px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;font-size:16px}
            .tile:focus,.tile:hover{outline:2px solid #8ab4f8;background:#303134}.ico{width:54px;height:54px;border-radius:50%;background:#303134;display:grid;place-items:center;font-size:26px;font-weight:bold;color:#8ab4f8}
            .hint{margin-top:60px;color:#9aa0a6;font-size:14px}
            </style></head><body><div class="top"><span>Gmail</span><span>Images</span><span>▦</span></div>
            <main class="main"><div class="logo">Google</div>
            <form class="search" action="https://www.google.com/search"><span>⌕</span><input name="q" placeholder="Search Google or type a URL"><button type="submit">🎙</button></form>
            <section class="grid">
            <a class="tile" href="https://www.youtube.com"><span class="ico">▶</span>YouTube</a>
            <a class="tile" href="https://mail.google.com"><span class="ico">M</span>Gmail</a>
            <a class="tile" href="https://www.wikipedia.org"><span class="ico">W</span>Wikipedia</a>
            <a class="tile" href="https://www.instagram.com"><span class="ico">◎</span>Instagram</a>
            <a class="tile" href="https://www.facebook.com"><span class="ico">f</span>Facebook</a>
            <a class="tile" href="https://www.google.com/maps"><span class="ico">⌖</span>Maps</a>
            </section><p class="hint">Search the web or open one of your favourite websites</p></main></body></html>
        """.trimIndent()
        active.url = "data:text/html,home"
        active.title = "Home"
        address.setText("")
        active.web.loadDataWithBaseURL("https://home.tvbrowser.local/", html, "text/html", "UTF-8", null)
    }

    private fun goBack() {
        if (active.web.canGoBack()) active.web.goBack()
        else if (tabs.size > 1) closeTab(activeIndex)
        else homePage()
    }

    private fun closeTab(index: Int) {
        if (tabs.size == 1) { active.web.clearHistory(); homePage(); return }
        val old = tabs.removeAt(index)
        old.web.stopLoading(); old.web.loadUrl("about:blank"); old.web.destroy()
        activeIndex = (index - 1).coerceAtLeast(0)
        showActive()
    }

    private fun tabsDialog() {
        val names = tabs.mapIndexed { i, t -> (if (i == activeIndex) "✓ " else "") +
            (if (t.privateMode) "Private • " else "") + t.title }.toMutableList()
        names.add("＋ Open new tab")
        AlertDialog.Builder(this).setTitle("Tabs (${tabs.size})").setItems(names.toTypedArray()) { _, which ->
            if (which == tabs.size) openTab(false) else { activeIndex = which; showActive() }
        }.setNegativeButton("Close current") { _, _ -> closeTab(activeIndex) }.show()
    }

    private fun readBookmarks() {
        bookmarks.clear()
        try {
            val arr = JSONArray(prefs.getString("bookmarks", "[]"))
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                bookmarks.add(item.optString("title") to item.optString("url"))
            }
        } catch (_: Exception) { }
    }

    private fun persistBookmarks() {
        val arr = JSONArray()
        bookmarks.forEach { (title, url) -> arr.put(org.json.JSONObject().put("title", title).put("url", url)) }
        prefs.edit().putString("bookmarks", arr.toString()).apply()
    }

    private fun saveBookmark() {
        val url = active.url
        if (url.startsWith("data:")) { Toast.makeText(this, "Open a website first", Toast.LENGTH_SHORT).show(); return }
        if (bookmarks.any { it.second == url }) { Toast.makeText(this, "Already saved", Toast.LENGTH_SHORT).show(); return }
        bookmarks.add((active.title.ifBlank { url }) to url)
        persistBookmarks()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun bookmarksDialog() {
        if (bookmarks.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Bookmarks").setMessage("No saved bookmarks yet.").setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Saved bookmarks").setItems(bookmarks.map { it.first }.toTypedArray()) { _, which ->
            active.url = bookmarks[which].second
            active.web.loadUrl(active.url)
        }.setNeutralButton("Delete all") { _, _ -> bookmarks.clear(); persistBookmarks() }
            .setNegativeButton("Close", null).show()
    }

    private fun download(url: String, agent: String?, disposition: String?, mime: String?) {
        try {
            val name = android.webkit.URLUtil.guessFileName(url, disposition, mime)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(name); setDescription("TV Browser download")
                if (mime != null) setMimeType(mime)
                if (!agent.isNullOrBlank()) addRequestHeader("User-Agent", agent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, name)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Download started: $name", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, "Download could not start", Toast.LENGTH_LONG).show() }
    }

    private fun downloadsInfo() {
        val folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "App downloads folder"
        AlertDialog.Builder(this).setTitle("Downloads").setMessage("Browser downloads are saved here:\\n$folder\\nAndroid shows a notification when a download finishes.")
            .setPositiveButton("OK", null).show()
    }

    private fun addHistory(title: String, url: String) {
        if (url.isBlank()) return
        history.removeAll { it.second == url }
        history.add(0, (title.ifBlank { url }) to url)
        if (history.size > 300) history.removeAt(history.lastIndex)
        val arr = JSONArray()
        history.forEach { (t, u) -> arr.put(org.json.JSONObject().put("title", t).put("url", u)) }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        history.clear()
        try {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                history.add(item.optString("title") to item.optString("url"))
            }
        } catch (_: Exception) { }
    }

    private fun historyDialog() {
        loadHistory()
        if (history.isEmpty()) {
            AlertDialog.Builder(this).setTitle("History").setMessage("No browsing history yet. Private tabs are not added.").setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Recent history").setItems(history.take(80).map { it.first }.toTypedArray()) { _, which ->
            active.url = history[which].second
            active.web.loadUrl(active.url)
        }.setNeutralButton("Clear history") { _, _ ->
            history.clear()
            prefs.edit().remove("history").apply()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Close", null).show()
    }

    private fun findOnPage() {
        val field = EditText(this).apply { hint = "Find text on this page"; singleLine = true }
        AlertDialog.Builder(this).setTitle("Find in page").setView(field)
            .setPositiveButton("Find next") { _, _ ->
                val q = field.text.toString()
                if (q.isNotBlank()) active.web.findAllAsync(q)
            }.setNeutralButton("Clear highlights") { _, _ -> active.web.clearMatches() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun sharePage() {
        val url = active.web.url ?: active.url
        if (url.isBlank() || url.startsWith("data:")) {
            Toast.makeText(this, "Open a website first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "Share page"))
    }

    private fun downloadsDialog() {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
        ))
        val lines = mutableListOf<String>()
        cursor?.use {
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonCol = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            while (it.moveToNext()) {
                val state = when (it.getInt(statusCol)) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    DownloadManager.STATUS_RUNNING -> "Downloading"
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    else -> "Waiting"
                }
                lines.add(it.getString(titleCol) + " — " + state + " (#" + it.getLong(idCol) + ")")
            }
        }
        val message = if (lines.isEmpty()) "No downloads found. Downloads started in this app appear here." else lines.take(25).joinToString("\n")
        AlertDialog.Builder(this).setTitle("Downloads").setMessage(message)
            .setPositiveButton("Open downloads folder") { _, _ ->
                val folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (folder != null) {
                    val uri = Uri.parse(folder.absolutePath)
                    try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    catch (_: Exception) { downloadsInfo() }
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun settingsDialog() {
        val options = arrayOf(
            "Desktop site: " + if (desktopMode) "ON" else "OFF",
            "Increase text size",
            "Decrease text size",
            "Clear cache and cookies",
            "Clear browsing history"
        )
        AlertDialog.Builder(this).setTitle("NOVA Settings").setItems(options) { _, which ->
            when (which) {
                0 -> {
                    desktopMode = !desktopMode
                    tabs.forEach { tab ->
                        tab.web.settings.userAgentString = if (desktopMode) {
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        } else null
                        tab.web.settings.useWideViewPort = desktopMode
                        tab.web.settings.loadWithOverviewMode = desktopMode
                        tab.web.reload()
                    }
                    Toast.makeText(this, "Desktop site " + if (desktopMode) "enabled" else "disabled", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    textZoom = (textZoom + 10).coerceAtMost(200)
                    tabs.forEach { it.web.settings.textZoom = textZoom }
                    Toast.makeText(this, "Text size: " + textZoom + "%", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    textZoom = (textZoom - 10).coerceAtLeast(70)
                    tabs.forEach { it.web.settings.textZoom = textZoom }
                    Toast.makeText(this, "Text size: " + textZoom + "%", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    tabs.forEach { it.web.clearCache(true); it.web.clearFormData() }
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    Toast.makeText(this, "Cache, cookies and saved form data cleared", Toast.LENGTH_LONG).show()
                }
                4 -> {
                    history.clear()
                    prefs.edit().remove("history").apply()
                    Toast.makeText(this, "Browsing history cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun voiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your search")
            }
            startActivityForResult(intent, 701)
        } catch (_: Exception) { Toast.makeText(this, "Voice recognition is not available on this TV", Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 701 && resultCode == RESULT_OK) {
            val words = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!words.isNullOrBlank()) { address.setText(words); loadAddress() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { goBack(); return true }
        if (keyCode == KeyEvent.KEYCODE_MENU) { tabsDialog(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        tabs.forEach { it.web.stopLoading(); it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }
}