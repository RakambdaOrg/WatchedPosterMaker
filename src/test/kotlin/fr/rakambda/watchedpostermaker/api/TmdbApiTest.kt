package fr.rakambda.watchedpostermaker.api

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

@DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "Required service not available on CI")
class TmdbApiTest {
    @Test
    fun getTvDetails(): Unit = runBlocking {
        TmdbApi.getTvDetails(215943)
    }

}