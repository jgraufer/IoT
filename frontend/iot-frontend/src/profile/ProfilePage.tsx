import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Stack,
  Typography,
} from "@mui/material";
import { useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { apiErrorMessage } from "../shared/errorMessages";
import { useAuth } from "../auth/AuthContext";
import { useEdges } from "../session/EdgeProvider";
import { useInvitations } from "../invitations/InvitationsContext";
import { ROLE_COLOR, ROLE_LABEL } from "../shared/roleDisplay";

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("es-ES", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

function MyInvitationsCard() {
  const { invitations, accept, reject } = useInvitations();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (invitations.length === 0) return null;

  async function resolve(id: string, action: (id: string) => Promise<void>) {
    setBusyId(id);
    setError(null);
    try {
      await action(id);
    } catch (err) {
      setError(apiErrorMessage(err, "No se pudo procesar la invitación."));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Card sx={{ borderRadius: 4 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Mis invitaciones
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 1 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        <Stack divider={<Divider flexItem />} sx={{ mt: 1 }}>
          {invitations.map((inv) => (
            <Box
              key={inv.id}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 2,
                py: 1,
              }}
            >
              <Typography variant="body1">
                Te han invitado a <strong>{inv.edgeName ?? "una casa"}</strong>
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  variant="contained"
                  disabled={busyId !== null}
                  onClick={() => resolve(inv.id, accept)}
                >
                  Aceptar
                </Button>
                <Button
                  size="small"
                  color="inherit"
                  disabled={busyId !== null}
                  onClick={() => resolve(inv.id, reject)}
                >
                  Rechazar
                </Button>
              </Stack>
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ display: "flex", gap: 2, alignItems: "baseline" }}>
      <Typography
        variant="body2"
        color="text.secondary"
        sx={{ width: 140, flexShrink: 0 }}
      >
        {label}
      </Typography>
      <Box sx={{ minWidth: 0 }}>{children}</Box>
    </Box>
  );
}

export function ProfilePage() {
  const { user } = useAuth();
  const { edges, edgesLoading } = useEdges();
  const navigate = useNavigate();

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Usuario
      </Typography>

      <Stack spacing={3} sx={{ maxWidth: 560 }}>
        <Card sx={{ borderRadius: 4 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Tu cuenta
            </Typography>
            {user ? (
              <Stack spacing={1.5} sx={{ mt: 1 }}>
                <Field label="Nombre">
                  <Typography variant="body1">{user.name}</Typography>
                </Field>
                <Field label="Email">
                  <Typography variant="body1">{user.email}</Typography>
                </Field>
                <Field label="Usuario">
                  <Typography variant="body1">
                    {user.username ?? "—"}
                  </Typography>
                </Field>
                {user.createdAt && (
                  <Field label="Miembro desde">
                    <Typography variant="body1">
                      {formatDate(user.createdAt)}
                    </Typography>
                  </Field>
                )}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                No hay datos de sesión.
              </Typography>
            )}
          </CardContent>
        </Card>

        <MyInvitationsCard />

        <Card sx={{ borderRadius: 4 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Tus casas
            </Typography>
            {edgesLoading ? (
              <Typography variant="body2" color="text.secondary">
                Cargando casas…
              </Typography>
            ) : edges.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                No perteneces a ninguna casa todavía.
              </Typography>
            ) : (
              <Stack divider={<Divider flexItem />} sx={{ mt: 1 }}>
                {edges.map((edge) => (
                  <Box
                    key={edge.edgeId}
                    onClick={() => navigate(`/edges/${edge.edgeId}`)}
                    sx={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      gap: 2,
                      py: 1,
                      cursor: "pointer",
                      "&:hover": { bgcolor: "action.hover" },
                    }}
                  >
                    <Typography variant="body1">{edge.name}</Typography>
                    <Chip
                      size="small"
                      label={ROLE_LABEL[edge.role]}
                      color={ROLE_COLOR[edge.role]}
                    />
                  </Box>
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
}
