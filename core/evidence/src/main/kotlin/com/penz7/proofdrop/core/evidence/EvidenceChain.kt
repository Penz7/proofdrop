package com.penz7.proofdrop.core.evidence

import com.penz7.proofdrop.core.model.EvidenceDraft
import com.penz7.proofdrop.core.model.EvidenceRecord
import kotlin.math.roundToLong

/**
 * Append-only hash chain for proof-of-delivery records, the same idea as a
 * chain-of-custody log for digital evidence.
 */
object EvidenceChain {
    val GENESIS: String = "0".repeat(64)

    fun seal(draft: EvidenceDraft, previous: EvidenceRecord?): EvidenceRecord =
        seal(draft, previous?.let { ChainAnchor(it.sequence, it.recordHash) } ?: ChainAnchor.START)

    /** Seals [draft] directly after [anchor], e.g. the chain head fetched from the server. */
    fun seal(draft: EvidenceDraft, anchor: ChainAnchor): EvidenceRecord {
        val sequence = anchor.sequence + 1
        val previousHash = anchor.recordHash
        val unsealed = EvidenceRecord(
            id = draft.id,
            sequence = sequence,
            orderId = draft.orderId,
            fileName = draft.fileName,
            fileSha256 = draft.fileSha256,
            capturedAt = draft.capturedAt,
            latitude = draft.latitude,
            longitude = draft.longitude,
            bleVerified = draft.bleVerified,
            previousHash = previousHash,
            recordHash = "",
        )
        return unsealed.copy(recordHash = hashOf(unsealed))
    }

    /**
     * Checks links and hashes in sequence order. [fileHashOf] optionally re-hashes the
     * stored media so a swapped photo is caught too; return null if a file is missing.
     * [anchor] is the trusted record just before the first one (the start of the chain by default).
     */
    fun verify(
        records: List<EvidenceRecord>,
        anchor: ChainAnchor = ChainAnchor.START,
        fileHashOf: ((EvidenceRecord) -> String?)? = null,
    ): ChainVerification {
        var expectedPrevious = anchor.recordHash
        var expectedSequence = anchor.sequence + 1
        for (record in records.sortedBy { it.sequence }) {
            if (record.sequence != expectedSequence) {
                return ChainVerification.Broken(expectedSequence, "Record #$expectedSequence is missing")
            }
            if (record.previousHash != expectedPrevious) {
                return ChainVerification.Broken(record.sequence, "Link to previous record does not match")
            }
            if (hashOf(record) != record.recordHash) {
                return ChainVerification.Broken(record.sequence, "Record contents were modified")
            }
            if (fileHashOf != null) {
                val actual = fileHashOf(record)
                    ?: return ChainVerification.Broken(record.sequence, "Media file is missing")
                if (actual != record.fileSha256) {
                    return ChainVerification.Broken(record.sequence, "Media file was altered")
                }
            }
            expectedPrevious = record.recordHash
            expectedSequence++
        }
        return ChainVerification.Valid(records.size)
    }

    /** True if [record]'s own hash matches its contents (used when records arrive one by one). */
    fun isSealedCorrectly(record: EvidenceRecord): Boolean = hashOf(record) == record.recordHash

    /**
     * Stable, explicit field order: changing this breaks every existing chain.
     * Coordinates are encoded as integer 1e-7 degrees so Kotlin and the TypeScript backend
     * produce byte-identical strings (their Double-to-String rules differ).
     */
    fun canonical(r: EvidenceRecord): String = listOf(
        r.sequence,
        r.id,
        r.orderId,
        r.fileName,
        r.fileSha256,
        r.capturedAt,
        r.latitude?.let(::e7) ?: "",
        r.longitude?.let(::e7) ?: "",
        r.bleVerified,
        r.previousHash,
    ).joinToString("|")

    private fun e7(degrees: Double): Long = (degrees * 1e7).roundToLong()

    private fun hashOf(r: EvidenceRecord): String = Sha256.of(canonical(r))
}

/** A trusted point in a chain: the next record must have sequence + 1 and link to [recordHash]. */
data class ChainAnchor(val sequence: Long, val recordHash: String) {
    companion object {
        val START = ChainAnchor(0, EvidenceChain.GENESIS)
    }
}

sealed interface ChainVerification {
    data class Valid(val count: Int) : ChainVerification
    data class Broken(val atSequence: Long, val reason: String) : ChainVerification
}
