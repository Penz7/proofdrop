import { Controller, Get, Logger, Module, ServiceUnavailableException } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { Public } from '../auth/decorators';
import { PrismaService } from '../prisma/prisma.service';
import { StorageService } from '../storage/storage.module';

@ApiTags('health')
@Controller('health')
export class HealthController {
  private readonly logger = new Logger(HealthController.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly storage: StorageService,
  ) {}

  @Public()
  @Get()
  async check() {
    // Public endpoint: report up/down only; the details go to the server log.
    const db = await this.prisma.$queryRaw`SELECT 1`.then(() => 'ok').catch((e: Error) => this.fail('db', e));
    const storage = await this.storage.ping().then(() => 'ok').catch((e: Error) => this.fail('storage', e));
    const body = { status: db === 'ok' && storage === 'ok' ? 'ok' : 'degraded', db, storage };
    if (body.status !== 'ok') throw new ServiceUnavailableException(body);
    return body;
  }

  private fail(part: string, e: Error): string {
    this.logger.error(`Health check failed for ${part}: ${e.message}`);
    return 'error';
  }
}

@Module({ controllers: [HealthController] })
export class HealthModule {}
