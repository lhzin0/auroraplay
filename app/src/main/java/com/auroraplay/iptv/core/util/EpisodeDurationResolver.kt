package com.auroraplay.iptv.core.util

/**
 * Decides what runtime, if any, to show for a series episode on the detail
 * page. Xtream providers routinely stamp a whole season with one wrong
 * duration ("21min" across a season whose episodes really run ~52min), so
 * two better sources override the static label:
 *
 *  1. a runtime the player actually measured on a past watch (ground truth), then
 *  2. TMDB's per-episode `runtime`.
 *
 * And once either of those contradicts the provider's label for *any*
 * episode of a season by a wide margin, none of that season's static labels
 * is shown at all — a confidently wrong "21min" is worse than no line.
 *
 * Pure and framework-free so the thresholds are unit-tested directly.
 */
object EpisodeDurationResolver {

    /** real runtime ≥ this × the static label -> the label understates badly. */
    const val LABEL_UNDERSTATES_RATIO = 1.4

    /** real runtime ≤ this × the static label -> the label overstates badly. */
    const val LABEL_OVERSTATES_RATIO = 0.65

    /** Below this a "measured" value is a stray partial write, not a runtime. */
    const val MIN_REAL_MILLIS = 60_000L

    /** One episode's static label paired with the best real runtime known for
     * it (player-measured minutes win over TMDB minutes; null when neither). */
    data class Sample(val staticLabel: String?, val realMinutes: Int?)

    /**
     * The label to render under an episode, or null for "show nothing".
     * [measuredMillis] is a player measurement, [tmdbMinutes] the TMDB value;
     * [trustStatic] comes from [seasonStaticLabelsTrustworthy].
     */
    fun runtimeLabel(
        staticLabel: String?,
        measuredMillis: Long?,
        tmdbMinutes: Int?,
        trustStatic: Boolean,
    ): String? {
        measuredMillis?.takeIf { it > MIN_REAL_MILLIS }
            ?.let { return MetadataSanitizer.durationFromMillis(it) }
        tmdbMinutes?.takeIf { it > 0 }
            ?.let { return MetadataSanitizer.durationFromMillis(it * 60_000L) }
        return staticLabel?.takeIf { trustStatic && it.isNotBlank() }
    }

    /** The best real runtime in minutes for one episode, or null. */
    fun realMinutes(measuredMillis: Long?, tmdbMinutes: Int?): Int? =
        measuredMillis?.takeIf { it > MIN_REAL_MILLIS }?.let { (it / 60_000L).toInt() }
            ?: tmdbMinutes?.takeIf { it > 0 }

    /**
     * True while the provider's per-episode labels for a season may still be
     * shown. Flips to false as soon as one episode's real runtime is ≥
     * [LABEL_UNDERSTATES_RATIO]× or ≤ [LABEL_OVERSTATES_RATIO]× its label.
     * Seasons with no measured/TMDB runtime yet stay trusted.
     */
    fun seasonStaticLabelsTrustworthy(samples: List<Sample>): Boolean =
        samples.none { sample ->
            val real = sample.realMinutes ?: return@none false
            val label = MetadataSanitizer.minutesFromLabel(sample.staticLabel) ?: return@none false
            val ratio = real.toDouble() / label.toDouble()
            ratio >= LABEL_UNDERSTATES_RATIO || ratio <= LABEL_OVERSTATES_RATIO
        }
}
