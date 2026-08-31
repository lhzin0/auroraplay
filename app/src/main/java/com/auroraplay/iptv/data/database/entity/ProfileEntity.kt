package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarColorHex: String,
    val avatarEmoji: String,
    val avatarUri: String? = null,
    val isKids: Boolean = false,
    val createdAtMillis: Long,
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
)
