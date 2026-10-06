#!/usr/bin/env node
/**
 * Creates (or with --force, updates) a ProofDrop account. Used to bootstrap the first dispatcher
 * on a fresh production database, where demo accounts are never seeded.
 *
 *   npm run create-user -- --email ops@example.com --name "Ops" --role DISPATCHER
 *   docker compose -f docker-compose.prod.yml exec backend npm run create-user -- --email ... --role COURIER
 *
 * The password is prompted for (hidden) unless --password is given. Exit code 0 on success.
 */
const bcrypt = require('bcryptjs');
const readline = require('readline');
const { PrismaClient } = require('@prisma/client');

const ROLES = ['DISPATCHER', 'COURIER'];
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD = 8;

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i];
    if (!key.startsWith('--')) throw new Error(`Unexpected argument: ${key}`);
    const name = key.slice(2);
    if (name === 'force' || name === 'help') {
      args[name] = true;
    } else {
      const value = argv[++i];
      if (value === undefined || value.startsWith('--')) throw new Error(`Missing value for --${name}`);
      args[name] = value;
    }
  }
  return args;
}

/** Validates arguments; returns a list of problems (empty when OK). */
function validate(args) {
  const problems = [];
  if (!args.email || !EMAIL.test(args.email)) problems.push('--email must be a valid email address');
  if (!args.name || !args.name.trim()) problems.push('--name is required');
  if (!ROLES.includes(args.role)) problems.push(`--role must be one of ${ROLES.join(', ')}`);
  if (args.password !== undefined && args.password.length < MIN_PASSWORD) {
    problems.push(`--password must be at least ${MIN_PASSWORD} characters`);
  }
  return problems;
}

/** Reads a line from the terminal without echoing it. */
function promptHidden(question) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    rl.stdoutMuted = false;
    rl._writeToOutput = (text) => {
      if (!rl.stdoutMuted || text.includes(question)) rl.output.write(text);
    };
    rl.question(question, (answer) => {
      rl.close();
      process.stdout.write('\n');
      resolve(answer);
    });
    rl.stdoutMuted = true;
  });
}

async function main(argv) {
  const args = parseArgs(argv);
  if (args.help) {
    console.log('Usage: create-user --email <email> --name <name> --role DISPATCHER|COURIER [--password <pw>] [--force]');
    return 0;
  }
  const problems = validate(args);
  if (problems.length) {
    problems.forEach((p) => console.error(`Error: ${p}`));
    return 1;
  }

  let password = args.password;
  if (password === undefined) {
    password = await promptHidden('Password: ');
    if ((await promptHidden('Repeat password: ')) !== password) {
      console.error('Error: passwords do not match');
      return 1;
    }
    if (password.length < MIN_PASSWORD) {
      console.error(`Error: password must be at least ${MIN_PASSWORD} characters`);
      return 1;
    }
  }

  const prisma = new PrismaClient();
  try {
    const email = args.email.toLowerCase().trim();
    const data = { email, name: args.name.trim(), role: args.role, passwordHash: await bcrypt.hash(password, 10) };
    const existing = await prisma.user.findUnique({ where: { email } });
    if (existing && !args.force) {
      console.error(`Error: ${email} already exists (use --force to update its name, role and password)`);
      return 1;
    }
    const user = existing
      ? await prisma.user.update({ where: { email }, data })
      : await prisma.user.create({ data });
    console.log(`${existing ? 'Updated' : 'Created'} ${user.role} ${user.email} (${user.id})`);
    return 0;
  } finally {
    await prisma.$disconnect();
  }
}

module.exports = { parseArgs, validate };

if (require.main === module) {
  main(process.argv.slice(2))
    .then((code) => process.exit(code))
    .catch((e) => {
      console.error(`Error: ${e.message}`);
      process.exit(1);
    });
}
