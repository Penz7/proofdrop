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

/** Bearer header first, then `?access_token=` for EventSource, <img> and WebSocket clients. */
export function extractToken(headers: Record<string, unknown>, url: string | undefined): string | null {
  const auth = headers['authorization'];
  if (typeof auth === 'string' && auth.startsWith('Bearer ')) return auth.slice(7).trim() || null;
  if (url) {
    const query = url.includes('?') ? url.slice(url.indexOf('?') + 1) : '';
    const token = new URLSearchParams(query).get('access_token');
    if (token) return token;
  }
  return null;
}

export function payloadToUser(p: JwtPayload): AuthUser {
  return { id: p.sub, email: p.email, name: p.name, role: p.role };
}
