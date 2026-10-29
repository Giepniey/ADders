package com.adders.app

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlin.math.abs

/**
 * Notification-listener services are kept alive by the system once the user grants
 * Notification access, and that grant is also what allows reading other apps' media sessions.
 * So no separate foreground service is needed.
 */
class AdListenerService : NotificationListenerService() {
    private lateinit var prefs: SharedPreferences
    private lateinit var vol: VolumeController
    private var controllers: List<MediaController> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private var lastTrackKey: String? = null
    private var fadeOutTriggered = false
    private var lastRemainingMs = -1L
    private var lastStatusState: String? = null
    private var lastStatusApp: String? = null

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = evaluate()
        override fun onPlaybackStateChanged(state: PlaybackState?) = evaluate()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        setControllers(it ?: emptyList())
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == Keys.ENABLED || k == Keys.AD_PCT || k == Keys.FALLBACK_SEC ||
            k == Keys.FADE_IN_SEC || k == Keys.FADE_OUT_SEC
        ) evaluate()
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (::prefs.isInitialized) evaluate()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onListenerConnected() {
        prefs = getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
        vol = VolumeController(getSystemService(Context.AUDIO_SERVICE) as AudioManager, prefs)
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val me = ComponentName(this, AdListenerService::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsListener, me)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        vol.restore() // clean up if we were killed mid-ad or mid-transition last time
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, POLL_MS)
        setControllers(msm.getActiveSessions(me))
    }

    override fun onListenerDisconnected() {
        if (!::prefs.isInitialized) return
        handler.removeCallbacks(ticker)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager)
            .removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(cb) }
        controllers = emptyList()
        vol.restore()
        resetTracking()
        status("OFF", "-")
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::vol.isInitialized) vol.onPaused()
        super.onDestroy()
    }

    private fun setControllers(new: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(cb) }
        controllers = new
        controllers.forEach { it.registerCallback(cb) }
        evaluate()
    }

    private fun evaluate() {
        if (!prefs.getBoolean(Keys.ENABLED, true)) {
            vol.restore()
            resetTracking()
            status("OFF", "-")
            return
        }

        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (c == null) {
            // No timed volume changes while paused. Keep a pending ad volume until play resumes.
            vol.onPaused()
            fadeOutTriggered = false
            lastRemainingMs = -1L
            // Player closed: put the volume back. Merely paused: keep the ad volume until playback resumes.
            if (controllers.isEmpty()) {
                vol.restore()
                resetTracking()
                status("IDLE", "-")
            }
            return
        }

        val m = c.metadata
        val key = trackKey(c, m)
        if (key != lastTrackKey) {
            if (lastTrackKey != null) {
                vol.onTrackChanged()
                fadeOutTriggered = false
                lastRemainingMs = -1L
            }
            lastTrackKey = key
        }

        val kind = AdClassifier.classify(
            m?.getString(MediaMetadata.METADATA_KEY_TITLE),
            m?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            prefs.getInt(Keys.FALLBACK_SEC, 60) * 1000L
        )
        val fadeInSec = prefs.getInt(Keys.FADE_IN_SEC, 0).coerceIn(0, 15)
        val fadeOutSec = prefs.getInt(Keys.FADE_OUT_SEC, 0).coerceIn(0, 15)

        when (kind) {
            Kind.AD -> {
                fadeOutTriggered = false
                lastRemainingMs = -1L
                vol.onAd(prefs.getInt(Keys.AD_PCT, 0))
            }
            Kind.SONG -> {
                vol.onSong(fadeInSec)
                if (fadeOutSec in 1..15) {
                    maybeStartFadeOut(c, m, fadeOutSec, fadeInSec)
                } else {
                    // Fade-out is off. Make sure an interrupted fade cannot leave its latch behind.
                    fadeOutTriggered = false
                    lastRemainingMs = -1L
                    vol.onUnexpectedChange()
                    vol.onSong(fadeInSec)
                }
            }
            // shortcut: an unclassifiable segment changes nothing, so the ad volume
            // stays until the next recognised song; this avoids flapping on brief metadata gaps.
            Kind.UNKNOWN -> {}
        }
        status(
            when (kind) { Kind.AD -> "ADVERTISEMENT"; Kind.SONG -> "SONG"; Kind.UNKNOWN -> "UNKNOWN" },
            c.packageName
        )
    }

    /**
     * Starts a fade-out only for a real song with usable duration/position data. A track
     * change is handled separately, so it can never be mistaken for the end of a song.
     */
    private fun maybeStartFadeOut(
        c: MediaController,
        m: MediaMetadata?,
        fadeOutSec: Int,
        fadeInSec: Int
    ) {
        val fadeMs = fadeOutSec * 1000L
        val remaining = remainingMs(c, m)

        if (remaining == null) {
            // Playback information disappeared: do not guess that the song is ending.
            if (fadeOutTriggered) {
                fadeOutTriggered = false
                lastRemainingMs = -1L
                vol.onUnexpectedChange()
                vol.onSong(fadeInSec)
            }
            return
        }

        if (fadeOutTriggered) {
            val previous = lastRemainingMs
            lastRemainingMs = remaining

            // A large jump in either direction means a seek (or otherwise changed playback).
            val jumped = previous >= 0 && abs(remaining - previous) > SEEK_JUMP_MS
            val seekedBack = remaining > fadeMs + SEEK_JUMP_MS
            if (jumped || seekedBack) {
                fadeOutTriggered = false
                lastRemainingMs = -1L
                vol.onUnexpectedChange()
                vol.onSong(fadeInSec)
            }
            return
        }

        lastRemainingMs = remaining
        if (remaining <= 0L) return
        if (remaining > fadeMs) {
            // Not near the end. If an interrupted fade-out left its latch behind, let the
            // normal song handling restore the preferred volume.
            vol.onUnexpectedChange()
            vol.onSong(fadeInSec)
            return
        }

        // Use the time actually left if detection begins late, so the fade still finishes
        // by the end of the track. The advertisement volume takes over if the ad arrives first.
        fadeOutTriggered = true
        vol.fadeOut(prefs.getInt(Keys.AD_PCT, 0), minOf(fadeMs, remaining))
    }

    /** Best available estimate of the time left, extrapolated from the last playback update. */
    private fun remainingMs(c: MediaController, m: MediaMetadata?): Long? {
        val st = c.playbackState ?: return null
        if (st.state != PlaybackState.STATE_PLAYING) return null
        val speed = st.playbackSpeed
        if (speed <= 0f || st.position < 0L) return null
        val duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (duration <= 0L) return null

        val age = if (st.lastPositionUpdateTime > 0L) {
            (SystemClock.elapsedRealtime() - st.lastPositionUpdateTime).coerceAtLeast(0L)
        } else {
            0L
        }
        val position = st.position + (age * speed).toLong()
        return (duration - position).coerceAtLeast(0L)
    }

    private fun trackKey(c: MediaController, m: MediaMetadata?): String = buildString {
        append(c.sessionToken)
        append('|').append(m?.getString(MediaMetadata.METADATA_KEY_TITLE))
        append('|').append(m?.getString(MediaMetadata.METADATA_KEY_ARTIST))
        append('|').append(m?.getString(MediaMetadata.METADATA_KEY_ALBUM))
        append('|').append(m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
    }

    private fun resetTracking() {
        lastTrackKey = null
        fadeOutTriggered = false
        lastRemainingMs = -1L
    }

    private fun status(state: String, app: String) {
        if (state == lastStatusState && app == lastStatusApp) return
        lastStatusState = state
        lastStatusApp = app
        prefs.edit().putString(Keys.STATE, state).putString(Keys.APP, app).apply()
    }

    private companion object {
        const val POLL_MS = 500L
        const val SEEK_JUMP_MS = 2_000L
    }
}
