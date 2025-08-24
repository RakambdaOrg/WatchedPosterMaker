package fr.rakambda.watchedpostermaker

import fr.rakambda.watchedpostermaker.tools.api.cache.CacheDatabaseHandler
import fr.rakambda.watchedpostermaker.processor.AnilistProcessor
import fr.rakambda.watchedpostermaker.processor.TraktProcessor
import fr.rakambda.watchedpostermaker.util.ExecutionCache

suspend fun main(args: Array<String>) {
    System.setProperty("fr.rakambda.configLocation", args.getOrNull(0) ?: "config.yaml")

    val cacheDatabaseHandler: CacheDatabaseHandler by lazy {
        CacheDatabaseHandler(
            AppConfiguration.instance.executionCache.database.driver,
            AppConfiguration.instance.executionCache.database.url,
            AppConfiguration.instance.executionCache.database.username,
            AppConfiguration.instance.executionCache.database.password,
        )
    }
    val executionCache: ExecutionCache by lazy { ExecutionCache(cacheDatabaseHandler) }

    AnilistProcessor(executionCache).process()
    TraktProcessor(executionCache).process()
}