package fr.rakambda.watchedpostermaker.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.rakambda.watchedpostermaker.AppConfiguration
import fr.rakambda.watchedpostermaker.util.GraphQlUtils
import fr.rakambda.watchedpostermaker.util.JacksonUtils
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

object AnilistApi {
    private val logger = KotlinLogging.logger {}
    private val client = HttpClient {
        install(Auth) {
            bearer {
                loadTokens { BearerTokens(AppConfiguration.instance.anilist.token, null) }
                sendWithoutRequest { true }
            }
        }
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(DefaultRequest) {
            url("https://graphql.anilist.co")
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

    suspend fun getViewerId(): Int? {
        val result = postGql<GqlResponse.ViewerData>("viewer", emptyMap())
        return result.data?.viewer?.id
    }

    suspend fun getUserActivity(
        userId: Int,
        since: ZonedDateTime = ZonedDateTime.ofInstant(Instant.EPOCH, UTC),
    ): List<GqlResponse.ActivityData> {
        val result = postPagedGql<GqlResponse.ActivityData>(
            "activity",
            mapOf(
                "userId" to userId,
                "date" to since.toEpochSecond()
            )
        )
        return result
    }

    suspend fun getUserMediaList(
        userId: Int,
        since: ZonedDateTime = ZonedDateTime.ofInstant(Instant.EPOCH, UTC),
    ): List<GqlResponse.MediaListData> {
        val result = postPagedGql<GqlResponse.MediaListData>(
            "media-list",
            mapOf(
                "userId" to userId,
            ),
            halter = { l -> l.any { it.updatedAt.isBefore(since) } }
        )
        return result.filterNot { since.isAfter(it.updatedAt) }
    }

    private suspend inline fun <reified T> postPagedGql(
        queryName: String,
        variables: Map<String, Any>,
        perPage: Int = 150,
        maxPages: Int = Int.MAX_VALUE,
        halter: (data: List<T>) -> Boolean = { false }
    ): List<T> {
        val elements: MutableList<T> = mutableListOf()
        var currentPage = 1

        do {
            val pagedVariables = variables.toMutableMap()
            pagedVariables["perPage"] = perPage
            pagedVariables["page"] = currentPage

            val response = postGql<GqlResponse.PagedData<T>>(queryName, pagedVariables)
            val newElements = response.data?.page?.elements ?: emptyList()
            elements.addAll(newElements)
            currentPage++
            if (halter(elements)) break
        } while (response.data?.page?.pageInfo?.hasNextPage ?: false && currentPage <= maxPages)

        return elements
    }

    private suspend inline fun <reified T> postGql(queryName: String, variables: Map<String, Any>): GqlResponse<T> {
        val resource = AnilistApi::class.java.getResource("/api/anilist/gql/query/$queryName.gql")
            ?: throw AnilistApiException("Query $queryName not found")
        val payload = GqlQuery(GraphQlUtils.readQuery(resource), variables)
        val response = client.post {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        return handleResponse<GqlResponse<T>>(response).second
    }

    private suspend inline fun <reified T : GqlResponse<*>> handleResponse(response: HttpResponse): Pair<HttpResponse, T> {
        if (!response.status.isSuccess()) {
            logger.error { "Invalid response with status ${response.status}" }
            throw AnilistApiException("Invalid Anilist response")
        }
        val body = response.body<T>()
        if (body.errors != null && body.errors.isNotEmpty()) {
            logger.error { "Response is not a success: $body" }
            throw AnilistApiException("Error Anilist response", body)
        }
        return Pair(response, body)
    }

    data class GqlQuery(
        val query: String,
        val variables: Map<String, Any>
    )

    data class GqlResponse<T>(
        val data: T?,
        val errors: List<GqlError>? = null,
    ) {
        data class PagedData<T>(
            val page: Page<T>
        ) {
            data class Page<T>(
                val pageInfo: PageInfo,
                val elements: List<T>
            )
        }

        data class GqlError(
            val message: String,
        )

        data class PageInfo(
            val total: Long,
            val currentPage: Long,
            val lastPage: Long,
            val hasNextPage: Boolean,
            val perPage: Long,
        )

        data class ViewerData(
            val viewer: Viewer,
        ) {
            data class Viewer(
                val id: Int,
                val name: String,
            )
        }

        data class ActivityData(
            val id: Int,
            @field:JsonDeserialize(using = JacksonUtils.SQLTimestampDeserializer::class) val createdAt: ZonedDateTime,
            val media: Media,
            val status: String,
            val progress: String? = null,
        )

        data class MediaListData(
            val id: Int,
            @field:JsonDeserialize(using = JacksonUtils.SQLTimestampDeserializer::class) val updatedAt: ZonedDateTime,
            val media: Media,
            val progress: Int,
        )

        data class Media(
            val id: Int,
            val coverImage: Image,
            val type: Type,
            val chapters: Int?,
            val episodes: Int?,
            val status: Status,
        ) {
            data class Image(
                val extraLarge: String,
                val large: String,
            )

            enum class Type {
                ANIME,
                MANGA,
            }

            enum class Status {
                CANCELLED,
                FINISHED,
                HIATUS,
                NOT_YET_RELEASED,
                RELEASING,
            }
        }
    }

    class AnilistApiException(
        message: String?,
        val body: GqlResponse<*>? = null,
        e: Throwable? = null,
    ) : Exception(message, e)
}