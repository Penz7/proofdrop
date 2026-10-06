import { ConflictException, HttpException, Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { toEvidenceListItem } from '../common/mappers';
import { EventsService } from '../events/events.module';
import { OrdersService } from '../orders/orders.service';
import { PrismaService } from '../prisma/prisma.service';
import { StorageService } from '../storage/storage.module';
import { ChainRecord, ChainVerification, GENESIS, isSealedCorrectly, sha256Hex, verifyChain } from './evidence-chain';

export interface UploadResult {
  accepted: boolean;
  message: string;
}

const reject = (message: string): never => {
  throw new HttpException({ accepted: false, message }, 422);
};

const listInclude = { courier: { select: { id: true, name: true } }, order: { select: { codeNumber: true } } } as const;

@Injectable()
export class EvidenceService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly storage: StorageService,
    private readonly orders: OrdersService,
    private readonly events: EventsService,
  ) {}

  async head(courierId: string): Promise<{ sequence: number; recordHash: string }> {
    const last = await this.prisma.evidence.findFirst({ where: { courierId }, orderBy: { sequence: 'desc' } });
    return last ? { sequence: last.sequence, recordHash: last.recordHash } : { sequence: 0, recordHash: GENESIS };
  }

  /** Implements the ordered rules of `POST /api/evidence` in docs/API.md. */
  async upload(
    courierId: string,
    rawRecord: string | undefined,
    file: { buffer: Buffer; mimetype?: string } | undefined,
  ): Promise<UploadResult> {
    // 1. Both parts present, the record is well-formed and the file really is an image
    if (!rawRecord || !file?.buffer?.length) reject('Missing record or file');
    const record = parseRecord(rawRecord!);
    const contentType = sniffImageType(file!.buffer) ?? reject('Unsupported media type: upload a JPEG, PNG or WebP photo');

    // 2. Idempotent retry
    const existing = await this.prisma.evidence.findUnique({ where: { id: record.id } });
    if (existing && existing.courierId === courierId && existing.recordHash === record.recordHash) {
      return { accepted: true, message: 'Already stored' };
    }

    // 3. File matches the hash the phone sealed
    if (sha256Hex(file!.buffer) !== record.fileSha256) reject('File hash mismatch');

    // 4. Seal is intact
    if (!isSealedCorrectly(record)) reject('Record seal is invalid');

    // 5. The order belongs to this courier
    const order = await this.prisma.order.findUnique({ where: { id: record.orderId } });
    if (!order || order.courierId !== courierId) reject('Order not assigned to you');

    // 6 + 7. Link to the previous record of this courier's chain
    let expectedPrevious = GENESIS;
    if (record.sequence > 1) {
      const previous = await this.prisma.evidence.findUnique({
        where: { courierId_sequence: { courierId, sequence: record.sequence - 1 } },
      });
      if (!previous) throw new ConflictException('Previous record not uploaded yet');
      expectedPrevious = previous.recordHash;
    }
    if (record.previousHash !== expectedPrevious) reject('Chain link mismatch');

    // 8. Sequence not taken by a different record
    if (existing) reject('Sequence already used');
    const taken = await this.prisma.evidence.findUnique({
      where: { courierId_sequence: { courierId, sequence: record.sequence } },
    });
    if (taken) {
      // An overlapping retry of this same record may have committed since step 2.
      if (taken.id === record.id && taken.recordHash === record.recordHash) {
        return { accepted: true, message: 'Already stored' };
      }
      reject('Sequence already used');
    }

    // 9. Store (the key only contains validated UUIDs and digits, so it can't escape the bucket/dir)
    const storageKey = `${courierId}/${String(record.sequence).padStart(6, '0')}-${record.id}.jpg`;
    await this.storage.put(storageKey, file!.buffer, contentType);
    try {
      const saved = await this.prisma.evidence.create({
        data: {
          id: record.id,
          courierId,
          sequence: record.sequence,
          orderId: record.orderId,
          fileName: record.fileName,
          fileSha256: record.fileSha256,
          capturedAt: BigInt(record.capturedAt),
          latitude: record.latitude,
          longitude: record.longitude,
          bleVerified: record.bleVerified,
          previousHash: record.previousHash,
          recordHash: record.recordHash,
          storageKey,
          sizeBytes: file!.buffer.length,
          contentType,
        },
        include: listInclude,
      });
      await this.orders.markDeliveredByEvidence(record.orderId);
      this.events.emitDispatch({ type: 'evidence', data: toEvidenceListItem(saved) });
    } catch (e) {
      if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2002') {
        // Lost a race. If the winner is this very record (a retry that overlapped the first
        // attempt), the upload succeeded; otherwise another record took the sequence.
        const winner = await this.prisma.evidence.findUnique({ where: { id: record.id } });
        if (winner && winner.courierId === courierId && winner.recordHash === record.recordHash) {
          return { accepted: true, message: 'Already stored' };
        }
        reject('Sequence already used');
      }
      throw e;
    }
    return { accepted: true, message: `Stored evidence #${record.sequence}` };
  }

  async list(filter: { courierId?: string; orderId?: string }) {
    const rows = await this.prisma.evidence.findMany({
      where: { courierId: filter.courierId, orderId: filter.orderId },
      include: listInclude,
      orderBy: { receivedAt: 'desc' },
    });
    return rows.map(toEvidenceListItem);
  }

  async photo(id: string): Promise<{ body: Buffer; contentType: string }> {
    const row = await this.prisma.evidence.findUnique({ where: { id } });
    if (!row) throw new NotFoundException('Evidence not found');
    const body = await this.storage.get(row.storageKey);
    if (!body) throw new NotFoundException('Media file missing from storage');
    return { body, contentType: row.contentType };
  }

  /** Re-downloads and re-hashes every photo, then walks the chain from GENESIS. */
  async verify(courierId: string): Promise<ChainVerification> {
    const rows = await this.prisma.evidence.findMany({ where: { courierId }, orderBy: { sequence: 'asc' } });
    const fileHashes = new Map<string, string | null>();
    for (const row of rows) {
      const body = await this.storage.get(row.storageKey);
      fileHashes.set(row.id, body ? sha256Hex(body) : null);
    }
    const records: ChainRecord[] = rows.map((r) => ({
      id: r.id,
      sequence: r.sequence,
      orderId: r.orderId,
      fileName: r.fileName,
      fileSha256: r.fileSha256,
      capturedAt: Number(r.capturedAt),
      latitude: r.latitude,
      longitude: r.longitude,
      bleVerified: r.bleVerified,
      previousHash: r.previousHash,
      recordHash: r.recordHash,
    }));
    return verifyChain(records, fileHashes);
  }
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SHA256 = /^[0-9a-f]{64}$/;
const FILE_NAME = /^[A-Za-z0-9._-]{1,128}$/;
const MAX_SEQUENCE = 2_147_483_647; // Postgres INT

/**
 * Strict shape check. Every string that ends up in a storage key, a DB column or the canonical
 * hash is constrained, so a crafted record can't traverse paths or overflow columns.
 */
function parseRecord(raw: string): ChainRecord {
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return reject('Invalid record');
  }
  if (typeof value !== 'object' || value === null) reject('Invalid record');
  const r = value as Record<string, unknown>;
  const matches = (k: string, re: RegExp) => typeof r[k] === 'string' && re.test(r[k] as string);
  const coord = (k: string, limit: number) =>
    r[k] === null || r[k] === undefined || (typeof r[k] === 'number' && Number.isFinite(r[k]) && Math.abs(r[k] as number) <= limit);
  const ok =
    matches('id', UUID) &&
    Number.isInteger(r.sequence) && (r.sequence as number) >= 1 && (r.sequence as number) <= MAX_SEQUENCE &&
    matches('orderId', UUID) &&
    matches('fileName', FILE_NAME) &&
    matches('fileSha256', SHA256) &&
    Number.isSafeInteger(r.capturedAt) && (r.capturedAt as number) > 0 &&
    coord('latitude', 90) && coord('longitude', 180) &&
    typeof r.bleVerified === 'boolean' &&
    matches('previousHash', SHA256) && matches('recordHash', SHA256);
  if (!ok) reject('Invalid record');
  return {
    id: r.id as string,
    sequence: r.sequence as number,
    orderId: r.orderId as string,
    fileName: r.fileName as string,
    fileSha256: r.fileSha256 as string,
    capturedAt: r.capturedAt as number,
    latitude: (r.latitude as number | null | undefined) ?? null,
    longitude: (r.longitude as number | null | undefined) ?? null,
    bleVerified: r.bleVerified as boolean,
    previousHash: r.previousHash as string,
    recordHash: r.recordHash as string,
  };
}

/** Identifies the image by its magic bytes; the client-declared MIME type is never trusted. */
export function sniffImageType(buf: Buffer): 'image/jpeg' | 'image/png' | 'image/webp' | null {
  if (buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
  if (buf.length >= 8 && buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (buf.length >= 12 && buf.toString('ascii', 0, 4) === 'RIFF' && buf.toString('ascii', 8, 12) === 'WEBP') return 'image/webp';
  return null;
}
