package com.buildrabbi.atv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ফুল স্ক্রিন প্লেয়ার ভিউ তৈরি
        playerView = PlayerView(this)
        setContentView(playerView)

        // এক্সোপ্লেয়ার (ExoPlayer) চালু করা
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // আপনার দেওয়া ডিফল্ট প্লেলিস্ট/স্ট্রিমিং লিঙ্ক
        val videoUrl = "https://is.gd/yQuS1g.m3u"
        val mediaItem = MediaItem.fromUri(videoUrl)
        
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true // অটো-প্লে হবে
    }

    override fun onStop() {
        super.onStop()
        // অ্যাপ ব্যাকগ্রাউন্ডে গেলে প্লেয়ার বন্ধ করা
        player?.release()
        player = null
    }
}
