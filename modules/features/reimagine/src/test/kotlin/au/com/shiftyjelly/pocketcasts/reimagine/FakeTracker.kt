package au.com.shiftyjelly.pocketcasts.reimagine

import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.Tracker
import au.com.shiftyjelly.pocketcasts.analytics.TrackerType

class FakeTracker : Tracker {
    private val _events = mutableListOf<TrackEvent>()

    val events get() = _events.toList()

    override fun track(event: AnalyticsEvent, properties: Map<String, Any>) {
        _events += TrackEvent(event, properties)
    }

    override fun refreshMetadata() = Unit

    override fun getTrackerType() = TrackerType.FirstParty

    override fun flush() = Unit

    override fun clearAllData() = Unit
}

data class TrackEvent(
    val type: AnalyticsEvent,
    val properties: Map<String, Any>,
)
