package fr.rakambda.watchedpostermaker.util

import fr.rakambda.watchedpostermaker.tools.api.cache.CacheDatabaseHandler

class ExecutionCache(
    private val database: CacheDatabaseHandler
) {
    fun getOrDefault(category: String, key: String, defaultValue: String): String {
        return database.getValue(category, key).takeIf { it != "null" } ?: defaultValue
    }

    fun setValue(category: String, key: String, value: String?) {
        if (value == null) {
            database.deleteValue(category, key)
            return
        }
        database.setValue(category, key, value)
    }
}