package com.pennywiseai.tracker.presentation.add

/**
 * Ranks previously-used merchants against what the user has typed so far.
 *
 * [all] arrives already ordered by usage, so ties keep that order — your
 * regular merchants surface first. Prefix matches always beat mid-string
 * matches ("SET" → "SETEL" before "OFFSET CAFE"), which is what makes a
 * single typed character useful.
 */
fun filterMerchantSuggestions(
    query: String,
    all: List<String>,
    limit: Int = 6,
): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val needle = q.lowercase()

    val prefix = mutableListOf<String>()
    val contains = mutableListOf<String>()
    for (merchant in all) {
        val candidate = merchant.lowercase()
        when {
            // An exact match needs no suggestion — the user already typed it.
            candidate == needle -> continue
            candidate.startsWith(needle) -> prefix += merchant
            candidate.contains(needle) -> contains += merchant
        }
    }
    return (prefix + contains).take(limit)
}
