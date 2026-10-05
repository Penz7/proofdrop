import { Controller, Get, Module } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { Roles } from '../auth/decorators';
import { FleetService } from '../fleet/fleet.service';
import { PrismaService } from '../prisma/prisma.service';

@ApiTags('dispatch')
@ApiBearerAuth()
@Roles('DISPATCHER')
@Controller('dispatch/couriers')
export class DispatchCouriersController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly fleet: FleetService,
  ) {}

  @Get()
  async list() {
    const couriers = await this.prisma.user.findMany({
      where: { role: 'COURIER' },
      orderBy: { name: 'asc' },
      include: { _count: { select: { orders: { where: { status: { in: ['ASSIGNED', 'PICKED_UP'] } } } } } },
    });
    return couriers.map((c) => ({
      id: c.id,
      name: c.name,
      email: c.email,
      online: this.fleet.isOnline(c.id),
      lastPosition:
        this.fleet.lastPosition(c.id) ??
        (c.lastLat != null && c.lastLng != null && c.lastSeenAt
          ? {
              courierId: c.id,
              name: c.name,
              latitude: c.lastLat,
              longitude: c.lastLng,
              status: 'OFFLINE' as const,
              updatedAt: c.lastSeenAt.getTime(),
            }
          : null),
      activeOrders: c._count.orders,
    }));
  }
}

@Module({ controllers: [DispatchCouriersController] })
export class CouriersModule {}
