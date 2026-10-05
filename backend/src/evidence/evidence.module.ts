import { Body, Controller, Get, HttpCode, Module, Param, Post, Query, Res, UploadedFile, UseInterceptors } from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { IsOptional, IsString, IsUUID } from 'class-validator';
import { Response } from 'express';
import { memoryStorage } from 'multer';
import type { AuthUser } from '../auth/auth.types';
import { CurrentUser, Roles } from '../auth/decorators';
import { OrdersModule } from '../orders/orders.module';
import { EvidenceService } from './evidence.service';

export class EvidenceQueryDto {
  @IsOptional() @IsUUID() courierId?: string;
  @IsOptional() @IsString() orderId?: string;
}

export class VerifyQueryDto {
  @IsUUID() courierId: string;
}

const MAX_PHOTO_BYTES = 25 * 1024 * 1024;

@ApiTags('courier')
@ApiBearerAuth()
@Roles('COURIER')
@Controller('evidence')
export class CourierEvidenceController {
  constructor(private readonly evidence: EvidenceService) {}

  @Get('head')
  head(@CurrentUser() user: AuthUser) {
    return this.evidence.head(user.id);
  }

  /** multipart/form-data: `record` (JSON string) + `file` (JPEG). */
  @Post()
  @HttpCode(200)
  @UseInterceptors(FileInterceptor('file', { storage: memoryStorage(), limits: { fileSize: MAX_PHOTO_BYTES } }))
  upload(
    @CurrentUser() user: AuthUser,
    @Body('record') record: string | undefined,
    @UploadedFile() file: Express.Multer.File | undefined,
  ) {
    return this.evidence.upload(user.id, record, file);
  }
}

@ApiTags('dispatch')
@ApiBearerAuth()
@Roles('DISPATCHER')
@Controller('dispatch/evidence')
export class DispatchEvidenceController {
  constructor(private readonly evidence: EvidenceService) {}

  @Get()
  list(@Query() query: EvidenceQueryDto) {
    return this.evidence.list(query);
  }

  @Get('verify')
  verify(@Query() query: VerifyQueryDto) {
    return this.evidence.verify(query.courierId);
  }

  @Get(':id/photo')
  async photo(@Param('id') id: string, @Res() res: Response) {
    const { body, contentType } = await this.evidence.photo(id);
    res.setHeader('Content-Type', contentType);
    res.setHeader('Cache-Control', 'private, max-age=3600');
    res.send(body);
  }
}

@Module({
  imports: [OrdersModule],
  controllers: [CourierEvidenceController, DispatchEvidenceController],
  providers: [EvidenceService],
})
export class EvidenceModule {}
