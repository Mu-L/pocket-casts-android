package au.com.shiftyjelly.pocketcasts.reimagine.episode

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.Devices
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.reimagine.ui.BackgroundAssetController
import au.com.shiftyjelly.pocketcasts.reimagine.ui.EpisodeCard
import au.com.shiftyjelly.pocketcasts.reimagine.ui.HorizontalEpisodeCard
import au.com.shiftyjelly.pocketcasts.reimagine.ui.HorizontalSharePage
import au.com.shiftyjelly.pocketcasts.reimagine.ui.ShareColors
import au.com.shiftyjelly.pocketcasts.reimagine.ui.VerticalSharePage
import au.com.shiftyjelly.pocketcasts.sharing.CardType
import au.com.shiftyjelly.pocketcasts.sharing.SharingResponse
import au.com.shiftyjelly.pocketcasts.sharing.SocialPlatform
import au.com.shiftyjelly.pocketcasts.sharing.VisualCardType
import java.sql.Date
import java.time.Instant
import kotlinx.coroutines.launch
import au.com.shiftyjelly.pocketcasts.localization.R as LR

internal interface ShareEpisodePageListener {
    suspend fun onShare(podcast: Podcast, episode: PodcastEpisode, platform: SocialPlatform, cardType: VisualCardType): SharingResponse
    fun onClose()

    companion object {
        val Preview = object : ShareEpisodePageListener {
            override suspend fun onShare(podcast: Podcast, episode: PodcastEpisode, platform: SocialPlatform, cardType: VisualCardType) = SharingResponse(
                isSuccsessful = true,
                feedbackMessage = null,
                error = null,
            )
            override fun onClose() = Unit
        }
    }
}

@Composable
internal fun ShareEpisodePage(
    podcast: Podcast?,
    episode: PodcastEpisode?,
    useEpisodeArtwork: Boolean,
    socialPlatforms: Set<SocialPlatform>,
    shareColors: ShareColors,
    assetController: BackgroundAssetController,
    listener: ShareEpisodePageListener,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> HorizontalShareEpisodePage(
            podcast = podcast,
            episode = episode,
            useEpisodeArtwork = useEpisodeArtwork,
            socialPlatforms = socialPlatforms,
            shareColors = shareColors,
            assetController = assetController,
            listener = listener,
            snackbarHostState = snackbarHostState,
        )
        else -> VerticalShareEpisodePage(
            podcast = podcast,
            episode = episode,
            useEpisodeArtwork = useEpisodeArtwork,
            socialPlatforms = socialPlatforms,
            shareColors = shareColors,
            assetController = assetController,
            listener = listener,
            snackbarHostState = snackbarHostState,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerticalShareEpisodePage(
    podcast: Podcast?,
    episode: PodcastEpisode?,
    useEpisodeArtwork: Boolean,
    socialPlatforms: Set<SocialPlatform>,
    shareColors: ShareColors,
    assetController: BackgroundAssetController,
    listener: ShareEpisodePageListener,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    VerticalSharePage(
        shareTitle = stringResource(LR.string.share_episode_title),
        shareDescription = stringResource(LR.string.share_episode_description),
        shareColors = shareColors,
        socialPlatforms = socialPlatforms,
        snackbarHostState = snackbarHostState,
        onClose = listener::onClose,
        onShareToPlatform = { platform, cardType ->
            if (podcast != null && episode != null) {
                scope.launch {
                    val response = listener.onShare(podcast, episode, platform, cardType)
                    response.feedbackMessage?.let { snackbarHostState.showSnackbar(it) }
                }
            }
        },
        middleContent = { cardType, cardSize, modifier ->
            if (podcast != null && episode != null) {
                val captureController = assetController.captureController(cardType)
                EpisodeCard(
                    cardType = cardType,
                    podcast = podcast,
                    episode = episode,
                    useEpisodeArtwork = useEpisodeArtwork,
                    shareColors = shareColors,
                    captureController = captureController,
                    constrainedSize = { _, _ -> cardSize },
                    modifier = modifier,
                )
            }
        },
    )
}

@Composable
private fun HorizontalShareEpisodePage(
    podcast: Podcast?,
    episode: PodcastEpisode?,
    useEpisodeArtwork: Boolean,
    socialPlatforms: Set<SocialPlatform>,
    shareColors: ShareColors,
    assetController: BackgroundAssetController,
    listener: ShareEpisodePageListener,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    HorizontalSharePage(
        shareTitle = stringResource(LR.string.share_episode_title),
        shareDescription = stringResource(LR.string.share_episode_description),
        shareColors = shareColors,
        socialPlatforms = socialPlatforms,
        snackbarHostState = snackbarHostState,
        onClose = listener::onClose,
        onShareToPlatform = { platform, cardType ->
            if (podcast != null && episode != null) {
                scope.launch {
                    val response = listener.onShare(podcast, episode, platform, cardType)
                    response.feedbackMessage?.let { snackbarHostState.showSnackbar(it) }
                }
            }
        },
        middleContent = {
            if (podcast != null && episode != null) {
                HorizontalEpisodeCard(
                    podcast = podcast,
                    episode = episode,
                    useEpisodeArtwork = useEpisodeArtwork,
                    shareColors = shareColors,
                    constrainedSize = { maxWidth, maxHeight -> DpSize(maxWidth.coerceAtMost(400.dp), maxHeight) },
                    captureController = assetController.captureController(CardType.Horizontal),
                )
            }
        },
    )
}

@Preview(name = "ShareEpisodeVerticalRegularPreview", device = Devices.PORTRAIT_REGULAR)
@Composable
private fun ShareEpisodeVerticalRegularPreview() = ShareEpisodePagePreview()

@Preview(name = "ShareEpisodeVerticalSmallPreview", device = Devices.PORTRAIT_SMALL)
@Composable
private fun ShareEpisodeVerticalSmallPreviewPreview() = ShareEpisodePagePreview()

@Preview(name = "ShareEpisodeVerticalTabletPreview", device = Devices.PORTRAIT_TABLET)
@Composable
private fun ShareEpisodeVerticalTabletPreview() = ShareEpisodePagePreview()

@Preview(name = "ShareEpisodeHorizontalRegularPreview", device = Devices.LANDSCAPE_REGULAR)
@Composable
private fun ShareEpisodeHorizontalRegularPreview() = ShareEpisodePagePreview()

@Preview(name = "ShareEpisodeHorizontalSmallPreview", device = Devices.LANDSCAPE_SMALL)
@Composable
private fun ShareEpisodeHorizontalSmallPreviewPreview() = ShareEpisodePagePreview()

@Preview(name = "ShareEpisodeHorizontalTabletPreview", device = Devices.LANDSCAPE_TABLET)
@Composable
private fun ShareEpisodeHorizontalTabletPreview() = ShareEpisodePagePreview()

@Composable
private fun ShareEpisodePagePreview(
    color: Long = 0xFFEC0404,
) = ShareEpisodePage(
    podcast = Podcast(
        uuid = "podcast-id",
        title = "Podcast title",
    ),
    episode = PodcastEpisode(
        uuid = "episode-id",
        podcastUuid = "podcast-id",
        publishedDate = Date.from(Instant.parse("2024-12-03T10:15:30.00Z")),
        title = "Episode title",
    ),
    useEpisodeArtwork = false,
    socialPlatforms = SocialPlatform.entries.toSet(),
    shareColors = ShareColors(Color(color)),
    assetController = BackgroundAssetController.preview(),
    listener = ShareEpisodePageListener.Preview,
)
