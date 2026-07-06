package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.BankNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankNotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: BankNotificationEntity): Long

    @Query(
        """
        UPDATE bank_notifications
        SET processed = :processed, transaction_id = :transactionId
        WHERE id = :id
        """
    )
    suspend fun updateStatus(id: Long, processed: Boolean, transactionId: Long?)

    @Query("SELECT * FROM bank_notifications WHERE processed = 0 ORDER BY posted_at ASC")
    suspend fun getUnprocessed(): List<BankNotificationEntity>

    @Query("SELECT * FROM bank_notifications ORDER BY posted_at ASC")
    fun getAllNotifications(): Flow<List<BankNotificationEntity>>

    @Query("DELETE FROM bank_notifications")
    suspend fun deleteAllNotifications()

    /** Keep only processed rows younger than [cutoff]; unprocessed rows are
     *  kept regardless of age — they may still be needed for diagnosis. */
    @Query("DELETE FROM bank_notifications WHERE posted_at < :cutoff AND processed = 1")
    suspend fun deleteProcessedOlderThan(cutoff: java.time.LocalDateTime)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(notification: BankNotificationEntity): Long
}
