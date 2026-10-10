package fr.rakambda.watchedpostermaker.api.trakt

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import fr.rakambda.watchedpostermaker.AppConfiguration
import fr.rakambda.watchedpostermaker.api.TraktApi.TraktApiException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.Instant

/**
 * Trakt tokens are shared with RSN-Discord through its database, so the database is always the source of truth.
 */
class TraktTokenHandler(
    private val config: AppConfiguration.Trakt,
) {
    companion object {
        // Smaller than RSN-Discord margin (24h) so it usually rotates first and we don't race it
        private val REFRESH_MARGIN = Duration.ofHours(1)
    }

    private val logger = KotlinLogging.logger {}

    private val db: Database = Database.connect(
        config.database.url,
        driver = config.database.driver,
        user = config.database.username ?: "",
        password = config.database.password ?: "",
    )

    private val client = HttpClient {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(UserAgent) {
            agent = "WatchedPosterMaker"
        }
    }

    suspend fun getAccessToken(): String {
        val tokens = readTokens()
        if (tokens.expire.isAfter(Instant.now().plus(REFRESH_MARGIN))) {
            return tokens.accessToken
        }
        return refresh(tokens)
    }

    /**
     * Called when [rejectedAccessToken] got refused by Trakt.
     */
    suspend fun refreshAccessToken(rejectedAccessToken: String?): String {
        val tokens = readTokens()
        if (tokens.accessToken != rejectedAccessToken) {
            logger.info { "Trakt token was rotated by another app, using it" }
            return tokens.accessToken
        }
        return refresh(tokens)
    }

    private suspend fun refresh(tokens: StoredTokens): String {
        logger.info { "Refreshing Trakt token for user ${config.userId}" }
        val response = client.post("https://api.trakt.tv/oauth/token") {
            contentType(ContentType.Application.Json)
            setBody(
                RefreshTokenRequest(
                    clientId = config.clientId,
                    clientSecret = config.clientSecret,
                    refreshToken = tokens.refreshToken,
                )
            )
        }

        if (!response.status.isSuccess()) {
            // Another app may have used the refresh token between our read and our request
            val current = readTokens()
            if (current.refreshToken != tokens.refreshToken) {
                logger.info { "Trakt token was rotated by another app while refreshing, using it" }
                return current.accessToken
            }
            logger.error { "Failed to refresh Trakt token, status ${response.status}" }
            throw TraktApiException("Failed to refresh Trakt token")
        }

        val body = response.body<AccessTokenResponse>()
        transaction(db) {
            TraktTable.update({ TraktTable.id eq config.userId }) {
                it[accessToken] = body.accessToken
                it[refreshToken] = body.refreshToken
                it[refreshTokenExpire] = Instant.now().plusSeconds(body.expiresIn)
            }
        }
        return body.accessToken
    }

    private fun readTokens(): StoredTokens = transaction(db) {
        val row = TraktTable.selectAll()
            .where { TraktTable.id eq config.userId }
            .firstOrNull()
            ?: throw TraktApiException("No Trakt token found for user ${config.userId}")

        StoredTokens(
            accessToken = row[TraktTable.accessToken] ?: throw TraktApiException("Trakt user ${config.userId} is not logged in"),
            refreshToken = row[TraktTable.refreshToken] ?: throw TraktApiException("Trakt user ${config.userId} is not logged in"),
            expire = row[TraktTable.refreshTokenExpire] ?: Instant.EPOCH,
        )
    }

    private data class StoredTokens(
        val accessToken: String,
        val refreshToken: String,
        val expire: Instant,
    )

    private data class RefreshTokenRequest(
        @field:JsonProperty("client_id") val clientId: String,
        @field:JsonProperty("client_secret") val clientSecret: String,
        @field:JsonProperty("refresh_token") val refreshToken: String,
        @field:JsonProperty("grant_type") val grantType: String = "refresh_token",
        @field:JsonProperty("redirect_uri") val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
    )

    private data class AccessTokenResponse(
        @field:JsonProperty("access_token") val accessToken: String,
        @field:JsonProperty("refresh_token") val refreshToken: String,
        @field:JsonProperty("expires_in") val expiresIn: Long,
    )
}
