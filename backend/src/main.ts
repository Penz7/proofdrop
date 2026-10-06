import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { WsAdapter } from '@nestjs/platform-ws';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';

export function configureApp(app: import('@nestjs/common').INestApplication) {
  app.setGlobalPrefix('api');
  // Behind a reverse proxy (dashboard nginx, Caddy), trust that many hops of X-Forwarded-For so
  // req.ip is the real client: login rate limiting is keyed on it.
  const hops = Number(process.env.TRUST_PROXY ?? 0);
  if (hops > 0) (app.getHttpAdapter().getInstance() as { set(key: string, value: unknown): void }).set('trust proxy', hops);
  // Bearer tokens (not cookies) are used, so an open CORS policy is acceptable for local dev;
  // set CORS_ORIGINS (comma-separated) to lock it down in production.
  const origins = process.env.CORS_ORIGINS?.split(',').map((o) => o.trim()).filter(Boolean);
  app.enableCors(origins?.length ? { origin: origins } : undefined);
  app.useWebSocketAdapter(new WsAdapter(app));
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
  app.enableShutdownHooks();
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  configureApp(app);

  const config = app.get(ConfigService);
  const swaggerEnabled = config.get<string>('SWAGGER_ENABLED') === 'true';
  if (swaggerEnabled) {
    const swagger = new DocumentBuilder()
      .setTitle('ProofDrop API')
      .setDescription('Dispatch backend for the ProofDrop proof-of-delivery app. Contract: docs/API.md')
      .setVersion('1.0')
      .addBearerAuth()
      .build();
    SwaggerModule.setup('api/docs', app, SwaggerModule.createDocument(app, swagger));
  }

  const port = config.get<number>('PORT') ?? 3000;
  await app.listen(port, '0.0.0.0');
  console.log(`ProofDrop backend listening on http://localhost:${port}${swaggerEnabled ? ' (docs: /api/docs)' : ''}`);
}

if (require.main === module) {
  void bootstrap();
}
