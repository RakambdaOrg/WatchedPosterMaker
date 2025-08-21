package fr.rakambda.watchedpostermaker

import fr.rakambda.tools.api.cache.CacheDatabaseHandler
import fr.rakambda.watchedpostermaker.processor.AnilistProcessor
import fr.rakambda.watchedpostermaker.processor.TraktProcessor
import fr.rakambda.watchedpostermaker.util.ExecutionCache

private val executionCache: ExecutionCache by lazy { ExecutionCache(cacheDatabaseHandler) }
private val cacheDatabaseHandler: CacheDatabaseHandler by lazy {
    CacheDatabaseHandler(
        AppConfiguration.instance.executionCache.database.driver,
        AppConfiguration.instance.executionCache.database.url,
        AppConfiguration.instance.executionCache.database.username,
        AppConfiguration.instance.executionCache.database.password,
    )
}

suspend fun main() {
    AnilistProcessor(executionCache).process()
    TraktProcessor(executionCache).process()
}