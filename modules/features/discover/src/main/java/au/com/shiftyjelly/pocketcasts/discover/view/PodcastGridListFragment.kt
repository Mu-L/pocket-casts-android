package au.com.shiftyjelly.pocketcasts.discover.view

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.analytics.discoverListPodcastSubscribed
import au.com.shiftyjelly.pocketcasts.analytics.discoverListPodcastTapped
import au.com.shiftyjelly.pocketcasts.discover.R
import au.com.shiftyjelly.pocketcasts.discover.util.DiscoverDeepLinkManager.Companion.STAFF_PICKS_LIST_ID
import au.com.shiftyjelly.pocketcasts.discover.view.DiscoverFragment.Companion.EPISODE_UUID_KEY
import au.com.shiftyjelly.pocketcasts.discover.view.DiscoverFragment.Companion.LIST_ID_KEY
import au.com.shiftyjelly.pocketcasts.discover.view.DiscoverFragment.Companion.PODCAST_UUID_KEY
import au.com.shiftyjelly.pocketcasts.discover.view.DiscoverFragment.Companion.SOURCE_KEY
import au.com.shiftyjelly.pocketcasts.discover.view.DiscoverFragment.Companion.UUID_KEY
import au.com.shiftyjelly.pocketcasts.discover.viewmodel.PodcastListViewModel
import au.com.shiftyjelly.pocketcasts.localization.helper.tryToLocalise
import au.com.shiftyjelly.pocketcasts.models.type.EpisodeViewSource
import au.com.shiftyjelly.pocketcasts.podcasts.view.episode.EpisodeContainerFragment
import au.com.shiftyjelly.pocketcasts.podcasts.view.podcast.PodcastFragment
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.notification.NotificationManager
import au.com.shiftyjelly.pocketcasts.repositories.notification.OnboardingNotificationType
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.servers.model.DiscoverEpisode
import au.com.shiftyjelly.pocketcasts.servers.model.DiscoverPodcast
import au.com.shiftyjelly.pocketcasts.servers.model.DisplayStyle
import au.com.shiftyjelly.pocketcasts.servers.model.ExpandedStyle
import au.com.shiftyjelly.pocketcasts.servers.model.ListFeed
import au.com.shiftyjelly.pocketcasts.servers.model.ListType
import au.com.shiftyjelly.pocketcasts.servers.model.NetworkLoadableList
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.ui.images.ThemedImageTintTransformation
import au.com.shiftyjelly.pocketcasts.views.activity.WebViewActivity
import au.com.shiftyjelly.pocketcasts.views.extensions.hide
import au.com.shiftyjelly.pocketcasts.views.extensions.show
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import coil.load
import coil.transform.CircleCropTransformation
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber
import au.com.shiftyjelly.pocketcasts.localization.R as LR

open class PodcastGridListFragment :
    BaseFragment(),
    Toolbar.OnMenuItemClickListener {

    @Inject lateinit var podcastManager: PodcastManager

    @Inject lateinit var settings: Settings

    @Inject lateinit var analyticsTracker: AnalyticsTracker

    @Inject lateinit var notificationManager: NotificationManager

    companion object {
        internal const val ARG_LIST_UUID = "listUuid"
        internal const val ARG_TITLE = "title"
        internal const val ARG_SOURCE_URL = "url"
        internal const val ARG_LIST_TYPE = "listType"
        internal const val ARG_DISPLAY_STYLE = "displayStyle"
        internal const val ARG_EXPANDED_STYLE = "expandedStyle"
        internal const val ARG_BACKGROUND_COLOR = "backgroundColor"
        internal const val ARG_TAGLINE = "tagline"
        internal const val ARG_CURATED = "curated"
        internal const val ARG_INFERRED_ID = "inferredId"
        internal const val ARG_AUTHENTICATED = "authenticated"

        fun newInstanceBundle(
            networkLoadableList: NetworkLoadableList,
        ): Bundle {
            return Bundle().apply {
                putString(ARG_LIST_UUID, networkLoadableList.listUuid)
                putString(ARG_INFERRED_ID, networkLoadableList.inferredId())
                putString(ARG_TITLE, networkLoadableList.title)
                putString(ARG_SOURCE_URL, networkLoadableList.source)
                putString(ARG_LIST_TYPE, networkLoadableList.type.stringValue)
                putString(ARG_DISPLAY_STYLE, networkLoadableList.displayStyle.stringValue)
                putString(ARG_EXPANDED_STYLE, networkLoadableList.expandedStyle.stringValue)
                putString(ARG_TAGLINE, networkLoadableList.expandedTopItemLabel)
                putBoolean(ARG_CURATED, networkLoadableList.curated)
                putBoolean(ARG_AUTHENTICATED, networkLoadableList.authenticated ?: false)
            }
        }
    }

    val listType: ListType
        get() = arguments?.getString(ARG_LIST_TYPE)?.let { ListType.fromString(it) } ?: ListType.PodcastList

    val displayStyle: DisplayStyle
        get() = DisplayStyle.fromString(arguments?.getString(ARG_DISPLAY_STYLE)!!) ?: DisplayStyle.SmallList()

    val expandedStyle: ExpandedStyle
        get() = ExpandedStyle.fromString(arguments?.getString(ARG_EXPANDED_STYLE)) ?: ExpandedStyle.PlainList()

    val backgroundColor: String?
        get() = arguments?.getString(ARG_BACKGROUND_COLOR)

    val tagline: String?
        get() = arguments?.getString(ARG_TAGLINE)

    val listUuid: String?
        get() = arguments?.getString(ARG_LIST_UUID)

    val inferredId: String
        get() = arguments?.getString(ARG_INFERRED_ID) ?: NetworkLoadableList.Companion.NONE

    val sourceUrl: String?
        get() = arguments?.getString(ARG_SOURCE_URL)

    val shareUrl: String?
        get() = sourceUrl?.replace("(\\.json)?".toRegex(), "")

    val curated: Boolean
        get() = arguments?.getBoolean(ARG_CURATED) ?: false

    protected val viewModel: PodcastListViewModel by viewModels()

    val onPodcastClicked: (DiscoverPodcast) -> Unit = { podcast ->
        val listDate = viewModel.listFeed?.date
        analyticsTracker.discoverListPodcastTapped(podcastUuid = podcast.uuid, listId = listUuid, listDate = listDate)
        val sourceView = when (expandedStyle) {
            is ExpandedStyle.RankedList -> SourceView.DISCOVER_RANKED_LIST
            is ExpandedStyle.PlainList -> SourceView.DISCOVER_PLAIN_LIST
            else -> SourceView.DISCOVER
        }
        val fragment = PodcastFragment.newInstance(
            podcastUuid = podcast.uuid,
            fromListUuid = listUuid,
            fromListDate = listDate,
            sourceView = sourceView,
        )
        (activity as FragmentHostListener).addFragment(fragment)
    }

    val onPodcastSubscribe: (String) -> Unit = { podcastUuid ->
        analyticsTracker.discoverListPodcastSubscribed(podcastUuid = podcastUuid, listId = listUuid, listDate = viewModel.listFeed?.date)
        var podcastSubscribedSource = SourceView.DISCOVER
        if (expandedStyle is ExpandedStyle.RankedList) {
            podcastSubscribedSource = SourceView.DISCOVER_RANKED_LIST
        } else if (expandedStyle is ExpandedStyle.PlainList) {
            podcastSubscribedSource = SourceView.DISCOVER_PLAIN_LIST
        }
        analyticsTracker.track(AnalyticsEvent.PODCAST_SUBSCRIBED, mapOf(SOURCE_KEY to podcastSubscribedSource.analyticsValue, UUID_KEY to podcastUuid))
        podcastManager.subscribeToPodcast(podcastUuid, sync = true)
    }

    val onEpisodeClick: (DiscoverEpisode) -> Unit = { episode ->
        listUuid?.let { listUuid ->
            analyticsTracker.track(
                AnalyticsEvent.DISCOVER_LIST_EPISODE_TAPPED,
                mapOf(LIST_ID_KEY to listUuid, PODCAST_UUID_KEY to episode.podcast_uuid, EPISODE_UUID_KEY to episode.uuid),
            )
        }
        val fragment = EpisodeContainerFragment.newInstance(
            episodeUuid = episode.uuid,
            source = EpisodeViewSource.DISCOVER,
            podcastUuid = episode.podcast_uuid,
            fromListUuid = listUuid,
        )
        fragment.show(parentFragmentManager, "episode_card")
    }

    val onEpisodePlayClick: (DiscoverEpisode) -> Unit = { episode ->
        viewModel.findOrDownloadEpisode(episode) { databaseEpisode ->
            viewModel.playEpisode(databaseEpisode)
        }
    }

    val onEpisodeStopClick: () -> Unit = {
        viewModel.stopPlayback()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (listUuid == STAFF_PICKS_LIST_ID) {
            lifecycleScope.launch {
                notificationManager.updateUserFeatureInteraction(OnboardingNotificationType.StaffPicks)
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.share_list) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareUrl ?: "")
            }
            analyticsTracker.track(AnalyticsEvent.DISCOVER_LIST_SHARE_TAPPED)
            startActivity(Intent.createChooser(intent, getString(LR.string.podcasts_share_via)))
            return true
        }
        return false
    }

    protected fun updateCollectionHeaderView(
        listFeed: ListFeed,
        headshotImageView: ImageView,
        headerImageView: ImageView,
        tintImageView: ImageView,
        titleTextView: TextView,
        subTitleTextView: TextView,
        bodyTextView: TextView,
        linkView: ConstraintLayout,
        linkTextView: TextView,
        toolbar: Toolbar,
    ) {
        toolbar.title = listFeed.subtitle?.tryToLocalise(resources)
        toolbar.menu.findItem(R.id.share_list)?.isVisible = curated

        subTitleTextView.text = listFeed.subtitle?.uppercase()
        titleTextView.text = listFeed.title
        bodyTextView.text = listFeed.description

        // website
        val linkTitle = listFeed.webLinkTitle
        val linkUrl = listFeed.webLinkUrl
        if (linkTitle != null && linkUrl != null) {
            linkView.visibility = View.VISIBLE
            linkTextView.text = linkTitle
            linkView.setOnClickListener {
                analyticsTracker.track(AnalyticsEvent.DISCOVER_COLLECTION_LINK_TAPPED, mapOf(LIST_ID_KEY to inferredId))
                WebViewActivity.show(context, linkTitle, linkUrl)
            }
        }

        // circular headshot image
        val headshotImageUrl = listFeed.collectionImageUrl
        headshotImageView.apply {
            if (headshotImageUrl == null) {
                hide()
            } else {
                show()
                load(headshotImageUrl) {
                    transformations(ThemedImageTintTransformation(context), CircleCropTransformation())
                }
            }
        }

        val headerImageUrl = listFeed.headerImageUrl
        if (headerImageUrl == null) {
            // use the background collage image if background hasn't been manually added
            val backgroundImageUrl = listFeed.collageImages?.find { collage -> collage.key == "mobile" }?.imageUrl
            if (backgroundImageUrl != null) {
                headerImageView.load(backgroundImageUrl) {
                    transformations(ThemedImageTintTransformation(headerImageView.context))
                }
            }
            // tint the header background image if there is also a headshot
            headerImageView.colorFilter = if (headshotImageUrl == null) {
                null
            } else {
                val colorMatrix = ColorMatrix().apply { setSaturation(0.0f) }
                ColorMatrixColorFilter(colorMatrix)
            }
        } else {
            headerImageView.load(headerImageUrl)
            headerImageView.alpha = 1f
            tintImageView.hide()
        }

        listFeed.tintColors?.let { tintColors ->
            try {
                val tintColor = tintColors.tintColorInt(theme.isDarkTheme) ?: return@let
                subTitleTextView.setTextColor(tintColor)
                tintImageView.setBackgroundColor(tintColor)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}
