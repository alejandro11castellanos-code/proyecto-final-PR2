import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);

function fakeArtistaRepository(seed = []) {
  const items = [...seed];
  let nextId = items.length + 1;
  return {
    async findAll() {
      return items;
    },
    async findById(id) {
      return items.find((a) => a.id_artista === Number(id)) ?? null;
    },
    async create(data) {
      const artista = { id_artista: nextId++, ...data };
      items.push(artista);
      return artista;
    },
    async update(id, data) {
      const artista = items.find((a) => a.id_artista === Number(id));
      if (!artista) return null;
      Object.assign(artista, data);
      return artista;
    },
    async remove(id) {
      const index = items.findIndex((a) => a.id_artista === Number(id));
      if (index === -1) return false;
      items.splice(index, 1);
      return true;
    },
  };
}

test('GET /artistas exige autenticación', async () => {
  const app = createApp({ artistaRepository: fakeArtistaRepository(), jwtSecret });
  const response = await request(app).get('/artistas');
  assert.equal(response.status, 401);
});

test('GET /artistas lista artistas para cualquier rol autenticado', async () => {
  const app = createApp({
    artistaRepository: fakeArtistaRepository([
      { id_artista: 1, nombre_artistico: 'Gaby Moreno', genero_musical: 'Folk', pais_origen: 'Guatemala' },
    ]),
    jwtSecret,
  });

  const response = await request(app).get('/artistas').set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 200);
  assert.equal(response.body.length, 1);
});

test('POST /artistas rechaza a un vendedor', async () => {
  const app = createApp({ artistaRepository: fakeArtistaRepository(), jwtSecret });

  const response = await request(app)
    .post('/artistas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ nombre_artistico: 'Nuevo', genero_musical: 'Rock' });

  assert.equal(response.status, 403);
});

test('POST /artistas valida campos obligatorios', async () => {
  const app = createApp({ artistaRepository: fakeArtistaRepository(), jwtSecret });

  const response = await request(app)
    .post('/artistas')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_artistico: '   ' });

  assert.equal(response.status, 400);
});

test('POST /artistas crea un artista como administrador', async () => {
  const app = createApp({ artistaRepository: fakeArtistaRepository(), jwtSecret });

  const response = await request(app)
    .post('/artistas')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_artistico: 'Bohemia Suburbana', genero_musical: 'Rock Alterno' });

  assert.equal(response.status, 201);
  assert.equal(response.body.nombre_artistico, 'Bohemia Suburbana');
  assert.equal(response.body.pais_origen, null);
});

test('PUT /artistas/:id actualiza y DELETE elimina', async () => {
  const app = createApp({
    artistaRepository: fakeArtistaRepository([
      { id_artista: 1, nombre_artistico: 'Viejo Nombre', genero_musical: 'Rock', pais_origen: null },
    ]),
    jwtSecret,
  });

  const update = await request(app)
    .put('/artistas/1')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_artistico: 'Nombre Actualizado', genero_musical: 'Pop' });
  assert.equal(update.status, 200);
  assert.equal(update.body.nombre_artistico, 'Nombre Actualizado');

  const missingUpdate = await request(app)
    .put('/artistas/99')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ nombre_artistico: 'X', genero_musical: 'Y' });
  assert.equal(missingUpdate.status, 404);

  const del = await request(app).delete('/artistas/1').set('Authorization', `Bearer ${adminToken}`);
  assert.equal(del.status, 204);

  const missingDelete = await request(app)
    .delete('/artistas/1')
    .set('Authorization', `Bearer ${adminToken}`);
  assert.equal(missingDelete.status, 404);
});

test('DELETE /artistas/:id responde 409 si tiene conciertos asociados', async () => {
  const repo = fakeArtistaRepository([
    { id_artista: 1, nombre_artistico: 'X', genero_musical: 'Rock', pais_origen: null },
  ]);
  repo.remove = async () => {
    const error = new Error('foreign key violation');
    error.code = '23503';
    throw error;
  };
  const app = createApp({ artistaRepository: repo, jwtSecret });

  const response = await request(app).delete('/artistas/1').set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 409);
});
