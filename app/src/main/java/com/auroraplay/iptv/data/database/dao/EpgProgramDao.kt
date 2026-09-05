package com.auroraplay.iptv.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.auroraplay.iptv.data.database.entity.EpgProgramEntity

@Dao
interface EpgProgramDao {
    @Query(
        """SELECT * FROM epg_programs WHERE connectionId = :connectionId AND epgChannelId = :epgChannelId
        ORDER BY startMillis ASC LIMIT :limit""",
    )
    suspend fun getForChannel(connectionId: String, epgChannelId: String, limit: Int): List<EpgProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE connectionId = :connectionId")
    suspend fun clear(connectionId: String)

    /** Atomic swap — an XMLTV re-import never leaves a reader looking at an
     * empty guide mid-refresh. */
    @Transaction
    suspend fun replace(connectionId: String, programs: List<EpgProgramEntity>) {
        clear(connectionId)
        upsertAll(programs)
    }
}
