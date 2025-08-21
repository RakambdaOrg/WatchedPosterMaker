package fr.rakambda.watchedpostermaker.util

import java.net.URL

object GraphQlUtils {
    private val INCLUDE_PATTERN: Regex = Regex("#include \"(.*)\"")

    fun readQuery(resource: URL): String {
        return resource.readText().lines()
            .map {
                val match = INCLUDE_PATTERN.matchEntire(it) ?: return@map it

                val path = match.groups[1]?.value ?: return@map it
                val include = resource.toURI().resolve(path).toURL()

                return@map readQuery(include)
            }
            .joinToString("\n")
            .trim()
    }
}