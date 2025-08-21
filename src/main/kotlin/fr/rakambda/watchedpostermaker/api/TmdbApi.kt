package fr.rakambda.watchedpostermaker.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import fr.rakambda.watchedpostermaker.AppConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*

object TmdbApi {
    private val logger = KotlinLogging.logger {}
    private val client = HttpClient {
        install(Auth) {
            bearer {
                loadTokens { BearerTokens(AppConfiguration.instance.tmdb.token, null) }
                sendWithoutRequest { true }
            }
        }
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(DefaultRequest) {
            url("https://api.themoviedb.org/3/")
        }
        install(Logging) {
            level = LogLevel.NONE
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
        }
        install(UserAgent) {
            agent = "WatchedPosterMaker"
        }
        followRedirects = true
    }

    suspend fun getMovieDetails(id: Long): MediaDetails {
        return handleResponse<MediaDetails>(client.get {
            url {
                appendPathSegments("movie", id.toString())
            }
        }).second
    }

    suspend fun getTvDetails(id: Long): MediaDetails {
        return handleResponse<MediaDetails>(client.get {
            url {
                appendPathSegments("tv", id.toString())
            }
        }).second
    }

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): Pair<HttpResponse, T> {
        if (!response.status.isSuccess()) {
            logger.error { "Invalid response with status ${response.status}" }
            throw TmdbApiException("Invalid Tmdb response")
        }
        val body = response.body<T>()
        return Pair(response, body)
    }

    data class MediaDetails(
        @field:JsonProperty("poster_path") val posterPath: String,
    )

    class TmdbApiException(
        message: String?,
        e: Throwable? = null,
    ) : Exception(message, e)
}
