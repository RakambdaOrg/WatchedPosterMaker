package fr.rakambda.watchedpostermaker.api.trakt

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

// Table owned by RSN-Discord, never create or migrate it from here
object TraktTable : Table("trakt") {
    val id = long("id")
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val refreshTokenExpire = timestamp("refresh_token_expire").nullable()

    override val primaryKey = PrimaryKey(id)
}
