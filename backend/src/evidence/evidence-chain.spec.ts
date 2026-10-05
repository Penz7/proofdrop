import { canonical, ChainRecord, GENESIS, seal, sha256Hex, verifyChain } from './evidence-chain';

const draft = (n: number) => ({
  id: `ev-${n}`,
  orderId: `order-${n}`,
  fileName: `ev-${n}.jpg`,
  fileSha256: sha256Hex(`photo-${n}`),
  capturedAt: 1_700_000_000_000 + n,
  latitude: 10.77,
  longitude: 106.7,
  bleVerified: n % 2 === 0,
});

const chainOf = (size: number): ChainRecord[] =>
  Array.from({ length: size }, (_, i) => i + 1).reduce<ChainRecord[]>(
    (acc, n) => [...acc, seal(draft(n), acc[acc.length - 1] ?? null)],
    [],
  );

describe('evidence chain', () => {
  // Same vector as the Kotlin test `cross-language test vector` and docs/API.md.
  it('matches the cross-language test vector', () => {
    const record = seal(
      {
        id: 'ev-vector',
        orderId: 'ord-vector',
        fileName: 'ev-vector.jpg',
        fileSha256: sha256Hex('abc'),
        capturedAt: 1791000000000,
        latitude: 10.7743,
        longitude: 106.7038,
        bleVerified: true,
      },
      null,
    );
    expect(canonical(record)).toBe(
      '1|ev-vector|ord-vector|ev-vector.jpg|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|1791000000000|107743000|1067038000|true|' +
        GENESIS,
    );
    expect(record.recordHash).toBe('7dba06ac87db559e5033d279f1c9b78232b21e101040bf7f1cc8e177ebdd7e38');
  });

  it('encodes missing coordinates as empty fields', () => {
    const record = seal({ ...draft(1), latitude: null, longitude: null }, null);
    expect(canonical(record)).toContain('|1700000000001|||false|');
  });

  it('verifies an untouched chain', () => {
    expect(verifyChain(chainOf(5))).toEqual({ valid: true, count: 5 });
  });

  it('detects an edited field', () => {
    const chain = chainOf(3);
    chain[1] = { ...chain[1], latitude: 0 };
    expect(verifyChain(chain)).toMatchObject({ valid: false, atSequence: 2, reason: 'Record contents were modified' });
  });

  it('detects a deleted record', () => {
    const chain = chainOf(4).filter((r) => r.sequence !== 2);
    expect(verifyChain(chain)).toMatchObject({ valid: false, atSequence: 2 });
  });

  it('detects a re-sealed forgery through the next link', () => {
    const chain = chainOf(3);
    chain[0] = seal({ ...draft(1), orderId: 'someone-else' }, null);
    expect(verifyChain(chain)).toMatchObject({ valid: false, atSequence: 2, reason: 'Link to previous record does not match' });
  });

  it('detects a swapped media file', () => {
    const chain = chainOf(2);
    const hashes = new Map(chain.map((r) => [r.id, r.fileSha256] as [string, string | null]));
    hashes.set('ev-2', sha256Hex('different photo'));
    expect(verifyChain(chain, hashes)).toEqual({ valid: false, atSequence: 2, reason: 'Media file was altered' });
  });
});
