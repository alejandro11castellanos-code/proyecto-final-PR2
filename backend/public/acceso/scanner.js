import {
  crearAntirrebote, describirResultado, normalizarCodigo, pareceBoleto, porcentaje, puedeEscanear,
} from './nucleo.js';

const $ = (id) => document.getElementById(id);
const CLAVE_SESION = 'svb_acceso';
const REFRESCO_MS = 5000;
const CICLO_MS = 120;

const antirrebote = crearAntirrebote(5000);
const lienzo = document.createElement('canvas');
const contexto = lienzo.getContext('2d', { willReadFrequently: true });

let sesion = leerSesion();
let flujo = null;
let temporizadorCiclo = null;
let ocupado = false;
let audio = null;

// ---------------------------------------------------------------- sesión y red

function leerSesion() {
  try {
    return JSON.parse(sessionStorage.getItem(CLAVE_SESION));
  } catch {
    return null;
  }
}

function guardarSesion(valor) {
  try {
    if (valor) sessionStorage.setItem(CLAVE_SESION, JSON.stringify(valor));
    else sessionStorage.removeItem(CLAVE_SESION);
  } catch {
    // Sin almacenamiento (modo privado): la sesión dura mientras no se recargue la página.
  }
}

async function api(ruta, { metodo = 'GET', cuerpo, token } = {}) {
  const controlador = new AbortController();
  // El servidor gratuito puede tardar hasta un minuto en despertar.
  const limite = setTimeout(() => controlador.abort(), 75000);
  try {
    const respuesta = await fetch(ruta, {
      method: metodo,
      headers: {
        ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: cuerpo ? JSON.stringify(cuerpo) : undefined,
      signal: controlador.signal,
    });
    let datos = null;
    try {
      datos = await respuesta.json();
    } catch {
      // Respuesta sin JSON: se informa solo el código HTTP.
    }
    return { estado: respuesta.status, cuerpo: datos };
  } catch {
    return { estado: 0, cuerpo: null };
  } finally {
    clearTimeout(limite);
  }
}

function setEstado(id, texto, esError = false) {
  const elemento = $(id);
  elemento.textContent = texto;
  elemento.classList.toggle('error', esError);
}

// ---------------------------------------------------------------- vistas

function mostrarLogin(mensaje) {
  detenerCamara();
  $('vista-escaner').hidden = true;
  $('vista-login').hidden = false;
  $('sesion').hidden = true;
  setEstado('login-estado', mensaje ?? '', Boolean(mensaje));
}

function mostrarEscaner() {
  $('vista-login').hidden = true;
  $('vista-escaner').hidden = false;
  $('sesion').hidden = false;
  $('usuario').textContent = sesion.nombre;
  cargarDatos();
}

function cerrarSesion(mensaje) {
  sesion = null;
  guardarSesion(null);
  mostrarLogin(mensaje);
}

$('form-login').addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const boton = $('login-boton');
  boton.disabled = true;
  setEstado('login-estado', 'Conectando... el servidor gratuito puede tardar hasta un minuto en despertar.');

  const { estado, cuerpo } = await api('/auth/login', {
    metodo: 'POST',
    cuerpo: { nombre_usuario: $('login-usuario').value.trim(), contrasena: $('login-clave').value },
  });
  boton.disabled = false;

  if (estado === 200) {
    if (!puedeEscanear(cuerpo.usuario.rol)) {
      setEstado('login-estado', 'Este usuario no puede registrar ingresos. Use uno de control de acceso.', true);
      return;
    }
    sesion = { token: cuerpo.token, nombre: cuerpo.usuario.nombre_completo };
    guardarSesion(sesion);
    $('login-clave').value = '';
    mostrarEscaner();
    return;
  }
  const mensajes = {
    400: 'Escriba usuario y contraseña.',
    401: 'Usuario o contraseña incorrectos.',
    429: 'Demasiados intentos fallidos. Espere unos minutos.',
    0: 'No se pudo conectar con el servidor. Reintente.',
  };
  setEstado('login-estado', mensajes[estado] ?? 'Error del servidor. Intente de nuevo.', true);
});

$('salir').addEventListener('click', () => cerrarSesion());

// ---------------------------------------------------------------- señales (sonido y vibración)

function prepararAudio() {
  // Debe crearse dentro de un gesto del usuario (el clic en "Activar cámara").
  if (!audio && window.AudioContext) audio = new window.AudioContext();
}

function pitido(tipo) {
  if (!audio) return;
  const notas = tipo === 'ok' ? [[880, 0, 0.12]] : [[220, 0, 0.16], [220, 0.22, 0.16]];
  for (const [frecuencia, inicio, duracion] of notas) {
    const oscilador = audio.createOscillator();
    const volumen = audio.createGain();
    oscilador.frequency.value = frecuencia;
    volumen.gain.value = 0.15;
    oscilador.connect(volumen);
    volumen.connect(audio.destination);
    oscilador.start(audio.currentTime + inicio);
    oscilador.stop(audio.currentTime + inicio + duracion);
  }
}

function senal(tipo) {
  pitido(tipo);
  if (navigator.vibrate) navigator.vibrate(tipo === 'ok' ? [90] : [70, 60, 70]);
}

// ---------------------------------------------------------------- resultado

function mostrarResultado(resultado) {
  const caja = $('resultado');
  caja.hidden = false;
  caja.className = 'resultado';
  void caja.offsetWidth; // reinicia la animación aunque el resultado anterior fuera del mismo tipo
  caja.classList.add(resultado.tipo);
  $('resultado-titulo').textContent = resultado.titulo;
  $('resultado-detalle').textContent = resultado.detalle;
}

async function registrar(codigo) {
  ocupado = true;
  const { estado, cuerpo } = await api('/accesos', { metodo: 'POST', cuerpo: { codigo }, token: sesion?.token });
  ocupado = false;
  if (estado === 401) {
    cerrarSesion('La sesión venció. Ingrese de nuevo.');
    return;
  }
  const resultado = describirResultado(estado, cuerpo);
  mostrarResultado(resultado);
  senal(resultado.tipo);
  cargarDatos();
}

// ---------------------------------------------------------------- cámara

function detectado(texto) {
  const codigo = normalizarCodigo(texto);
  if (!codigo || ocupado || !antirrebote.permitir(codigo, Date.now())) return;
  if (!pareceBoleto(codigo)) {
    const resultado = { tipo: 'error', titulo: 'No es un boleto', detalle: 'Ese QR no es de SVB-GUA.' };
    mostrarResultado(resultado);
    senal('error');
    return;
  }
  registrar(codigo);
}

function cicloEscaneo() {
  if (!flujo) return;
  const video = $('video');
  if (video.readyState >= video.HAVE_CURRENT_DATA && video.videoWidth) {
    const escala = Math.min(1, 640 / video.videoWidth);
    lienzo.width = Math.round(video.videoWidth * escala);
    lienzo.height = Math.round(video.videoHeight * escala);
    contexto.drawImage(video, 0, 0, lienzo.width, lienzo.height);
    const imagen = contexto.getImageData(0, 0, lienzo.width, lienzo.height);
    const qr = window.jsQR(imagen.data, imagen.width, imagen.height, { inversionAttempts: 'dontInvert' });
    if (qr?.data) detectado(qr.data);
  }
  temporizadorCiclo = setTimeout(cicloEscaneo, CICLO_MS);
}

function detenerCamara() {
  clearTimeout(temporizadorCiclo);
  if (flujo) flujo.getTracks().forEach((pista) => pista.stop());
  flujo = null;
  $('video').srcObject = null;
  $('visor').classList.remove('activo');
  $('btn-camara').textContent = 'Activar cámara';
}

async function activarCamara() {
  if (flujo) {
    detenerCamara();
    setEstado('camara-estado', '');
    return;
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    setEstado('camara-estado', 'Este navegador no permite usar la cámara aquí (hace falta HTTPS). Escriba el código a mano.', true);
    return;
  }
  prepararAudio();
  try {
    flujo = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    });
  } catch (error) {
    const mensajes = {
      NotAllowedError: 'Permiso de cámara denegado. Permítalo en el navegador y reintente.',
      NotFoundError: 'No se encontró una cámara en este dispositivo.',
      NotReadableError: 'La cámara está en uso por otra aplicación.',
    };
    setEstado('camara-estado', mensajes[error.name] ?? 'No se pudo abrir la cámara.', true);
    return;
  }
  const video = $('video');
  video.srcObject = flujo;
  await video.play();
  $('visor').classList.add('activo');
  $('btn-camara').textContent = 'Detener cámara';
  setEstado('camara-estado', 'Apunte la cámara al código QR del boleto.');
  cicloEscaneo();
}

$('btn-camara').addEventListener('click', activarCamara);

$('form-manual').addEventListener('submit', (evento) => {
  evento.preventDefault();
  const codigo = normalizarCodigo($('codigo').value);
  if (!codigo || ocupado) return;
  $('codigo').value = '';
  registrar(codigo);
});

// ---------------------------------------------------------------- asistencia y recientes

function crear(etiqueta, texto, clase) {
  const elemento = document.createElement(etiqueta);
  if (texto !== undefined) elemento.textContent = texto;
  if (clase) elemento.className = clase;
  return elemento;
}

function pintarAsistencia(conciertos) {
  const contenedor = $('asistencia');
  contenedor.replaceChildren();
  if (conciertos.length === 0) {
    contenedor.append(crear('p', 'Aún no hay conciertos.', 'vacio'));
    return;
  }
  for (const concierto of conciertos) {
    const avance = porcentaje(concierto.ingresados, concierto.vendidos);
    const barra = crear('div', undefined, 'barra-progreso');
    const relleno = document.createElement('i');
    relleno.style.width = `${avance}%`;
    barra.append(relleno);
    const bloque = crear('div', undefined, 'concierto');
    bloque.append(
      crear('b', concierto.titulo_evento),
      crear('span', concierto.nombre_artistico),
      barra,
      crear('span', `${concierto.ingresados} de ${concierto.vendidos} ingresaron (${avance}%)`),
    );
    contenedor.append(bloque);
  }
}

function pintarRecientes(ingresos) {
  const lista = $('recientes');
  lista.replaceChildren();
  if (ingresos.length === 0) {
    lista.append(crear('li', 'Aún no ha ingresado nadie.', 'vacio'));
    return;
  }
  for (const ingreso of ingresos.slice(0, 8)) {
    const iso = ingreso.fecha_ingreso;
    const cuando = `${iso.slice(11, 16)} · ${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
    const detalle = crear('div');
    detalle.append(crear('div', `${ingreso.titulo_evento} · ${ingreso.nombre_localidad}`), crear('small', ingreso.codigo));
    const fila = crear('li');
    fila.append(detalle, crear('small', cuando));
    lista.append(fila);
  }
}

async function cargarDatos() {
  if (!sesion) return;
  const [resumen, recientes] = await Promise.all([
    api('/accesos/resumen', { token: sesion.token }),
    api('/accesos', { token: sesion.token }),
  ]);
  if (resumen.estado === 401 || recientes.estado === 401) {
    cerrarSesion('La sesión venció. Ingrese de nuevo.');
    return;
  }
  if (resumen.estado === 200) pintarAsistencia(resumen.cuerpo);
  if (recientes.estado === 200) pintarRecientes(recientes.cuerpo);
}

// ---------------------------------------------------------------- arranque

setInterval(() => {
  if (sesion && document.visibilityState === 'visible') cargarDatos();
}, REFRESCO_MS);

if (sesion) mostrarEscaner();
else mostrarLogin();
