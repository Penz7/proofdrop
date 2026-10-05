import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { AuthModule } from './auth/auth.module';
import { JwtAuthGuard } from './auth/jwt-auth.guard';
import { RolesGuard } from './auth/roles.guard';
import { validateEnv } from './config/env.validation';
import { CouriersModule } from './couriers/couriers.module';
import { DevicesModule } from './devices/devices.module';
import { EventsModule } from './events/events.module';
import { EvidenceModule } from './evidence/evidence.module';
import { FleetModule } from './fleet/fleet.module';
import { HealthModule } from './health/health.module';
import { OrdersModule } from './orders/orders.module';
import { PrismaModule } from './prisma/prisma.module';
import { StorageModule } from './storage/storage.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, validate: validateEnv }),
    PrismaModule,
    StorageModule,
    EventsModule,
    AuthModule,
    OrdersModule,
    DevicesModule,
    EvidenceModule,
    FleetModule,
    CouriersModule,
    HealthModule,
  ],
  providers: [
    // Every route requires a valid JWT unless marked @Public(); @Roles() narrows further.
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: RolesGuard },
  ],
})
export class AppModule {}
