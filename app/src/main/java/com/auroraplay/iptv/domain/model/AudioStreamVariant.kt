package com.auroraplay.iptv.domain.model

import com.auroraplay.iptv.core.util.MetadataSanitizer

/**
 * One playable copy of a title that a provider published as a separate stream
 * for a specific audio treatment — the "DUBLADO" and "LEGENDADO" twins Xtream
 * lists side by side. Surfaced in the player's audio picker so a viewer can
 * switch between the dubbed and original-audio stream without leaving playback.
 */
data class AudioStreamVariant(
    val label: String,
    val streamUrl: String,
    val variant: MetadataSanitizer.AudioVariant,
)
