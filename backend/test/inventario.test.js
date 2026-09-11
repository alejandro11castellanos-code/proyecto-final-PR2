import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';

const jwtSecret = 'secreto-de-prueba';
const adminToken = jwt.sign({ id_usuario: 1, rol: 'administrador' }, jwtSecret);
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);

const conciertoExistente = { id_concierto: 1, titulo_evento: 'Gira' };
const localidadExistente = { id_localidad: 1, nombre: 'VIP' };

function fakeConciertoRepository() {
  return {
    async findById(id) {
      return Number(id) === conciertoExistente.id_concierto ? conciertoExistente : null;
    },
  };
}

function fakeLocalidadRepository() {
  return {
    async findById(id) {
      return Number(id) === localidadExistente.id_localidad ? localidadExistente : null;
    },
  };
}

function fakeInventarioRepository(seed = []) {
  const items = seed.map((item) => ({ ...item }));
  let nextId = items.length + 1;
  return {
    async findAllByConcierto(idConcierto) {
      return items.filter((i) => i.id_concierto === Number(idConcierto));
    },
    async findById(id) {
      return items.find((i) => i.id_inventario === Number(id)) ?? null;
    },
    async create(data) {
      if (items.some((i) => i.id_concierto === data.id_concierto && i.id_localidad === data.id_localidad)) {
        const error = new Error('unique violation');
        error.code = '23505';
        throw error;
      }
      const registro = {
        id_inventario: nextId++,
        ...data,
        cantidad_disponible: data.cantidad_total,
        // La API real obtiene este campo de un JOIN con localidades al
        // releer el registro; la réplica en memoria lo resuelve así.
        nombre_localidad: data.id_localidad === localidadExistente.id_localidad
          ? localidadExistente.nombre
          : undefined,
      };
      items.push(registro);
      return registro;
    },
    async updatePrecioYAforo(id, { precio, cantidad_total }) {
      const registro = items.find((i) => i.id_inventario === Number(id));
      if (!registro) return null;
      const nuevaDisponible = registro.cantidad_disponible + (cantidad_total - registro.cantidad_total);
      if (nuevaDisponible < 0 || nuevaDisponible > cantidad_total) {
        const error = new Error('check violation');
        error.code = '23514';
        throw error;
      }
      registro.precio = precio;
      registro.cantidad_total = cantidad_total;
      registro.cantidad_disponible = nuevaDisponible;
      return registro;
    },
    async remove(id) {
      const index = items.findIndex((i) => i.id_inventario === Number(id));
      if (index === -1) return false;
      items.splice(index, 1);
      return true;
    },
  };
}

function buildApp(seed = []) {
  return createApp({
    inventarioRepository: fakeInventarioRepository(seed),
    conciertoRepository: fakeConciertoRepository(),
    localidadRepository: fakeLocalidadRepository(),
    jwtSecret,
  });
}

test('GET /conciertos/:id/inventario exige autenticación', async () => {
  const response = await request(buildApp()).get('/conciertos/1/inventario');
  assert.equal(response.status, 401);
});

test('GET /conciertos/:id/inventario responde 404 si el concierto no existe', async () => {
  const response = await request(buildApp())
    .get('/conciertos/999/inventario')
    .set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 404);
});

test('GET /conciertos/:id/inventario lista el aforo para cualquier rol autenticado', async () => {
  const app = buildApp([
    {
      id_inventario: 1,
      id_concierto: 1,
      id_localidad: 1,
      precio: '450.00',
      cantidad_total: 300,
      cantidad_disponible: 300,
    },
  ]);
  const response = await request(app).get('/conciertos/1/inventario').set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 200);
  assert.equal(response.body.length, 1);
});

test('POST /conciertos/:id/inventario rechaza a un vendedor', async () => {
  const response = await request(buildApp())
    .post('/conciertos/1/inventario')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ id_localidad: 1, precio: 450, cantidad_total: 300 });
  assert.equal(response.status, 403);
});

test('POST /conciertos/:id/inventario valida campos obligatorios', async () => {
  const response = await request(buildApp())
    .post('/conciertos/1/inventario')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ id_localidad: 1 });
  assert.equal(response.status, 400);
});

test('POST /conciertos/:id/inventario rechaza una localidad inexistente', async () => {
  const response = await request(buildApp())
    .post('/conciertos/1/inventario')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ id_localidad: 999, precio: 450, cantidad_total: 300 });
  assert.equal(response.status, 400);
  assert.equal(response.body.error, 'La localidad indicada no existe.');
});

test('POST /conciertos/:id/inventario asigna aforo correctamente', async () => {
  const response = await request(buildApp())
    .post('/conciertos/1/inventario')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ id_localidad: 1, precio: 450, cantidad_total: 300 });
  assert.equal(response.status, 201);
  assert.equal(response.body.cantidad_disponible, 300);
  assert.equal(response.body.nombre_localidad, 'VIP');
});

test('POST /conciertos/:id/inventario responde 409 si la localidad ya tiene aforo asignado', async () => {
  const app = buildApp([
    { id_inventario: 1, id_concierto: 1, id_localidad: 1, precio: 450, cantidad_total: 300, cantidad_disponible: 300 },
  ]);
  const response = await request(app)
    .post('/conciertos/1/inventario')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ id_localidad: 1, precio: 500, cantidad_total: 100 });
  assert.equal(response.status, 409);
});

test('PUT actualiza precio y aforo preservando lo ya vendido', async () => {
  const app = buildApp([
    // Se vendieron 50 de 300 (disponible = 250).
    { id_inventario: 1, id_concierto: 1, id_localidad: 1, precio: 450, cantidad_total: 300, cantidad_disponible: 250 },
  ]);

  const response = await request(app)
    .put('/conciertos/1/inventario/1')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ precio: 500, cantidad_total: 400 });

  assert.equal(response.status, 200);
  assert.equal(response.body.precio, 500);
  assert.equal(response.body.cantidad_total, 400);
  assert.equal(response.body.cantidad_disponible, 350); // 250 + (400-300)
});

test('PUT responde 400 si el nuevo aforo cae por debajo de lo ya vendido', async () => {
  const app = buildApp([
    // Se vendieron 280 de 300 (disponible = 20).
    { id_inventario: 1, id_concierto: 1, id_localidad: 1, precio: 450, cantidad_total: 300, cantidad_disponible: 20 },
  ]);

  const response = await request(app)
    .put('/conciertos/1/inventario/1')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ precio: 450, cantidad_total: 100 });

  assert.equal(response.status, 400);
});

test('PUT responde 404 si el inventario no pertenece a ese concierto', async () => {
  const app = buildApp([
    { id_inventario: 1, id_concierto: 2, id_localidad: 1, precio: 450, cantidad_total: 300, cantidad_disponible: 300 },
  ]);

  const response = await request(app)
    .put('/conciertos/1/inventario/1')
    .set('Authorization', `Bearer ${adminToken}`)
    .send({ precio: 450, cantidad_total: 300 });

  assert.equal(response.status, 404);
});

test('DELETE elimina el registro y responde 409 si tiene ventas asociadas', async () => {
  const app = buildApp([
    { id_inventario: 1, id_concierto: 1, id_localidad: 1, precio: 450, cantidad_total: 300, cantidad_disponible: 300 },
  ]);

  const notFound = await request(app)
    .delete('/conciertos/1/inventario/99')
    .set('Authorization', `Bearer ${adminToken}`);
  assert.equal(notFound.status, 404);

  const deleted = await request(app)
    .delete('/conciertos/1/inventario/1')
    .set('Authorization', `Bearer ${adminToken}`);
  assert.equal(deleted.status, 204);
});

test('DELETE responde 409 si el inventario tiene boletos vendidos', async () => {
  const repo = fakeInventarioRepository([
    { id_inventario: 1, id_concierto: 1, id_localidad: 1, precio: 450, cantidad_total: 300, cantidad_disponible: 300 },
  ]);
  repo.remove = async () => {
    const error = new Error('foreign key violation');
    error.code = '23503';
    throw error;
  };
  const app = createApp({
    inventarioRepository: repo,
    conciertoRepository: fakeConciertoRepository(),
    localidadRepository: fakeLocalidadRepository(),
    jwtSecret,
  });

  const response = await request(app)
    .delete('/conciertos/1/inventario/1')
    .set('Authorization', `Bearer ${adminToken}`);
  assert.equal(response.status, 409);
});
