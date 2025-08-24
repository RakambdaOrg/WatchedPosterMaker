package fr.rakambda.watchedpostermaker.tools.api.cache

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object CacheTable : CompositeIdTable("Cache") {
    val category = varchar("Category", 64)
    val key = text("Key")
    val value = varchar("Value", 32)

    override val primaryKey = PrimaryKey(category, key)
}
