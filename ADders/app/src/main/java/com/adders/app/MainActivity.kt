package com.adders.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var am: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val seek = findViewById<SeekBar>(R.id.slider)
        val pct = findViewById<TextView>(R.id.percent)
        seek.progress = prefs.getInt(Keys.AD_PCT, 0)
        pct.text = "${seek.progress}%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                pct.text = "$p%"
                prefs.edit().putInt(Keys.AD_PCT, p).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })

        val auto = findViewById<Switch>(R.id.autoSwitch)
        auto.isChecked = prefs.getBoolean(Keys.ENABLED, true)
        auto.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Keys.ENABLED, on).apply() }

        // Tap to cycle the fallback threshold.
        findViewById<TextView>(R.id.fallback).setOnClickListener {
            val opts = listOf(30, 60, 90, 120)
            val next = opts[(opts.indexOf(prefs.getInt(Keys.FALLBACK_SEC, 60)) + 1) % opts.size]
            prefs.edit().putInt(Keys.FALLBACK_SEC, next).apply()
            refresh()
        }

        findViewById<Button>(R.id.grant).setOnClickListener { openAccessSettings() }
        if (!hasAccess()) explainAccess()
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    private fun hasAccess() =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun openAccessSettings() =
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun explainAccess() = AlertDialog.Builder(this)
        .setTitle("Notification access needed")
        .setMessage("Android only lets an app see what other apps are playing (song title, artist, play state) through Notification access. ADders reads nothing else and never reads your notifications' content.")
        .setPositiveButton("Open settings") { _, _ -> openAccessSettings() }
        .setNegativeButton("Later", null)
        .show()

    private fun refresh() {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val now = if (max == 0) 0 else (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()
        val granted = hasAccess()
        findViewById<Button>(R.id.grant).visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<TextView>(R.id.app).text = "App: ${prefs.getString(Keys.APP, "-")}"
        findViewById<TextView>(R.id.state).text =
            "Detection: ${if (granted) prefs.getString(Keys.STATE, "IDLE") else "NO ACCESS"}"
        findViewById<TextView>(R.id.volume).text = "Media volume: $now%"
        findViewById<TextView>(R.id.fallback).text =
            "Fallback: unknown < ${prefs.getInt(Keys.FALLBACK_SEC, 60)}s = ad (tap to change)"
    }
}
