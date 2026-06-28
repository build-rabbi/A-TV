package com.buildrabbi.atv

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String, val category: String = "Other", val img: String = "")

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var channelListView: ListView
    private lateinit var searchInput: EditText
    private lateinit var sidePanel: View
    private lateinit var overlayPanel: View
    private lateinit var loadingLayout: View
    private lateinit var loadingText: TextView
    private lateinit var channelNameText: TextView
    private lateinit var currentChName: TextView
    private lateinit var playPauseIcon: ImageView
    private lateinit var playPauseLabel: TextView
    private lateinit var ratioLabel: TextView
    private lateinit var btnMenu: View
    private lateinit var btnProfile: View
    private lateinit var btnRatio: View
    private lateinit var btnPrev: View
    private lateinit var btnNext: View
    private lateinit var btnPlayPause: View
    private lateinit var categoryTabs: LinearLayout
    private lateinit var welcomeScreen: FrameLayout
    private lateinit var welcomeLogo: ImageView
    private lateinit var welcomeText: TextView
    private lateinit var statusPopup: FrameLayout
    private lateinit var popupIcon: TextView
    private lateinit var popupTitle: TextView
    private lateinit var popupMessage: TextView
    private lateinit var popupWhatsapp: TextView

    private val allChannels = mutableListOf<Channel>()
    private val filteredChannels = mutableListOf<Channel>()
    private var channelAdapter: ChannelAdapter? = null
    private var currentIndex = 0
    private var sidePanelVisible = false
    private var selectedCategory = "All"
    private var adminWhatsApp = ""
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }

    private val ratioModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    )
    private val ratioNames = listOf("Fit", "Fill", "Zoom", "Width", "Height")
    private var ratioIndex = 0

    companion object {
        const val DEFAULT_URL = "https://raw.githubusercontent.com/build-rabbi/A-TV/main/LiveTV/LiveTV.txt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        setContentView(R.layout.activity_player)
        initViews()
        setupListeners()
        showWelcomeOrLoad()
    }

    private fun initViews() {
        playerView      = findViewById(R.id.playerView)
        channelListView = findViewById(R.id.channelList)
        searchInput     = findViewById(R.id.searchInput)
        sidePanel       = findViewById(R.id.sidePanel)
        overlayPanel    = findViewById(R.id.overlayPanel)
        loadingLayout   = findViewById(R.id.loadingLayout)
        loadingText     = findViewById(R.id.loadingText)
        channelNameText = findViewById(R.id.channelName)
        currentChName   = findViewById(R.id.currentChName)
        playPauseIcon   = findViewById(R.id.playPauseIcon)
        playPauseLabel  = findViewById(R.id.playPauseLabel)
        ratioLabel      = findViewById(R.id.ratioLabel)
        btnMenu         = findViewById(R.id.btnMenu)
        btnProfile      = findViewById(R.id.btnProfile)
        btnRatio        = findViewById(R.id.btnRatio)
        btnPrev         = findViewById(R.id.btnPrev)
        btnNext         = findViewById(R.id.btnNext)
        btnPlayPause    = findViewById(R.id.btnPlayPause)
        categoryTabs    = findViewById(R.id.categoryTabs)
        welcomeScreen   = findViewById(R.id.welcomeScreen)
        welcomeLogo     = findViewById(R.id.welcomeLogo)
        welcomeText     = findViewById(R.id.welcomeText)
        statusPopup     = findViewById(R.id.statusPopup)
        popupIcon       = findViewById(R.id.popupIcon)
        popupTitle      = findViewById(R.id.popupTitle)
        popupMessage    = findViewById(R.id.popupMessage)
        popupWhatsapp   = findViewById(R.id.popupWhatsapp)
    }

    private fun setupListeners() {
        playerView.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP) toggleOverlay(); true }
        btnMenu.setOnClickListener { toggleSidePanel() }
        btnProfile.setOnClickListener { showProfile() }

        btnRatio.setOnClickListener {
            ratioIndex = (ratioIndex + 1) % ratioModes.size
            playerView.resizeMode = ratioModes[ratioIndex]
            ratioLabel.text = ratioNames[ratioIndex]
            resetHideTimer()
        }

        btnPlayPause.setOnClickListener {
            player?.let {
                if (it.isPlaying) {
                    it.pause()
                    playPauseIcon.setImageResource(R.drawable.ic_play)
                    playPauseLabel.text = "Play"
                } else {
                    it.play()
                    playPauseIcon.setImageResource(R.drawable.ic_pause)
                    playPauseLabel.text = "Pause"
                }
            }
            resetHideTimer()
        }

        btnPrev.setOnClickListener {
            if (filteredChannels.isNotEmpty()) {
                currentIndex = if (currentIndex > 0) currentIndex - 1 else filteredChannels.size - 1
                playChannel(filteredChannels[currentIndex]); resetHideTimer()
            }
        }
        btnNext.setOnClickListener {
            if (filteredChannels.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % filteredChannels.size
                playChannel(filteredChannels[currentIndex]); resetHideTimer()
            }
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { applyFilter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        channelListView.setOnItemClickListener { _, _, pos, _ ->
            currentIndex = pos
            playChannel(filteredChannels[pos])
            channelAdapter?.setActiveIndex(pos)
            hideSidePanel()
        }
    }

    private fun showWelcomeOrLoad() {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        adminWhatsApp = prefs.getString("admin_wa", "") ?: ""
        val shown = prefs.getBoolean("welcome_shown", false)

        if (!shown) {
            prefs.edit().putBoolean("welcome_shown", true).apply()
            val userName = prefs.getString("user_name", "") ?: ""
            welcomeScreen.visibility = View.VISIBLE
            welcomeLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_anim))
            Handler(Looper.getMainLooper()).postDelayed({
                welcomeText.text = if (userName.isNotEmpty()) "Welcome, $userName!" else "Welcome to A-TV"
                welcomeText.visibility = View.VISIBLE
                welcomeText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.welcome_fade_in))
                Handler(Looper.getMainLooper()).postDelayed({
                    welcomeScreen.startAnimation(AnimationUtils.loadAnimation(this, R.anim.welcome_fade_out))
                    Handler(Looper.getMainLooper()).postDelayed({
                        welcomeScreen.visibility = View.GONE
                        loadChannels()
                    }, 500)
                }, 1500)
            }, 900)
        } else {
            welcomeScreen.visibility = View.GONE
            loadChannels()
        }
    }

    private fun getPlaylistUrl(): String {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        return prefs.getString("playlist_url", DEFAULT_URL) ?: DEFAULT_URL
    }

    private fun loadChannels() {
        loadingLayout.visibility = View.VISIBLE
        allChannels.clear()
        filteredChannels.clear()

        Thread {
            try {
                val conn = (URL(getPlaylistUrl()).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 20000
                    setRequestProperty("User-Agent", "Mozilla/5.0"); connect()
                }
                val content = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()

                val arr = JSONArray(content)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optString("url", "")
                    if (url.isNotEmpty()) allChannels.add(Channel(
                        name = obj.optString("name", "Channel"),
                        url = url,
                        category = obj.optString("category", obj.optString("type", "Other")),
                        img = obj.optString("img", "")
                    ))
                }

                Handler(Looper.getMainLooper()).post {
                    loadingLayout.visibility = View.GONE
                    if (allChannels.isNotEmpty()) {
                        buildCategoryTabs()
                        applyFilter("")
                        playChannel(allChannels[0])
                    } else {
                        loadingText.text = "No channels found"
                        loadingLayout.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    loadingText.text = "Error loading channels"
                    loadingLayout.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun buildCategoryTabs() {
        val cats = listOf("All") + allChannels.map { it.category }.distinct().sorted()
        categoryTabs.removeAllViews()
        for (cat in cats) {
            val tab = TextView(this).apply {
                text = cat; textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(20, 6, 20, 6)
                setOnClickListener { selectCategory(cat) }
            }
            styleTab(tab, cat == selectedCategory)
            categoryTabs.addView(tab)
        }
    }

    private fun selectCategory(cat: String) {
        selectedCategory = cat
        for (i in 0 until categoryTabs.childCount)
            (categoryTabs.getChildAt(i) as? TextView)?.let { styleTab(it, it.text == cat) }
        searchInput.setText("")
        applyFilter("")
    }

    private fun styleTab(tab: TextView, active: Boolean) {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ).apply { setMargins(3, 4, 3, 4) }
        tab.layoutParams = p
        if (active) { tab.setBackgroundColor(Color.parseColor("#00C2FF")); tab.setTextColor(Color.BLACK) }
        else { tab.setBackgroundColor(Color.parseColor("#1E2830")); tab.setTextColor(Color.parseColor("#AAAAAA")) }
    }

    private fun applyFilter(query: String) {
        filteredChannels.clear()
        filteredChannels.addAll(allChannels.filter { ch ->
            (selectedCategory == "All" || ch.category == selectedCategory) &&
            (query.isEmpty() || ch.name.contains(query, true))
        })
        channelAdapter = ChannelAdapter(this, filteredChannels, currentIndex)
        channelListView.adapter = channelAdapter
    }

    private fun updateChannelList() {
        channelAdapter = ChannelAdapter(this, filteredChannels, currentIndex)
        channelListView.adapter = channelAdapter
    }

    private fun playChannel(ch: Channel) {
        currentChName.text = ch.name
        channelNameText.text = ch.name
        channelNameText.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ channelNameText.visibility = View.GONE }, 3000)
        playPauseIcon.setImageResource(R.drawable.ic_pause)
        playPauseLabel.text = "Pause"

        player?.release()
        val dsf = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0")

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val src = if (ch.url.contains(".m3u8") || ch.url.contains("tracks-v") || ch.url.contains("index.m3u"))
                HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(ch.url)))
            else ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(ch.url)))
            exo.setMediaSource(src); exo.prepare(); exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (currentIndex < filteredChannels.size - 1) { currentIndex++; playChannel(filteredChannels[currentIndex]) }
                }
            })
        }
        channelAdapter?.setActiveIndex(currentIndex)
    }

    private fun showProfile() {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        val currentUrl = prefs.getString("playlist_url", null)
        val ispLabel = if (currentUrl == null || currentUrl == DEFAULT_URL) "Default" else "Custom"

        val items = arrayOf(
            "📡  Change ISP / Playlist  ($ispLabel)",
            "📞  Contact Admin",
            "🚪  Logout"
        )

        AlertDialog.Builder(this)
            .setTitle("👤  ${prefs.getString("user_name", "Profile")}")
            .setMessage("Key: ${prefs.getString("saved_key","—")}\nExpiry: ${prefs.getString("expiry","No Limit")}\nISP: $ispLabel")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showPlaylistDialog(currentUrl)
                    1 -> contactAdmin()
                    2 -> { prefs.edit().remove("saved_key").remove("welcome_shown").apply(); finish() }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPlaylistDialog(currentUrl: String?) {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        val isDefault = currentUrl == null || currentUrl == DEFAULT_URL
        val input = EditText(this).apply {
            hint = "Paste playlist URL here..."
            setText(if (isDefault) "" else currentUrl)
            setPadding(40, 24, 40, 24)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#5A6A7A"))
            setBackgroundColor(Color.parseColor("#141A22"))
        }

        AlertDialog.Builder(this)
            .setTitle("📡  Change ISP / Playlist")
            .setMessage("Current: ${ if (isDefault) "Default playlist" else currentUrl }\n\nPaste a new playlist URL or leave empty to use default:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty() || url == DEFAULT_URL) {
                    prefs.edit().remove("playlist_url").apply()
                } else {
                    prefs.edit().putString("playlist_url", url).apply()
                }
                loadChannels()
            }
            .setNeutralButton("Use Default") { _, _ ->
                prefs.edit().remove("playlist_url").apply()
                loadChannels()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun contactAdmin() {
        val wa = adminWhatsApp.replace("+", "").replace(" ", "")
        if (wa.isEmpty()) { Toast.makeText(this, "Admin contact not set", Toast.LENGTH_SHORT).show(); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa"))) }
        catch (e: Exception) { Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show() }
    }

    fun showStatusPopup(icon: String, title: String, message: String) {
        runOnUiThread {
            player?.pause()
            popupIcon.text = icon
            popupTitle.text = title
            popupMessage.text = message
            popupWhatsapp.setOnClickListener { contactAdmin() }
            statusPopup.visibility = View.VISIBLE
        }
    }

    private fun toggleOverlay() { if (overlayPanel.visibility == View.VISIBLE) hideOverlay() else showOverlay() }
    private fun showOverlay() { overlayPanel.visibility = View.VISIBLE; resetHideTimer() }
    private fun hideOverlay() { if (!sidePanelVisible) overlayPanel.visibility = View.GONE }
    private fun resetHideTimer() { hideHandler.removeCallbacks(hideRunnable); hideHandler.postDelayed(hideRunnable, 4000) }
    private fun toggleSidePanel() { if (sidePanelVisible) hideSidePanel() else showSidePanel() }
    private fun showSidePanel() { sidePanel.visibility = View.VISIBLE; sidePanelVisible = true; showOverlay(); hideHandler.removeCallbacks(hideRunnable) }
    private fun hideSidePanel() { sidePanel.visibility = View.GONE; sidePanelVisible = false; hideHandler.postDelayed(hideRunnable, 3000) }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (statusPopup.visibility == View.VISIBLE) return true
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { if (sidePanelVisible) { hideSidePanel(); true } else { finish(); true } }
            KeyEvent.KEYCODE_MENU -> { toggleSidePanel(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (!sidePanelVisible) { toggleOverlay(); true } else false }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (!sidePanelVisible) { showSidePanel(); true } else false }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (sidePanelVisible) { hideSidePanel(); true } else false }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (sidePanelVisible) return false
                if (filteredChannels.isNotEmpty()) { currentIndex = if (currentIndex > 0) currentIndex - 1 else filteredChannels.size - 1; playChannel(filteredChannels[currentIndex]); showOverlay() }; true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (sidePanelVisible) return false
                if (filteredChannels.isNotEmpty()) { currentIndex = (currentIndex + 1) % filteredChannels.size; playChannel(filteredChannels[currentIndex]); showOverlay() }; true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { btnPlayPause.performClick(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStop() { super.onStop(); player?.release(); player = null }
}
