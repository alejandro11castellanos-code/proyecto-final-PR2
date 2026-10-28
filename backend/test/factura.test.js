import test from 'node:test';
import assert from 'node:assert/strict';
import { armarFactura, generarFacturaPdf, createFacturaProvider, normalizarNit } from '../src/services/factura.js';

const venta = {
  id_venta: 42,
  id_vendedor: 2,
  nombre_vendedor: 'Ana López',
  fecha_venta: '2026-10-24T10:05:00',
  total_venta: '1120.00',
  metodo_pago: 'efectivo',
  items: [
    {
      titulo_evento: 'RHCP', nombre_artistico: 'Red Hot', nombre_localidad: 'General Norte',
      cantidad: 2, precio_unitario: '10.00', subtotal: '20.00',
    },
    {
      titulo_evento: 'Concierto de prueba', nombre_artistico: 'Banda', nombre_localidad: 'VIP',
      cantidad: 1, precio_unitario: '1100.00', subtotal: '1100.00',
    },
  ],
};

test('armarFactura numera la factura con el id de la venta', () => {
  assert.equal(armarFactura(venta, { email: 'a@b.co' }).numero, 'A-000042');
});

test('armarFactura formatea la fecha local, el método de pago y quién atendió', () => {
  const f = armarFactura(venta, { email: 'a@b.co' });
  assert.equal(f.fecha, '24/10/2026 10:05');
  assert.equal(f.metodoPago, 'Efectivo');
  assert.equal(f.vendedor, 'Ana López');
  assert.equal(armarFactura({ ...venta, metodo_pago: 'tarjeta' }, {}).metodoPago, 'Tarjeta');
});

test('armarFactura describe cada renglón con concierto, localidad, cantidad y montos', () => {
  const [primero, segundo] = armarFactura(venta, {}).lineas;
  assert.equal(primero.descripcion, 'RHCP (Red Hot) — General Norte');
  assert.equal(primero.cantidad, 2);
  assert.equal(primero.precioUnitario, 'Q 10.00');
  assert.equal(primero.subtotal, 'Q 20.00');
  assert.equal(segundo.precioUnitario, 'Q 1,100.00');
});

test('armarFactura separa el IVA incluido (12 %) del total', () => {
  const f = armarFactura(venta, {});
  assert.equal(f.total, 'Q 1,120.00');
  assert.equal(f.base, 'Q 1,000.00');
  assert.equal(f.iva, 'Q 120.00');
});

test('armarFactura: base + IVA siempre suman el total, también cuando hay redondeo', () => {
  // 450.00 / 1.12 = 401.7857... -> la base se redondea y el IVA es la diferencia exacta.
  const f = armarFactura({ ...venta, total_venta: '450.00' }, {});
  assert.equal(f.base, 'Q 401.79');
  assert.equal(f.iva, 'Q 48.21');
  assert.equal(f.total, 'Q 450.00');
});

test('armarFactura usa "Consumidor final / CF" y el correo como datos del cliente', () => {
  const f = armarFactura(venta, { email: 'cliente@correo.com' });
  assert.deepEqual(f.cliente, { nombre: 'Consumidor final', nit: 'CF', email: 'cliente@correo.com' });
});

test('armarFactura tolera una venta sin vendedor ni método de pago (ventas antiguas)', () => {
  const { nombre_vendedor, metodo_pago, ...antigua } = venta;
  const f = armarFactura(antigua, {});
  assert.equal(f.metodoPago, 'Tarjeta');
  assert.equal(f.vendedor, null);
});

test('armarFactura toma el emisor de la configuración, con SVB-GUA por defecto', () => {
  assert.equal(armarFactura(venta, {}).emisor.nombre, 'SVB-GUA');
  const f = armarFactura(venta, { emisor: { nombre: 'Boletos GT', nit: '1234567-8', direccion: 'Zona 1' } });
  assert.deepEqual(f.emisor, { nombre: 'Boletos GT', nit: '1234567-8', direccion: 'Zona 1' });
});

test('generarFacturaPdf devuelve un PDF válido', async () => {
  const pdf = await generarFacturaPdf(venta, { email: 'a@b.co' });
  assert.ok(Buffer.isBuffer(pdf));
  assert.equal(pdf.subarray(0, 5).toString(), '%PDF-');
  assert.match(pdf.subarray(-16).toString(), /%%EOF/);
  assert.ok(pdf.length > 2000, 'un PDF con contenido pesa más de 2 KB');
});

test('generarFacturaPdf con muchos renglones pagina sin fallar', async () => {
  const muchos = {
    ...venta,
    items: Array.from({ length: 60 }, (_, i) => ({ ...venta.items[0], titulo_evento: `Concierto ${i}` })),
  };
  const pdf = await generarFacturaPdf(muchos, {});
  assert.equal(pdf.subarray(0, 5).toString(), '%PDF-');
  assert.ok(pdf.length > (await generarFacturaPdf(venta, {})).length, 'más renglones, más páginas, más bytes');
});

test('createFacturaProvider entrega nombre de archivo y contenido listos para adjuntar', async () => {
  const { nombre, contenido } = await createFacturaProvider().generar({ venta, email: 'a@b.co' });
  assert.equal(nombre, 'factura-A-000042.pdf');
  assert.equal(contenido.subarray(0, 5).toString(), '%PDF-');
});

// --- NIT y nombre del cliente (opcionales) ---

test('armarFactura usa el nombre y el NIT del cliente cuando se dan', () => {
  const f = armarFactura(venta, { email: 'a@b.co', cliente: { nombre: '  Juan Pérez  ', nit: '12345678' } });
  assert.deepEqual(f.cliente, { nombre: 'Juan Pérez', nit: '1234567-8', email: 'a@b.co' });
});

test('armarFactura: sin datos del cliente sigue siendo Consumidor final / CF', () => {
  for (const cliente of [undefined, {}, { nombre: '', nit: '' }, { nombre: null, nit: null }]) {
    const f = armarFactura(venta, { cliente });
    assert.equal(f.cliente.nombre, 'Consumidor final');
    assert.equal(f.cliente.nit, 'CF');
  }
});

test('normalizarNit acepta guion, espacios, minúsculas, dígito verificador K y CF', () => {
  assert.equal(normalizarNit('1234567-8'), '12345678');
  assert.equal(normalizarNit(' 1234 567 - 8 '), '12345678');
  assert.equal(normalizarNit('123456-k'), '123456K');
  assert.equal(normalizarNit('cf'), 'CF');
  assert.equal(normalizarNit('C/F'), 'CF');
});

test('normalizarNit rechaza lo que no es un NIT', () => {
  for (const malo of ['abc', '12-34-56x', '1234567890123456', '-', '1', 'NIT123']) {
    assert.equal(normalizarNit(malo), null, malo);
  }
});

test('la factura muestra el NIT con su guion y la generación del PDF acepta al cliente', async () => {
  const pdf = await generarFacturaPdf(venta, { cliente: { nombre: 'María del Carmen López', nit: '9876543-2' } });
  assert.equal(pdf.subarray(0, 5).toString(), '%PDF-');
});
