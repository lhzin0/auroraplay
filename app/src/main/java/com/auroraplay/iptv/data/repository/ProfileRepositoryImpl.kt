package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.data.database.dao.ProfileDao
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.datastore.SettingsDataStore
import com.auroraplay.iptv.data.mapper.toDomain
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val dao: ProfileDao,
    private val settingsDataStore: SettingsDataStore,
) : ProfileRepository {

    override fun observeProfiles(): Flow<List<Profile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addProfile(name: String, avatarColorHex: String, avatarEmoji: String, avatarUri: String?, isKids: Boolean): Profile {
        val entity = ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Perfil" },
            avatarColorHex = avatarColorHex,
            avatarEmoji = avatarEmoji,
            avatarUri = avatarUri,
            isKids = isKids,
            createdAtMillis = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun updateProfile(profile: Profile) {
        val existing = dao.getById(profile.id) ?: return
        dao.upsert(
            existing.copy(
                name = profile.name.ifBlank { existing.name },
                avatarColorHex = profile.avatarColorHex,
                avatarEmoji = profile.avatarEmoji,
                avatarUri = profile.avatarUri,
                isKids = profile.isKids,
                pinHash = profile.pinHash,
                biometricEnabled = profile.biometricEnabled,
            )
        )
    }

    override suspend fun getProfile(id: String): Profile? = dao.getById(id)?.toDomain()

    override suspend fun deleteProfile(id: String) = dao.delete(id)

    override suspend fun getActiveProfileId(): String? = settingsDataStore.activeProfileIdFlow.first()

    override suspend fun setActiveProfile(id: String) = settingsDataStore.setActiveProfileId(id)

    override fun observeActiveProfile(): Flow<Profile?> =
        combine(dao.observeAll(), settingsDataStore.activeProfileIdFlow) { profiles, activeId ->
            profiles.find { it.id == activeId }?.toDomain()
        }
}
