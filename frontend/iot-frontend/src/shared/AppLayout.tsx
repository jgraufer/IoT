import { AppBar, Badge, Box, Button, IconButton, Toolbar, Tooltip } from '@mui/material';
import LogoutIcon from '@mui/icons-material/Logout';
import NotificationsIcon from '@mui/icons-material/Notifications';
import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { InvitationsProvider, useInvitations } from '../invitations/InvitationsContext';
import { EdgeSelector } from './EdgeSelector';
import { Sidebar } from './Sidebar';

export function AppLayout() {
  const { logout, hasCloud } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <InvitationsProvider>
      <Box sx={{ display: 'flex', height: '100vh' }}>
        <Sidebar />
        <Box sx={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <AppBar position="static" color="default">
            <Toolbar sx={{ gap: 2 }}>
              <Box sx={{ display: 'flex', gap: 2, flexGrow: 1 }}>
                <EdgeSelector />
              </Box>
              {hasCloud && <InvitationsBell />}
              <Button color="inherit" startIcon={<LogoutIcon />} onClick={handleLogout}>
                Salir
              </Button>
            </Toolbar>
          </AppBar>
          <Box component="main" sx={{ flexGrow: 1, p: 3, overflow: 'auto' }}>
            <Outlet />
          </Box>
        </Box>
      </Box>
    </InvitationsProvider>
  );
}

function InvitationsBell() {
  const { count } = useInvitations();
  const navigate = useNavigate();
  return (
    <Tooltip title={count > 0 ? `${count} invitación(es) pendiente(s)` : 'Sin invitaciones'}>
      <IconButton color="inherit" onClick={() => navigate('/profile')} aria-label="Invitaciones">
        <Badge badgeContent={count} color="error">
          <NotificationsIcon />
        </Badge>
      </IconButton>
    </Tooltip>
  );
}
