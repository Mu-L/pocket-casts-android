package au.com.shiftyjelly.pocketcasts.podcasts.view.episode

import android.content.Context
import androidx.annotation.ColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.toLiveData
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.analytics.EpisodeAnalytics
import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.download.DownloadManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.UpNextQueue
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.shownotes.ShowNotesManager
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import au.com.shiftyjelly.pocketcasts.servers.shownotes.ShowNotesState
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.utils.Network
import au.com.shiftyjelly.pocketcasts.views.helper.WarningsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function4
import io.reactivex.schedulers.Schedulers
import java.util.Date
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlowable

@HiltViewModel
class EpisodeFragmentViewModel @Inject constructor(
    val episodeManager: EpisodeManager,
    val podcastManager: PodcastManager,
    val theme: Theme,
    val downloadManager: DownloadManager,
    val playbackManager: PlaybackManager,
    val settings: Settings,
    private val showNotesManager: ShowNotesManager,
    private val analyticsTracker: AnalyticsTracker,
    private val episodeAnalytics: EpisodeAnalytics,
    private val transcriptManager: TranscriptManager,
) : ViewModel(),
    CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private val source = SourceView.EPISODE_DETAILS
    lateinit var state: LiveData<EpisodeFragmentState>
    lateinit var showNotesState: LiveData<ShowNotesState>
    lateinit var inUpNext: LiveData<Boolean>
    val isPlaying: LiveData<Boolean> = playbackManager.playbackStateLive.map {
        it.episodeUuid == episode?.uuid && it.isPlaying
    }

    val disposables = CompositeDisposable()

    var episode: PodcastEpisode? = null
    var isFragmentChangingConfigurations: Boolean = false

    private var startPlaybackTimestamp: Duration? = null
    private var autoDispatchPlay = false

    private var loadTranscriptJob: Job? = null
    private val _transcript = MutableStateFlow<Transcript?>(null)
    val transcript = _transcript.asStateFlow()

    fun setup(
        episodeUuid: String,
        podcastUuid: String?,
        timestamp: Duration?,
        autoPlay: Boolean,
        forceDark: Boolean,
    ) {
        startPlaybackTimestamp = timestamp
        autoDispatchPlay = autoPlay
        val isDarkTheme = forceDark || theme.isDarkTheme
        val progressUpdatesObservable = downloadManager.progressUpdateRelay
            .filter { it.episodeUuid == episodeUuid }
            .map { it.downloadProgress }
            .startWith(0f)
            .toFlowable(BackpressureStrategy.LATEST)

        // If we can't find it in the database and we know the podcast uuid we can try load it
        // from the server
        val onEmptyHandler = if (podcastUuid != null) {
            podcastManager.findOrDownloadPodcastRxSingle(podcastUuid).flatMapMaybe {
                val episode = it.episodes.find { episode -> episode.uuid == episodeUuid }
                if (episode != null) {
                    Maybe.just(episode)
                } else {
                    episodeManager.downloadMissingEpisodeRxMaybe(episodeUuid, podcastUuid, PodcastEpisode(uuid = episodeUuid, publishedDate = Date()), podcastManager, downloadMetaData = true, source = source).flatMap { missingEpisode ->
                        if (missingEpisode is PodcastEpisode) {
                            Maybe.just(missingEpisode)
                        } else {
                            Maybe.empty()
                        }
                    }
                }
            }
        } else {
            Maybe.empty()
        }

        @Suppress("DEPRECATION")
        val maybeEpisode = episodeManager.findByUuidRxMaybe(episodeUuid)

        val stateObservable: Flowable<EpisodeFragmentState> = maybeEpisode
            .switchIfEmpty(onEmptyHandler)
            .flatMapPublisher { episode ->
                val zipper: Function4<PodcastEpisode, Podcast, ShowNotesState, Float, EpisodeFragmentState> = Function4 { episodeLoaded: PodcastEpisode, podcast: Podcast, showNotesState: ShowNotesState, downloadProgress: Float ->
                    val tintColor = podcast.getTintColor(isDarkTheme)
                    val podcastColor = podcast.getTintColor(isDarkTheme)
                    EpisodeFragmentState.Loaded(episodeLoaded, podcast, showNotesState, tintColor, podcastColor, downloadProgress)
                }
                return@flatMapPublisher Flowable.combineLatest(
                    episodeManager.findByUuidFlow(episodeUuid).asFlowable(),
                    podcastManager.findPodcastByUuidRxMaybe(episode.podcastUuid).toFlowable(),
                    showNotesManager.loadShowNotesFlow(podcastUuid = episode.podcastUuid, episodeUuid = episode.uuid).asFlowable(),
                    progressUpdatesObservable,
                    zipper,
                )
            }
            .doOnNext {
                if (it is EpisodeFragmentState.Loaded) {
                    if (autoDispatchPlay) {
                        val playTimestamp = startPlaybackTimestamp
                        autoDispatchPlay = false
                        startPlaybackTimestamp = null
                        play(it.episode, playTimestamp)
                    }
                    episode = it.episode
                }
            }
            .onErrorReturn { EpisodeFragmentState.Error(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())

        state = stateObservable.toLiveData()

        showNotesState = state
            .map { episodeState ->
                when (episodeState) {
                    is EpisodeFragmentState.Loaded -> episodeState.showNotesState
                    is EpisodeFragmentState.Error -> ShowNotesState.NotFound
                }
            }
            .distinctUntilChanged()

        val inUpNextObservable = playbackManager.upNextQueue.changesObservable.toFlowable(BackpressureStrategy.LATEST)
            .map { upNext -> (upNext is UpNextQueue.State.Loaded) && (upNext.episode == episode || upNext.queue.map { it.uuid }.contains(episodeUuid)) }
        inUpNext = inUpNextObservable.toLiveData()

        if (transcript.value?.episodeUuid != episodeUuid) {
            val oldJob = loadTranscriptJob
            loadTranscriptJob = launch {
                oldJob?.cancelAndJoin()
                _transcript.value = transcriptManager.loadTranscript(episodeUuid)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }

    fun deleteDownloadedEpisode() {
        episode?.let {
            launch {
                episodeManager.deleteEpisodeFile(it, playbackManager, disableAutoDownload = true, removeFromUpNext = true)
                episodeAnalytics.trackEvent(
                    event = AnalyticsEvent.EPISODE_DOWNLOAD_DELETED,
                    source = source,
                    uuid = it.uuid,
                )
            }
        }
    }

    fun shouldDownload(): Boolean {
        return episode?.let {
            it.downloadTaskId == null && !it.isDownloaded
        } ?: false
    }

    fun downloadEpisode() {
        launch {
            episode?.let {
                var analyticsEvent: AnalyticsEvent? = null
                if (it.downloadTaskId != null) {
                    episodeManager.stopDownloadAndCleanUp(it, "episode card")
                    analyticsEvent = AnalyticsEvent.EPISODE_DOWNLOAD_CANCELLED
                } else if (!it.isDownloaded) {
                    it.autoDownloadStatus = PodcastEpisode.AUTO_DOWNLOAD_STATUS_MANUAL_OVERRIDE_WIFI
                    downloadManager.addEpisodeToQueue(it, "episode card", fireEvent = true, source = source)
                    analyticsEvent = AnalyticsEvent.EPISODE_DOWNLOAD_QUEUED
                }
                episodeManager.clearPlaybackErrorBlocking(episode)
                analyticsEvent?.let { event ->
                    episodeAnalytics.trackEvent(event, source = source, uuid = it.uuid)
                }
            }
        }
    }

    fun markAsPlayedClicked(isOn: Boolean) {
        launch {
            val event: AnalyticsEvent
            episode?.let { episode ->
                if (isOn) {
                    event = AnalyticsEvent.EPISODE_MARKED_AS_PLAYED
                    episodeManager.markAsPlayedBlocking(episode, playbackManager, podcastManager)
                } else {
                    event = AnalyticsEvent.EPISODE_MARKED_AS_UNPLAYED
                    episodeManager.markAsNotPlayedBlocking(episode)
                }
                episodeAnalytics.trackEvent(event, source, episode.uuid)
            }
        }
    }

    fun addToUpNext(isOn: Boolean, addLast: Boolean = false): Boolean {
        episode?.let { episode ->
            return if (!isOn) {
                launch {
                    if (addLast) {
                        playbackManager.playLast(episode = episode, source = source)
                    } else {
                        playbackManager.playNext(episode = episode, source = source)
                    }
                }

                true
            } else {
                playbackManager.removeEpisode(episodeToRemove = episode, source = source)

                false
            }
        }

        return false
    }

    fun shouldShowUpNextDialog(): Boolean {
        return playbackManager.upNextQueue.queueEpisodes.isNotEmpty()
    }

    fun seekToTimeMs(positionMs: Int) {
        playbackManager.seekToTimeMs(positionMs)
    }

    fun isCurrentlyPlayingEpisode(): Boolean {
        return playbackManager.getCurrentEpisode()?.uuid == episode?.uuid
    }

    fun archiveClicked(isOn: Boolean) {
        launch {
            episode?.let { episode ->
                if (isOn) {
                    episodeManager.archiveBlocking(episode, playbackManager)
                    episodeAnalytics.trackEvent(AnalyticsEvent.EPISODE_ARCHIVED, source, episode.uuid)
                } else {
                    episodeManager.unarchiveBlocking(episode)
                    episodeAnalytics.trackEvent(AnalyticsEvent.EPISODE_UNARCHIVED, source, episode.uuid)
                }
            }
        }
    }

    fun shouldShowStreamingWarning(context: Context): Boolean {
        return isPlaying.value == false && episode?.isDownloaded == false && settings.warnOnMeteredNetwork.value && !Network.isUnmeteredConnection(context)
    }

    fun playClickedGetShouldClose(
        warningsHelper: WarningsHelper,
        showedStreamWarning: Boolean,
        force: Boolean = false,
        fromListUuid: String? = null,
    ): Boolean {
        episode?.let { episode ->
            val timestamp = startPlaybackTimestamp
            when {
                isPlaying.value == true -> {
                    playbackManager.pause(sourceView = source)
                    return false
                }
                timestamp != null -> {
                    startPlaybackTimestamp = null
                    autoDispatchPlay = false
                    play(episode, timestamp)
                    return true
                } else -> {
                    startPlaybackTimestamp = null
                    autoDispatchPlay = false
                    fromListUuid?.let {
                        analyticsTracker.track(AnalyticsEvent.DISCOVER_LIST_EPISODE_PLAY, mapOf(LIST_ID_KEY to it, PODCAST_ID_KEY to episode.podcastUuid))
                    }
                    playbackManager.playNow(
                        episode = episode,
                        forceStream = force,
                        showedStreamWarning = showedStreamWarning,
                        sourceView = source,
                    )
                    warningsHelper.showBatteryWarningSnackbarIfAppropriate()
                    return true
                }
            }
        }

        return false
    }

    private fun play(
        episode: BaseEpisode,
        timestamp: Duration?,
    ) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            playbackManager.playNowSync(episode, sourceView = source)
            if (timestamp != null) {
                playbackManager.seekToTimeMsSuspend(timestamp.toInt(DurationUnit.MILLISECONDS))
            }
        }
    }

    fun starClicked() {
        episode?.let { episode ->
            viewModelScope.launch {
                episodeManager.toggleStarEpisode(episode, source)
            }
        }
    }

    companion object {
        private const val LIST_ID_KEY = "list_id"
        private const val PODCAST_ID_KEY = "podcast_id"
    }
}

sealed class EpisodeFragmentState {
    data class Loaded(
        val episode: PodcastEpisode,
        val podcast: Podcast,
        val showNotesState: ShowNotesState,
        @ColorInt val tintColor: Int,
        @ColorInt val podcastColor: Int,
        val downloadProgress: Float,
    ) : EpisodeFragmentState()

    data class Error(val error: Throwable) : EpisodeFragmentState()
}
