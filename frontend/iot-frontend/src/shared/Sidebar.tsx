import {
  Avatar,
  Box,
  Divider,
  List,
  ListItemAvatar,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
} from "@mui/material";
import DashboardIcon from "@mui/icons-material/Dashboard";
import DevicesIcon from "@mui/icons-material/Devices";
import type { ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const SECTIONS: { path: string; label: string; icon: ReactNode }[] = [
  { path: "/dashboard", label: "Dashboard", icon: <DashboardIcon /> },
  { path: "/devices", label: "Dispositivos", icon: <DevicesIcon /> },
];

function initials(name: string | undefined, email: string | undefined): string {
  const words = (name ?? "").trim().split(/\s+/).filter(Boolean);
  if (words.length > 0) {
    return words
      .slice(0, 2)
      .map((w) => w[0]!.toUpperCase())
      .join("");
  }
  return (email ?? "?").charAt(0).toUpperCase();
}

export function Sidebar() {
  const { user, hasCloud } = useAuth();
  const { pathname } = useLocation();
  const isProfileActive = pathname.startsWith("/profile");

  return (
    <Box
      component="nav"
      sx={{
        width: 220,
        flexShrink: 0,
        borderRight: 1,
        borderColor: "divider",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <Box sx={{ p: 2, borderBottom: 1, borderColor: "divider" }}>
        <Typography variant="h6" noWrap>
          IoT Platform
        </Typography>
      </Box>

      <Box sx={{ mt: 1, ml: 2 }}>
        <Typography variant="caption" color="textDisabled">
          Navegación
        </Typography>
      </Box>

      <List sx={{ flexGrow: 24 }}>
        {SECTIONS.map((s) => (
          <ListItemButton
            key={s.path}
            component={Link}
            to={s.path}
            selected={pathname.startsWith(s.path)}
          >
            <ListItemIcon>{s.icon}</ListItemIcon>
            <ListItemText primary={s.label} />
          </ListItemButton>
        ))}
      </List>

      {hasCloud && user && (
        <>
          <Divider />
          <ListItemButton component={Link} to="/profile" selected={isProfileActive}>
            <ListItemAvatar>
              <Avatar>{initials(user.name, user.email)}</Avatar>
            </ListItemAvatar>
            <ListItemText primary={user.name} slotProps={{ primary: { noWrap: true } }} />
          </ListItemButton>
        </>
      )}
    </Box>
  );
}
