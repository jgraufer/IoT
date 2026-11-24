import axios from 'axios';

const GENERIC = 'Ha ocurrido un error inesperado. Inténtalo de nuevo.';
const OFFLINE = 'No se pudo conectar con el servidor. Revisa tu conexión.';
const SERVER = 'Error del servidor. Inténtalo de nuevo más tarde.';

/**
 * Traducción de errores del backend por método + endpoint + código HTTP (el backend
 * no envía código de error en estos flujos y su texto está en inglés). Los 409 de
 * registro e invitación agrupan dos causas que el backend no distingue sin texto.
 */
const RULES: Array<[method: string, path: RegExp, status: number, message: string]> = [
  ['post', /^\/auth\/login$/, 400, 'Introduce tu usuario o email y la contraseña.'],
  ['post', /^\/auth\/login$/, 401, 'Usuario o contraseña incorrectos.'],

  ['post', /^\/auth\/register$/, 400, 'Revisa los datos del formulario.'],
  ['post', /^\/auth\/register$/, 409, 'Ese email o nombre de usuario ya está registrado.'],

  ['post', /^\/edges\/[^/]+\/invitations$/, 400, 'Introduce un email válido.'],
  ['post', /^\/edges\/[^/]+\/invitations$/, 403, 'Solo el propietario de la casa puede enviar invitaciones.'],
  ['post', /^\/edges\/[^/]+\/invitations$/, 404, 'No hay ningún usuario registrado con ese email.'],
  ['post', /^\/edges\/[^/]+\/invitations$/, 409, 'Ese usuario ya es miembro de la casa o tiene una invitación pendiente.'],

  ['delete', /^\/edges\/[^/]+\/invitations\/[^/]+$/, 403, 'Solo el propietario de la casa puede cancelar invitaciones.'],
  ['delete', /^\/edges\/[^/]+\/invitations\/[^/]+$/, 404, 'La invitación ya no existe.'],
  ['delete', /^\/edges\/[^/]+\/invitations\/[^/]+$/, 409, 'La invitación ya no está pendiente o ha caducado.'],

  ['post', /^\/users\/me\/invitations\/[^/]+\/accept$/, 403, 'Esta invitación no es para ti.'],
  ['post', /^\/users\/me\/invitations\/[^/]+\/accept$/, 404, 'La invitación ya no existe.'],
  ['post', /^\/users\/me\/invitations\/[^/]+\/accept$/, 409, 'La invitación ya no está pendiente, ha caducado o ya eres miembro de la casa.'],

  ['post', /^\/users\/me\/invitations\/[^/]+\/reject$/, 403, 'Esta invitación no es para ti.'],
  ['post', /^\/users\/me\/invitations\/[^/]+\/reject$/, 404, 'La invitación ya no existe.'],
  ['post', /^\/users\/me\/invitations\/[^/]+\/reject$/, 409, 'La invitación ya no está pendiente o ha caducado.'],
];

/** Mensaje en español para un error de API; nunca devuelve el texto del backend. */
export function apiErrorMessage(err: unknown, fallback = GENERIC): string {
  if (!axios.isAxiosError(err)) return fallback;
  const res = err.response;
  if (!res) return OFFLINE;
  const method = (err.config?.method ?? '').toLowerCase();
  const path = (err.config?.url ?? '').split('?')[0];
  const rule = RULES.find(([m, p, s]) => m === method && s === res.status && p.test(path));
  if (rule) return rule[3];
  if (res.status >= 500) return SERVER;
  return fallback;
}
