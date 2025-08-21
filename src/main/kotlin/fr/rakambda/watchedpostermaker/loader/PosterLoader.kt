package fr.rakambda.watchedpostermaker.loader

import fr.rakambda.watchedpostermaker.api.TmdbApi
import java.awt.image.BufferedImage
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO

sealed interface PosterLoader {
    suspend fun loadPoster(): BufferedImage

    companion object {
        val CACHE: MutableMap<String, BufferedImage> = mutableMapOf()
    }

    class StaticPosterLoader(private val url: URL) : PosterLoader {
        override suspend fun loadPoster(): BufferedImage {
            return CACHE.getOrPut(url.toString()) { ImageIO.read(url) }
        }
    }

    class TmdbPosterLoader(private val id: Long, private val type: TmdbType) : PosterLoader {
        companion object {
            fun forMovie(id: Long) = TmdbPosterLoader(id, TmdbType.MOVIE)
            fun forTv(id: Long) = TmdbPosterLoader(id, TmdbType.TV)
        }

        enum class TmdbType {
            MOVIE,
            TV,
        }

        override suspend fun loadPoster(): BufferedImage {
            return CACHE.getOrPut("tmdb://$type-$id") {
                val path = when (type) {
                    TmdbType.MOVIE -> TmdbApi.getMovieDetails(id).posterPath
                    TmdbType.TV -> TmdbApi.getTvDetails(id).posterPath
                }
                val url = URI.create("https://image.tmdb.org/t/p/original/${path.trimStart('/')}").toURL()
                ImageIO.read(url)
            }
        }
    }
}