package au.com.shiftyjelly.pocketcasts.reimagine.clip

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.reimagine.clip.ShareClipViewModel.SnackbarMessage
import au.com.shiftyjelly.pocketcasts.reimagine.ui.ShareColors
import au.com.shiftyjelly.pocketcasts.reimagine.ui.rememberBackgroundAssetControler
import au.com.shiftyjelly.pocketcasts.sharing.Clip
import au.com.shiftyjelly.pocketcasts.sharing.SharingClient
import au.com.shiftyjelly.pocketcasts.sharing.SocialPlatform
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.utils.parceler.ColorParceler
import au.com.shiftyjelly.pocketcasts.utils.parceler.DurationParceler
import au.com.shiftyjelly.pocketcasts.utils.toHhMmSs
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

@AndroidEntryPoint
class ShareClipFragment : BaseDialogFragment() {
    private val args get() = requireNotNull(arguments?.let { BundleCompat.getParcelable(it, NEW_INSTANCE_ARG, Args::class.java) })

    private val shareColors get() = ShareColors(args.baseColor)

    private val viewModel by viewModels<ShareClipViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<ShareClipViewModel.Factory> { factory ->
                factory.create(
                    args.episodeUuid,
                    args.clipRange,
                    clipPlayerFactory.create(requireActivity().applicationContext),
                    sharingClient.asClipClient(),
                    clipAnalytics,
                )
            }
        },
    )

    @Inject
    lateinit var clipPlayerFactory: ClipPlayer.Factory

    @Inject
    lateinit var sharingClient: SharingClient

    @Inject
    lateinit var analyticsTracker: AnalyticsTracker

    @Inject
    lateinit var clipAnalyticsFactory: ClipAnalytics.Factory

    private lateinit var clipAnalytics: ClipAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, UR.style.Theme_ClipSharing)
        clipAnalytics = clipAnalyticsFactory.create(
            episodeId = args.episodeUuid,
            podcastId = args.podcastUuid,
            clipId = args.clipUuid,
            sourceView = args.source,
            initialClipRange = args.clipRange,
        )
        if (savedInstanceState == null) {
            viewModel.onScreenShown()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        val platforms = remember {
            SocialPlatform.getAvailablePlatforms(
                requireContext(),
                // Exclude IG video clips are available
                exclude = setOf(SocialPlatform.Instagram),
            )
        }
        val isTalkbackOn = remember { Util.isTalkbackOn(requireContext()) }

        val assetController = rememberBackgroundAssetControler(shareColors)
        val listener = remember { ShareClipListener(this@ShareClipFragment, viewModel, assetController, args.source) }
        val snackbarHostState = remember { SnackbarHostState() }

        val uiState by viewModel.uiState.collectAsState()
        ShareClipPage(
            episode = uiState.episode,
            podcast = uiState.podcast,
            clipRange = uiState.clipRange,
            playbackProgress = uiState.playbackProgress,
            isPlaying = uiState.isPlaying,
            sharingState = uiState.sharingState,
            useEpisodeArtwork = uiState.useEpisodeArtwork,
            platforms = platforms,
            shareColors = shareColors,
            useKeyboardInput = isTalkbackOn,
            assetController = assetController,
            listener = listener,
            snackbarHostState = snackbarHostState,
            onNavigationButtonClick = { analyticsTracker.track(AnalyticsEvent.SHARE_SCREEN_NAVIGATION_BUTTON_TAPPED) },
            onEditClick = { analyticsTracker.track(AnalyticsEvent.SHARE_SCREEN_EDIT_BUTTON_TAPPED) },
            onCloseClick = { analyticsTracker.track(AnalyticsEvent.SHARE_SCREEN_CLOSE_BUTTON_TAPPED) },
        )

        LaunchedEffect(Unit) {
            viewModel.snackbarMessages.collect { message ->
                val text = when (message) {
                    is SnackbarMessage.SharingResponse -> message.message
                    is SnackbarMessage.PlayerIssue -> getString(LR.string.podcast_episode_playback_error)
                    is SnackbarMessage.GenericIssue -> getString(LR.string.share_error_message)
                    is SnackbarMessage.ClipStartAfterEnd -> getString(LR.string.share_invalid_clip_message)
                    is SnackbarMessage.ClipEndAfterEpisodeDuration -> getString(LR.string.share_clip_too_long_message, message.episodeDuration.toHhMmSs())
                }
                snackbarHostState.showSnackbar(text)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDialogTint(color = shareColors.background.toArgb())
    }

    @Parcelize
    private class Args(
        val episodeUuid: String,
        val podcastUuid: String,
        val clipUuid: String,
        @TypeParceler<Duration, DurationParceler>() val clipStart: Duration,
        @TypeParceler<Duration, DurationParceler>() val clipEnd: Duration,
        @TypeParceler<Color, ColorParceler>() val baseColor: Color,
        val source: SourceView,
    ) : Parcelable {
        val clipRange get() = Clip.Range(clipStart, clipEnd)
    }

    companion object {
        private const val NEW_INSTANCE_ARG = "ShareClipFragmentArgs"

        fun newInstance(
            episode: PodcastEpisode,
            @ColorInt baseColor: Int,
            source: SourceView,
        ) = ShareClipFragment().apply {
            val clipRange = Clip.Range.fromPosition(
                playbackPosition = episode.playedUpTo.seconds,
                episodeDuration = episode.duration.seconds,
            )

            arguments = bundleOf(
                NEW_INSTANCE_ARG to Args(
                    episodeUuid = episode.uuid,
                    podcastUuid = episode.podcastUuid,
                    clipUuid = UUID.randomUUID().toString(),
                    clipStart = clipRange.start,
                    clipEnd = clipRange.end,
                    baseColor = Color(baseColor),
                    source = source,
                ),
            )
        }
    }
}
