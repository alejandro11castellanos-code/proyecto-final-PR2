import test from 'node:test';
import assert from 'node:assert/strict';
import { aFechaLocalIso } from '../src/db/timestamps.js';

test('aFechaLocalIso convierte el formato de Postgres a ISO sin zona', () => {
  assert.equal(aFechaLocalIso('2026-11-15 20:00:00'), '2026-11-15T20:00:00');
});

test('aFechaLocalIso descarta la fracción de segundo', () => {
  assert.equal(aFechaLocalIso('2026-09-30 04:36:39.944294'), '2026-09-30T04:36:39');
});

test('aFechaLocalIso no depende de la zona horaria del proceso', () => {
  const original = process.env.TZ;
  try {
    for (const zona of ['UTC', 'America/Guatemala', 'Asia/Tokyo']) {
      process.env.TZ = zona;
      assert.equal(aFechaLocalIso('2026-12-12 00:00:00'), '2026-12-12T00:00:00');
    }
  } finally {
    if (original === undefined) delete process.env.TZ;
    else process.env.TZ = original;
  }
});
