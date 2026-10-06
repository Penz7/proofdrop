import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthService } from './auth.module';
import { AuthedRequest, extractToken } from './auth.types';
import { ALLOW_QUERY_TOKEN, IS_PUBLIC } from './decorators';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly auth: AuthService,
    private readonly reflector: Reflector,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (context.getType() !== 'http') return true; // the WebSocket gateway authenticates on connect
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, [context.getHandler(), context.getClass()]);
    if (isPublic) return true;

    const request = context.switchToHttp().getRequest<AuthedRequest>();
    const allowQuery = this.reflector.getAllAndOverride<boolean>(ALLOW_QUERY_TOKEN, [context.getHandler(), context.getClass()]);
    const token = extractToken(request.headers, request.originalUrl ?? request.url, allowQuery === true);
    if (!token) throw new UnauthorizedException('Missing access token');
    request.user = await this.auth.authenticate(token);
    return true;
  }
}
