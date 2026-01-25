package fr.rakambda.watchedpostermaker.processor

import fr.rakambda.watchedpostermaker.AppConfiguration
import fr.rakambda.watchedpostermaker.api.TraktApi
import fr.rakambda.watchedpostermaker.labeler.PosterLabeler
import fr.rakambda.watchedpostermaker.loader.PosterLoader
import fr.rakambda.watchedpostermaker.saver.PosterSaver
import fr.rakambda.watchedpostermaker.util.ExecutionCache
import fr.rakambda.watchedpostermaker.util.clone
import fr.rakambda.watchedpostermaker.util.fixed
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class TraktProcessor(
    private val executionCache: ExecutionCache
) {
    private val logger = KotlinLogging.logger {}
    private val config = AppConfiguration.instance.trakt
    private val seasonCache = mutableMapOf<Long, List<TraktApi.TraktResponse.ShowSeason>>()

    companion object {
        private const val CACHE_CATEGORY_LAST_ACTIVITY = "trakt_activity_last-date"
        private val DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
    }

    suspend fun process() {
        if (config.runFromActivity) processFromActivity()
    }

    private suspend fun processFromActivity() {
        logger.info { "Processing Trakt from activity" }
        config.output.mkdirs()

        val username = TraktApi.getUsername()
        val previousActivityDate = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(executionCache.getOrDefault(CACHE_CATEGORY_LAST_ACTIVITY, username, Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(30)).toEpochMilli().toString()).toLong()),
            ZoneId.systemDefault()
        )

        val activities = TraktApi.getUserActivity(username, previousActivityDate.plusSeconds(1))
        logger.info { "Found ${activities.size} new Trakt activities since $previousActivityDate" }
        activities.sortedBy { it.watchedAt }.forEach { makePosterFromActivity(it) }

        activities.maxOfOrNull { it.watchedAt }?.toInstant()?.toEpochMilli()?.let { executionCache.setValue(CACHE_CATEGORY_LAST_ACTIVITY, username, it.toString()) }
    }

    private suspend fun makePosterFromActivity(activity: TraktApi.TraktResponse.UserHistory) {
        activity.movie?.let { makePosterFromMovie(activity.watchedAt, it) }
        if (activity.show != null && activity.episode != null) {
            makePosterFromShow(activity.watchedAt, activity.show, activity.episode)
        }
    }

    private suspend fun makePosterFromMovie(watchedAt: ZonedDateTime, media: TraktApi.TraktResponse.UserHistory.Media) {
        makePoster(watchedAt, media, null, PosterLoader.TmdbPosterLoader.forMovie(media.ids.tmdb))
    }

    private suspend fun makePosterFromShow(watchedAt: ZonedDateTime, media: TraktApi.TraktResponse.UserHistory.Media, episode: TraktApi.TraktResponse.UserHistory.Episode) {
        val text = if (config.activityOnlyCompleted) {
            val season = seasonCache.getOrPut(media.ids.trakt) { TraktApi.getShowSeasons(media.ids.trakt.toString()) }
            val episodeCount = season.first { it.number == episode.season }.episodeCount
            if (episode.number != episodeCount) return
            "S${episode.season.fixed(2)}"
        } else "S${episode.season.fixed(2)}E${episode.number.fixed(2)}"

        makePoster(watchedAt, media, text, PosterLoader.TmdbPosterLoader.forTv(media.ids.tmdb, episode.season))
    }

    private suspend fun makePoster(watchedAt: ZonedDateTime, media: TraktApi.TraktResponse.UserHistory.Media, text: String?, posterLoader: PosterLoader) {
        val outFile = config.output.resolve("${DF.format(watchedAt.withZoneSameInstant(ZoneId.systemDefault()))}-trakt-${media.ids.tmdb}-$text.png")
        if (outFile.exists()) return

        logger.info { "Creating poster for media `${media.ids.tmdb}` at `$text`. Will be saved at `$outFile`" }
        val poster = posterLoader.loadPoster()

        val newImage = PosterLabeler.SimplePosterLabeler().addLabel(poster.clone(), text)
        PosterSaver.StaticPosterSaver(outFile).savePoster(newImage)
    }
}