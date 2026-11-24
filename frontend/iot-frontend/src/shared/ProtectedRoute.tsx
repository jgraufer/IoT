import { Alert, Box, CircularProgress } from '@mui/material';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { edgeApi } from '@target';
import { EdgeAccessGate } from '../pairing/EdgeAccessGate';

export function ProtectedRoute() {
  const { isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) {
    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}

export function RequireSession({ backend }: { backend: 'devices' | 'cloud' }) {
  const { hasDeviceSession, hasCloud } = useAuth();

  if (backend === 'devices' && !hasDeviceSession) {
    if (edgeApi) return <EdgeAccessGate />;
    return (
      <Alert severity="info">
        No hay sesión con el Edge de esta casa: o todavía no tienes acceso (acepta la invitación
        desde tu perfil y vuelve a iniciar sesión) o el Edge no responde.
      </Alert>
    );
  }
  if (backend === 'cloud' && !hasCloud) {
    return (
      <Alert severity="warning">
        Sin conexión con el Cloud: el perfil y la gestión de casas no están disponibles. Los
        dispositivos siguen funcionando en local.
      </Alert>
    );
  }
  return <Outlet />;
}
