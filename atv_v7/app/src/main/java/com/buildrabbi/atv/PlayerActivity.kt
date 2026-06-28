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
    private var currentIndex = 0
    private var sidePanelVisible = false
    private var selectedCategory = "All"
    private var adminWhatsApp = ""
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }

    private val ratios = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private val ratioLabels = listOf("Fit", "Fill", "Zoom")
    private var ratioIndex = 0

    private val DEFAULT_URL = "https://raw.githubusercontent.com/build-rabbi/A-TV/main/LiveTV/LiveTV.txt"

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
        setupClickListeners()
        showWelcome()
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

    private fun setupClickListeners() {
        playerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) toggleOverlay()
            true
        }
        btnMenu.setOnClickListener { toggleSidePanel() }
        btnProfile.setOnClickListener { showProfile() }
        btnRatio.setOnClickListener {
            ratioIndex = (ratioIndex + 1) % ratios.size
            playerView.resizeMode = ratios[ratioIndex]
            Toast.makeText(this, "Ratio: ${ratioLabels[ratioIndex]}", Toast.LENGTH_SHORT).show()
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
            hideSidePanel()
        }
    }

    private fun showWelcome() {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "") ?: ""
        adminWhatsApp = prefs.getString("admin_wa", "") ?: ""
        val isFirstLogin = !prefs.getBoolean("welcome_shown", false)

        if (!isFirstLogin) {
            welcomeScreen.visibility = View.GONE
            loadChannels()
            return
        }
        prefs.edit().putBoolean("welcome_shown", true).apply()
        welcomeScreen.visibility = View.VISIBLE

        // Logo animation
        val logoAnim = AnimationUtils.loadAnimation(this, R.anim.logo_anim)
        welcomeLogo.startAnimation(logoAnim)

        // Show welcome text after logo
        Handler(Looper.getMainLooper()).postDelayed({
            if (userName.isNotEmpty()) {
                welcomeText.text = "Welcome, $userName!"
            } else {
                welcomeText.text = "Welcome to A-TV"
            }
            welcomeText.visibility = View.VISIBLE
            val textAnim = AnimationUtils.loadAnimation(this, R.anim.welcome_fade_in)
            welcomeText.startAnimation(textAnim)

            // Hide welcome and start loading
            Handler(Looper.getMainLooper()).postDelayed({
                val fadeOut = AnimationUtils.loadAnimation(this, R.anim.welcome_fade_out)
                welcomeScreen.startAnimation(fadeOut)
                Handler(Looper.getMainLooper()).postDelayed({
                    welcomeScreen.visibility = View.GONE
                    loadChannels()
                }, 500)
            }, 1500)
        }, 800)
    }

    private fun getChannelUrl(): String {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        return prefs.getString("playlist_url", DEFAULT_URL) ?: DEFAULT_URL
    }

    private fun loadChannels() {
        loadingLayout.visibility = View.VISIBLE
        Thread {
            try {
                val conn = (URL(getChannelUrl()).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 20000
                    setRequestProperty("User-Agent", "Mozilla/5.0"); connect()
                }
                val content = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()

                val arr = JSONArray(content)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optString("url", "")
                    if (url.isNotEmpty()) {
                        allChannels.add(Channel(
                            name = obj.optString("name", "Channel"),
                            url = url,
                            category = obj.optString("category", obj.optString("type", "Other")),
                            img = obj.optString("img", "")
                        ))
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    loadingLayout.visibility = View.GONE
                    if (allChannels.isNotEmpty()) {
                        buildCategoryTabs()
                        applyFilter("")
                        playChannel(allChannels[0])
                    } else {
                        loadingLayout.visibility = View.VISIBLE
                        loadingText.text = "No channels found"
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    loadingLayout.visibility = View.VISIBLE
                    loadingText.text = "Error loading channels"
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
        for (i in 0 until categoryTabs.childCount) {
            (categoryTabs.getChildAt(i) as? TextView)?.let { styleTab(it, it.text == cat) }
        }
        searchInput.setText("")
        applyFilter("")
    }

    private fun styleTab(tab: TextView, active: Boolean) {
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ).apply { setMargins(3, 4, 3, 4) }
        tab.layoutParams = params
        if (active) {
            tab.setBackgroundColor(Color.parseColor("#00C2FF"))
            tab.setTextColor(Color.BLACK)
        } else {
            tab.setBackgroundColor(Color.parseColor("#1E2830"))
            tab.setTextColor(Color.parseColor("#AAAAAA"))
        }
    }

    private fun applyFilter(query: String) {
        filteredChannels.clear()
        filteredChannels.addAll(allChannels.filter { ch ->
            (selectedCategory == "All" || ch.category == selectedCategory) &&
            (query.isEmpty() || ch.name.contains(query, true))
        })
        updateChannelList()
    }

    private fun updateChannelList() {
        val names = filteredChannels.mapIndexed { i, ch -> "${i + 1}.  ${ch.name}" }
        channelListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    private fun playChannel(channel: Channel) {
        currentChName.text = channel.name
        currentChName.visibility = View.VISIBLE
        channelNameText.text = channel.name
        channelNameText.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ channelNameText.visibility = View.GONE }, 3000)

        playPauseIcon.setImageResource(R.drawable.ic_pause)
        playPauseLabel.text = "Pause"

        player?.release()
        val dsFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0")

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val url = channel.url
            val src = if (url.contains(".m3u8") || url.contains("tracks-v") || url.contains("index.m3u")) {
                HlsMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            } else {
                ProgressiveMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            exo.setMediaSource(src); exo.prepare(); exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (currentIndex < filteredChannels.size - 1) {
                        currentIndex++; playChannel(filteredChannels[currentIndex])
                    }
                }
            })
        }
    }

    fun showStatusPopup(icon: String, title: String, message: String) {
        runOnUiThread {
            player?.pause()
            popupIcon.text = icon
            popupTitle.text = title
            popupMessage.text = message
            popupWhatsapp.setOnClickListener {
                try {
                    val num = adminWhatsApp.replace("+", "").replace(" ", "")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
                } catch (e: Exception) {}
            }
            statusPopup.visibility = View.VISIBLE
        }
    }

    private fun showProfile() {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        val currentUrl = prefs.getString("playlist_url", null)
        val playlistLabel = if (currentUrl == null || currentUrl == DEFAULT_URL) "Default" else "Custom"
        val items = arrayOf("Change ISP / Playlist ($playlistLabel)", "Logout")
        AlertDialog.Builder(this)
            .setTitle("👤 ${prefs.getString("user_name","Profile")}")
            .setMessage("Key: ${prefs.getString("saved_key","Unknown")}\nExpiry: ${prefs.getString("expiry","No Limit")}\nPlaylist: $playlistLabel")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showPlaylistDialog(currentUrl)
                    1 -> { prefs.edit().remove("saved_key").remove("welcome_shown").apply(); finish() }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPlaylistDialog(currentUrl: String?) {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        val input = EditText(this).apply {
            hint = "Playlist URL (leave empty for default)"
            setText(if (currentUrl == DEFAULT_URL || currentUrl == null) "" else currentUrl)
            setPadding(40, 20, 40, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Change Playlist")
            .setMessage("Current: ${if (currentUrl == null || currentUrl == DEFAULT_URL) "Default" else currentUrl}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty()) {
                    prefs.edit().remove("playlist_url").apply()
                } else {
                    prefs.edit().putString("playlist_url", url).apply()
                }
                allChannels.clear()
                filteredChannels.clear()
                loadChannels()
            }
            .setNegativeButton("Use Default") { _, _ ->
                prefs.edit().remove("playlist_url").apply()
                allChannels.clear()
                filteredChannels.clear()
                loadChannels()
            }
            .show()
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
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!sidePanelVisible) { toggleOverlay(); true } else false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (!sidePanelVisible) { showSidePanel(); true } else false }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (sidePanelVisible) { hideSidePanel(); true } else false }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (sidePanelVisible) return false
                if (filteredChannels.isNotEmpty()) {
                    currentIndex = if (currentIndex > 0) currentIndex - 1 else filteredChannels.size - 1
                    playChannel(filteredChannels[currentIndex]); showOverlay()
                }; true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (sidePanelVisible) return false
                if (filteredChannels.isNotEmpty()) {
                    currentIndex = (currentIndex + 1) % filteredChannels.size
                    playChannel(filteredChannels[currentIndex]); showOverlay()
                }; true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { btnPlayPause.performClick(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStop() { super.onStop(); player?.release(); player = null }
}
