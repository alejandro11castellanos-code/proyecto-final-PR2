import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);

function fakeLocalidadRepository(seed = []) {
  const items = [...seed];
  let nextId = items.length + 1;
  return {
    async findAll() {
      return items;
    },
    async create({ nombre }) {
      if (items.some((l) => l.nombre === nombre)) {
        const error = new Error('unique violation');
        error.code = '23505';
        throw error;
      }
      const localidad = { id_localidad: nextId++, nombre };
      items.push(localidad);
      return localidad;
    },
    async update(id, { nombre }) {
      const localidad = items.find((l) => l.id_localidad === Number(id));
      if (!localidad) return null;
      localidad.nombre = nombre;
      return localidad;
    },
    async remove(id) {
      const index = items.findIndex((l) => l.id_localidad === Number(id));
      if (index === -1) return false;
      items.splice(index, 1);
      return true;
    },
  };
}

test('GET /localidades exige autenticación', async () => {
  const app = createApp({ localidadRepository: fakeLocalidadRepository(), jwtSecret });
  const response = await request(app).get('/localidades');
  assert.equal(response.status, 401);
});

test('POST /localidades rechaza a un vendedor', async () => {
  const app = createApp({ localidadRepository: fakeLocalidadRepository(), jwtSecret });

  const response = await request(app)
    .post('/localidades')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ nombre: 'VIP' });

  assert.equal(response.status, 403);
});

test('POST /localidades crea una localidad como administrador', async () => {
  const app = createApp({ localidadRepository: fakeLocalidadRepository(), jwtSecret });

  const response = await request(app)
    .post('/localidades')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre: 'VIP' });

  assert.equal(response.status, 201);
  assert.equal(response.body.nombre, 'VIP');
});

test('POST /localidades responde 409 si el nombre ya existe', async () => {
  const app = createApp({
    localidadRepository: fakeLocalidadRepository([{ id_localidad: 1, nombre: 'VIP' }]),
    jwtSecret,
  });

  const response = await request(app)
    .post('/localidades')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre: 'VIP' });

  assert.equal(response.status, 409);
});

test('DELETE /localidades/:id responde 409 si tiene inventario asociado', async () => {
  const repo = fakeLocalidadRepository([{ id_localidad: 1, nombre: 'VIP' }]);
  repo.remove = async () => {
    const error = new Error('foreign key violation');
    error.code = '23503';
    throw error;
  };
  const app = createApp({ localidadRepository: repo, jwtSecret });

  const response = await request(app)
    .delete('/localidades/1')
    .set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 409);
});
