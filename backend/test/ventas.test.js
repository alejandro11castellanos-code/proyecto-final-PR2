import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { InventarioNoEncontradoError, DisponibilidadInsuficienteError } from '../src/repositories/ventas.js';
import { MailerError } from '../src/services/mailer.js';

const jwtSecret = 'secreto-de-prueba';
const vendedorToken = jwt.sign({ id_usuario: 2, rol: 'vendedor' }, jwtSecret);

const ventaCreada = {
  id_venta: 1,
  id_vendedor: 2,
  fecha_venta: '2026-09-11T00:00:00.000Z',
  total_venta: '900.00',
  items: [{ id_inventario: 1, cantidad: 2, precio_unitario: 450, subtotal: 900 }],
};

// Forma real de findById: con los joins de localidad/concierto que necesita
// la generación de boletos (a diferencia de lo que devuelve "crear").
const ventaConDetalle = {
  id_venta: 1,
  id_vendedor: 2,
  fecha_venta: '2026-09-11T00:00:00.000Z',
  total_venta: '900.00',
  items: [{
    id_detalle: 10,
    id_inventario: 1,
    cantidad: 2,
    precio_unitario: '450.00',
    subtotal: '900.00',
    nombre_localidad: 'Platea',
    id_concierto: 1,
    titulo_evento: 'Gira Aniversario',
  }],
};

function fakeVentaRepository({ crear, findById } = {}) {
  return {
    crear: crear ?? (async () => ventaCreada),
    findById: findById ?? (async () => null),
  };
}

function fakeQrProvider() {
  return {
    async generar(texto) {
      return `data:image/png;base64,${Buffer.from(texto).toString('base64')}`;
    },
  };
}

function buildApp(repoOverrides, { qrProvider, mailer } = {}) {
  return createApp({
    ventaRepository: fakeVentaRepository(repoOverrides),
    qrProvider: qrProvider ?? fakeQrProvider(),
    mailer: mailer ?? { enviarBoletos: async () => {} },
    jwtSecret,
  });
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

test('GET /ventas/:id/boletos responde 404 si la venta no existe', async () => {
  const response = await request(buildApp())
    .get('/ventas/999/boletos')
    .set('Authorization', `Bearer ${vendedorToken}`);
  assert.equal(response.status, 404);
});

test('GET /ventas/:id/boletos genera un QR por cada ítem de la venta', async () => {
  const app = buildApp({ findById: async (id) => (Number(id) === 1 ? ventaConDetalle : null) });

  const response = await request(app).get('/ventas/1/boletos').set('Authorization', `Bearer ${vendedorToken}`);

  assert.equal(response.status, 200);
  assert.equal(response.body.id_venta, 1);
  assert.equal(response.body.boletos.length, 1);
  assert.equal(response.body.boletos[0].codigo, 'SVBGUA-V1-D10');
  assert.equal(response.body.boletos[0].nombre_localidad, 'Platea');
  assert.match(response.body.boletos[0].qr, /^data:image\/png;base64,/);
});

test('POST /ventas/:id/enviar valida el formato del email', async () => {
  const app = buildApp({ findById: async () => ventaConDetalle });

  const response = await request(app)
    .post('/ventas/1/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'esto-no-es-un-email' });

  assert.equal(response.status, 400);
});

test('POST /ventas/:id/enviar responde 404 si la venta no existe', async () => {
  const response = await request(buildApp())
    .post('/ventas/999/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'ana@example.com' });
  assert.equal(response.status, 404);
});

test('POST /ventas/:id/enviar arma los boletos y se los pasa al mailer', async () => {
  let recibido;
  const mailer = {
    async enviarBoletos(args) {
      recibido = args;
    },
  };
  const app = buildApp({ findById: async () => ventaConDetalle }, { mailer });

  const response = await request(app)
    .post('/ventas/1/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'ana@example.com' });

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, { enviado: true, destinatario: 'ana@example.com' });
  assert.equal(recibido.destinatario, 'ana@example.com');
  assert.equal(recibido.boletos.length, 1);
  assert.equal(recibido.boletos[0].codigo, 'SVBGUA-V1-D10');
});

test('POST /ventas/:id/enviar responde 502 si el correo no se pudo enviar', async () => {
  const mailer = {
    async enviarBoletos() {
      throw new MailerError('El servidor de correo no está configurado (SMTP_HOST).');
    },
  };
  const app = buildApp({ findById: async () => ventaConDetalle }, { mailer });

  const response = await request(app)
    .post('/ventas/1/enviar')
    .set('Authorization', `Bearer ${vendedorToken}`)
    .send({ email: 'ana@example.com' });

  assert.equal(response.status, 502);
});
