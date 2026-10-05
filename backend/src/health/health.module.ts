import { Controller, Get, Module, ServiceUnavailableException } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { Public } from '../auth/decorators';
import { PrismaService } from '../prisma/prisma.service';
import { StorageService } from '../storage/storage.module';

@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly storage: StorageService,
  ) {}

  @Public()
  @Get()
  async check() {
    const db = await this.prisma.$queryRaw`SELECT 1`.then(() => 'ok').catch((e: Error) => e.message);
    const storage = await this.storage.ping().then(() => 'ok').catch((e: Error) => e.message);
    const body = { status: db === 'ok' && storage === 'ok' ? 'ok' : 'degraded', db, storage };
    if (body.status !== 'ok') throw new ServiceUnavailableException(body);
    return body;
  }
}

@Module({ controllers: [HealthController] })
export class HealthModule {}
