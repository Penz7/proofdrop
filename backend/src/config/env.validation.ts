import { plainToInstance, Type } from 'class-transformer';
import { IsIn, IsInt, IsNotEmpty, IsOptional, IsString, MinLength, validateSync } from 'class-validator';

export class Env {
  @Type(() => Number)
  @IsInt()
  PORT: number = 3000;

  @IsString()
  @IsNotEmpty()
  DATABASE_URL: string;

  @IsString()
  @MinLength(16)
  JWT_SECRET: string;

  @IsIn(['local', 's3'])
  STORAGE_DRIVER: 'local' | 's3' = 'local';

  @IsString()
  UPLOAD_DIR = './uploads';

  @IsOptional() @IsString() S3_ENDPOINT?: string;
  @IsString() S3_REGION = 'us-east-1';
  @IsOptional() @IsString() S3_ACCESS_KEY?: string;
  @IsOptional() @IsString() S3_SECRET_KEY?: string;
  @IsString() S3_BUCKET = 'evidence';
}

/** Fails fast at boot with a readable list of what is missing. */
export function validateEnv(raw: Record<string, unknown>): Env {
  const env = plainToInstance(Env, raw, { enableImplicitConversion: true, exposeDefaultValues: true });
  const errors = validateSync(env, { skipMissingProperties: false });
  if (errors.length > 0) {
    const details = errors.map((e) => `${e.property}: ${Object.values(e.constraints ?? {}).join(', ')}`);
    throw new Error(`Invalid environment:\n  ${details.join('\n  ')}`);
  }
  if (env.STORAGE_DRIVER === 's3' && (!env.S3_ACCESS_KEY || !env.S3_SECRET_KEY)) {
    throw new Error('Invalid environment: S3_ACCESS_KEY and S3_SECRET_KEY are required when STORAGE_DRIVER=s3');
  }
  return env;
}
