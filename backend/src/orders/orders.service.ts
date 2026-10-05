import { ConflictException, Injectable, NotFoundException, UnprocessableEntityException } from '@nestjs/common';
import { OrderStatus, Prisma } from '@prisma/client';
import { courierSelect, OrderDto, toOrderDto } from '../common/mappers';
import { EventsService } from '../events/events.module';
import { PrismaService } from '../prisma/prisma.service';
import { canCourierTransition } from './order-status';

export interface CreateOrderInput {
  customerName: string;
  customerPhone?: string | null;
  address: string;
  latitude: number;
  longitude: number;
  items: string;
  beaconId?: string | null;
  courierId?: string | null;
}

const include = { courier: courierSelect } as const;

@Injectable()
export class OrdersService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly events: EventsService,
  ) {}

  async listForCourier(courierId: string): Promise<OrderDto[]> {
    const orders = await this.prisma.order.findMany({
      where: { courierId, status: { not: 'CREATED' } },
      include,
      orderBy: [{ assignedAt: 'desc' }, { codeNumber: 'desc' }],
    });
    return orders.map(toOrderDto);
  }

  async listAll(status?: OrderStatus): Promise<OrderDto[]> {
    const orders = await this.prisma.order.findMany({
      where: status ? { status } : undefined,
      include,
      orderBy: { codeNumber: 'desc' },
    });
    return orders.map(toOrderDto);
  }

  async updateStatusByCourier(courierId: string, orderId: string, status: OrderStatus): Promise<OrderDto> {
    const order = await this.prisma.order.findFirst({ where: { id: orderId, courierId } });
    if (!order || order.status === 'CREATED') throw new NotFoundException('Order not found');
    if (!canCourierTransition(order.status, status)) {
      throw new ConflictException(`Cannot change order from ${order.status} to ${status}`);
    }
    if (order.status === status) return toOrderDto(await this.get(orderId));

    const updated = await this.prisma.order.update({
      where: { id: orderId },
      data: { status, deliveredAt: status === 'DELIVERED' ? new Date() : order.deliveredAt },
      include,
    });
    const dto = toOrderDto(updated);
    this.events.emitDispatch({ type: 'order', data: dto });
    return dto;
  }

  async create(input: CreateOrderInput): Promise<OrderDto> {
    if (input.courierId) await this.requireCourier(input.courierId);
    const order = await this.prisma.order.create({
      data: {
        customerName: input.customerName,
        customerPhone: input.customerPhone ?? null,
        address: input.address,
        latitude: input.latitude,
        longitude: input.longitude,
        items: input.items,
        beaconId: input.beaconId || null,
        courierId: input.courierId ?? null,
        status: input.courierId ? 'ASSIGNED' : 'CREATED',
        assignedAt: input.courierId ? new Date() : null,
      },
      include,
    });
    const dto = toOrderDto(order);
    this.events.emitDispatch({ type: 'order', data: dto });
    if (dto.courierId) this.events.emitCourier({ courierId: dto.courierId, type: 'assignment', data: dto });
    return dto;
  }

  async assign(orderId: string, courierId: string | null): Promise<OrderDto> {
    const order = await this.prisma.order.findUnique({ where: { id: orderId } });
    if (!order) throw new NotFoundException('Order not found');
    if (order.status === 'DELIVERED') throw new ConflictException('Delivered orders cannot be reassigned');
    if (courierId) await this.requireCourier(courierId);

    const data: Prisma.OrderUncheckedUpdateInput = courierId
      ? {
          courierId,
          // A fresh assignment (or reassignment) restarts the delivery for the new courier.
          status: order.courierId === courierId ? order.status : 'ASSIGNED',
          assignedAt: order.courierId === courierId ? order.assignedAt : new Date(),
        }
      : { courierId: null, status: 'CREATED', assignedAt: null };

    const updated = await this.prisma.order.update({ where: { id: orderId }, data, include });
    const dto = toOrderDto(updated);
    this.events.emitDispatch({ type: 'order', data: dto });
    if (order.courierId && order.courierId !== courierId) {
      this.events.emitCourier({ courierId: order.courierId, type: 'unassigned', data: { id: orderId } });
    }
    if (courierId) this.events.emitCourier({ courierId, type: 'assignment', data: dto });
    return dto;
  }

  /** Called when accepted evidence proves delivery. */
  async markDeliveredByEvidence(orderId: string): Promise<void> {
    const updated = await this.prisma.order.update({
      where: { id: orderId },
      data: { status: 'DELIVERED', deliveredAt: new Date() },
      include,
    });
    this.events.emitDispatch({ type: 'order', data: toOrderDto(updated) });
  }

  private async get(orderId: string) {
    return this.prisma.order.findUniqueOrThrow({ where: { id: orderId }, include });
  }

  private async requireCourier(courierId: string) {
    const courier = await this.prisma.user.findUnique({ where: { id: courierId } });
    if (!courier || courier.role !== 'COURIER') throw new UnprocessableEntityException('Unknown courier');
  }
}
