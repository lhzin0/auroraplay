package com.auroraplay.iptv.domain.model

@androidx.compose.runtime.Immutable
data class Channel(
    val id: String,
    val connectionId: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    val epgChannelId: String?,
    val isFavorite: Boolean = false,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
)

@androidx.compose.runtime.Immutable
data class EpgProgram(
    val id: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    fun progressFraction(nowMillis: Long = System.currentTimeMillis()): Float {
        if (endMillis <= startMillis) return 0f
        val fraction = (nowMillis - startMillis).toFloat() / (endMillis - startMillis).toFloat()
        return fraction.coerceIn(0f, 1f)
    }
}
