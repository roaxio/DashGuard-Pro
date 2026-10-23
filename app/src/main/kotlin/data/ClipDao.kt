package io.github.roaxio.dashguardpro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.roaxio.dashguardpro.model.VideoClip
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM video_clips ORDER BY timestamp DESC")
    fun getAllClips(): Flow<List<VideoClip>>

    @Query("SELECT * FROM video_clips WHERE isLocked = 1 ORDER BY timestamp DESC")
    fun getLockedClips(): Flow<List<VideoClip>>

    @Query("SELECT * FROM video_clips WHERE isSentryEvent = 1 ORDER BY timestamp DESC")
    fun getSentryClips(): Flow<List<VideoClip>>

    @Query("SELECT * FROM video_clips WHERE id = :id LIMIT 1")
    suspend fun getClipById(id: Long): VideoClip?

    @Query("SELECT * FROM video_clips WHERE filePath = :filePath LIMIT 1")
    suspend fun getClipByPath(filePath: String): VideoClip?

    @Query("SELECT * FROM video_clips ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestClip(): VideoClip?

    @Query("SELECT * FROM video_clips WHERE isLocked = 0 ORDER BY timestamp ASC")
    suspend fun getUnlockedClipsOldestFirst(): List<VideoClip>

    @Query("SELECT SUM(fileSizeBytes) FROM video_clips")
    suspend fun getTotalVideoBytes(): Long?

    @Query("SELECT * FROM video_clips")
    suspend fun getAllClipsList(): List<VideoClip>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: VideoClip): Long

    @Update
    suspend fun updateClip(clip: VideoClip)

    @Delete
    suspend fun deleteClip(clip: VideoClip)

    @Query("DELETE FROM video_clips WHERE id = :id")
    suspend fun deleteClipById(id: Long)

    @Query("UPDATE video_clips SET isLocked = :isLocked WHERE id = :id")
    suspend fun setLockStatus(id: Long, isLocked: Boolean)

    @Query("UPDATE video_clips SET incidentLatitude = :lat, incidentLongitude = :lng, latitude = :lat, longitude = :lng WHERE id = :id")
    suspend fun updateClipIncidentLocation(id: Long, lat: Double, lng: Double)

    @Query("UPDATE video_clips SET triggerReason = 'Motion Sentry Trigger' WHERE (isSentryEvent = 1 OR fileName LIKE 'VID_SENTRY_%') AND triggerReason = 'Normal Loop'")
    suspend fun healSentryNormalLoopReasons()

    @Query("DELETE FROM video_clips WHERE id NOT IN (SELECT MIN(id) FROM video_clips GROUP BY filePath)")
    suspend fun deduplicateClipsByPath(): Int
}
