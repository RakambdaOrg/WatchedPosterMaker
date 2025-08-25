package fr.rakambda.watchedpostermaker

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

data class AppConfiguration(
    @field:JsonProperty("executionCache") val executionCache: Cache,
    @field:JsonProperty("anilist") val anilist: Anilist,
    @field:JsonProperty("trakt") val trakt: Trakt,
    @field:JsonProperty("tmdb") val tmdb: Tmdb,
) {
    companion object {
        val instance by lazy { read(File(System.getProperty("fr.rakambda.configLocation") ?: "config.yaml")) }

        private fun read(file: File): AppConfiguration {
            val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
            return mapper.readerFor(AppConfiguration::class.java).readValue(file)
        }
    }

    data class Database(
        @field:JsonProperty("driver") val driver: String,
        @field:JsonProperty("url") val url: String,
        @field:JsonProperty("username") val username: String?,
        @field:JsonProperty("password") val password: String?,
    )

    data class Cache(
        @field:JsonProperty("database") val database: Database,
    )

    data class Anilist(
        @field:JsonProperty("token") val token: String,
        @field:JsonProperty("outputFolder") val output: File,
        @field:JsonProperty("runFromActivity") val runFromActivity: Boolean = false,
        @field:JsonProperty("runFromHistory") val runFromHistory: Boolean = false,
        @field:JsonProperty("historyOnlyLast") val historyOnlyLast: Boolean = false,
    )

    data class Trakt(
        @field:JsonProperty("clientId") val clientId: String,
        @field:JsonProperty("token") val token: String,
        @field:JsonProperty("outputFolder") val output: File,
        @field:JsonProperty("runFromActivity") val runFromActivity: Boolean = false,
        @field:JsonProperty("activityOnlyCompleted") val activityOnlyCompleted: Boolean = false,
    )

    data class Tmdb(
        @field:JsonProperty("token") val token: String,
    )
}