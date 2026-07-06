package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Tombstone for a user-deleted transaction (review C1, approved 2026-07-07).
 *
 * Soft-deleting a transaction renames its `transaction_hash` to
 * `DELETED_<id>_<hash>` so the same details can be re-added manually — but
 * that frees the ORIGINAL hash, so a full SMS rescan would re-insert the
 * deleted transaction. This table keeps the original hash as a tombstone:
 * the ingestion pipeline skips any parsed message whose hash is here, while
 * manual adds (which use their own hash schemes) are unaffected.
 *
 * Backup contract: all fields defaulted (the empty-string PK default is
 * never used in practice — it exists so `BackupSchemaGuardTest` and older
 * backups without this table stay compatible).
 */
@Entity(tableName = "deleted_transaction_hashes")
@Serializable
data class DeletedHashEntity(
    @PrimaryKey
    @ColumnInfo(name = "hash")
    val hash: String = "",

    @ColumnInfo(name = "deleted_at")
    @Contextual
    val deletedAt: LocalDateTime = LocalDateTime.now()
)
