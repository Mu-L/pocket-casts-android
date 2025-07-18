package au.com.shiftyjelly.pocketcasts.deeplink

import android.app.SearchManager
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_VIEW
import android.content.Intent.EXTRA_STREAM
import android.net.Uri
import android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
import androidx.core.content.IntentCompat
import au.com.shiftyjelly.pocketcasts.deeplink.BuildConfig.SERVER_LIST_HOST
import au.com.shiftyjelly.pocketcasts.deeplink.BuildConfig.SERVER_SHORT_HOST
import au.com.shiftyjelly.pocketcasts.deeplink.BuildConfig.SERVER_WEB_PLAYER_HOST
import au.com.shiftyjelly.pocketcasts.deeplink.BuildConfig.WEB_BASE_HOST
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.ACTION_OPEN_ADD_BOOKMARK
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.ACTION_OPEN_BOOKMARK
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.ACTION_OPEN_CHANGE_BOOKMARK_TITLE
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.ACTION_OPEN_DELETE_BOOKMARK
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.ACTION_OPEN_DOWNLOADS
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.ACTION_OPEN_EPISODE
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.ACTION_OPEN_PODCAST
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.EXTRA_AUTO_PLAY
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.EXTRA_BOOKMARK_UUID
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.EXTRA_EPISODE_UUID
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.EXTRA_FILTER_ID
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.EXTRA_PAGE
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.EXTRA_PODCAST_UUID
import au.com.shiftyjelly.pocketcasts.deeplink.DeepLink.Companion.EXTRA_SOURCE_VIEW
import timber.log.Timber

class DeepLinkFactory(
    private val webBaseHost: String = WEB_BASE_HOST,
    private val listHost: String = SERVER_LIST_HOST,
    private val shareHost: String = SERVER_SHORT_HOST,
    private val webPlayerHost: String = SERVER_WEB_PLAYER_HOST,
) {
    private val adapters = listOf(
        DownloadsAdapter(),
        AddBookmarkAdapter(),
        ChangeBookmarkTitleAdapter(),
        ShowBookmarkAdapter(),
        DeleteBookmarkAdapter(),
        ShowPodcastAdapter(),
        ShowEpisodeAdapter(),
        ShowPageAdapter(),
        ShowFiltersAdapter(),
        UpNextAdapter(),
        PocketCastsWebsiteGetAdapter(webBaseHost),
        ReferralsAdapter(webBaseHost),
        PodloveAdapter(),
        SonosAdapter(),
        ShareListAdapter(listHost),
        ShareListNativeAdapter(),
        SubscribeOnAndroidAdapter(),
        AppleAdapter(),
        CloudFilesAdapter(),
        UpsellAdapter(),
        UpgradeAccountAdapter(),
        FeaturesAdapter(),
        PromoCodeAdapter(),
        ShareLinkNativeAdapter(),
        SignInAdapter(shareHost),
        ShareLinkAdapter(shareHost),
        WebPlayerShareLinkAdapter(webBaseHost = webBaseHost, webPlayerHost = webPlayerHost),
        OpmlAdapter(listOf(listHost, shareHost)),
        ImportAdapter(),
        DiscoverAdapter(),
        PodcastUrlSchemeAdapter(listOf(listHost, shareHost, webBaseHost)),
        PlayFromSearchAdapter(),
        AssistantAdapter(),
        ThemesAdapter(),
        AppOpenAdapter(),
        CreateAccountAdapter(),
        DeveloperOptionsAdapter(),
    )

    fun create(intent: Intent): DeepLink? {
        Timber.tag(TAG).i("Deep linking to: $intent")
        val deepLinks = adapters.mapNotNull { it.create(intent) }
        return when (deepLinks.size) {
            1 -> {
                val deepLink = deepLinks.first()
                Timber.tag(TAG).d("Found a matching deep link: $deepLink")
                deepLink
            }
            0 -> {
                Timber.tag(TAG).w("No matching deep links found")
                null
            }
            else -> {
                Timber.tag(TAG).w("Found multiple matching deep links: $deepLinks")
                deepLinks.first()
            }
        }
    }

    private companion object {
        val TAG = "DeepLinking"
    }
}

private interface DeepLinkAdapter {
    fun create(intent: Intent): DeepLink?
}

private class DownloadsAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        return when {
            isUriMatch(intent) -> DownloadsDeepLink
            intent.action == ACTION_OPEN_DOWNLOADS -> DownloadsDeepLink
            else -> null
        }
    }

    private fun isUriMatch(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        return intent.action == ACTION_VIEW &&
            uri.scheme == "pktc" &&
            uri.host == "profile" &&
            uri.path == "/downloads"
    }
}

private class AddBookmarkAdapter : DeepLinkAdapter {
    override fun create(intent: Intent) = if (intent.action == ACTION_OPEN_ADD_BOOKMARK) {
        AddBookmarkDeepLink
    } else {
        null
    }
}

private class ChangeBookmarkTitleAdapter : DeepLinkAdapter {
    override fun create(intent: Intent) = if (intent.action == ACTION_OPEN_CHANGE_BOOKMARK_TITLE) {
        intent.getStringExtra(EXTRA_BOOKMARK_UUID)?.let(::ChangeBookmarkTitleDeepLink)
    } else {
        null
    }
}

private class ShowBookmarkAdapter : DeepLinkAdapter {
    override fun create(intent: Intent) = if (intent.action == ACTION_OPEN_BOOKMARK) {
        intent.getStringExtra(EXTRA_BOOKMARK_UUID)?.let(::ShowBookmarkDeepLink)
    } else {
        null
    }
}

private class DeleteBookmarkAdapter : DeepLinkAdapter {
    override fun create(intent: Intent) = if (intent.action == ACTION_OPEN_DELETE_BOOKMARK) {
        intent.getStringExtra(EXTRA_BOOKMARK_UUID)?.let(::DeleteBookmarkDeepLink)
    } else {
        null
    }
}

private class ShowPodcastAdapter : DeepLinkAdapter {
    override fun create(intent: Intent) = if (intent.action == ACTION_OPEN_PODCAST) {
        intent.getStringExtra(EXTRA_PODCAST_UUID)?.let { podcastUuid ->
            ShowPodcastDeepLink(
                podcastUuid = podcastUuid,
                sourceView = intent.getStringExtra(EXTRA_SOURCE_VIEW),
            )
        }
    } else {
        null
    }
}

private class ShowEpisodeAdapter : DeepLinkAdapter {
    override fun create(intent: Intent) = if (ACTION_REGEX.matches(intent.action.orEmpty())) {
        intent.getStringExtra(EXTRA_EPISODE_UUID)?.let { episodeUuid ->
            ShowEpisodeDeepLink(
                episodeUuid = episodeUuid,
                podcastUuid = intent.getStringExtra(EXTRA_PODCAST_UUID),
                autoPlay = intent.getBooleanExtra(EXTRA_AUTO_PLAY, false),
                sourceView = intent.getStringExtra(EXTRA_SOURCE_VIEW),
            )
        }
    } else {
        null
    }

    private companion object {
        // We match on this pattern to handle notification intents that add numbers to actions for pending intents
        private val ACTION_REGEX = ("^" + ACTION_OPEN_EPISODE + """\d*$""").toRegex()
    }
}

private class ShowPageAdapter : DeepLinkAdapter {
    override fun create(intent: Intent) = if (intent.action == ACTION_VIEW) {
        when (intent.getStringExtra(EXTRA_PAGE)) {
            "podcasts" -> ShowPodcastsDeepLink
            "search" -> ShowDiscoverDeepLink
            "upnext" -> ShowUpNextModalDeepLink
            "playlist" -> ShowFilterDeepLink(filterId = intent.getLongExtra(EXTRA_FILTER_ID, -1))
            else -> null
        }
    } else {
        null
    }
}

private class ShowFiltersAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "filters") {
            ShowFiltersDeepLink
        } else {
            null
        }
    }
}

private class CreateAccountAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "signup") {
            CreateAccountDeepLink
        } else {
            null
        }
    }
}

private class UpNextAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data ?: return null
        val scheme = uriData.scheme
        val host = uriData.host
        val location = uriData.getQueryParameter("location")

        if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "upnext" && location == "tab") {
            return ShowUpNextTabDeepLink
        }
        return null
    }
}

private class PocketCastsWebsiteGetAdapter(
    private val webBaseHost: String,
) : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val data = intent.data ?: return null
        val pathSegments = data.pathSegments

        return if (intent.action == ACTION_VIEW && data.host == webBaseHost && pathSegments.isNotEmpty() && pathSegments.first() == "get") {
            PocketCastsWebsiteGetDeepLink
        } else {
            null
        }
    }
}

private class ReferralsAdapter(
    private val webBaseHost: String,
) : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val data = intent.data ?: return null
        val pathSegments = data.pathSegments

        return if (intent.action == ACTION_VIEW &&
            data.host == webBaseHost &&
            pathSegments.size == 2 &&
            pathSegments.first() == "redeem"
        ) {
            ReferralsDeepLink(code = pathSegments.last())
        } else {
            null
        }
    }
}

private class PodloveAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.dataString.orEmpty()
        val groupValues = PODLOVE_REGEX.matchEntire(uriData)?.groupValues

        return if (intent.action == ACTION_VIEW && groupValues != null) {
            val scheme = if (groupValues[1] == "subscribe") "http" else "https"
            ShowPodcastFromUrlDeepLink("$scheme://${groupValues[2]}")
        } else {
            null
        }
    }

    private companion object {
        private val PODLOVE_REGEX = """^pktc://(subscribe|subscribehttps)/(.{3,})$""".toRegex()
    }
}

private class SonosAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host
        val state = uriData?.getQueryParameter("state")

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "applink" && state != null) {
            SonosDeepLink(state)
        } else {
            null
        }
    }
}

private class ShareListAdapter(
    private val listHost: String,
) : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host
        val path = uriData?.path?.takeIf { it != "/" }
        val source = uriData?.getQueryParameter(EXTRA_SOURCE_VIEW)

        return if (intent.action == ACTION_VIEW && scheme in listOf("http", "https") && host == listHost && path != null) {
            ShareListDeepLink(path, source)
        } else {
            null
        }
    }
}

private class ShareListNativeAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host
        val path = uriData?.path?.takeIf { it != "/" }
        val source = uriData?.getQueryParameter(EXTRA_SOURCE_VIEW)

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "sharelist" && path != null) {
            ShareListDeepLink(path, source)
        } else {
            null
        }
    }
}

// http://subscribeonandroid.com/geeknewscentral.com/podcast.xml
private class SubscribeOnAndroidAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host
        val path = uriData?.path?.replaceFirst("/", "")?.takeIf { it.length >= 3 }

        return if (intent.action == ACTION_VIEW &&
            scheme in listOf("http", "https") &&
            host in listOf("subscribeonandroid.com", "www.subscribeonandroid.com") &&
            path != null
        ) {
            ShowPodcastFromUrlDeepLink("$scheme://$path")
        } else {
            null
        }
    }
}

private class AppleAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && host in listOf("itunes.apple.com", "podcasts.apple.com") && uriData != null) {
            ShowPodcastFromUrlDeepLink(uriData.toString())
        } else {
            null
        }
    }
}

private class CloudFilesAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "cloudfiles") {
            CloudFilesDeepLink
        } else {
            null
        }
    }
}

private class UpsellAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "upsell") {
            UpsellDeepLink
        } else {
            null
        }
    }
}

private class UpgradeAccountAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "upgrade") {
            UpgradeAccountDeepLink
        } else {
            null
        }
    }
}

private class FeaturesAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "features") {
            when (val path = uriData.pathSegments.firstOrNull()) {
                "suggestedFolders" -> SmartFoldersDeepLink
                else -> null
            }
        } else {
            null
        }
    }
}

private class PromoCodeAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val dataString = uriData?.toString().orEmpty()
        val pathSegments = uriData?.pathSegments.orEmpty()

        return if (intent.action == ACTION_VIEW && dataString.startsWith("pktc://redeem/promo") && pathSegments.size >= 2) {
            PromoCodeDeepLink(pathSegments.last())
        } else {
            null
        }
    }
}

private class ShareLinkNativeAdapter : DeepLinkAdapter {
    private val timestampParser = SharingUrlTimestampParser()

    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host
        val pathSegments = uriData?.pathSegments.orEmpty()

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && pathSegments.isNotEmpty() && host !in EXCLUDED_HOSTS) {
            val timestamps = uriData.getQueryParameter("t")?.let(timestampParser::parseTimestamp)
            NativeShareDeepLink(
                uri = uriData,
                startTimestamp = timestamps?.first,
                endTimestamp = timestamps?.second,
            )
        } else {
            null
        }
    }

    private companion object {
        val EXCLUDED_HOSTS = listOf(
            "subscribe",
            "subscribehttps",
            "applink",
            "sharelist",
            "cloudfiles",
            "upgrade",
            "redeem",
            "settings",
            "discover",
            "features",
            "developer_options",
        )
    }
}

private class ShareLinkAdapter(
    private val shareHost: String,
) : DeepLinkAdapter {
    private val timestampParser = SharingUrlTimestampParser()

    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && scheme in listOf("http", "https") && host == shareHost) {
            val timestamps = uriData.getQueryParameter("t")?.let(timestampParser::parseTimestamp)
            when {
                uriData.pathSegments.size < 2 -> NativeShareDeepLink(
                    uri = uriData,
                    startTimestamp = timestamps?.first,
                    endTimestamp = timestamps?.second,
                )
                uriData.pathSegments[0] == "podcast" -> ShowPodcastDeepLink(
                    podcastUuid = uriData.pathSegments[1],
                    sourceView = uriData.getQueryParameter(EXTRA_SOURCE_VIEW),
                )
                uriData.pathSegments[0] == "episode" -> ShowEpisodeDeepLink(
                    episodeUuid = uriData.pathSegments[1],
                    podcastUuid = null,
                    startTimestamp = timestamps?.first,
                    endTimestamp = timestamps?.second,
                    autoPlay = uriData.getQueryParameter(EXTRA_AUTO_PLAY).toBoolean(),
                    sourceView = uriData.getQueryParameter(EXTRA_SOURCE_VIEW),
                )
                // handle the different podcast share links such as /itunes/itunes_id, /feed/feed_url
                else -> ShowPodcastFromUrlDeepLink(uriData.toString())
            }
        } else {
            null
        }
    }
}

private class WebPlayerShareLinkAdapter(
    private val webBaseHost: String,
    private val webPlayerHost: String,
) : DeepLinkAdapter {
    private val timestampParser = SharingUrlTimestampParser()

    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data ?: return null
        val scheme = uriData.scheme
        val host = uriData.host

        if (intent.action != ACTION_VIEW ||
            scheme !in listOf("http", "https") ||
            (host != webBaseHost && host != webPlayerHost) ||
            uriData.pathSegments.size <= 1 ||
            uriData.pathSegments.first() != "podcasts"
        ) {
            return null
        }

        val podcastUuid = uriData.pathSegments[1]
        val episodeUuid = uriData.pathSegments.getOrNull(2)
        val timestamps = uriData.getQueryParameter("t")?.let(timestampParser::parseTimestamp)
        val sourceView = uriData.getQueryParameter(EXTRA_SOURCE_VIEW)
        val autoPlay = uriData.getQueryParameter(EXTRA_AUTO_PLAY)?.toBoolean() ?: false

        return if (episodeUuid != null) {
            // episode share link https://play.pocketcasts.com/podcasts/4eb5b260-c933-0134-10da-25324e2a541d/720079de-cce4-4b55-84d1-1be117ab1149?t=17s
            ShowEpisodeDeepLink(
                episodeUuid = episodeUuid,
                podcastUuid = podcastUuid,
                startTimestamp = timestamps?.first,
                endTimestamp = timestamps?.second,
                autoPlay = autoPlay,
                sourceView = sourceView,
            )
        } else {
            // podcast share link https://play.pocketcasts.com/podcasts/4eb5b260-c933-0134-10da-25324e2a541d
            ShowPodcastDeepLink(
                podcastUuid = podcastUuid,
                sourceView = sourceView,
            )
        }
    }
}

private class OpmlAdapter(
    excludedHosts: List<String>,
) : DeepLinkAdapter {
    private val excludedHosts = EXCLUDED_HOSTS + excludedHosts

    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_SEND) {
            val uri = IntentCompat.getParcelableExtra(intent, EXTRA_STREAM, Uri::class.java)
            uri?.let(::OpmlImportDeepLink)
        } else if (intent.action == ACTION_VIEW && uriData != null && scheme !in EXCLUDED_SCHEMES && host !in excludedHosts) {
            OpmlImportDeepLink(uriData)
        } else {
            null
        }
    }

    private companion object {
        val EXCLUDED_SCHEMES = listOf("rss", "feed", "pcast", "itpc", "http", "https")

        val EXCLUDED_HOSTS = listOf(
            "subscribe",
            "subscribehttps",
            "applink",
            "sharelist",
            "cloudfiles",
            "upgrade",
            "redeem",
            "settings",
            "subscribeonandroid.com",
            "www.subscribeonandroid.com",
            "discover",
            "open",
            "signup",
            "features",
            "developer_options",
        )
    }
}

private class ImportAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data ?: return null
        val scheme = uriData.scheme
        val host = uriData.host
        val path = uriData.path

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "settings" && path == "/import") {
            ImportDeepLink
        } else {
            null
        }
    }
}

private class AppOpenAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data ?: return null
        val scheme = uriData.scheme
        val host = uriData.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "open") {
            AppOpenDeepLink
        } else {
            null
        }
    }
}

private class DiscoverAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data ?: return null
        val scheme = uriData.scheme
        val host = uriData.host
        val path = uriData.path

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "discover") {
            when (path) {
                "/staffpicks" -> {
                    StaffPicksDeepLink
                }
                "/trending" -> {
                    TrendingDeepLink
                }
                "/recommendations" -> {
                    RecommendationsDeepLink
                }
                else -> {
                    null
                }
            }
        } else {
            null
        }
    }
}

private class PodcastUrlSchemeAdapter(
    excludedHosts: List<String>,
) : DeepLinkAdapter {
    private val excludedHosts = EXCLUDED_HOSTS + excludedHosts

    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host

        return if (intent.action == ACTION_VIEW && uriData != null && scheme in ALLOWED_SCHEMES && host !in excludedHosts) {
            ShowPodcastFromUrlDeepLink(uriData.toString())
        } else {
            null
        }
    }

    private companion object {
        val ALLOWED_SCHEMES = listOf("rss", "feed", "pcast", "itpc", "http", "https")

        val EXCLUDED_HOSTS = listOf(
            "subscribe",
            "subscribehttps",
            "applink",
            "sharelist",
            "cloudfiles",
            "upgrade",
            "redeem",
            "subscribeonandroid.com",
            "www.subscribeonandroid.com",
        )
    }
}

private class PlayFromSearchAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val query = intent.extras?.getString(SearchManager.QUERY)
        return if (intent.action == INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH && !query.isNullOrBlank()) {
            PlayFromSearchDeepLink(query)
        } else {
            null
        }
    }
}

private class AssistantAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        return if (intent.extras?.getBoolean("extra_accl_intent", false) == true || intent.extras?.getBoolean("handled_by_nga", false) == true) {
            AssistantDeepLink
        } else {
            null
        }
    }
}

private class SignInAdapter(
    private val webBaseHost: String,
) : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data
        val scheme = uriData?.scheme
        val host = uriData?.host
        val path = uriData?.path?.takeIf { it != "/" }
        val source = uriData?.getQueryParameter(EXTRA_SOURCE_VIEW)

        return if (intent.action == ACTION_VIEW && scheme in listOf("http", "https") && host == webBaseHost && path == "/sign-in") {
            SignInDeepLink(source)
        } else {
            null
        }
    }
}

private class ThemesAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data ?: return null
        val scheme = uriData.scheme
        val host = uriData.host
        val path = uriData.path

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "settings" && path == "/themes") {
            ThemesDeepLink
        } else {
            null
        }
    }
}

private class DeveloperOptionsAdapter : DeepLinkAdapter {
    override fun create(intent: Intent): DeepLink? {
        val uriData = intent.data ?: return null
        val scheme = uriData.scheme
        val host = uriData.host

        return if (intent.action == ACTION_VIEW && scheme == "pktc" && host == "developer_options") {
            DeveloperOptionsDeeplink
        } else {
            null
        }
    }
}
