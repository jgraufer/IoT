import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import axios from "axios";
import { useAuth } from "../auth/AuthContext";
import { useEdges } from "../session/EdgeProvider";
import { listMyEdges } from "../session/edgesApi";
import { isOffline } from "../api/client";
import { getEdgeInfo, getPairingCode, pairEdge } from "./pairingApi";

const POLL_MS = 2_000;
const WAIT_MS = 30_000;

const NO_CODE_MSG =
  "El Edge no consigue un código del Cloud. Comprueba que tiene conexión a internet.";
const OTHER_ACCOUNT_MSG =
  "Esta casa ya se ha emparejado con otra cuenta. Si no has sido tú, hay que restablecer el Edge.";
const EDGE_DOWN_MSG = "El Edge no responde.";

type PasswordMode = "afterPair" | "granted" | "unknown";

type Step =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "needCloud" }
  | { kind: "pair" }
  | { kind: "noAccess" }
  | { kind: "password"; mode: PasswordMode };

const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

function startTimeout(): () => boolean {
  const end = Date.now() + WAIT_MS;
  return () => Date.now() > end;
}

function status(err: unknown): number | undefined {
  return axios.isAxiosError(err) ? err.response?.status : undefined;
}

function apiCode(err: unknown): string | undefined {
  return axios.isAxiosError(err)
    ? (err.response?.data as { code?: string } | undefined)?.code
    : undefined;
}

async function isMyEdge(edgeId: string | null): Promise<boolean> {
  const edges = await listMyEdges();
  return edges.some((e) => e.edgeId === edgeId);
}

async function resolveStep(hasCloud: boolean): Promise<{ step: Step; edgeId: string | null }> {
  let edgeId: string | null = null;
  try {
    const info = await getEdgeInfo();
    edgeId = info.edgeId;
    if (!info.paired) {
      return { step: hasCloud ? { kind: "pair" } : { kind: "needCloud" }, edgeId };
    }
    let mine: boolean | null = null;
    try {
      mine = await isMyEdge(edgeId);
    } catch {
    }
    const step: Step =
      mine === null
        ? { kind: "password", mode: "unknown" }
        : mine
          ? { kind: "password", mode: "granted" }
          : { kind: "noAccess" };
    return { step, edgeId };
  } catch (err) {
    return {
      step: {
        kind: "error",
        message: isOffline(err) ? EDGE_DOWN_MSG : "No se pudo comprobar el estado de la casa.",
      },
      edgeId,
    };
  }
}

export function EdgeAccessGate() {
  const { user, hasCloud, loginEdge } = useAuth();
  const { refreshEdges } = useEdges();
  const [step, setStep] = useState<Step>({ kind: "loading" });
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const edgeIdRef = useRef<string | null>(null);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const isMine = () => isMyEdge(edgeIdRef.current);

  const resolve = useCallback(() => {
    let cancelled = false;
    resolveStep(hasCloud).then(({ step: next, edgeId }) => {
      if (cancelled) return;
      edgeIdRef.current = edgeId;
      setStep(next);
    });
    return () => {
      cancelled = true;
    };
  }, [hasCloud]);

  useEffect(() => resolve(), [resolve]);

  async function pair() {
    setBusy("Emparejando…");
    setError(null);
    const timedOut = startTimeout();
    try {
      while (alive.current) {
        let code: string | null = null;
        try {
          code = (await getPairingCode()).pairingCode;
        } catch (err) {
          if (status(err) !== 404) throw err;
          if ((await getEdgeInfo()).paired) return await afterInvalidCode();
        }
        if (code) {
          try {
            await pairEdge(code);
            void refreshEdges();
            setStep({ kind: "password", mode: "afterPair" });
            return;
          } catch (err) {
            const errCode = apiCode(err);
            if (errCode === "PAIRING_CODE_INVALID" || status(err) === 409) {
              return await afterInvalidCode();
            }
            if (errCode !== "PAIRING_CODE_EXPIRED") throw err;
          }
        }
        if (timedOut()) {
          setError(NO_CODE_MSG);
          return;
        }
        await sleep(POLL_MS);
      }
    } catch (err) {
      if (alive.current) {
        setError(isOffline(err) ? "Sin conexión con el Edge o el Cloud." : "No se pudo emparejar la casa.");
      }
    } finally {
      if (alive.current) setBusy(null);
    }
  }

  async function afterInvalidCode() {
    if (await isMine()) {
      void refreshEdges();
      setStep({ kind: "password", mode: "afterPair" });
    } else {
      setError(OTHER_ACCOUNT_MSG);
    }
  }

  async function waitPaired(): Promise<boolean> {
    const timedOut = startTimeout();
    while (alive.current) {
      if ((await getEdgeInfo()).paired) return true;
      if (timedOut()) return false;
      setBusy("Terminando de configurar la casa…");
      await sleep(POLL_MS);
    }
    return false;
  }

  async function enter(e: FormEvent, mode: PasswordMode) {
    e.preventDefault();
    const identifier = user?.username ?? user?.email;
    if (!identifier) return;
    setBusy("Entrando…");
    setError(null);
    try {
      if (mode === "afterPair" && !(await waitPaired())) {
        setError(
          "La casa aún no ha recibido tu acceso. Comprueba que el Edge tiene conexión a internet y vuelve a intentarlo.",
        );
        return;
      }
      const timedOut = startTimeout();
      while (alive.current) {
        try {
          await loginEdge(identifier, password);
          return;
        } catch (err) {
          if (status(err) !== 401) throw err;
          if (mode !== "granted") {
            setError(
              mode === "afterPair"
                ? "Contraseña incorrecta."
                : "Contraseña incorrecta o sin acceso a esta casa.",
            );
            return;
          }
          if (timedOut()) {
            setError(
              "No se pudo entrar. Revisa la contraseña; si acabas de aceptar la invitación, comprueba que el Edge tiene conexión a internet.",
            );
            return;
          }
          setBusy("Terminando de configurar la casa…");
          await sleep(POLL_MS);
        }
      }
    } catch (err) {
      if (alive.current) setError(isOffline(err) ? EDGE_DOWN_MSG : "No se pudo entrar en la casa.");
    } finally {
      if (alive.current) setBusy(null);
    }
  }

  let body;
  switch (step.kind) {
    case "loading":
      body = (
        <Box sx={{ display: "flex", justifyContent: "center", py: 2 }}>
          <CircularProgress />
        </Box>
      );
      break;
    case "error":
      body = (
        <Stack spacing={2}>
          <Alert severity="error">{step.message}</Alert>
          <Box>
            <Button
              onClick={() => {
                setStep({ kind: "loading" });
                resolve();
              }}
            >
              Reintentar
            </Button>
          </Box>
        </Stack>
      );
      break;
    case "needCloud":
      body = (
        <Alert severity="info">
          Esta casa aún no está emparejada. Para emparejarla necesitas conexión con el Cloud: vuelve
          a iniciar sesión cuando haya internet.
        </Alert>
      );
      break;
    case "noAccess":
      body = <Alert severity="info">No tienes acceso a esta casa. Pide una invitación al propietario.</Alert>;
      break;
    case "pair":
      body = (
        <Stack spacing={2}>
          <Typography variant="h6">Emparejar esta casa con tu cuenta</Typography>
          <Typography variant="body2">
            Este Edge todavía no tiene propietario. Al emparejarlo pasarás a ser su propietario y
            podrás gestionarlo desde aquí y desde fuera de casa.
          </Typography>
          <Alert severity="warning">
            Quien empareje primero esta casa se convierte en su propietario. Hazlo nada más instalar
            el Edge.
          </Alert>
          {error && <Alert severity="error">{error}</Alert>}
          <Box>
            <Button variant="contained" onClick={() => void pair()} disabled={busy !== null}>
              {busy ?? "Emparejar"}
            </Button>
          </Box>
        </Stack>
      );
      break;
    case "password":
      body = (
        <Box component="form" onSubmit={(e) => void enter(e, step.mode)} noValidate>
          <Stack spacing={2}>
            <Typography variant="h6">
              {step.mode === "afterPair" ? "Casa emparejada" : "Entrar en esta casa"}
            </Typography>
            <Typography variant="body2">
              Introduce tu contraseña para entrar en el Edge de esta casa.
            </Typography>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="Usuario"
              value={user?.username ?? user?.email ?? ""}
              disabled
              fullWidth
              autoComplete="username"
            />
            <TextField
              label="Contraseña"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              fullWidth
              autoFocus
              autoComplete="current-password"
              required
            />
            <Box>
              <Button
                type="submit"
                variant="contained"
                disabled={busy !== null || password === "" || !user}
              >
                {busy ?? "Entrar"}
              </Button>
            </Box>
          </Stack>
        </Box>
      );
      break;
  }

  return (
    <Card sx={{ borderRadius: 4, maxWidth: 520 }}>
      <CardContent>{body}</CardContent>
    </Card>
  );
}
