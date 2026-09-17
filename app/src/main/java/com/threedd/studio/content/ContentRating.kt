package com.threedd.studio.content

/** Content classification for library items and studio presets. */
enum class ContentRating(val minimumAge: Int, val label: String) {
    GENERAL(0, "General"),
    TEEN(13, "Teen"),
    MATURE(18, "18+ Mature");

    companion object {
        fun of(mature: Boolean): ContentRating = if (mature) MATURE else GENERAL
    }
}

/** What a mature module is allowed to contain. Deliberately explicit and narrow. */
object AdultContentPolicy {
    val allowedThemes: Set<String> = setOf("anatomy", "costume", "silhouette")
    val disallowedCapabilities: Set<String> = setOf(
        "synthetic depiction of real identifiable people",
        "sexualised depiction of minors",
        "non-consensual sexual content"
    )

    fun isAllowed(theme: String, rating: ContentRating, unlocked: Boolean): Boolean {
        if (rating != ContentRating.MATURE) return true
        return unlocked && theme in allowedThemes
    }
}
