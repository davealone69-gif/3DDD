package com.threedd.studio.render

/**
 * Wraps a Filament call so a failure names the operation and the sizes involved.
 *
 * Filament's JNI layer throws bare IllegalArgumentException("Precondition") with no context,
 * which is impossible to act on. This turns it into a message that identifies the call site.
 * Errors still propagate - nothing is swallowed.
 */
internal inline fun <T> guarded(what: () -> String, block: () -> T): T =
    try {
        block()
    } catch (t: Throwable) {
        val detail = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
        throw IllegalStateException("Filament failed: ${what()} -> $detail", t)
    }
