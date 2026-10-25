package com.adders.app

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

/**
 * Notification-listener services are kept alive by the system once the user grants
 * Notification access, and that grant is also what allows reading other apps' media sessions.
 * So no separate foreground service is needed.
 */
class AdListenerService : NotificationListenerService() {
    private lateinit var prefs: SharedPreferences
    private lateinit var vol: VolumeController
    private var controllers: List<MediaController> = emptyList()

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = evaluate()
        override fun onPlaybackStateChanged(state: PlaybackState?) = evaluate()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        setControllers(it ?: emptyList())
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == Keys.ENABLED || k == Keys.AD_PCT || k == Keys.FALLBACK_SEC) evaluate()
    }

    override fun onListenerConnected() {
        prefs = getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
        vol = VolumeController(getSystemService(Context.AUDIO_SERVICE) as AudioManager, prefs)
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val me = ComponentName(this, AdListenerService::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsListener, me)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        vol.restore() // clean up if we were killed mid-ad last time
        setControllers(msm.getActiveSessions(me))
    }

    override fun onListenerDisconnected() {
        if (!::prefs.isInitialized) return
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager)
            .removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(cb) }
        controllers = emptyList()
        vol.restore()
        status("OFF", "-")
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
            status("OFF", "-")
            return
        }
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (c == null) {
            // Player closed: put the volume back. Merely paused: keep the ad volume until playback resumes.
            if (controllers.isEmpty()) { vol.restore(); status("IDLE", "-") }
            return
        }
        val m = c.metadata
        val kind = AdClassifier.classify(
            m?.getString(MediaMetadata.METADATA_KEY_TITLE),
            m?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            prefs.getInt(Keys.FALLBACK_SEC, 60) * 1000L
        )
        when (kind) {
            Kind.AD -> vol.onAd(prefs.getInt(Keys.AD_PCT, 0))
            Kind.SONG -> vol.restore()
            // shortcut: an unclassifiable long segment changes nothing, so the ad volume
            // stays until the next recognised song; this avoids flapping on brief metadata gaps.
            Kind.UNKNOWN -> {}
        }
        status(
            when (kind) { Kind.AD -> "ADVERTISEMENT"; Kind.SONG -> "SONG"; Kind.UNKNOWN -> "UNKNOWN" },
            c.packageName
        )
    }

    private fun status(state: String, app: String) {
        prefs.edit().putString(Keys.STATE, state).putString(Keys.APP, app).apply()
    }
}
