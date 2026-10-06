// eslint-disable-next-line @typescript-eslint/no-require-imports
const { parseArgs, validate } = require('../scripts/create-user');

describe('create-user CLI arguments', () => {
  it('parses flags and values', () => {
    expect(parseArgs(['--email', 'a@b.co', '--name', 'Ops', '--role', 'DISPATCHER', '--force'])).toEqual({
      email: 'a@b.co',
      name: 'Ops',
      role: 'DISPATCHER',
      force: true,
    });
  });

  it('rejects a flag without a value', () => {
    expect(() => parseArgs(['--email'])).toThrow('Missing value for --email');
  });

  it('reports every invalid field', () => {
    expect(validate({ email: 'nope', name: ' ', role: 'ADMIN', password: 'short' })).toEqual([
      '--email must be a valid email address',
      '--name is required',
      '--role must be one of DISPATCHER, COURIER',
      '--password must be at least 8 characters',
    ]);
  });

  it('accepts a complete set (password may be prompted for instead)', () => {
    expect(validate({ email: 'ops@example.com', name: 'Ops', role: 'COURIER' })).toEqual([]);
  });
});
