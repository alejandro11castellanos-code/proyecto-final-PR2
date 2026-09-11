// Genera un hash bcrypt para una contrasena.
// Uso:  node scripts/hash.js "miPassword"
import bcrypt from 'bcryptjs';

const password = process.argv[2];

if (!password) {
  console.error('Uso: node scripts/hash.js "<password>"');
  process.exit(1);
}

console.log(bcrypt.hashSync(password, 10));
