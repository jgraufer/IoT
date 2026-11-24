import type { EdgeRole } from '../types/api';

export const ROLE_LABEL: Record<EdgeRole, string> = {
  OWNER: 'Propietario',
  ADMIN: 'Administrador',
  MEMBER: 'Miembro',
};

export const ROLE_COLOR: Record<EdgeRole, 'success' | 'info' | 'default'> = {
  OWNER: 'success',
  ADMIN: 'info',
  MEMBER: 'default',
};
