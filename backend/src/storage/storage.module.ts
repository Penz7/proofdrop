import {
  CreateBucketCommand,
  GetObjectCommand,
  HeadBucketCommand,
  PutObjectCommand,
  S3Client,
} from '@aws-sdk/client-s3';
import { Global, Injectable, Logger, Module, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { promises as fs } from 'fs';
import * as path from 'path';

/**
 * Evidence media storage. `local` writes under UPLOAD_DIR; `s3` talks to any S3-compatible
 * store (MinIO locally, AWS S3 or Cloudflare R2 in production) so moving to the cloud is config only.
 */
@Injectable()
export class StorageService implements OnModuleInit {
  private readonly logger = new Logger(StorageService.name);
  private readonly driver: 'local' | 's3';
  private readonly bucket: string;
  private readonly uploadDir: string;
  private readonly s3?: S3Client;

  constructor(config: ConfigService) {
    this.driver = config.get<'local' | 's3'>('STORAGE_DRIVER') ?? 'local';
    this.bucket = config.get<string>('S3_BUCKET') ?? 'evidence';
    this.uploadDir = path.resolve(config.get<string>('UPLOAD_DIR') ?? './uploads');
    if (this.driver === 's3') {
      this.s3 = new S3Client({
        endpoint: config.get<string>('S3_ENDPOINT') || undefined,
        region: config.get<string>('S3_REGION') ?? 'us-east-1',
        forcePathStyle: true,
        credentials: {
          accessKeyId: config.getOrThrow<string>('S3_ACCESS_KEY'),
          secretAccessKey: config.getOrThrow<string>('S3_SECRET_KEY'),
        },
      });
    }
  }

  async onModuleInit() {
    if (this.s3) {
      try {
        await this.s3.send(new HeadBucketCommand({ Bucket: this.bucket }));
      } catch {
        await this.s3.send(new CreateBucketCommand({ Bucket: this.bucket }));
        this.logger.log(`Created bucket "${this.bucket}"`);
      }
    } else {
      await fs.mkdir(this.uploadDir, { recursive: true });
    }
    this.logger.log(`Evidence storage: ${this.driver}${this.s3 ? ` (bucket ${this.bucket})` : ` (${this.uploadDir})`}`);
  }

  async put(key: string, body: Buffer, contentType: string): Promise<void> {
    if (this.s3) {
      await this.s3.send(new PutObjectCommand({ Bucket: this.bucket, Key: key, Body: body, ContentType: contentType }));
      return;
    }
    const file = this.localPath(key);
    await fs.mkdir(path.dirname(file), { recursive: true });
    await fs.writeFile(file, body);
  }

  /** Returns null if the object does not exist. */
  async get(key: string): Promise<Buffer | null> {
    try {
      if (this.s3) {
        const res = await this.s3.send(new GetObjectCommand({ Bucket: this.bucket, Key: key }));
        if (!res.Body) return null;
        return Buffer.from(await res.Body.transformToByteArray());
      }
      return await fs.readFile(this.localPath(key));
    } catch (e: unknown) {
      // S3 SDK errors carry the reason in `name`; Node fs errors have name "Error" and use `code`.
      const { name, code } = (e ?? {}) as { name?: string; code?: string };
      if (name === 'NoSuchKey' || name === 'NotFound' || code === 'ENOENT') return null;
      throw e;
    }
  }

  async ping(): Promise<void> {
    if (this.s3) await this.s3.send(new HeadBucketCommand({ Bucket: this.bucket }));
    else await fs.access(this.uploadDir);
  }

  private localPath(key: string): string {
    const resolved = path.resolve(this.uploadDir, key);
    // path.relative catches both "../" and sibling directories that merely share a prefix
    // (e.g. /data/uploads-old when UPLOAD_DIR is /data/uploads), which startsWith() would not.
    const relative = path.relative(this.uploadDir, resolved);
    if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Invalid storage key');
    return resolved;
  }
}

@Global()
@Module({ providers: [StorageService], exports: [StorageService] })
export class StorageModule {}
