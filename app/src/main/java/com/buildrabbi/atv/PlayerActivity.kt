package com.buildrabbi.atv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String, val group: String = "", val img: String = "")

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var channelListView: ListView
    private lateinit var searchInput: EditText
    private lateinit var sidePanel: View
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var loadingText: TextView
    private lateinit var channelNameText: TextView
    private lateinit var currentChName: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnProfile: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private val allChannels = mutableListOf<Channel>()
    private val filteredChannels = mutableListOf<Channel>()
    private var currentIndex = 0
    private var sidePanelVisible = false
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideBars() }

    companion object {
        private const val CHANNEL_URL = "https://raw.githubusercontent.com/build-rabbi/A-TV/main/LiveTV/LiveTV.txt"
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

        playerView      = findViewById(R.id.playerView)
        channelListView = findViewById(R.id.channelList)
        searchInput     = findViewById(R.id.searchInput)
        sidePanel       = findViewById(R.id.sidePanel)
        topBar          = findViewById(R.id.topBar)
        bottomBar       = findViewById(R.id.bottomBar)
        loadingText     = findViewById(R.id.loadingText)
        channelNameText = findViewById(R.id.channelName)
        currentChName   = findViewById(R.id.currentChName)
        btnMenu         = findViewById(R.id.btnMenu)
        btnProfile      = findViewById(R.id.btnProfile)
        btnSearch       = findViewById(R.id.btnSearch)
        btnPrev         = findViewById(R.id.btnPrev)
        btnNext         = findViewById(R.id.btnNext)

        playerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (topBar.visibility == View.VISIBLE) hideBars() else showBars()
            }
            true
        }

        btnMenu.setOnClickListener { toggleSidePanel() }
        btnSearch.setOnClickListener { toggleSidePanel() }
        btnProfile.setOnClickListener { showProfile() }

        btnPrev.setOnClickListener {
            if (filteredChannels.isNotEmpty()) {
                currentIndex = if (currentIndex > 0) currentIndex - 1 else filteredChannels.size - 1
                playChannel(filteredChannels[currentIndex])
            }
        }
        btnNext.setOnClickListener {
            if (filteredChannels.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % filteredChannels.size
                playChannel(filteredChannels[currentIndex])
            }
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { filterChannels(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        channelListView.setOnItemClickListener { _, _, pos, _ ->
            currentIndex = pos
            playChannel(filteredChannels[pos])
            hideSidePanel()
        }

        loadChannels()
    }

    private fun showBars() {
        topBar.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    private fun hideBars() {
        if (!sidePanelVisible) {
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
        }
    }

    private fun loadChannels() {
        loadingText.visibility = View.VISIBLE
        loadingText.text = "Loading channels..."

        Thread {
            try {
                val conn = (URL(CHANNEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connect()
                }
                val content = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()

                val jsonArray = JSONArray(content)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "Channel")
                    val url = obj.optString("url", "")
                    val group = obj.optString("type", "")
                    val img = obj.optString("img", "")
                    if (url.isNotEmpty()) {
                        allChannels.add(Channel(name, url, group, img))
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    loadingText.visibility = View.GONE
                    if (allChannels.isNotEmpty()) {
                        filteredChannels.addAll(allChannels)
                        updateChannelList()
                        playChannel(allChannels[0])
                    } else {
                        loadingText.visibility = View.VISIBLE
                        loadingText.text = "No channels found"
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    loadingText.visibility = View.VISIBLE
                    loadingText.text = "Error: ${e.message}"
                }
            }
        }.start()
    }

    private fun updateChannelList() {
        val names = filteredChannels.map { "${it.name}${if (it.group.isNotEmpty()) "  •  ${it.group}" else ""}" }
        channelListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    private fun filterChannels(query: String) {
        filteredChannels.clear()
        filteredChannels.addAll(
            if (query.isEmpty()) allChannels
            else allChannels.filter { it.name.contains(query, true) || it.group.contains(query, true) }
        )
        updateChannelList()
    }

    private fun playChannel(channel: Channel) {
        currentChName.text = channel.name
        channelNameText.text = channel.name
        channelNameText.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ channelNameText.visibility = View.GONE }, 3000)

        player?.release()

        val dsFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0")

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val url = channel.url
            val src = if (url.contains(".m3u8") || url.contains("tracks-v") || url.contains("index.m3u")) {
                HlsMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            } else {
                ProgressiveMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            exo.setMediaSource(src)
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (currentIndex < filteredChannels.size - 1) {
                        currentIndex++
                        playChannel(filteredChannels[currentIndex])
                    }
                }
            })
        }
    }

    private fun toggleSidePanel() {
        if (sidePanelVisible) hideSidePanel() else showSidePanel()
    }

    private fun showSidePanel() {
        sidePanel.visibility = View.VISIBLE
        sidePanelVisible = true
        showBars()
        hideHandler.removeCallbacks(hideRunnable)
    }

    private fun hideSidePanel() {
        sidePanel.visibility = View.GONE
        sidePanelVisible = false
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    private fun showProfile() {
        val prefs = getSharedPreferences("atv_prefs", MODE_PRIVATE)
        val key = prefs.getString("saved_key", "Unknown") ?: "Unknown"
        val userName = prefs.getString("user_name", "User") ?: "User"
        val expiry = prefs.getString("expiry", "No Limit") ?: "No Limit"
        AlertDialog.Builder(this)
            .setTitle("👤 Profile")
            .setMessage("Name: $userName\nKey: $key\nExpiry: $expiry")
            .setPositiveButton("OK", null)
            .setNegativeButton("Logout") { _, _ ->
                getSharedPreferences("atv_prefs", MODE_PRIVATE).edit().remove("saved_key").apply()
                finish()
            }.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { if (sidePanelVisible) { hideSidePanel(); true } else { finish(); true } }
            KeyEvent.KEYCODE_MENU -> { toggleSidePanel(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { showSidePanel(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { hideSidePanel(); true }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!sidePanelVisible && filteredChannels.isNotEmpty()) {
                    currentIndex = if (currentIndex > 0) currentIndex - 1 else filteredChannels.size - 1
                    playChannel(filteredChannels[currentIndex])
                }; true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!sidePanelVisible && filteredChannels.isNotEmpty()) {
                    currentIndex = (currentIndex + 1) % filteredChannels.size
                    playChannel(filteredChannels[currentIndex])
                }; true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
