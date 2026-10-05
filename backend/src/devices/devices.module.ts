import {
  Body,
  ConflictException,
  Controller,
  Get,
  HttpCode,
  Injectable,
  Module,
  NotFoundException,
  Param,
  Post,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { DeviceType, Prisma } from '@prisma/client';
import { Type } from 'class-transformer';
import { IsEnum, IsInt, IsNotEmpty, IsOptional, IsString, Matches, Max, Min } from 'class-validator';
import type { AuthUser } from '../auth/auth.types';
import { CurrentUser, Roles } from '../auth/decorators';
import { courierSelect, toDeviceDto } from '../common/mappers';
import { PrismaService } from '../prisma/prisma.service';

export class CheckoutDto {
  // Sent by the app but ignored: the holder is always the authenticated user.
  @IsOptional() @IsString() courierId?: string;
  @IsOptional() @IsString() courierName?: string;
  @IsOptional() @Type(() => Number) @IsInt() at?: number;
}

export class CreateDeviceDto {
  @Matches(/^[A-Z0-9-]{3,32}$/, { message: 'id must look like DEV-001' }) id: string;
  @IsString() @IsNotEmpty() name: string;
  @IsEnum(DeviceType) type: DeviceType;
  @IsString() @IsNotEmpty() serial: string;
  @Type(() => Number) @IsInt() @Min(0) @Max(100) batteryPct: number;
}

const include = { holder: courierSelect } as const;

@Injectable()
export class DevicesService {
  constructor(private readonly prisma: PrismaService) {}

  async list() {
    const devices = await this.prisma.device.findMany({ include, orderBy: { id: 'asc' } });
    return devices.map(toDeviceDto);
  }

  /** Someone else holding it is not an error: the unchanged device tells the client who has it. */
  async checkout(deviceId: string, user: AuthUser, at?: number) {
    const device = await this.prisma.device.findUnique({ where: { id: deviceId }, include });
    if (!device) throw new NotFoundException('Unknown device');
    if (device.holderId && device.holderId !== user.id) return toDeviceDto(device);
    if (device.holderId === user.id) return toDeviceDto(device);

    // Conditional update so two couriers scanning at once can't both win.
    // If it was taken in between, the re-read below shows the winner.
    await this.prisma.device.updateMany({
      where: { id: deviceId, holderId: null },
      data: { holderId: user.id, checkedOutAt: at ? new Date(at) : new Date() },
    });
    return toDeviceDto(await this.prisma.device.findUniqueOrThrow({ where: { id: deviceId }, include }));
  }

  async returnDevice(deviceId: string, user: AuthUser) {
    const device = await this.prisma.device.findUnique({ where: { id: deviceId }, include });
    if (!device) throw new NotFoundException('Unknown device');
    if (device.holderId === null) return toDeviceDto(device); // already returned (offline retry)
    if (device.holderId !== user.id) throw new ConflictException('Only the holder can return this device');
    const updated = await this.prisma.device.update({
      where: { id: deviceId },
      data: { holderId: null, checkedOutAt: null },
      include,
    });
    return toDeviceDto(updated);
  }

  async create(dto: CreateDeviceDto) {
    try {
      const device = await this.prisma.device.create({ data: dto, include });
      return toDeviceDto(device);
    } catch (e) {
      if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2002') {
        throw new ConflictException(`Device ${dto.id} already exists`);
      }
      throw e;
    }
  }
}

@ApiTags('courier')
@ApiBearerAuth()
@Roles('COURIER')
@Controller('devices')
export class CourierDevicesController {
  constructor(private readonly devices: DevicesService) {}

  @Get()
  list() {
    return this.devices.list();
  }

  @Post(':id/checkout')
  @HttpCode(200)
  checkout(@CurrentUser() user: AuthUser, @Param('id') id: string, @Body() dto: CheckoutDto) {
    return this.devices.checkout(id, user, dto.at);
  }

  @Post(':id/return')
  @HttpCode(200)
  returnDevice(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    return this.devices.returnDevice(id, user);
  }
}

@ApiTags('dispatch')
@ApiBearerAuth()
@Roles('DISPATCHER')
@Controller('dispatch/devices')
export class DispatchDevicesController {
  constructor(private readonly devices: DevicesService) {}

  @Get()
  list() {
    return this.devices.list();
  }

  @Post()
  create(@Body() dto: CreateDeviceDto) {
    return this.devices.create(dto);
  }
}

@Module({
  controllers: [CourierDevicesController, DispatchDevicesController],
  providers: [DevicesService],
})
export class DevicesModule {}
