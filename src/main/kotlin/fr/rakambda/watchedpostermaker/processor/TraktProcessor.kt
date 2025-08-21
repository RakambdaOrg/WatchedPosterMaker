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
import java.util.concurrent.TimeUnit

class TraktProcessor(
    private val executionCache: ExecutionCache
) {
    private val logger = KotlinLogging.logger {}
    private val config = AppConfiguration.instance.trakt

    companion object {
        private const val CACHE_CATEGORY_LAST_ACTIVITY = "trakt_activity_last-date"
    }

    suspend fun process() {
        if (config.runFromActivity) processFromActivity()
    }

    private suspend fun processFromActivity() {
        config.output.mkdirs()

        val username = TraktApi.getUsername()
        val previousActivityDate = Instant.ofEpochMilli(executionCache.getOrDefault(CACHE_CATEGORY_LAST_ACTIVITY, username, Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(30)).toEpochMilli().toString()).toLong())

        val activities = TraktApi.getUserActivity(username, previousActivityDate.plusSeconds(1))
        logger.info { "Found ${activities.size} new Trakt activities" }
        activities.forEach { makePosterFromActivity(it) }

        executionCache.setValue(CACHE_CATEGORY_LAST_ACTIVITY, username, activities.maxOfOrNull { it.watchedAt }?.toInstant()?.toEpochMilli()?.toString())
    }

    private suspend fun makePosterFromActivity(activity: TraktApi.TraktResponse.UserHistory) {
        activity.movie?.let { makePosterFromMovie(it) }
        if (activity.show != null && activity.episode != null) makePosterFromShow(activity.show, activity.episode)
    }

    private suspend fun makePosterFromMovie(media: TraktApi.TraktResponse.UserHistory.Media) {
        makePoster(media, "", PosterLoader.TmdbPosterLoader.forMovie(media.ids.tmdb))
    }

    private suspend fun makePosterFromShow(media: TraktApi.TraktResponse.UserHistory.Media, episode: TraktApi.TraktResponse.UserHistory.Episode) {
        makePoster(media, "S${episode.season.fixed(2)}E${episode.number.fixed(2)}", PosterLoader.TmdbPosterLoader.forTv(media.ids.tmdb))
    }

    private suspend fun makePoster(media: TraktApi.TraktResponse.UserHistory.Media, text: String, posterLoader: PosterLoader) {
        val outFile = config.output.resolve("trakt-${media.ids.tmdb}-$text.png")
        if (outFile.exists()) return

        logger.info { "Creating poster for media `${media.ids.tmdb}` at `$text`. Will be saved at `$outFile`" }
        val poster = posterLoader.loadPoster()

        val newImage = PosterLabeler.SimplePosterLabeler().addLabel(poster.clone(), text)
        PosterSaver.StaticPosterSaver(outFile).savePoster(newImage)
    }
}