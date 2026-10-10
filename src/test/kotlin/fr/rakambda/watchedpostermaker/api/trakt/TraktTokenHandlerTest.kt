package fr.rakambda.watchedpostermaker.api.trakt

import fr.rakambda.watchedpostermaker.AppConfiguration
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class TraktTokenHandlerTest {
    @Test
    fun usesStoredTokens(@TempDir dir: Path): Unit = runBlocking {
        val handler = handler(dir, "stored", Instant.now().plus(Duration.ofDays(7)))

        assertEquals("stored", handler.getAccessToken())
        // Token in DB differs from the rejected one: another app already rotated it
        assertEquals("stored", handler.refreshAccessToken("old"))
    }

    private fun handler(dir: Path, accessToken: String, expire: Instant): TraktTokenHandler {
        val database = AppConfiguration.Database("org.sqlite.JDBC", "jdbc:sqlite:${dir.resolve("trakt.db")}", null, null)
        transaction(Database.connect(database.url, database.driver)) {
            SchemaUtils.create(TraktTable)
            TraktTable.insert {
                it[id] = 1L
                it[TraktTable.accessToken] = accessToken
                it[refreshToken] = "refresh"
                it[refreshTokenExpire] = expire
            }
        }
        return TraktTokenHandler(AppConfiguration.Trakt("clientId", "clientSecret", 1L, database, File(".")))
    }
}
