import test from 'node:test';
import assert from 'node:assert/strict';
import { authorizeRoles } from '../src/middleware/auth.js';

function runMiddleware(middleware, user) {
  const req = { user };
  const response = {
    statusCode: 200,
    body: null,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(body) {
      this.body = body;
      return this;
    },
  };
  let nextCalled = false;
  middleware(req, response, () => {
    nextCalled = true;
  });
  return { response, nextCalled };
}

test('authorizeRoles permite un rol autorizado', () => {
  const result = runMiddleware(authorizeRoles('administrador'), { rol: 'administrador' });

  assert.equal(result.nextCalled, true);
  assert.equal(result.response.statusCode, 200);
});

test('authorizeRoles responde 403 para un rol no autorizado', () => {
  const result = runMiddleware(authorizeRoles('administrador'), { rol: 'vendedor' });

  assert.equal(result.nextCalled, false);
  assert.equal(result.response.statusCode, 403);
  assert.deepEqual(result.response.body, { error: 'No tiene permisos para realizar esta acción.' });
});
