import test from 'node:test';
import assert from 'node:assert/strict';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const passwordHash = bcrypt.hashSync('clave-correcta', 4);
const savedUser = {
  id_usuario: 7,
  nombre_usuario: 'ana',
  contrasena_hash: passwordHash,
  nombre_completo: 'Ana López',
  rol: 'vendedor',
};

function repositoryWith(user = savedUser) {
  return {
    async findByUsername(username) {
      return username === user?.nombre_usuario ? user : null;
    },
    async findPublicById(id) {
      if (Number(id) !== user?.id_usuario) return null;
      const { contrasena_hash: _password, ...publicUser } = user;
      return publicUser;
    },
  };
}

test('POST /auth/login devuelve un JWT y los datos públicos del usuario', async () => {
  const app = createApp({ userRepository: repositoryWith(), jwtSecret });

  const response = await request(app)
    .post('/auth/login')
    .send({ nombre_usuario: 'ana', contrasena: 'clave-correcta' });

  assert.equal(response.status, 200);
  assert.deepEqual(response.body.usuario, {
    id_usuario: 7,
    nombre_usuario: 'ana',
    nombre_completo: 'Ana López',
    rol: 'vendedor',
  });
  const payload = jwt.verify(response.body.token, jwtSecret);
  assert.equal(payload.id_usuario, 7);
  assert.equal(payload.rol, 'vendedor');
});

test('POST /auth/login valida credenciales faltantes o en blanco', async () => {
  const app = createApp({ userRepository: repositoryWith(), jwtSecret });

  for (const body of [
    {},
    { nombre_usuario: '   ', contrasena: 'clave-correcta' },
    { nombre_usuario: 'ana', contrasena: '   ' },
  ]) {
    const response = await request(app).post('/auth/login').send(body);
    assert.equal(response.status, 400);
    assert.equal(response.body.error, 'Usuario y contraseña son obligatorios.');
  }
});

test('POST /auth/login no revela si falló el usuario o la contraseña', async () => {
  const app = createApp({ userRepository: repositoryWith(), jwtSecret });

  const unknown = await request(app)
    .post('/auth/login')
    .send({ nombre_usuario: 'nadie', contrasena: 'clave-correcta' });
  const wrongPassword = await request(app)
    .post('/auth/login')
    .send({ nombre_usuario: 'ana', contrasena: 'incorrecta' });

  assert.equal(unknown.status, 401);
  assert.equal(wrongPassword.status, 401);
  assert.deepEqual(unknown.body, { error: 'Credenciales inválidas.' });
  assert.deepEqual(wrongPassword.body, { error: 'Credenciales inválidas.' });
});

test('GET /auth/me devuelve solo campos públicos con un Bearer token válido', async () => {
  const app = createApp({ userRepository: repositoryWith(), jwtSecret });
  const token = jwt.sign({ id_usuario: 7, rol: 'vendedor' }, jwtSecret);

  const response = await request(app)
    .get('/auth/me')
    .set('Authorization', `Bearer ${token}`);

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, {
    id_usuario: 7,
    nombre_usuario: 'ana',
    nombre_completo: 'Ana López',
    rol: 'vendedor',
  });
  assert.equal('contrasena_hash' in response.body, false);
});

test('GET /auth/me rechaza tokens ausentes, inválidos y usuarios inexistentes', async () => {
  const app = createApp({ userRepository: repositoryWith(), jwtSecret });
  const deletedUserToken = jwt.sign({ id_usuario: 99, rol: 'vendedor' }, jwtSecret);

  const missing = await request(app).get('/auth/me');
  const invalid = await request(app).get('/auth/me').set('Authorization', 'Bearer no-es-jwt');
  const deleted = await request(app)
    .get('/auth/me')
    .set('Authorization', `Bearer ${deletedUserToken}`);

  assert.equal(missing.status, 401);
  assert.equal(invalid.status, 401);
  assert.equal(deleted.status, 401);
});
