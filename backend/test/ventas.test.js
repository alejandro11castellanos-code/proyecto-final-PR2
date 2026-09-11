import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { InventarioNoEncontradoError, DisponibilidadInsuficienteError } from '../src/repositories/ventas.js';

const jwtSecret = 'secreto-de-prueba';
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);

const ventaCreada = {
  id_venta: 1,
  id_vendedor: 2,
  fecha_venta: '2026-09-11T00:00:00.000Z',
  total_venta: '900.00',
  items: [{ id_inventario: 1, cantidad: 2, precio_unitario: 450, subtotal: 900 }],
};

function fakeVentaRepository({ crear, findById } = {}) {
  return {
    crear: crear ?? (async () => ventaCreada),
    findById: findById ?? (async () => null),
  };
}

function buildApp(overrides) {
  return createApp({ ventaRepository: fakeVentaRepository(overrides), jwtSecret });
}

test('POST /ventas exige autenticación', async () => {
  const response = await request(buildApp()).post('/ventas').send({ items: [{ id_inventario: 1, cantidad: 1 }] });
  assert.equal(response.status, 401);
});

test('POST /ventas rechaza una lista de items vacía o ausente', async () => {
  const app = buildApp();
  for (const body of [{}, { items: [] }, { items: 'no-es-lista' }]) {
    const response = await request(app).post('/ventas').set('Authorization', `Bearer ${vendedorToken}`).send(body);
    assert.equal(response.status, 400);
  }
});

test('POST /ventas rechaza items con id_inventario o cantidad inválidos', async () => {
  const app = buildApp();
  for (const items of [
    [{ id_inventario: 0, cantidad: 1 }],
    [{ id_inventario: 1, cantidad: 0 }],
    [{ id_inventario: 1, cantidad: -3 }],
    [{ cantidad: 1 }],
    [{ id_inventario: 1 }],
  ]) {
    const response = await request(app).post('/ventas').set('Authorization', `Bearer ${vendedorToken}`).send({ items });
    assert.equal(response.status, 400);
  }
});

test('POST /ventas rechaza items repetidos', async () => {
  const app = buildApp();
  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 1, cantidad: 1 }, { id_inventario: 1, cantidad: 2 }] });
  assert.equal(response.status, 400);
});

test('POST /ventas crea la venta y usa el id del token como vendedor', async () => {
  let idVendedorRecibido;
  const app = buildApp({
    crear: async ({ idVendedor }) => {
      idVendedorRecibido = idVendedor;
      return ventaCreada;
    },
  });

  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 1, cantidad: 2 }] });

  assert.equal(response.status, 201);
  assert.deepEqual(response.body, ventaCreada);
  assert.equal(idVendedorRecibido, 2);
});

test('POST /ventas responde 404 si algún inventario no existe', async () => {
  const app = buildApp({
    crear: async () => {
      throw new InventarioNoEncontradoError(99);
    },
  });

  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 99, cantidad: 1 }] });

  assert.equal(response.status, 404);
});

test('POST /ventas responde 409 si no hay disponibilidad suficiente', async () => {
  const app = buildApp({
    crear: async () => {
      throw new DisponibilidadInsuficienteError(1, 3, 10);
    },
  });

  const response = await request(app)
    .post('/ventas')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ items: [{ id_inventario: 1, cantidad: 10 }] });

  assert.equal(response.status, 409);
});

test('GET /ventas/:id responde 404 si no existe', async () => {
  const response = await request(buildApp())
    .get('/ventas/999')
    .set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 404);
});

test('GET /ventas/:id devuelve la venta con sus ítems', async () => {
  const app = buildApp({ findById: async (id) => (Number(id) === 1 ? ventaCreada : null) });

  const response = await request(app).get('/ventas/1').set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, ventaCreada);
});
