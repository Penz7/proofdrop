import { Role } from '@prisma/client';
import { Request } from 'express';

export interface AuthUser {
  id: string;
  email: string;
  name: string;
  role: Role;
}

export interface JwtPayload {
  sub: string;
  email: string;
  name: string;
  role: Role;
}

export type AuthedRequest = Request & { user: AuthUser };

/**
 * Bearer header first. `?access_token=` is only honoured when [allowQuery] is set: on the few
 * routes used by EventSource, <img> and WebSocket clients, which cannot send headers. Elsewhere a
 * token in the URL would end up in proxy and access logs for no reason.
 */
export function extractToken(headers: Record<string, unknown>, url: string | undefined, allowQuery: boolean): string | null {
  const auth = headers['authorization'];
  if (typeof auth === 'string' && auth.startsWith('Bearer ')) return auth.slice(7).trim() || null;
  if (allowQuery && url) {
    const query = url.includes('?') ? url.slice(url.indexOf('?') + 1) : '';
    const token = new URLSearchParams(query).get('access_token');
    if (token) return token;
  }
  return null;
}
