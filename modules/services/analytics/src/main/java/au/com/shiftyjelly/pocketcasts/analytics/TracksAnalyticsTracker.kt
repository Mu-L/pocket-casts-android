package au.com.shiftyjelly.pocketcasts.analytics

import android.content.Context
import au.com.shiftyjelly.pocketcasts.models.to.SubscriptionStatus
import au.com.shiftyjelly.pocketcasts.utils.DisplayUtil
import com.automattic.android.tracks.TracksClient
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class TracksAnalyticsTracker @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val displayUtil: DisplayUtil,
) : Tracker(appContext) {
    private val tracksClient: TracksClient? = TracksClient.getClient(appContext)
    override val anonIdPrefKey: String = TRACKS_ANON_ID

    private var cachedSubscriptionStatus: (() -> SubscriptionStatus?)? = null
    private var isLoggedIn = { false }
    private val plusSubscription: SubscriptionStatus.Plus?
        get() = cachedSubscriptionStatus?.invoke() as? SubscriptionStatus.Plus

    private val predefinedEventProperties: Map<String, Any>
        get() {
            val isLoggedIn = isLoggedIn.invoke()
            val hasSubscription = plusSubscription != null
            val hasLifetime = plusSubscription?.isLifetimePlus
                ?: false
            val subscriptionType = plusSubscription?.type?.toString()
                ?: INVALID_OR_NULL_VALUE
            val subscriptionPlatform = plusSubscription?.platform?.toString()
                ?: INVALID_OR_NULL_VALUE
            val subscriptionFrequency = plusSubscription?.frequency?.toString()
                ?: INVALID_OR_NULL_VALUE

            return mapOf(
                PredefinedEventProperty.HAS_DYNAMIC_FONT_SIZE to displayUtil.hasDynamicFontSize(),
                PredefinedEventProperty.USER_IS_LOGGED_IN to isLoggedIn,
                PredefinedEventProperty.PLUS_HAS_SUBSCRIPTION to hasSubscription,
                PredefinedEventProperty.PLUS_HAS_LIFETIME to hasLifetime,
                PredefinedEventProperty.PLUS_SUBSCRIPTION_TYPE to subscriptionType,
                PredefinedEventProperty.PLUS_SUBSCRIPTION_PLATFORM to subscriptionPlatform,
                PredefinedEventProperty.PLUS_SUBSCRIPTION_FREQUENCY to subscriptionFrequency,
            ).mapKeys { it.key.analyticsKey }
        }

    fun setup(cachedSubscriptionStatus: (() -> SubscriptionStatus?)?, isLoggedIn: () -> Boolean) {
        this.cachedSubscriptionStatus = cachedSubscriptionStatus
        this.isLoggedIn = isLoggedIn
    }

    override fun track(event: AnalyticsEvent, properties: Map<String, Any>) {
        super.track(event, properties)
        if (tracksClient == null) return

        val eventKey = event.key
        val user = anonID ?: generateNewAnonID()
        val userType = TracksClient.NosaraUserType.ANON

        /* Create the merged JSON Object of properties.
        Properties defined by the user have precedence over the default ones pre-defined at "event level" */
        val propertiesToJSON = JSONObject(properties)
        predefinedEventProperties.keys.forEach { key ->
            if (propertiesToJSON.has(key)) {
                Timber.w("The user has defined a property named: '$key' that will override the same property pre-defined at event level. This may generate unexpected behavior!!")
                Timber.w("User value: " + propertiesToJSON.get(key).toString() + " - pre-defined value: " + predefinedEventProperties[key].toString())
            } else {
                propertiesToJSON.put(key, predefinedEventProperties[key])
            }
        }

        tracksClient.track(EVENTS_PREFIX + eventKey, propertiesToJSON, user, userType)
        if (propertiesToJSON.length() > 0) {
            Timber.i("\uD83D\uDD35 Tracked: $eventKey, Properties: $propertiesToJSON")
        } else {
            Timber.i("\uD83D\uDD35 Tracked: $eventKey")
        }
    }

    override fun flush() {
        tracksClient?.flush()
    }

    override fun clearAllData() {
        super.clearAllData()
        tracksClient?.clearUserProperties()
        tracksClient?.clearQueues()
    }

    enum class PredefinedEventProperty(val analyticsKey: String) {
        HAS_DYNAMIC_FONT_SIZE("has_dynamic_font_size"),
        USER_IS_LOGGED_IN("user_is_logged_in"),
        PLUS_HAS_SUBSCRIPTION("plus_has_subscription"),
        PLUS_HAS_LIFETIME("plus_has_lifetime"),
        PLUS_SUBSCRIPTION_TYPE("plus_subscription_type"),
        PLUS_SUBSCRIPTION_PLATFORM("plus_subscription_platform"),
        PLUS_SUBSCRIPTION_FREQUENCY("plus_subscription_frequency"),
    }

    companion object {
        private const val TRACKS_ANON_ID = "nosara_tracks_anon_id"
        private const val EVENTS_PREFIX = "pcandroid_"
        const val INVALID_OR_NULL_VALUE = "none"
    }
}
