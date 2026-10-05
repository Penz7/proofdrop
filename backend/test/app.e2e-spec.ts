import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { randomUUID } from 'crypto';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { seal, sha256Hex } from '../src/evidence/evidence-chain';
import { configureApp } from '../src/main';

/**
 * Runs against a real, migrated + seeded database (DATABASE_URL, e.g. docker compose postgres).
 * Skipped when DATABASE_URL is not set. Creates its own order, so it can be re-run.
 */
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

describeDb('ProofDrop API (e2e)', () => {
  let app: INestApplication;
  let courierToken: string;
  let courierId: string;
  let dispatcherToken: string;

  const login = async (email: string, password: string) => {
    const res = await request(app.getHttpServer()).post('/api/auth/login').send({ email, password }).expect(200);
    return res.body as { accessToken: string; user: { id: string; role: string } };
  };

  beforeAll(async () => {
    process.env.JWT_SECRET ??= 'e2e-test-secret-at-least-16';
    process.env.STORAGE_DRIVER ??= 'local';
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = moduleRef.createNestApplication();
    configureApp(app);
    await app.init();

    const courier = await login('courier1@proofdrop.dev', 'courier123');
    courierToken = courier.accessToken;
    courierId = courier.user.id;
    dispatcherToken = (await login('dispatcher@proofdrop.dev', 'dispatch123')).accessToken;
  });

  afterAll(async () => {
    await app?.close();
  });

  it('rejects a wrong password', async () => {
    await request(app.getHttpServer())
      .post('/api/auth/login')
      .send({ email: 'courier1@proofdrop.dev', password: 'wrong' })
      .expect(401);
  });

  it('lists only the courier own, assigned orders', async () => {
    const res = await request(app.getHttpServer()).get('/api/orders').set('Authorization', `Bearer ${courierToken}`).expect(200);
    expect(res.body.length).toBeGreaterThan(0);
    for (const order of res.body) {
      expect(order.courierId).toBe(courierId);
      expect(order.status).not.toBe('CREATED');
      expect(typeof order.assignedAt).toBe('number');
    }
  });

  it('keeps couriers out of dispatcher routes', async () => {
    await request(app.getHttpServer()).get('/api/dispatch/orders').set('Authorization', `Bearer ${courierToken}`).expect(403);
  });

  it('accepts sealed evidence and rejects a tampered photo', async () => {
    const server = app.getHttpServer();
    const order = await request(server)
      .post('/api/dispatch/orders')
      .set('Authorization', `Bearer ${dispatcherToken}`)
      .send({ customerName: 'E2E', address: '1 Test St', latitude: 10.776, longitude: 106.701, items: 'Box', courierId })
      .expect(201);
    expect(order.body.status).toBe('ASSIGNED');

    const head = await request(server).get('/api/evidence/head').set('Authorization', `Bearer ${courierToken}`).expect(200);
    const photo = Buffer.from(`e2e-photo-${randomUUID()}`);
    const record = seal(
      {
        id: randomUUID(),
        orderId: order.body.id,
        fileName: 'e2e.jpg',
        fileSha256: sha256Hex(photo),
        capturedAt: Date.now(),
        latitude: 10.7761,
        longitude: 106.7012,
        bleVerified: true,
      },
      head.body.sequence === 0 ? null : head.body,
    );

    const upload = (bytes: Buffer) =>
      request(server)
        .post('/api/evidence')
        .set('Authorization', `Bearer ${courierToken}`)
        .field('record', JSON.stringify(record))
        .attach('file', bytes, { filename: 'e2e.jpg', contentType: 'image/jpeg' });

    const tampered = await upload(Buffer.from('not the sealed photo')).expect(422);
    expect(tampered.body).toEqual({ accepted: false, message: 'File hash mismatch' });

    const ok = await upload(photo).expect(200);
    expect(ok.body.accepted).toBe(true);

    const orders = await request(server).get('/api/orders').set('Authorization', `Bearer ${courierToken}`).expect(200);
    expect(orders.body.find((o: { id: string }) => o.id === order.body.id).status).toBe('DELIVERED');

    const verify = await request(server)
      .get(`/api/dispatch/evidence/verify?courierId=${courierId}`)
      .set('Authorization', `Bearer ${dispatcherToken}`)
      .expect(200);
    expect(verify.body.valid).toBe(true);
  });
});
