import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);
const artistaExistente = { id_artista: 1, nombre_artistico: 'Gaby Moreno' };

function fakeArtistaRepository(seed = [artistaExistente]) {
  return {
    async findById(id) {
      return seed.find((a) => a.id_artista === Number(id)) ?? null;
    },
  };
}

function fakeConciertoRepository(seed = []) {
  const items = [...seed];
  let nextId = items.length + 1;
  return {
    async findAll() {
      return items;
    },
    async findById(id) {
      return items.find((c) => c.id_concierto === Number(id)) ?? null;
    },
    async create(data) {
      const concierto = { id_concierto: nextId++, ...data };
      items.push(concierto);
      return concierto;
    },
    async update(id, data) {
      const concierto = items.find((c) => c.id_concierto === Number(id));
      if (!concierto) return null;
      Object.assign(concierto, data);
      return concierto;
    },
    async remove(id) {
      const index = items.findIndex((c) => c.id_concierto === Number(id));
      if (index === -1) return false;
      items.splice(index, 1);
      return true;
    },
  };
}

function buildApp(seed = []) {
  return createApp({
    conciertoRepository: fakeConciertoRepository(seed),
    artistaRepository: fakeArtistaRepository(),
    jwtSecret,
  });
}

const conciertoValido = {
  id_artista: 1,
  titulo_evento: 'Gira Aniversario',
  fecha_concierto: '2026-11-15T20:00:00Z',
  recinto: 'Teatro Nacional',
};

test('GET /conciertos exige autenticación', async () => {
  const response = await request(buildApp()).get('/conciertos');
  assert.equal(response.status, 401);
});

test('POST /conciertos rechaza a un vendedor', async () => {
  const response = await request(buildApp())
    .post('/conciertos')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send(conciertoValido);

  assert.equal(response.status, 403);
});

test('POST /conciertos crea un concierto válido como administrador', async () => {
  const response = await request(buildApp())
    .post('/conciertos')
    .set('Authorization', `Bearer ${adminToken}`)
    .send(conciertoValido);

  assert.equal(response.status, 201);
  assert.equal(response.body.titulo_evento, 'Gira Aniversario');
});

test('POST /conciertos rechaza un id_artista inexistente', async () => {
  const response = await request(buildApp())
    .post('/conciertos')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ ...conciertoValido, id_artista: 999 });

  assert.equal(response.status, 400);
  assert.equal(response.body.error, 'El artista indicado no existe.');
});

test('POST /conciertos valida campos obligatorios', async () => {
  const response = await request(buildApp())
    .post('/conciertos')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ id_artista: 1 });

  assert.equal(response.status, 400);
});

test('POST /conciertos rechaza una fecha inválida', async () => {
  const response = await request(buildApp())
    .post('/conciertos')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ ...conciertoValido, fecha_concierto: 'no-es-una-fecha' });

  assert.equal(response.status, 400);
});

test('POST /conciertos rechaza un estado fuera del catálogo', async () => {
  const response = await request(buildApp())
    .post('/conciertos')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ ...conciertoValido, estado: 'inventado' });

  assert.equal(response.status, 400);
});

test('PUT /conciertos/:id exige estado explícito y actualiza', async () => {
  const app = buildApp([{ id_concierto: 1, ...conciertoValido, estado: 'programado' }]);

  const sinEstado = await request(app)
    .put('/conciertos/1')
    .set('Authorization', `Bearer ${adminToken}`)
    .send(conciertoValido);
  assert.equal(sinEstado.status, 400);

  const actualizado = await request(app)
    .put('/conciertos/1')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ ...conciertoValido, estado: 'activo' });
  assert.equal(actualizado.status, 200);
  assert.equal(actualizado.body.estado, 'activo');
});

test('DELETE /conciertos/:id responde 409 si tiene inventario o ventas asociadas', async () => {
  const repo = fakeConciertoRepository([{ id_concierto: 1, ...conciertoValido, estado: 'programado' }]);
  repo.remove = async () => {
    const error = new Error('foreign key violation');
    error.code = '23503';
    throw error;
  };
  const app = createApp({
    conciertoRepository: repo,
    artistaRepository: fakeArtistaRepository(),
    jwtSecret,
  });

  const response = await request(app)
    .delete('/conciertos/1')
    .set('Authorization', `Bearer ${adminToken}`);

  assert.equal(response.status, 409);
});
