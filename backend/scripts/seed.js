// Aplica database/seed.sql sustituyendo los marcadores de hash por hashes
// bcrypt reales, para no versionar credenciales calculadas.
// Uso:  npm run db:seed
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import bcrypt from 'bcryptjs';
import { pool } from '../src/db/pool.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const seedPath = path.resolve(here, '../../database/seed.sql');

const sql = (await readFile(seedPath, 'utf8'))
  .replaceAll('__BCRYPT_ADMIN__', bcrypt.hashSync('admin123', 10))
  .replaceAll('__BCRYPT_VENDEDOR__', bcrypt.hashSync('vendedor123', 10));

try {
  await pool.query(sql);
  console.log('Seed aplicado.');
} catch (err) {
  console.error('Fallo al aplicar el seed:', err.message);
  process.exitCode = 1;
} finally {
  await pool.end();
}
