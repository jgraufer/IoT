import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutlineOutlined";
import { useNavigate, useParams } from "react-router-dom";
import axios from "axios";
import { apiErrorMessage } from "../shared/errorMessages";
import type { EdgeDetail, EdgeMember, Invitation } from "../types/api";
import { useAuth } from "../auth/AuthContext";
import { getEdgeDetail } from "../session/edgesApi";
import {
  cancelInvitation,
  inviteToEdge,
  listSentInvitations,
} from "../invitations/invitationsApi";
import { ROLE_COLOR, ROLE_LABEL } from "../shared/roleDisplay";

function extractError(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const msg = (err.response?.data as { message?: string } | undefined)
      ?.message;
    if (msg) return msg;
  }
  return fallback;
}

function formatDate(iso: string | null | undefined, withTime = false): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return withTime
    ? d.toLocaleString("es-ES")
    : d.toLocaleDateString("es-ES", {
        day: "numeric",
        month: "long",
        year: "numeric",
      });
}

function displayName(m: EdgeMember): string {
  return m.name ?? m.username ?? m.email;
}

function MemberRow({ member }: { member: EdgeMember }) {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 2,
        py: 1,
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="body1" noWrap>
          {displayName(member)}
        </Typography>
        <Typography variant="body2" color="text.secondary" noWrap>
          {member.email}
        </Typography>
      </Box>
      <Chip
        size="small"
        label={ROLE_LABEL[member.role]}
        color={ROLE_COLOR[member.role]}
      />
    </Box>
  );
}

function InvitationsSection({ edgeId }: { edgeId: string }) {
  const [sent, setSent] = useState<Invitation[]>([]);
  const [loading, setLoading] = useState(false);
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    listSentInvitations(edgeId)
      .then((list) => {
        if (!cancelled) setSent(list);
      })
      .catch(() => {
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [edgeId]);

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    const value = email.trim();
    if (!value) return;
    setSubmitting(true);
    setError(null);
    try {
      const created = await inviteToEdge(edgeId, value);
      setSent((prev) => [...prev, created]);
      setEmail("");
    } catch (err) {
      setError(apiErrorMessage(err, "No se pudo enviar la invitación."));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancel(id: string) {
    setError(null);
    try {
      await cancelInvitation(edgeId, id);
      setSent((prev) => prev.filter((i) => i.id !== id));
    } catch (err) {
      setError(apiErrorMessage(err, "No se pudo cancelar la invitación."));
    }
  }

  return (
    <Card sx={{ borderRadius: 4 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Invitaciones
        </Typography>

        <Box
          component="form"
          onSubmit={handleInvite}
          sx={{ display: "flex", gap: 1, alignItems: "flex-start", mt: 1 }}
        >
          <TextField
            size="small"
            type="email"
            label="Email del invitado"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={submitting}
            fullWidth
          />
          <Button
            type="submit"
            variant="contained"
            disabled={submitting || email.trim() === ""}
            sx={{ flexShrink: 0, mt: 0.25 }}
          >
            Invitar
          </Button>
        </Box>

        {error && (
          <Alert severity="error" sx={{ mt: 1.5 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <Typography variant="subtitle2" color="text.secondary" sx={{ mt: 2.5 }}>
          Pendientes
        </Typography>
        {loading ? (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Cargando…
          </Typography>
        ) : sent.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            No hay invitaciones pendientes.
          </Typography>
        ) : (
          <Stack divider={<Divider flexItem />} sx={{ mt: 1 }}>
            {sent.map((inv) => (
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
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body1" noWrap>
                    {inv.invitedName ?? inv.invitedEmail}
                  </Typography>
                  {inv.invitedName && (
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {inv.invitedEmail}
                    </Typography>
                  )}
                </Box>
                <IconButton
                  aria-label="Cancelar invitación"
                  onClick={() => handleCancel(inv.id)}
                >
                  <DeleteOutlineIcon />
                </IconButton>
              </Box>
            ))}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

export function EdgeDetailPage() {
  const { edgeId } = useParams<{ edgeId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [detail, setDetail] = useState<EdgeDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!edgeId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    getEdgeDetail(edgeId)
      .then((d) => {
        if (!cancelled) setDetail(d);
      })
      .catch((err) => {
        if (!cancelled)
          setError(extractError(err, "No se pudo cargar la casa."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [edgeId]);

  const me = detail?.members.find((m) => m.userId === user?.id) ?? null;
  const others = detail?.members.filter((m) => m.userId !== user?.id) ?? [];

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
        <IconButton onClick={() => navigate(-1)} aria-label="Volver">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4">{detail ? detail.name : "Casa"}</Typography>
      </Box>

      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
          <CircularProgress />
        </Box>
      ) : error ? (
        <Alert severity="error">{error}</Alert>
      ) : detail ? (
        <Stack spacing={3} sx={{ maxWidth: 560 }}>
          <Card sx={{ borderRadius: 4 }}>
            <CardContent>
              <Box
                sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 2 }}
              >
                <Typography variant="h6">{detail.name}</Typography>
              </Box>
              <Stack spacing={1.5}>
                <Box sx={{ display: "flex", justifyContent: "space-between" }}>
                  <Typography variant="body2" color="text.secondary">
                    Emparejada el
                  </Typography>
                  <Typography variant="body2">
                    {formatDate(detail.pairedAt)}
                  </Typography>
                </Box>
                <Box sx={{ display: "flex", justifyContent: "space-between" }}>
                  <Typography variant="body2" color="text.secondary">
                    Última conexión
                  </Typography>
                  <Typography variant="body2">
                    {formatDate(detail.lastSeen, true)}
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>

          {me && (
            <Card sx={{ borderRadius: 4 }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Tú en esta casa
                </Typography>
                <MemberRow member={me} />
              </CardContent>
            </Card>
          )}

          <Card sx={{ borderRadius: 4 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Otros miembros
              </Typography>
              {others.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  No hay más miembros.
                </Typography>
              ) : (
                <Stack divider={<Divider flexItem />} sx={{ mt: 1 }}>
                  {others.map((m) => (
                    <MemberRow key={m.userId} member={m} />
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>

          {me?.role === "OWNER" && edgeId && <InvitationsSection edgeId={edgeId} />}
        </Stack>
      ) : null}
    </Box>
  );
}
