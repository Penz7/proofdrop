package com.penz7.proofdrop.core.evidence

import com.penz7.proofdrop.core.model.EvidenceDraft
import com.penz7.proofdrop.core.model.EvidenceRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EvidenceChainTest {

    private fun draft(n: Int) = EvidenceDraft(
        id = "ev-$n",
        orderId = "order-$n",
        fileName = "ev-$n.jpg",
        fileSha256 = Sha256.of("photo-$n"),
        capturedAt = 1_700_000_000_000L + n,
        latitude = 10.77,
        longitude = 106.70,
        bleVerified = n % 2 == 0,
    )

    private fun chainOf(size: Int): List<EvidenceRecord> =
        (1..size).fold(emptyList()) { acc, n -> acc + EvidenceChain.seal(draft(n), acc.lastOrNull()) }

    @Test
    fun `first record links to genesis`() {
        val first = EvidenceChain.seal(draft(1), previous = null)
        assertEquals(1, first.sequence)
        assertEquals(EvidenceChain.GENESIS, first.previousHash)
    }

    @Test
    fun `untouched chain verifies`() {
        assertEquals(ChainVerification.Valid(5), EvidenceChain.verify(chainOf(5)))
    }

    @Test
    fun `editing a field is detected`() {
        val chain = chainOf(3).toMutableList()
        chain[1] = chain[1].copy(latitude = 0.0)
        val result = assertIs<ChainVerification.Broken>(EvidenceChain.verify(chain))
        assertEquals(2, result.atSequence)
    }

    @Test
    fun `deleting a record is detected`() {
        val chain = chainOf(4).filterNot { it.sequence == 2L }
        val result = assertIs<ChainVerification.Broken>(EvidenceChain.verify(chain))
        assertEquals(2, result.atSequence)
    }

    @Test
    fun `re-sealing a forged record breaks the next link`() {
        val chain = chainOf(3).toMutableList()
        val forged = chain[0].copy(orderId = "someone-else")
        chain[0] = EvidenceChain.seal(
            draft(1).copy(orderId = forged.orderId),
            previous = null,
        )
        val result = assertIs<ChainVerification.Broken>(EvidenceChain.verify(chain))
        assertEquals(2, result.atSequence)
    }

    @Test
    fun `swapped media file is detected`() {
        val chain = chainOf(2)
        val result = EvidenceChain.verify(chain) { record: EvidenceRecord ->
            if (record.sequence == 2L) Sha256.of("different photo") else record.fileSha256
        }
        assertEquals(ChainVerification.Broken(2, "Media file was altered"), result)
    }

    @Test
    fun `sha256 matches known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc"),
        )
    }

    @Test
    fun `chain continues from a server anchor`() {
        val head = chainOf(3).last()
        val anchor = ChainAnchor(head.sequence, head.recordHash)
        val next = EvidenceChain.seal(draft(4), anchor)
        assertEquals(4, next.sequence)
        assertEquals(ChainVerification.Valid(1), EvidenceChain.verify(listOf(next), anchor))
        assertIs<ChainVerification.Broken>(EvidenceChain.verify(listOf(next)))
    }

    /** Shared with backend/src/evidence/evidence-chain.spec.ts: both must produce this exact hash. */
    @Test
    fun `cross-language test vector`() {
        val record = EvidenceChain.seal(
            EvidenceDraft(
                id = "ev-vector",
                orderId = "ord-vector",
                fileName = "ev-vector.jpg",
                fileSha256 = Sha256.of("abc"),
                capturedAt = 1_791_000_000_000L,
                latitude = 10.7743,
                longitude = 106.7038,
                bleVerified = true,
            ),
            previous = null,
        )
        assertEquals(
            "1|ev-vector|ord-vector|ev-vector.jpg|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|1791000000000|107743000|1067038000|true|" + EvidenceChain.GENESIS,
            EvidenceChain.canonical(record),
        )
        assertEquals("7dba06ac87db559e5033d279f1c9b78232b21e101040bf7f1cc8e177ebdd7e38", record.recordHash)
    }
}
