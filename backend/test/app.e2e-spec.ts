import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { randomUUID } from 'crypto';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { ChainRecord, seal, sha256Hex } from '../src/evidence/evidence-chain';
import { configureApp } from '../src/main';

/**
 * Runs against a real, migrated + seeded database (DATABASE_URL, e.g. docker compose postgres).
 * Skipped when DATABASE_URL is not set. Creates its own orders and devices, so it can be re-run.
 *
 * Evidence is uploaded as courier3 on purpose: courier1 is the account used for manual testing
 * on a phone, and appending to its chain here would make the phone's next upload collide.
 */
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

/** Smallest byte string that sniffs as a JPEG, made unique per call. */
const jpeg = () => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.from(`e2e-${randomUUID()}`)]);

describeDb('ProofDrop API (e2e)', () => {
  let app: INestApplication;
  let server: ReturnType<INestApplication['getHttpServer']>;
  let courier: { token: string; id: string };
  let otherCourier: { token: string; id: string };
  let dispatcherToken: string;

  const login = async (email: string, password: string) => {
    const res = await request(server).post('/api/auth/login').send({ email, password }).expect(200);
    return { token: res.body.accessToken as string, id: res.body.user.id as string };
  };
  const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

  const createOrder = async (courierId: string | null) => {
    const res = await request(server)
      .post('/api/dispatch/orders')
      .set(auth(dispatcherToken))
      .send({ customerName: 'E2E', address: '1 Test St', latitude: 10.776, longitude: 106.701, items: 'Box', courierId: courierId ?? undefined })
      .expect(201);
    return res.body as { id: string; status: string };
  };

  const head = async () =>
    (await request(server).get('/api/evidence/head').set(auth(courier.token)).expect(200)).body as { sequence: number; recordHash: string };

  const sealFor = (orderId: string, photo: Buffer, previous: { sequence: number; recordHash: string } | null): ChainRecord =>
    seal(
      {
        id: randomUUID(),
        orderId,
        fileName: 'e2e.jpg',
        fileSha256: sha256Hex(photo),
        capturedAt: Date.now(),
        latitude: 10.7761,
        longitude: 106.7012,
        bleVerified: true,
      },
      previous && previous.sequence > 0 ? previous : null,
    );

  const upload = (record: unknown, bytes: Buffer, token = courier.token) =>
    request(server)
      .post('/api/evidence')
      .set(auth(token))
      .field('record', typeof record === 'string' ? record : JSON.stringify(record))
      .attach('file', bytes, { filename: 'e2e.jpg', contentType: 'image/jpeg' });

  beforeAll(async () => {
    process.env.JWT_SECRET ??= 'e2e-test-secret-at-least-16';
    process.env.STORAGE_DRIVER ??= 'local';
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = moduleRef.createNestApplication();
    configureApp(app);
    await app.init();
    server = app.getHttpServer();

    courier = await login('courier3@proofdrop.dev', 'courier123');
    otherCourier = await login('courier2@proofdrop.dev', 'courier123');
    dispatcherToken = (await login('dispatcher@proofdrop.dev', 'dispatch123')).token;
  });

  afterAll(async () => {
    await app?.close();
  });

  describe('auth', () => {
    it('rejects a wrong password', async () => {
      await request(server).post('/api/auth/login').send({ email: 'courier3@proofdrop.dev', password: 'wrong' }).expect(401);
    });

    it('rejects an unknown email the same way', async () => {
      await request(server).post('/api/auth/login').send({ email: `nobody-${randomUUID()}@proofdrop.dev`, password: 'x' }).expect(401);
    });

    it('requires a token', async () => {
      await request(server).get('/api/orders').expect(401);
    });

    it('ignores ?access_token on routes that accept headers', async () => {
      await request(server).get(`/api/orders?access_token=${courier.token}`).expect(401);
    });

    it('throttles repeated login attempts for one account', async () => {
      const email = `throttle-${randomUUID()}@proofdrop.dev`;
      const codes: number[] = [];
      for (let i = 0; i < 12; i++) {
        codes.push((await request(server).post('/api/auth/login').send({ email, password: 'nope' })).status);
      }
      expect(codes.slice(0, 10).every((c) => c === 401)).toBe(true);
      expect(codes[11]).toBe(429);
    });

    it('keeps couriers out of dispatcher routes', async () => {
      await request(server).get('/api/dispatch/orders').set(auth(courier.token)).expect(403);
    });

    it('keeps dispatchers out of courier routes', async () => {
      await request(server).get('/api/orders').set(auth(dispatcherToken)).expect(403);
    });
  });

  describe('orders', () => {
    it('lists only the courier own, assigned orders', async () => {
      await createOrder(courier.id);
      const res = await request(server).get('/api/orders').set(auth(courier.token)).expect(200);
      expect(res.body.length).toBeGreaterThan(0);
      for (const order of res.body) {
        expect(order.courierId).toBe(courier.id);
        expect(order.status).not.toBe('CREATED');
        expect(typeof order.assignedAt).toBe('number');
      }
    });

    it("hides another courier's order", async () => {
      const theirs = await createOrder(otherCourier.id);
      const mine = await request(server).get('/api/orders').set(auth(courier.token)).expect(200);
      expect(mine.body.some((o: { id: string }) => o.id === theirs.id)).toBe(false);
      await request(server).post(`/api/orders/${theirs.id}/status`).set(auth(courier.token)).send({ status: 'PICKED_UP' }).expect(404);
    });

    it('enforces the status state machine', async () => {
      const order = await createOrder(courier.id);
      const set = (status: string) => request(server).post(`/api/orders/${order.id}/status`).set(auth(courier.token)).send({ status });
      await set('PICKED_UP').expect(200);
      await set('PICKED_UP').expect(200); // idempotent retry
      await set('ASSIGNED').expect(409);
      await set('CREATED').expect(409);
    });

    it('moves a reassigned order from the old courier to the new one', async () => {
      const order = await createOrder(otherCourier.id);
      await request(server).post(`/api/dispatch/orders/${order.id}/assign`).set(auth(dispatcherToken)).send({ courierId: courier.id }).expect(200);
      const mine = await request(server).get('/api/orders').set(auth(courier.token)).expect(200);
      expect(mine.body.find((o: { id: string }) => o.id === order.id)?.status).toBe('ASSIGNED');
      const theirs = await request(server).get('/api/orders').set(auth(otherCourier.token)).expect(200);
      expect(theirs.body.some((o: { id: string }) => o.id === order.id)).toBe(false);
    });
  });

  describe('evidence', () => {
    it('accepts sealed evidence, then enforces every chain rule', async () => {
      const order = await createOrder(courier.id);
      const photo = jpeg();
      const record = sealFor(order.id, photo, await head());

      // Rejections first; none of them may be stored.
      const tampered = await upload(record, jpeg()).expect(422);
      expect(tampered.body).toEqual({ accepted: false, message: 'File hash mismatch' });

      const html = Buffer.from('<html><script>alert(1)</script></html>');
      const notImage = await upload(sealFor(order.id, html, await head()), html).expect(422);
      expect(notImage.body.message).toMatch(/Unsupported media type/);

      const traversal = await upload({ ...record, id: '../../../../tmp/evil' }, photo).expect(422);
      expect(traversal.body.message).toBe('Invalid record');

      const theirOrder = await createOrder(otherCourier.id);
      const wrongOrderPhoto = jpeg();
      const wrongOrder = await upload(sealFor(theirOrder.id, wrongOrderPhoto, await head()), wrongOrderPhoto).expect(422);
      expect(wrongOrder.body.message).toBe('Order not assigned to you');

      // The real upload, twice at once: a retry overlapping the first attempt must not be rejected.
      const [a, b] = await Promise.all([upload(record, photo), upload(record, photo)]);
      expect([a.status, b.status]).toEqual([200, 200]);
      expect([a.body.accepted, b.body.accepted]).toEqual([true, true]);

      // Idempotent retry later on.
      expect((await upload(record, photo).expect(200)).body.message).toBe('Already stored');

      // Out of order: links to a record that doesn't exist yet.
      const gapPhoto = jpeg();
      const gap = sealFor(order.id, gapPhoto, { sequence: record.sequence + 1, recordHash: 'a'.repeat(64) });
      await upload(gap, gapPhoto).expect(409);

      // Right sequence, wrong link.
      const forkPhoto = jpeg();
      const fork = sealFor(order.id, forkPhoto, { sequence: record.sequence, recordHash: 'f'.repeat(64) });
      expect((await upload(fork, forkPhoto).expect(422)).body.message).toBe('Chain link mismatch');

      // Delivered + chain intact.
      const orders = await request(server).get('/api/orders').set(auth(courier.token)).expect(200);
      expect(orders.body.find((o: { id: string }) => o.id === order.id).status).toBe('DELIVERED');
      const verify = await request(server).get(`/api/dispatch/evidence/verify?courierId=${courier.id}`).set(auth(dispatcherToken)).expect(200);
      expect(verify.body).toEqual({ valid: true, count: record.sequence });

      // Delivered orders can't be reassigned.
      await request(server).post(`/api/dispatch/orders/${order.id}/assign`).set(auth(dispatcherToken)).send({ courierId: otherCourier.id }).expect(409);

      // Photo: dispatcher via ?access_token (for <img>), served so a browser can't run it as a page.
      const photoRes = await request(server).get(`/api/dispatch/evidence/${record.id}/photo?access_token=${dispatcherToken}`).expect(200);
      expect(photoRes.headers['content-type']).toBe('image/jpeg');
      expect(photoRes.headers['x-content-type-options']).toBe('nosniff');
      expect(photoRes.headers['content-security-policy']).toContain('sandbox');
      await request(server).get(`/api/dispatch/evidence/${record.id}/photo?access_token=${courier.token}`).expect(403);
    });
  });

  describe('devices', () => {
    it('lets exactly one of two simultaneous checkouts win', async () => {
      const id = `DEV-E2E-${randomUUID().slice(0, 8).toUpperCase()}`;
      await request(server)
        .post('/api/dispatch/devices')
        .set(auth(dispatcherToken))
        .send({ id, name: 'E2E Scanner', type: 'SCANNER', serial: 'E2E', batteryPct: 50 })
        .expect(201);

      const [a, b] = await Promise.all([
        request(server).post(`/api/devices/${id}/checkout`).set(auth(courier.token)).send({}),
        request(server).post(`/api/devices/${id}/checkout`).set(auth(otherCourier.token)).send({}),
      ]);
      expect(a.status).toBe(200);
      expect(b.status).toBe(200);
      expect(a.body.holderId).toBe(b.body.holderId);
      const winner = a.body.holderId === courier.id ? courier : otherCourier;
      const loser = winner === courier ? otherCourier : courier;

      await request(server).post(`/api/devices/${id}/return`).set(auth(loser.token)).expect(409);
      await request(server).post(`/api/devices/${id}/return`).set(auth(winner.token)).expect(200);
      await request(server).post(`/api/devices/${id}/return`).set(auth(winner.token)).expect(200); // offline retry
    });
  });
});
