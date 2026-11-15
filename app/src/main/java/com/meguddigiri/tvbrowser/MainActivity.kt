package com.meguddigiri.tvbrowser

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.graphics.Typeface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.net.Uri

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private val bg = Color.rgb(8, 13, 24)
    private val panel = Color.rgb(20, 30, 48)
    private val accent = Color.rgb(102, 217, 239)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        if (savedInstanceState == null) web.loadUrl("https://www.google.com")
        else web.restoreState(savedInstanceState)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(22, 14, 22, 12)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "TV BROWSER"
            textSize = 20f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 18, 0)
        }
        header.addView(brand, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 58))
        address = EditText(this).apply {
            hint = "Search Google or enter website"
            textSize = 17f
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSelectAllOnFocus(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(18, 0, 18, 0)
            setBackgroundColor(panel)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        header.addView(address, LinearLayout.LayoutParams(0, 54, 1f))
        header.addView(makeButton("GO") { loadAddress() }, LinearLayout.LayoutParams(88, 54).apply { leftMargin = 10 })
        header.addView(makeButton("BACK") { if (web.canGoBack()) web.goBack() }, LinearLayout.LayoutParams(96, 54).apply { leftMargin = 8 })
        header.addView(makeButton("NEXT") { if (web.canGoForward()) web.goForward() }, LinearLayout.LayoutParams(96, 54).apply { leftMargin = 8 })
        header.addView(makeButton("RELOAD") { web.reload() }, LinearLayout.LayoutParams(112, 54).apply { leftMargin = 8 })
        root.addView(header)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(panel)
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4))
        web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mediaPlaybackRequiresUserGesture = true
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    address.setText(url ?: "")
                    status.text = "Ready"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@MainActivity.progress.progress = newProgress
                    this@MainActivity.progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    status.text = title ?: "TV Browser"
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            text = "Use remote arrows, OK and Back"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 0)
        }
        root.addView(status)
        setContentView(root)
        address.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                loadAddress(); true
            } else false
        }
        address.setOnFocusChangeListener { _, hasFocus ->
            address.setBackgroundColor(if (hasFocus) Color.rgb(35, 55, 78) else panel)
        }
        address.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_UP) {
                web.requestFocus(); true
            } else false
        }
        address.requestFocus()
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 55, 78))
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { action() }
        }

    private fun loadAddress() {
        var value = address.text.toString().trim()
        if (value.isEmpty()) return
        if (!value.contains("://")) {
            value = if (value.contains(".") && !value.contains(" "))
                "https://$value"
            else "https://www.google.com/search?q=" + Uri.encode(value)
        }
        web.loadUrl(value)
        web.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (web.canGoBack()) { web.goBack(); return true }
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            address.requestFocus(); address.selectAll(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        web.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }
}