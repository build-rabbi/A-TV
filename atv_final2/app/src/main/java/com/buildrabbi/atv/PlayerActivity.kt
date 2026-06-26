package com.buildrabbi.atv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String, val logo: String = "", val group: String = "")

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var channelListView: ListView
    private lateinit var searchInput: EditText
    private lateinit var sidePanel: View
    private lateinit var loadingText: TextView
    private lateinit var channelNameText: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnProfile: ImageButton
    private lateinit var btnSearch: ImageButton

    private val allChannels = mutableListOf<Channel>()
    private val filteredChannels = mutableListOf<Channel>()
    private var currentIndex = 0
    private var sidePanelVisible = false

    companion object {
        private const val M3U_URL = "https://raw.githubusercontent.com/imShakil/tvlink/refs/heads/main/iptv.m3u8"
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

        playerView = findViewById(R.id.playerView)
        channelListView = findViewById(R.id.channelList)
        searchInput = findViewById(R.id.searchInput)
        sidePanel = findViewById(R.id.sidePanel)
        loadingText = findViewById(R.id.loadingText)
        channelNameText = findViewById(R.id.channelName)
        btnMenu = findViewById(R.id.btnMenu)
        btnProfile = findViewById(R.id.btnProfile)
        btnSearch = findViewById(R.id.btnSearch)

        btnMenu.setOnClickListener { toggleSidePanel() }
        btnSearch.setOnClickListener { toggleSidePanel() }

        btnProfile.setOnClickListener { showProfile() }

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

        loadM3U()
    }

    private fun loadM3U() {
        loadingText.visibility = View.VISIBLE
        loadingText.text = "Loading channels..."

        Thread {
            try {
                val urls = listOf(
                    "https://raw.githubusercontent.com/imShakil/tvlink/refs/heads/main/iptv.m3u8",
                    "https://raw.githubusercontent.com/bugsfreeweb/LiveTVCollector/refs/heads/main/LiveTV/Bangladesh/LiveTV.m3u"
                )

                var loaded = false
                for (m3uUrl in urls) {
                    try {
                        val url = URL(m3uUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 10000
                        conn.readTimeout = 15000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                        conn.connect()

                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val lines = reader.readLines()
                        reader.close()

                        var name = "Channel"
                        var logo = ""
                        var group = ""

                        for (line in lines) {
                            val l = line.trim()
                            when {
                                l.startsWith("#EXTINF") -> {
                                    name = if (l.contains(",")) l.substringAfterLast(",").trim() else "Channel"
                                    logo = Regex("tvg-logo=\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: ""
                                    group = Regex("group-title=\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: ""
                                }
                                l.startsWith("http") -> {
                                    allChannels.add(Channel(name, l, logo, group))
                                    loaded = true
                                }
                            }
                        }
                        if (loaded) break
                    } catch (e: Exception) { continue }
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
                    loadingText.text = "Error loading channels"
                }
            }
        }.start()
    }

    private fun updateChannelList() {
        val names = filteredChannels.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        channelListView.adapter = adapter
    }

    private fun filterChannels(query: String) {
        filteredChannels.clear()
        if (query.isEmpty()) {
            filteredChannels.addAll(allChannels)
        } else {
            filteredChannels.addAll(allChannels.filter {
                it.name.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true)
            })
        }
        updateChannelList()
    }

    private fun playChannel(channel: Channel) {
        channelNameText.text = channel.name
        channelNameText.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ channelNameText.visibility = View.GONE }, 3000)

        player?.release()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0")

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val url = channel.url
            val mediaSource = if (url.contains(".m3u8") || url.contains("m3u8") ||
                url.contains("tracks-v") || url.contains("index.m3u")) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // Try next channel on error
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
        searchInput.requestFocus()
    }

    private fun hideSidePanel() {
        sidePanel.visibility = View.GONE
        sidePanelVisible = false
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
                prefs.edit().remove("saved_key").apply()
                finish()
            }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                toggleSidePanel()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { showSidePanel(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { hideSidePanel(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
