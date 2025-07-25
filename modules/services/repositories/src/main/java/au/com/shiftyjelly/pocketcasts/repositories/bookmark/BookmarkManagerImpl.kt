package au.com.shiftyjelly.pocketcasts.repositories.bookmark

import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.models.db.AppDatabase
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.models.entity.Bookmark
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.type.SyncStatus
import au.com.shiftyjelly.pocketcasts.preferences.model.BookmarksSortTypeDefault
import au.com.shiftyjelly.pocketcasts.preferences.model.BookmarksSortTypeForPodcast
import au.com.shiftyjelly.pocketcasts.preferences.model.BookmarksSortTypeForProfile
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class BookmarkManagerImpl @Inject constructor(
    appDatabase: AppDatabase,
    private val analyticsTracker: AnalyticsTracker,
) : BookmarkManager,
    CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override var sourceView = SourceView.UNKNOWN

    private val bookmarkDao = appDatabase.bookmarkDao()

    /**
     * Add a bookmark for the given episode.
     */
    override suspend fun add(
        episode: BaseEpisode,
        timeSecs: Int,
        title: String,
        creationSource: BookmarkManager.CreationSource,
        addedAt: Instant,
    ): Bookmark {
        // Prevent adding more than one bookmark at the same place
        val existingBookmark = findByEpisodeTime(episode = episode, timeSecs = timeSecs)
        if (existingBookmark != null) {
            return existingBookmark
        }
        val addedAtMs = addedAt.toEpochMilli()
        val bookmark = Bookmark(
            uuid = UUID.randomUUID().toString(),
            episodeUuid = episode.uuid,
            podcastUuid = episode.podcastOrSubstituteUuid,
            timeSecs = timeSecs,
            createdAt = Date.from(addedAt),
            title = title,
            titleModified = addedAtMs,
            deleted = false,
            deletedModified = addedAtMs,
            syncStatus = SyncStatus.NOT_SYNCED,
        )
        bookmarkDao.insert(bookmark)
        val podcastUuid = if (episode is PodcastEpisode) episode.podcastOrSubstituteUuid else "user_file"
        analyticsTracker.track(
            AnalyticsEvent.BOOKMARK_CREATED,
            mapOf(
                "source" to creationSource.analyticsValue,
                "time" to addedAtMs,
                "episode_uuid" to episode.uuid,
                "podcast_uuid" to podcastUuid,
            ),
        )
        return bookmark
    }

    override suspend fun updateTitle(bookmarkUuid: String, title: String) {
        bookmarkDao.updateTitle(
            bookmarkUuid = bookmarkUuid,
            title = title,
            titleModified = System.currentTimeMillis(),
            syncStatus = SyncStatus.NOT_SYNCED,
        )
        analyticsTracker.track(
            AnalyticsEvent.BOOKMARK_UPDATE_TITLE,
            mapOf("source" to sourceView.analyticsValue),
        )
    }

    /**
     * Find the bookmark by its UUID.
     */
    override suspend fun findBookmark(
        bookmarkUuid: String,
        deleted: Boolean,
    ): Bookmark? {
        return bookmarkDao.findByUuid(bookmarkUuid, deleted)
    }

    /**
     * Find the bookmark by episode and time
     */
    override suspend fun findByEpisodeTime(episode: BaseEpisode, timeSecs: Int): Bookmark? {
        return bookmarkDao.findByEpisodeTime(podcastUuid = episode.podcastOrSubstituteUuid, episodeUuid = episode.uuid, timeSecs = timeSecs)
    }

    /**
     * Find all bookmarks for the given episode. The flow will be updated when the bookmarks change.
     */
    override suspend fun findEpisodeBookmarksFlow(
        episode: BaseEpisode,
        sortType: BookmarksSortTypeDefault,
    ) = when (sortType) {
        BookmarksSortTypeDefault.DATE_ADDED_NEWEST_TO_OLDEST ->
            bookmarkDao.findByEpisodeOrderCreatedAtFlow(
                podcastUuid = episode.podcastOrSubstituteUuid,
                episodeUuid = episode.uuid,
                isAsc = false,
            )

        BookmarksSortTypeDefault.DATE_ADDED_OLDEST_TO_NEWEST ->
            bookmarkDao.findByEpisodeOrderCreatedAtFlow(
                podcastUuid = episode.podcastOrSubstituteUuid,
                episodeUuid = episode.uuid,
                isAsc = true,
            )

        BookmarksSortTypeDefault.TIMESTAMP ->
            bookmarkDao.findByEpisodeOrderTimeFlow(
                podcastUuid = episode.podcastOrSubstituteUuid,
                episodeUuid = episode.uuid,
            )
    }

    /**
     * Find all bookmarks for the given podcast.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun findPodcastBookmarksFlow(
        podcastUuid: String,
        sortType: BookmarksSortTypeForPodcast,
    ) = when (sortType) {
        BookmarksSortTypeForPodcast.DATE_ADDED_NEWEST_TO_OLDEST ->
            bookmarkDao.findByPodcastOrderCreatedAtFlow(
                podcastUuid = podcastUuid,
                isAsc = false,
            ).flatMapLatest { helper -> flowOf(helper.map { it.toBookmark() }) }

        BookmarksSortTypeForPodcast.DATE_ADDED_OLDEST_TO_NEWEST ->
            bookmarkDao.findByPodcastOrderCreatedAtFlow(
                podcastUuid = podcastUuid,
                isAsc = true,
            ).flatMapLatest { helper -> flowOf(helper.map { it.toBookmark() }) }

        BookmarksSortTypeForPodcast.EPISODE ->
            bookmarkDao.findByPodcastOrderEpisodeAndTimeFlow(
                podcastUuid = podcastUuid,
            ).flatMapLatest { helper -> flowOf(helper.map { it.toBookmark() }) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun findBookmarksFlow(
        sortType: BookmarksSortTypeForProfile,
    ): Flow<List<Bookmark>> = when (sortType) {
        BookmarksSortTypeForProfile.DATE_ADDED_NEWEST_TO_OLDEST ->
            bookmarkDao.findAllBookmarksOrderByCreatedAtFlow(
                isAsc = false,
            )

        BookmarksSortTypeForProfile.DATE_ADDED_OLDEST_TO_NEWEST ->
            bookmarkDao.findAllBookmarksOrderByCreatedAtFlow(
                isAsc = true,
            )

        BookmarksSortTypeForProfile.PODCAST_AND_EPISODE ->
            bookmarkDao.findAllBookmarksByOrderPodcastAndEpisodeFlow()
                .flatMapLatest { helper -> flowOf(helper.map { it.toBookmark() }) }
    }

    override suspend fun searchInPodcastByTitle(podcastUuid: String, title: String) = bookmarkDao.searchInPodcastByTitle(podcastUuid, "%$title%").map { it.uuid }

    override suspend fun searchByBookmarkOrEpisodeTitle(title: String) = bookmarkDao.searchByBookmarkOrEpisodeTitle("%$title%").map { it.uuid }

    /**
     * Mark the bookmark as deleted so it can be synced to other devices.
     */
    override suspend fun deleteToSync(bookmarkUuid: String) {
        bookmarkDao.updateDeleted(
            uuid = bookmarkUuid,
            deleted = true,
            deletedModified = System.currentTimeMillis(),
            syncStatus = SyncStatus.NOT_SYNCED,
        )
    }

    /**
     * Remove the bookmark from the database.
     */
    override suspend fun deleteSynced(bookmarkUuid: String) {
        bookmarkDao.deleteByUuid(bookmarkUuid)
    }

    /**
     * Find all bookmarks that need to be synced.
     */
    override fun findBookmarksToSyncBlocking(): List<Bookmark> {
        return bookmarkDao.findNotSyncedBlocking()
    }

    /**
     * Upsert the bookmarks from the server. The insert will replace the bookmark if the UUID already exists.
     */
    override suspend fun upsertSynced(bookmark: Bookmark): Bookmark {
        bookmarkDao.insert(bookmark)
        return bookmark
    }

    override fun findUserEpisodesBookmarksFlow() = bookmarkDao.findUserEpisodesBookmarksFlow()
}
