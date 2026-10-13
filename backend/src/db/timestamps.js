/**
 * Las columnas TIMESTAMP (sin zona) guardan hora local de Guatemala. Por defecto
 * node-pg las convierte a Date interpretándolas en la zona del proceso, lo que
 * desplaza la hora según dónde corra el servidor. Aquí se devuelven como texto
 * ISO sin zona ("2026-11-15T20:00:00"), idéntico en cualquier máquina.
 */
export const OID_TIMESTAMP = 1114;

export function aFechaLocalIso(valor) {
  return valor.replace(' ', 'T').replace(/\.\d+$/, '');
}
