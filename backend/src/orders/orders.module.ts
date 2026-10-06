import { Body, Controller, Get, HttpCode, Module, Param, ParseUUIDPipe, Post, Query, Res } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { OrderStatus } from '@prisma/client';
import { Type } from 'class-transformer';
import { IsEnum, IsInt, IsLatitude, IsLongitude, IsNotEmpty, IsOptional, IsString, IsUUID, MaxLength, ValidateIf } from 'class-validator';
import { Response } from 'express';
import { map } from 'rxjs';
import type { AuthUser } from '../auth/auth.types';
import { AllowQueryToken, CurrentUser, Roles } from '../auth/decorators';
import { EventsService, streamSse } from '../events/events.module';
import { OrdersService } from './orders.service';

export class StatusUpdateDto {
  @IsEnum(OrderStatus)
  status: OrderStatus;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  at?: number;
}

export class CreateOrderDto {
  @IsString() @IsNotEmpty() @MaxLength(120) customerName: string;
  @IsOptional() @IsString() @MaxLength(32) customerPhone?: string;
  @IsString() @IsNotEmpty() @MaxLength(300) address: string;
  @Type(() => Number) @IsLatitude() latitude: number;
  @Type(() => Number) @IsLongitude() longitude: number;
  @IsString() @IsNotEmpty() @MaxLength(500) items: string;
  @IsOptional() @IsString() @MaxLength(64) beaconId?: string;
  @IsOptional() @IsUUID() courierId?: string;
}

export class AssignDto {
  @ValidateIf((_, v) => v !== null)
  @IsUUID()
  courierId: string | null;
}

export class OrderQueryDto {
  @IsOptional() @IsEnum(OrderStatus) status?: OrderStatus;
}

@ApiTags('courier')
@ApiBearerAuth()
@Roles('COURIER')
@Controller()
export class CourierOrdersController {
  constructor(
    private readonly orders: OrdersService,
    private readonly events: EventsService,
  ) {}

  @Get('orders')
  list(@CurrentUser() user: AuthUser) {
    return this.orders.listForCourier(user.id);
  }

  @Post('orders/:id/status')
  @HttpCode(200)
  updateStatus(@CurrentUser() user: AuthUser, @Param('id', ParseUUIDPipe) id: string, @Body() dto: StatusUpdateDto) {
    return this.orders.updateStatusByCourier(user.id, id, dto.status);
  }

  /** SSE: `assignment` / `unassigned` events for this courier. */
  @Get('assignments')
  @AllowQueryToken()
  assignments(@CurrentUser() user: AuthUser, @Res() res: Response) {
    streamSse(res, this.events.forCourier(user.id).pipe(map((e) => ({ type: e.type, data: e.data }))));
  }
}

@ApiTags('dispatch')
@ApiBearerAuth()
@Roles('DISPATCHER')
@Controller('dispatch')
export class DispatchOrdersController {
  constructor(
    private readonly orders: OrdersService,
    private readonly events: EventsService,
  ) {}

  @Get('orders')
  list(@Query() query: OrderQueryDto) {
    return this.orders.listAll(query.status);
  }

  @Post('orders')
  create(@Body() dto: CreateOrderDto) {
    return this.orders.create(dto);
  }

  @Post('orders/:id/assign')
  @HttpCode(200)
  assign(@Param('id', ParseUUIDPipe) id: string, @Body() dto: AssignDto) {
    return this.orders.assign(id, dto.courierId ?? null);
  }

  @Post('orders/:id/cancel')
  @HttpCode(200)
  cancel(@Param('id', ParseUUIDPipe) id: string) {
    return this.orders.cancel(id);
  }

  /** SSE: `order` and `evidence` events for every dispatcher. */
  @Get('events')
  @AllowQueryToken()
  stream(@Res() res: Response) {
    streamSse(res, this.events.forDispatchers());
  }
}

@Module({
  controllers: [CourierOrdersController, DispatchOrdersController],
  providers: [OrdersService],
  exports: [OrdersService],
})
export class OrdersModule {}
