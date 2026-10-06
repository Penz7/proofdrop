import { createParamDecorator, ExecutionContext, SetMetadata } from '@nestjs/common';
import { Role } from '@prisma/client';
import { AuthedRequest, AuthUser } from './auth.types';

export const IS_PUBLIC = 'isPublic';
export const ROLES = 'roles';
export const ALLOW_QUERY_TOKEN = 'allowQueryToken';

/** Skips JWT authentication for this route. */
export const Public = () => SetMetadata(IS_PUBLIC, true);

/** Accepts `?access_token=` on this route (SSE streams and <img> sources only). */
export const AllowQueryToken = () => SetMetadata(ALLOW_QUERY_TOKEN, true);

/** Restricts the route (or controller) to these roles. */
export const Roles = (...roles: Role[]) => SetMetadata(ROLES, roles);

export const CurrentUser = createParamDecorator(
  (_: unknown, ctx: ExecutionContext): AuthUser => ctx.switchToHttp().getRequest<AuthedRequest>().user,
);
