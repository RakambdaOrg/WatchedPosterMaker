package fr.rakambda.watchedpostermaker.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.rakambda.watchedpostermaker.AppConfiguration
import fr.rakambda.watchedpostermaker.util.JacksonUtils.ISO8601ZonedDateTimeDeserializer
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
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

object TraktApi {
    private val logger = KotlinLogging.logger {}
    private val client = HttpClient {
        install(Auth) {
            bearer {
                loadTokens { BearerTokens(AppConfiguration.instance.trakt.token, null) }
                sendWithoutRequest { true }
            }
        }
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(DefaultRequest) {
            url("https://api.trakt.tv/")
            header("trakt-api-key", AppConfiguration.instance.trakt.clientId)
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

    suspend fun getUsername(): String {
        val result = handleResponse<TraktResponse.UserSettingsResponse>(client.get("/users/settings"))
        return result.second.user.username
    }

    suspend fun getUserActivity(username: String, since: Instant?): List<TraktResponse.UserHistory> {
        return pagedQuery(
            { page ->
                handleResponse<List<TraktResponse.UserHistory>>(client.get {
                    url {
                        appendPathSegments("users", username, "history")
                        parameters.append("page", page.toString())
                        parameters.append("limit", 15.toString())
                        if (since != null) parameters.append("start_at", ISO_DATE_TIME.format(ZonedDateTime.ofInstant(since, UTC)))
                    }
                }).let { Pair(it.first.headers, it.second) }
            },
            maxPages = 10
        )
    }

    private suspend inline fun <reified T> pagedQuery(
        queryRunner: suspend (page: Int) -> Pair<Headers, List<T>>,
        maxPages: Int = Int.MAX_VALUE,
        halter: (data: List<T>) -> Boolean = { false }
    ): List<T> {
        val elements: MutableList<T> = mutableListOf()
        var nextPage = 1

        do {
            val result = queryRunner(nextPage)
            elements.addAll(result.second)
            if (halter(elements)) break

            val page = result.first["X-Pagination-Page"]?.toIntOrNull() ?: break
            val maxPage = result.first["X-Pagination-Page-Count"]?.toIntOrNull() ?: break
            nextPage = page + 1
        } while (page < maxPage && page <= maxPages)

        return elements
    }

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): Pair<HttpResponse, T> {
        if (!response.status.isSuccess()) {
            logger.error { "Invalid response with status ${response.status}" }
            throw TraktApiException("Invalid Trakt response")
        }
        val body = response.body<T>()
        return Pair(response, body)
    }

    interface TraktResponse {
        data class UserSettingsResponse(
            val user: User,
        ) {
            data class User(
                val username: String,
            )
        }

        data class UserHistory(
            @field:JsonProperty("watched_at") @field:JsonDeserialize(using = ISO8601ZonedDateTimeDeserializer::class) val watchedAt: ZonedDateTime,
            val movie: Media? = null,
            val show: Media? = null,
            val episode: Episode? = null,
        ) {
            data class Media(
                val ids: MediaIds,
            ) {
                data class MediaIds(
                    val tmdb: Long,
                )
            }

            data class Episode(
                val number: Int,
                val season: Int,
            )
        }
    }

    class TraktApiException(
        message: String?,
        e: Throwable? = null,
    ) : Exception(message, e)
}
