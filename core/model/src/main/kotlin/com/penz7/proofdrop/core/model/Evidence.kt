package com.penz7.proofdrop.core.model

import kotlinx.serialization.Serializable

/**
 * One sealed proof-of-delivery record.
 *
 * Records form a hash chain: [recordHash] covers every field plus [previousHash],
 * so editing, deleting or reordering any record breaks verification of everything after it.
 */
@Serializable
data class EvidenceRecord(
    val id: String,
    val sequence: Long,
    val orderId: String,
    val fileName: String,
    val fileSha256: String,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val bleVerified: Boolean,
    val previousHash: String,
    val recordHash: String,
)

/** Everything known about a capture before it is sealed into the chain. */
data class EvidenceDraft(
    val id: String,
    val orderId: String,
    val fileName: String,
    val fileSha256: String,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val bleVerified: Boolean,
)

@Serializable
data class EvidenceUploadResult(val accepted: Boolean, val message: String)
