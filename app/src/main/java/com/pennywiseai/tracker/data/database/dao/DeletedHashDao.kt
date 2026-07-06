package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.DeletedHashEntity

@Dao
interface DeletedHashDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tombstone: DeletedHashEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tombstones: List<DeletedHashEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_transaction_hashes WHERE hash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Query("SELECT * FROM deleted_transaction_hashes")
    suspend fun getAll(): List<DeletedHashEntity>

    @Query("DELETE FROM deleted_transaction_hashes WHERE hash = :hash")
    suspend fun remove(hash: String)
}
