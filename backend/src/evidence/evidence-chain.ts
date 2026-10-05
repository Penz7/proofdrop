import { createHash } from 'crypto';

/**
 * Port of Kotlin `core/evidence/EvidenceChain`. The canonical string must stay byte-identical
 * to the Android implementation, or uploads will be rejected. See docs/API.md.
 */
export const GENESIS = '0'.repeat(64);

export interface ChainRecord {
  id: string;
  sequence: number;
  orderId: string;
  fileName: string;
  fileSha256: string;
  capturedAt: number;
  latitude: number | null;
  longitude: number | null;
  bleVerified: boolean;
  previousHash: string;
  recordHash: string;
}

export type ChainVerification = { valid: true; count: number } | { valid: false; atSequence: number; reason: string };

export const sha256Hex = (data: string | Buffer): string => createHash('sha256').update(data).digest('hex');

/** Integer 1e-7 degrees; Math.round matches Kotlin's roundToLong (ties toward +infinity). */
const e7 = (degrees: number | null): string => (degrees === null || degrees === undefined ? '' : String(Math.round(degrees * 1e7)));

export function canonical(r: Omit<ChainRecord, 'recordHash'>): string {
  return [
    r.sequence,
    r.id,
    r.orderId,
    r.fileName,
    r.fileSha256,
    r.capturedAt,
    e7(r.latitude),
    e7(r.longitude),
    r.bleVerified ? 'true' : 'false',
    r.previousHash,
  ].join('|');
}

export const hashOf = (r: Omit<ChainRecord, 'recordHash'>): string => sha256Hex(canonical(r));

export const isSealedCorrectly = (r: ChainRecord): boolean => hashOf(r) === r.recordHash;

/**
 * Walks [records] in sequence order from GENESIS. [fileHashes] (record id -> sha256 of the stored
 * media, or null if missing) additionally catches swapped photos.
 */
export function verifyChain(records: ChainRecord[], fileHashes?: Map<string, string | null>): ChainVerification {
  let expectedPrevious = GENESIS;
  let expectedSequence = 1;
  for (const r of [...records].sort((a, b) => a.sequence - b.sequence)) {
    if (r.sequence !== expectedSequence) {
      return { valid: false, atSequence: expectedSequence, reason: `Record #${expectedSequence} is missing` };
    }
    if (r.previousHash !== expectedPrevious) {
      return { valid: false, atSequence: r.sequence, reason: 'Link to previous record does not match' };
    }
    if (!isSealedCorrectly(r)) {
      return { valid: false, atSequence: r.sequence, reason: 'Record contents were modified' };
    }
    if (fileHashes) {
      const actual = fileHashes.get(r.id);
      if (actual == null) return { valid: false, atSequence: r.sequence, reason: 'Media file is missing' };
      if (actual !== r.fileSha256) return { valid: false, atSequence: r.sequence, reason: 'Media file was altered' };
    }
    expectedPrevious = r.recordHash;
    expectedSequence++;
  }
  return { valid: true, count: records.length };
}

/** Test/seed helper mirroring Kotlin `EvidenceChain.seal`. */
export function seal(
  draft: Omit<ChainRecord, 'sequence' | 'previousHash' | 'recordHash'>,
  previous: { sequence: number; recordHash: string } | null,
): ChainRecord {
  const unsealed = {
    ...draft,
    sequence: (previous?.sequence ?? 0) + 1,
    previousHash: previous?.recordHash ?? GENESIS,
  };
  return { ...unsealed, recordHash: hashOf(unsealed) };
}
