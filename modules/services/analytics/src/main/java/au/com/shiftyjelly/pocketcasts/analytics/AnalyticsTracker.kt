package au.com.shiftyjelly.pocketcasts.analytics

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext

object AnalyticsTracker {
    private const val PREFKEY_SEND_USAGE_STATS = "pc_pref_send_usage_stats"
    private val trackers: MutableList<Tracker> = mutableListOf()
    @ApplicationContext
    private lateinit var appContext: Context

    var sendUsageStats: Boolean = true
        set(value) {
            if (value != field) {
                field = value
                storeUsagePref()
                if (!field) {
                    trackers.forEach { it.clearAllData() }
                }
            }
        }

    fun init(@ApplicationContext appContext: Context) {
        this.appContext = appContext
        trackers.forEach { it.clearAllData() }
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        sendUsageStats = prefs.getBoolean(PREFKEY_SEND_USAGE_STATS, true)
    }

    private fun storeUsagePref() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().putBoolean(PREFKEY_SEND_USAGE_STATS, sendUsageStats).apply()
    }

    fun registerTracker(tracker: Tracker?) {
        tracker?.let { trackers.add(tracker) }
    }

    fun track(event: AnalyticsEvent, properties: Map<String, Any> = emptyMap()) {
        // TODO only sending usage stats for debug builds while this feature is in development. Once we're
        // ready to release this, we should reverse this and default to _only_ sending usage stats when
        // it is _not_ a debug build (or do more checks when setting the `sendUsageStats` variable).
        if (sendUsageStats && BuildConfig.DEBUG) {
            trackers.forEach { it.track(event, properties) }
        }
    }

    fun refreshMetadata() {
        trackers.forEach { it.refreshMetadata() }
    }

    fun flush() {
        trackers.forEach { it.flush() }
    }

    fun clearAllData() {
        trackers.forEach { it.clearAllData() }
    }
}
