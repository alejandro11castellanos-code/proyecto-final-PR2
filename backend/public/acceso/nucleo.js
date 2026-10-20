// Lógica del escáner sin nada del navegador, para poder probarla en Node.

export function normalizarCodigo(texto) {
  return typeof texto === 'string' ? texto.trim().toUpperCase() : '';
}

// Forma de un código de boleto. Un QR cualquiera (un enlace, otra entrada) se descarta sin molestar al servidor.
export function pareceBoleto(codigo) {
  return /^SVBGUA-V\d+-D\d+-B\d+-[A-Z2-9]{8}$/.test(codigo);
}

export function puedeEscanear(rol) {
  return rol === 'administrador' || rol === 'acceso';
}

// La cámara ve el mismo QR muchas veces por segundo: cada código se ignora durante `ventanaMs`
// después de aceptarlo, para registrarlo una sola vez por pasada.
export function crearAntirrebote(ventanaMs) {
  const aceptados = new Map();
  return {
    permitir(codigo, ahoraMs) {
      for (const [otro, desde] of aceptados) {
        if (ahoraMs - desde >= ventanaMs) aceptados.delete(otro);
      }
      if (aceptados.has(codigo)) return false;
      aceptados.set(codigo, ahoraMs);
      return true;
    },
  };
}

export function porcentaje(ingresados, vendidos) {
  return vendidos > 0 ? Math.round((100 * ingresados) / vendidos) : 0;
}

// Traduce la respuesta de POST /accesos a lo que se muestra en pantalla.
export function describirResultado(estado, cuerpo) {
  const motivo = cuerpo?.error;
  if (estado === 200) {
    const boleto = cuerpo.cantidad > 1 ? `  ·  boleto ${cuerpo.numero} de ${cuerpo.cantidad}` : '';
    return {
      tipo: 'ok',
      titulo: 'Ingreso registrado',
      detalle: `${cuerpo.titulo_evento}  ·  ${cuerpo.nombre_localidad}${boleto}`,
    };
  }
  const error = (titulo, detalle) => ({ tipo: 'error', titulo, detalle });
  switch (estado) {
    case 409: return error('Acceso denegado', motivo ?? 'El boleto no se puede usar.');
    case 404: return error('Código no válido', motivo ?? 'El código no existe.');
    case 400: return error('Código no válido', motivo ?? 'Revise el código.');
    case 401: return error('Sesión vencida', 'Vuelva a iniciar sesión.');
    case 403: return error('Sin permiso', 'Este usuario no puede registrar ingresos.');
    case 429: return error('Demasiados intentos', 'Espere unos minutos.');
    case 0: return error('Sin conexión', 'No se pudo conectar con el servidor. Reintente.');
    default: return error('Error del servidor', 'Intente de nuevo en un momento.');
  }
}
