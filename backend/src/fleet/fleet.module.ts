import { Global, Module } from '@nestjs/common';
import { FleetGateway } from './fleet.gateway';
import { FleetService } from './fleet.service';

@Global()
@Module({
  providers: [FleetService, FleetGateway],
  exports: [FleetService],
})
export class FleetModule {}
