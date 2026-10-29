package com.adders.app

import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.roundToInt

object Keys {
    const val PREFS = "adders"
    const val ENABLED = "enabled"
    const val AD_PCT = "adPct"
    const val FALLBACK_SEC = "fallbackSec"
    const val FADE_IN_SEC = "fadeInSec"
    const val FADE_OUT_SEC = "fadeOutSec"
    const val STATE = "state"
    const val APP = "app"
    const val SAVED = "savedVol"     // preferred volume index to return to, -1 = none
    const val APPLIED = "appliedVol" // volume index we last set ourselves
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

/** Linear interpolation kept separate so the ramp maths can be unit-tested. */
object VolumeRamp {
    fun value(start: Int, target: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        return (start + (target - start) * f).roundToInt()
    }
}

/**
 * Controls STREAM_MUSIC. It keeps the user's preferred volume separate from temporary
 * advertisement/transition levels, ramps between them with timed updates, and survives
 * service restarts through SharedPreferences.
 */
class VolumeController(
    private val am: AudioManager,
    private val p: SharedPreferences,
    handler: Handler? = null
) {
    private enum class Ramp { NONE, FADE_IN, FADE_OUT }

    // Created only when a timed ramp is actually needed, so simple volume changes stay testable.
    private val h: Handler by lazy { handler ?: Handler(Looper.getMainLooper()) }

    private var saved: Int
        get() = p.getInt(Keys.SAVED, -1)
        set(v) { p.edit().putInt(Keys.SAVED, v).apply() }
    private var applied: Int
        get() = p.getInt(Keys.APPLIED, -1)
        set(v) { p.edit().putInt(Keys.APPLIED, v).apply() }

    private var ramp = Ramp.NONE
    private var rampTarget = -1
    private var rampStart = 0
    private var rampStartedAt = 0L
    private var rampDurationMs = 0L
    private var rampRunnable: Runnable? = null

    /** True once a song-ending fade has started; blocks a premature restore on the same song. */
    private var fadeOutLatched = false

    private val max get() = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val current get() = am.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun percent() = if (max == 0) 0 else (current * 100f / max).roundToInt()

    private fun indexFor(pct: Int): Int =
        if (max <= 0) 0 else (pct.coerceIn(0, 100) * max / 100f).roundToInt().coerceIn(0, max)

    private fun write(index: Int) {
        if (max <= 0) return
        val v = index.coerceIn(0, max)
        if (current != v) am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
    }

    /** An advertisement started (or is still playing). The ad volume always wins over a fade. */
    fun onAd(pct: Int) {
        val wasRamping = ramp != Ramp.NONE
        cancelRamp()
        fadeOutLatched = false

        val target = indexFor(pct)
        if (saved < 0) {
            saved = current // first ad segment: remember the real listening volume
        } else if (!wasRamping && applied >= 0 && target == applied && current != applied) {
            // The user moved the volume during the ad; keep their choice.
            return
        }
        applied = target
        write(target)
    }

    /**
     * A recognised song is playing. After an ad this returns to the preferred volume,
     * either immediately or with the configured fade-in.
     */
    fun onSong(fadeInSec: Int) {
        // A fade-out owns the volume until the song actually changes or an ad starts.
        if (fadeOutLatched) return
        if (saved < 0) {
            if (ramp == Ramp.FADE_OUT) cancelRamp()
            return
        }

        // If the user changed the volume during the ad, adopt it as the new normal.
        if (ramp != Ramp.FADE_IN && applied >= 0 && current != applied) {
            cancelRamp()
            saved = -1
            applied = -1
            return
        }

        val target = saved
        if (fadeInSec <= 0) {
            cancelRamp()
            write(target)
            saved = -1
            applied = -1
        } else {
            if (ramp == Ramp.FADE_IN && rampTarget == target) return
            startRamp(Ramp.FADE_IN, target, fadeInSec * 1000L)
        }
    }

    /**
     * Start the song-ending fade toward the advertisement volume. The preferred volume
     * is captured now, before any temporary levels are applied.
     */
    fun fadeOut(adPct: Int, durationMs: Long) {
        if (saved < 0) saved = current
        fadeOutLatched = true
        startRamp(Ramp.FADE_OUT, indexFor(adPct), durationMs)
    }

    /** Playback paused (or the player is not currently playing): stop timed updates safely. */
    fun onPaused() {
        cancelRamp()
    }

    /**
     * The track changed. A fade-out belongs to the previous song, so cancel it and let
     * onSong() restore the preferred volume. A fade-in is allowed to continue.
     */
    fun onTrackChanged() {
        if (!fadeOutLatched) return
        if (ramp == Ramp.FADE_OUT) cancelRamp()
        fadeOutLatched = false
    }

    /**
     * Playback jumped unexpectedly (for example the user seeked back). Abandon a fade-out
     * so the normal song/ad evaluation can take over; a fade-in keeps going.
     */
    fun onUnexpectedChange() {
        if (ramp == Ramp.FADE_OUT || fadeOutLatched) {
            if (ramp == Ramp.FADE_OUT) cancelRamp()
            fadeOutLatched = false
        }
    }

    /** Normal playback resumed, monitoring stopped, or the player went away. */
    fun restore() {
        cancelRamp()
        fadeOutLatched = false
        // If the user moved the volume during the ad, leave their choice alone.
        if (saved >= 0 && (applied < 0 || current == applied)) write(saved)
        saved = -1
        applied = -1
    }

    private fun startRamp(kind: Ramp, target: Int, durationMs: Long) {
        cancelRamp()

        val start = current
        ramp = kind
        rampStart = start
        rampTarget = target.coerceIn(0, max)
        rampStartedAt = SystemClock.uptimeMillis()
        rampDurationMs = durationMs.coerceAtLeast(0L)
        applied = start

        if (start == rampTarget || rampDurationMs == 0L) {
            applied = rampTarget
            write(rampTarget)
            finishRamp(kind)
            return
        }

        val step = object : Runnable {
            override fun run() {
                if (ramp != kind || rampRunnable !== this) return

                if (current != applied) {
                    // Something else (normally the user) moved the volume: stop fighting it.
                    cancelRamp()
                    saved = current
                    applied = -1
                    return
                }

                val elapsed = SystemClock.uptimeMillis() - rampStartedAt
                val fraction = (elapsed.toFloat() / rampDurationMs).coerceIn(0f, 1f)
                val value = VolumeRamp.value(rampStart, rampTarget, fraction).coerceIn(0, max)
                applied = value
                write(value)

                if (fraction >= 1f) finishRamp(kind) else h.postDelayed(this, STEP_MS)
            }
        }
        rampRunnable = step
        h.postDelayed(step, STEP_MS)
    }

    private fun finishRamp(kind: Ramp) {
        rampRunnable?.let { h.removeCallbacks(it) }
        rampRunnable = null
        ramp = Ramp.NONE
        if (kind == Ramp.FADE_IN) {
            // We are back at the preferred level; there is no ad volume left to undo.
            saved = -1
            applied = -1
        } else {
            // Keep saved so the next song can return to it; remember the level we reached.
            applied = current
        }
    }

    private fun cancelRamp() {
        rampRunnable?.let { h.removeCallbacks(it) }
        rampRunnable = null
        ramp = Ramp.NONE
    }

    private companion object {
        const val STEP_MS = 100L
    }
}
