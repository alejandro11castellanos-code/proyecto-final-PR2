import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);

function fakeUserRepository(seed = []) {
  const items = seed.map((u) => ({ ...u }));
  let nextId = items.length + 1;

  function publico({ contrasena_hash: _hash, ...publico }) {
    return publico;
  }

  return {
    async findAll() {
      return items.map(publico);
    },
    async create({ nombre_usuario, contrasena_hash, nombre_completo, rol }) {
      if (items.some((u) => u.nombre_usuario === nombre_usuario)) {
        const error = new Error('unique violation');
        error.code = '23505';
        throw error;
      }
      const usuario = { id_usuario: nextId++, nombre_usuario, contrasena_hash, nombre_completo, rol };
      items.push(usuario);
      return publico(usuario);
    },
    async update(id, { nombre_completo, rol }) {
      const usuario = items.find((u) => u.id_usuario === Number(id));
      if (!usuario) return null;
      usuario.nombre_completo = nombre_completo;
      usuario.rol = rol;
      return publico(usuario);
    },
    async updatePassword(id, contrasenaHash) {
      const usuario = items.find((u) => u.id_usuario === Number(id));
      if (!usuario) return false;
      usuario.contrasena_hash = contrasenaHash;
      return true;
    },
    async remove(id) {
      const index = items.findIndex((u) => u.id_usuario === Number(id));
      if (index === -1) return false;
      items.splice(index, 1);
      return true;
    },
  };
}

function buildApp(seed) {
  return createApp({ userRepository: fakeUserRepository(seed), jwtSecret });
}

const admin = { id_usuario: 1, nombre_usuario: 'admin', contrasena_hash: 'x', nombre_completo: 'Admin', rol: 'administrador' };
const vendedor1 = { id_usuario: 2, nombre_usuario: 'vendedor1', contrasena_hash: 'x', nombre_completo: 'Ana', rol: 'vendedor' };

test('GET /usuarios exige autenticación', async () => {
  const response = await request(buildApp([admin])).get('/usuarios');
  assert.equal(response.status, 401);
});

test('GET /usuarios rechaza a un vendedor', async () => {
  const response = await request(buildApp([admin]))
    .get('/usuarios')
    .set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 403);
});

test('GET /usuarios lista usuarios sin exponer el hash', async () => {
  const response = await request(buildApp([admin, vendedor1]))
    .get('/usuarios')
    .set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 200);
  assert.equal(response.body.length, 2);
  assert.equal(response.body.every((u) => !('contrasena_hash' in u)), true);
});

test('POST /usuarios valida campos obligatorios y rol', async () => {
  const app = buildApp([admin]);
  for (const body of [
    {},
    { nombre_usuario: 'nuevo', contrasena: 'clave123', nombre_completo: 'X', rol: 'invitado' },
    { nombre_usuario: '  ', contrasena: 'clave123', nombre_completo: 'X', rol: 'vendedor' },
  ]) {
    const response = await request(app)
      .post('/usuarios')
      .set('Authorization', `Bearer ${adminToken}`)
      .send(body);
    assert.equal(response.status, 400);
  }
});

test('POST /usuarios rechaza una contraseña corta', async () => {
  const response = await request(buildApp([admin]))
    .post('/usuarios')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_usuario: 'nuevo', contrasena: '123', nombre_completo: 'Nuevo', rol: 'vendedor' });
  assert.equal(response.status, 400);
});

test('POST /usuarios crea un usuario y no devuelve el hash', async () => {
  const response = await request(buildApp([admin]))
    .post('/usuarios')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_usuario: 'vendedor2', contrasena: 'clave123', nombre_completo: 'Carlos', rol: 'vendedor' });

  assert.equal(response.status, 201);
  assert.equal(response.body.nombre_usuario, 'vendedor2');
  assert.equal('contrasena_hash' in response.body, false);
});

test('POST /usuarios responde 409 si el nombre de usuario ya existe', async () => {
  const response = await request(buildApp([admin]))
    .post('/usuarios')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_usuario: 'admin', contrasena: 'clave123', nombre_completo: 'Otro', rol: 'vendedor' });
  assert.equal(response.status, 409);
});

test('PUT /usuarios/:id actualiza nombre y rol', async () => {
  const app = buildApp([admin, vendedor1]);
  const response = await request(app)
    .put('/usuarios/2')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_completo: 'Ana López', rol: 'administrador' });

  assert.equal(response.status, 200);
  assert.equal(response.body.nombre_completo, 'Ana López');
  assert.equal(response.body.rol, 'administrador');
});

test('PUT /usuarios/:id/contrasena exige una contraseña válida', async () => {
  const response = await request(buildApp([admin, vendedor1]))
    .put('/usuarios/2/contrasena')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ contrasena: '123' });
  assert.equal(response.status, 400);
});

test('PUT /usuarios/:id/contrasena actualiza la contraseña', async () => {
  const response = await request(buildApp([admin, vendedor1]))
    .put('/usuarios/2/contrasena')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ contrasena: 'nuevaClave123' });
  assert.equal(response.status, 200);
  assert.deepEqual(response.body, { actualizado: true });
});

test('DELETE /usuarios/:id rechaza que un administrador se elimine a sí mismo', async () => {
  const response = await request(buildApp([admin]))
    .delete('/usuarios/1')
    .set('Authorization', `Bearer ${adminToken}`);
  assert.equal(response.status, 400);
});

test('DELETE /usuarios/:id elimina y responde 404 si no existe', async () => {
  const app = buildApp([admin, vendedor1]);

  const eliminado = await request(app).delete('/usuarios/2').set('Authorization', `Bearer ${adminToken}`);
  assert.equal(eliminado.status, 204);

  const inexistente = await request(app).delete('/usuarios/2').set('Authorization', `Bearer ${adminToken}`);
  assert.equal(inexistente.status, 404);
});

test('DELETE /usuarios/:id responde 409 si el usuario tiene ventas asociadas', async () => {
  const repo = fakeUserRepository([admin, vendedor1]);
  repo.remove = async () => {
    const error = new Error('foreign key violation');
    error.code = '23503';
    throw error;
  };
  const app = createApp({ userRepository: repo, jwtSecret });

  const response = await request(app).delete('/usuarios/2').set('Authorization', `Bearer ${adminToken}`);
  assert.equal(response.status, 409);
});
