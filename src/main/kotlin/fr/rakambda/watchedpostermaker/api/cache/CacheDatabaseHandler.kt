package fr.rakambda.watchedpostermaker.tools.api.cache

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

class CacheDatabaseHandler(
    driver: String,
    url: String,
    username: String?,
    password: String?,
) {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    private val db: Database = Database.connect(
        url,
        driver = driver,
        user = username ?: "",
        password = password ?: "",
    )

    init {
        transaction(db) {
            SchemaUtils.create(CacheTable)
        }
    }

    fun getValue(category: String, key: String): String? = this.lock.withReadLock {
        return transaction(db) {
            return@transaction CacheTable.selectAll()
                .where { CacheTable.category eq category }
                .andWhere { CacheTable.key eq key }
                .firstOrNull()
                ?.let { it[CacheTable.value] }
        }
    }

    fun setValue(category: String, key: String, value: String) = this.lock.withWriteLock {
        transaction(db) {
            return@transaction CacheTable.upsert(CacheTable.category, CacheTable.key) {
                it[CacheTable.category] = category
                it[CacheTable.key] = key
                it[CacheTable.value] = value
            }.insertedCount
        }
    }

    fun deleteValue(category: String, key: String) = this.lock.withWriteLock {
        transaction(db) {
            return@transaction CacheTable.deleteWhere { CacheTable.category eq category and (CacheTable.key eq key) }
        }
    }

    private inline fun <T> ReadWriteLock.withReadLock(block: () -> T): T {
        readLock().withLock { return block() }
    }

    private inline fun <T> ReadWriteLock.withWriteLock(block: () -> T): T {
        writeLock().withLock { return block() }
    }

    private inline fun <T> Lock.withLock(block: () -> T): T {
        lock()
        try {
            return block()
        } finally {
            unlock()
        }
    }
}