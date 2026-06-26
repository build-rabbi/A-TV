package com.buildrabbi.atv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
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

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private val channels = mutableListOf<Pair<String, String>>() // name, url
    private var currentIndex = 0

    companion object {
        private const val M3U_URL = "https://raw.githubusercontent.com/bugsfreeweb/LiveTVCollector/refs/heads/main/LiveTV/Bangladesh/LiveTV.m3u"
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

        Toast.makeText(this, "Loading channels...", Toast.LENGTH_SHORT).show()
        loadM3U()
    }

    private fun loadM3U() {
        Thread {
            try {
                val url = URL(M3U_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connect()

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val lines = reader.readLines()
                reader.close()

                var name = "Channel"
                for (line in lines) {
                    when {
                        line.startsWith("#EXTINF") -> {
                            name = line.substringAfterLast(",").trim()
                            if (name.isEmpty()) name = "Channel"
                        }
                        line.startsWith("http") -> {
                            channels.add(Pair(name, line.trim()))
                        }
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    if (channels.isNotEmpty()) {
                        Toast.makeText(this, "${channels.size} channels loaded", Toast.LENGTH_SHORT).show()
                        initPlayer(channels[0].second)
                    } else {
                        Toast.makeText(this, "No channels found", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun initPlayer(streamUrl: String) {
        player?.release()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0")

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo

            val mediaSource = if (streamUrl.contains(".m3u8") || streamUrl.contains("tracks-v1")) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)))
            }

            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
