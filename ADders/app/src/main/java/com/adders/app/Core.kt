package com.adders.app

import android.content.SharedPreferences
import android.media.AudioManager
import kotlin.math.roundToInt

object Keys {
    const val PREFS = "adders"
    const val ENABLED = "enabled"
    const val AD_PCT = "adPct"
    const val FALLBACK_SEC = "fallbackSec"
    const val STATE = "state"
    const val APP = "app"
    const val SAVED = "savedVol"     // volume index to restore, -1 = none
    const val APPLIED = "appliedVol" // volume index we set for the ad
}

enum class Kind { SONG, AD, UNKNOWN }

object AdClassifier {
    // shortcut: Spotify has no official ad flag; this matches how its ads currently show up
    // in MediaSession metadata (title/artist "Advertisement"/"Spotify", no album). Update if that changes.
    fun classify(title: String?, artist: String?, album: String?, durationMs: Long, fallbackMs: Long): Kind {
        val t = title.orEmpty().trim().lowercase()
        val a = artist.orEmpty().trim().lowercase()
        if (t == "advertisement" || a == "advertisement" || (a == "spotify" && album.isNullOrBlank())) return Kind.AD
        if (t.isNotEmpty() && a.isNotEmpty() && !album.isNullOrBlank()) return Kind.SONG
        // Unidentified: duration is only a fallback signal. Unknown duration (<=0) is never an ad.
        return if (durationMs in 1 until fallbackMs) Kind.AD else Kind.UNKNOWN
    }
}

/** Lowers STREAM_MUSIC for an ad and puts it back after. Survives service restarts via prefs. */
class VolumeController(private val am: AudioManager, private val p: SharedPreferences) {
    private var saved: Int
        get() = p.getInt(Keys.SAVED, -1)
        set(v) { p.edit().putInt(Keys.SAVED, v).apply() }
    private var applied: Int
        get() = p.getInt(Keys.APPLIED, -1)
        set(v) { p.edit().putInt(Keys.APPLIED, v).apply() }

    private val max get() = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val current get() = am.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun percent() = if (max == 0) 0 else (current * 100f / max).roundToInt()

    fun onAd(pct: Int) {
        val target = (pct * max / 100f).roundToInt()
        if (saved < 0) saved = current // first ad segment: remember the real volume
        else if (current != applied && target == applied) return // user changed volume mid-ad: respect it
        applied = target
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    /** Normal playback resumed, monitoring stopped, or the player went away. */
    fun restore() {
        // If the user moved the volume during the ad, leave their choice alone.
        if (saved >= 0 && current == applied) am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
        saved = -1
        applied = -1
    }
}
