import { ConfigService } from '@nestjs/config';
import * as os from 'os';
import * as path from 'path';
import { StorageService } from '../storage/storage.module';
import { sniffImageType } from './evidence.service';

describe('sniffImageType', () => {
  it('recognises JPEG, PNG and WebP by their magic bytes', () => {
    expect(sniffImageType(Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00]))).toBe('image/jpeg');
    expect(sniffImageType(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00]))).toBe('image/png');
    expect(sniffImageType(Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WEBPVP8 ')]))).toBe('image/webp');
  });

  it('rejects anything else, whatever the client claims', () => {
    expect(sniffImageType(Buffer.from('<html><script>alert(1)</script></html>'))).toBeNull();
    expect(sniffImageType(Buffer.from('%PDF-1.7'))).toBeNull();
    expect(sniffImageType(Buffer.alloc(0))).toBeNull();
  });
});

describe('StorageService (local driver)', () => {
  const uploadDir = path.join(os.tmpdir(), `proofdrop-uploads-${process.pid}`);
  const storage = new StorageService({
    get: (key: string) => ({ STORAGE_DRIVER: 'local', UPLOAD_DIR: uploadDir })[key],
  } as unknown as ConfigService);

  beforeAll(() => storage.onModuleInit());

  it('stores and reads back a key inside the upload dir', async () => {
    await storage.put('courier/000001-x.jpg', Buffer.from('hi'), 'image/jpeg');
    expect((await storage.get('courier/000001-x.jpg'))?.toString()).toBe('hi');
  });

  it('refuses keys that escape the upload dir, including prefix-sharing siblings', async () => {
    await expect(storage.put('../evil.jpg', Buffer.from('x'), 'image/jpeg')).rejects.toThrow('Invalid storage key');
    const sibling = `../${path.basename(uploadDir)}-old/evil.jpg`;
    await expect(storage.put(sibling, Buffer.from('x'), 'image/jpeg')).rejects.toThrow('Invalid storage key');
  });
});
