package fr.rakambda.watchedpostermaker.processor

import fr.rakambda.watchedpostermaker.AppConfiguration
import fr.rakambda.watchedpostermaker.api.AnilistApi
import fr.rakambda.watchedpostermaker.labeler.PosterLabeler
import fr.rakambda.watchedpostermaker.loader.PosterLoader
import fr.rakambda.watchedpostermaker.saver.PosterSaver
import fr.rakambda.watchedpostermaker.util.ExecutionCache
import fr.rakambda.watchedpostermaker.util.clone
import fr.rakambda.watchedpostermaker.util.fixed
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class AnilistProcessor(
    private val executionCache: ExecutionCache
) {
    private val logger = KotlinLogging.logger {}
    private val config = AppConfiguration.instance.anilist

    companion object {
        private const val CACHE_CATEGORY_LAST_ACTIVITY = "anilist_activity_last-date"
        private const val CACHE_CATEGORY_MEDIA_LIST_LAST_UPDATE = "anilist_media-list_last-update"
        private const val CACHE_CATEGORY_MEDIA_LIST_PROGRESS = "anilist_media-list_progress"
        private val DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
    }

    suspend fun process() {
        if (config.runFromActivity) processFromActivity()
        if (config.runFromHistory) processFromHistory()
    }

    private suspend fun processFromActivity() {
        config.output.mkdirs()

        val userId = AnilistApi.getViewerId() ?: throw IllegalStateException("Viewer id is null")
        val previousActivityDate = Instant.ofEpochSecond(executionCache.getOrDefault(CACHE_CATEGORY_LAST_ACTIVITY, userId.toString(), Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(30)).toEpochMilli().toString()).toLong())

        val activities = AnilistApi.getUserActivity(userId, previousActivityDate)
        logger.info { "Found ${activities.size} new AniList activities since $previousActivityDate" }
        activities.forEach { makePosterFromActivity(it) }

        executionCache.setValue(CACHE_CATEGORY_LAST_ACTIVITY, userId.toString(), activities.maxOfOrNull { it.createdAt }?.toString())
    }

    private suspend fun processFromHistory() {
        config.output.mkdirs()

        val userId = AnilistApi.getViewerId() ?: throw IllegalStateException("Viewer id is null")
        val previousUpdateDate = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(executionCache.getOrDefault(CACHE_CATEGORY_MEDIA_LIST_LAST_UPDATE, userId.toString(), Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(30)).toEpochMilli().toString()).toLong()),
            ZoneId.systemDefault()
        )

        val medias = AnilistApi.getUserMediaList(userId, previousUpdateDate)
        logger.info { "Found ${medias.size} new AniList media list updates since $previousUpdateDate" }
        medias.forEach { makePosterFromMediaList(it) }

        executionCache.setValue(CACHE_CATEGORY_MEDIA_LIST_LAST_UPDATE, userId.toString(), medias.maxOfOrNull { it.updatedAt }?.toString())
    }

    private suspend fun makePosterFromActivity(activity: AnilistApi.GqlResponse.ActivityData) {
        if (activity.progress == null) return
        val progress = Progress.parse(activity.progress)
        makePoster(activity.createdAt, activity.media, progress)
    }

    private suspend fun makePosterFromMediaList(activity: AnilistApi.GqlResponse.MediaListData) {
        val previousProgress = executionCache.getOrDefault(CACHE_CATEGORY_MEDIA_LIST_PROGRESS, activity.id.toString(), "0").toInt()
        val progress = Progress(previousProgress + 1, activity.progress)
        makePoster(activity.updatedAt, activity.media, progress)
        executionCache.setValue(CACHE_CATEGORY_MEDIA_LIST_PROGRESS, activity.id.toString(), activity.progress.toString())
    }

    private suspend fun makePoster(watchedAt: ZonedDateTime, media: AnilistApi.GqlResponse.Media, progress: Progress) {
        val poster = PosterLoader.StaticPosterLoader(URI.create(media.coverImage.extraLarge).toURL()).loadPoster()

        for (index in progress.start..(progress.end ?: progress.start)) {
            val text = when (media.type) {
                "ANIME" -> "E${index.fixed(2)}"
                "MANGA" -> "CH${index.fixed(3)}"
                else -> index.toString()
            }
            val outFile = config.output.resolve("${DF.format(watchedAt.withZoneSameInstant(ZoneId.systemDefault()))}-anilist-${media.id}-$index.png")
            if (outFile.exists()) continue

            this.logger.info { "Creating poster for media `${media.id}` at index `$index`. Will be saved at `$outFile`" }
            val newImage = PosterLabeler.SimplePosterLabeler().addLabel(poster.clone(), text)
            PosterSaver.StaticPosterSaver(outFile).savePoster(newImage)
        }
    }

    data class Progress(val start: Int, val end: Int?) {
        companion object {
            fun parse(progress: String): Progress {
                val regex = Regex("""(\d+)( - (\d+))?""")
                val match = regex.find(progress)
                val min = match?.groups[1]?.value?.toInt() ?: throw IllegalArgumentException("Invalid progress: $progress")
                return Progress(min, match.groups[3]?.value?.toInt())
            }
        }
    }
}