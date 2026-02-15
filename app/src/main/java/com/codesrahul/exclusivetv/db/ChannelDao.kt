package com.codesrahul.exclusivetv.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY name ASC")
    fun getAll(): LiveData<List<TVEntity>>

    @Query("SELECT * FROM channels ORDER BY name ASC")
    suspend fun getAllSync(): List<TVEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<TVEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Transaction
    suspend fun refreshChannels(channels: List<TVEntity>) {
        deleteAll()
        insertAll(channels)
    }
}
