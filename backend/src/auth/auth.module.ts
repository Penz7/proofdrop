import { Body, Controller, ExecutionContext, Get, Global, HttpCode, Injectable, Module, Post, UnauthorizedException, UseGuards } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtModule, JwtService } from '@nestjs/jwt';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import * as bcrypt from 'bcryptjs';
import { Throttle, ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { IsEmail, IsString, MaxLength, MinLength } from 'class-validator';
import { PrismaService } from '../prisma/prisma.service';
import { AuthUser, JwtPayload } from './auth.types';
import { CurrentUser, Public } from './decorators';

export class LoginDto {
  @IsEmail()
  @MaxLength(254)
  email: string;

  @IsString()
  @MinLength(1)
  @MaxLength(128)
  password: string;
}

/**
 * Limits password guessing per (client IP, email). Keying on the email too means users behind
 * the same proxy/NAT (e.g. the dashboard's nginx) don't lock each other out.
 */
@Injectable()
export class LoginThrottlerGuard extends ThrottlerGuard {
  protected async getTracker(req: Record<string, any>): Promise<string> {
    const email = typeof req.body?.email === 'string' ? req.body.email.toLowerCase().trim() : '';
    return `${req.ip}:${email}`;
  }

  protected generateKey(context: ExecutionContext, tracker: string, throttlerName: string): string {
    return `${throttlerName}:${tracker}`;
  }
}

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
  ) {}

  async login(email: string, password: string): Promise<{ accessToken: string; user: AuthUser }> {
    const user = await this.prisma.user.findUnique({ where: { email: email.toLowerCase().trim() } });
    // Compare even when the user is missing so response time doesn't reveal which emails exist.
    const ok = await bcrypt.compare(password, user?.passwordHash ?? '$2b$10$invalidinvalidinvalidinvalidinvalidinvalidinvali');
    if (!user || !ok) throw new UnauthorizedException('Invalid email or password');

    const payload: JwtPayload = { sub: user.id, email: user.email, name: user.name, role: user.role };
    return {
      accessToken: await this.jwt.signAsync(payload),
      user: { id: user.id, email: user.email, name: user.name, role: user.role },
    };
  }

  /**
   * Verifies the token, then re-loads the user (one primary-key lookup) so a deleted account is
   * refused immediately and the current database role is used rather than the one in the token.
   * Shared by the HTTP guard (REST + SSE) and the fleet WebSocket.
   */
  async authenticate(token: string): Promise<AuthUser> {
    let payload: JwtPayload;
    try {
      payload = await this.jwt.verifyAsync<JwtPayload>(token);
    } catch {
      throw new UnauthorizedException('Invalid or expired access token');
    }
    const user = await this.prisma.user.findUnique({
      where: { id: payload.sub },
      select: { id: true, email: true, name: true, role: true },
    });
    if (!user) throw new UnauthorizedException('Account no longer exists');
    return user;
  }
}

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Public()
  @Post('login')
  @HttpCode(200)
  @UseGuards(LoginThrottlerGuard)
  @Throttle({ login: { limit: 10, ttl: 60_000 } })
  login(@Body() dto: LoginDto) {
    return this.auth.login(dto.email, dto.password);
  }

  @ApiBearerAuth()
  @Get('me')
  me(@CurrentUser() user: AuthUser): AuthUser {
    return user;
  }
}

@Global()
@Module({
  imports: [
    ThrottlerModule.forRoot([{ name: 'login', limit: 10, ttl: 60_000 }]),
    JwtModule.registerAsync({
      global: true,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.getOrThrow<string>('JWT_SECRET'),
        signOptions: { expiresIn: '7d' },
      }),
    }),
  ],
  controllers: [AuthController],
  providers: [AuthService],
  exports: [AuthService],
})
export class AuthModule {}
