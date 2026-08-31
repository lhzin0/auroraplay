package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    suspend fun addProfile(name: String, avatarColorHex: String, avatarEmoji: String, avatarUri: String?, isKids: Boolean): Profile
    suspend fun updateProfile(profile: Profile)
    suspend fun getProfile(id: String): Profile?
    suspend fun deleteProfile(id: String)
    suspend fun getActiveProfileId(): String?
    suspend fun setActiveProfile(id: String)
    fun observeActiveProfile(): Flow<Profile?>
}
