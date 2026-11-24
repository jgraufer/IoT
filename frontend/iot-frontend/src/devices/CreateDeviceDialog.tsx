import { useState } from "react";
import type { FormEvent } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormHelperText,
  FormLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import type { CapabilityType, Device, DeviceType } from "../types/api";
import { CAPABILITY_LABEL, DEVICE_TYPE_LABEL } from "../dashboard/deviceDisplay";
import { createDevice } from "./devicesApi";
import { parseApiError } from "../shared/apiErrors";
import { CopyField } from "../shared/CopyField";

const ALL_CAPABILITIES = Object.keys(CAPABILITY_LABEL) as CapabilityType[];
const ALL_TYPES = Object.keys(DEVICE_TYPE_LABEL) as DeviceType[];

interface Props {
  open: boolean;
  edgeId: string;
  onClose: () => void;
  onCreated: (device: Device) => void;
}

export function CreateDeviceDialog({ open, edgeId, onClose, onCreated }: Props) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DeviceType | "">("");
  const [capabilities, setCapabilities] = useState<CapabilityType[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState<Device | null>(null);

  function reset() {
    setName("");
    setType("");
    setCapabilities([]);
    setFieldErrors({});
    setError(null);
    setCreated(null);
  }

  function close() {
    if (submitting) return;
    onClose();
    reset();
  }

  function toggleCapability(cap: CapabilityType, checked: boolean) {
    setCapabilities((prev) =>
      ALL_CAPABILITIES.filter((c) => (c === cap ? checked : prev.includes(c))),
    );
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (type === "") return;
    setSubmitting(true);
    setFieldErrors({});
    setError(null);
    try {
      const device = await createDevice(edgeId, { name: name.trim(), type, capabilities });
      setCreated(device);
      onCreated(device);
    } catch (err) {
      const parsed = parseApiError(err, "No se pudo crear el dispositivo.");
      setFieldErrors(parsed.fieldErrors);
      setError(parsed.message);
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit = name.trim() !== "" && type !== "" && capabilities.length > 0 && !submitting;

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm">
      {created ? (
        <>
          <DialogTitle>Dispositivo creado</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Typography variant="body2">
                <strong>{created.name}</strong> ya está dado de alta. Configura este ID en el
                firmware del dispositivo (o en el simulador) para vincularlo: publicará su
                telemetría en el topic de abajo.
              </Typography>
              <CopyField label="ID del dispositivo" value={created.id} />
              <CopyField label="Topic de telemetría" value={`devices/${created.id}/telemetry`} />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button variant="contained" onClick={close}>
              Cerrar
            </Button>
          </DialogActions>
        </>
      ) : (
        <form onSubmit={submit} noValidate>
          <DialogTitle>Añadir dispositivo</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              {error && <Alert severity="error">{error}</Alert>}
              <TextField
                label="Nombre"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoFocus
                fullWidth
                slotProps={{ htmlInput: { maxLength: 128 } }}
                error={Boolean(fieldErrors.name)}
                helperText={fieldErrors.name}
              />
              <TextField
                select
                label="Tipo"
                value={type}
                onChange={(e) => setType(e.target.value as DeviceType)}
                required
                fullWidth
                error={Boolean(fieldErrors.type)}
                helperText={fieldErrors.type}
              >
                {ALL_TYPES.map((t) => (
                  <MenuItem key={t} value={t}>
                    {DEVICE_TYPE_LABEL[t]}
                  </MenuItem>
                ))}
              </TextField>
              <FormControl
                component="fieldset"
                required
                error={Boolean(fieldErrors.capabilities)}
              >
                <FormLabel component="legend">Capacidades</FormLabel>
                <FormGroup row>
                  {ALL_CAPABILITIES.map((cap) => (
                    <FormControlLabel
                      key={cap}
                      label={CAPABILITY_LABEL[cap]}
                      control={
                        <Checkbox
                          checked={capabilities.includes(cap)}
                          onChange={(e) => toggleCapability(cap, e.target.checked)}
                        />
                      }
                    />
                  ))}
                </FormGroup>
                <FormHelperText>
                  {fieldErrors.capabilities ??
                    "Al menos una. Describen el hardware y no se pueden cambiar después."}
                </FormHelperText>
              </FormControl>
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={close} disabled={submitting}>
              Cancelar
            </Button>
            <Button type="submit" variant="contained" disabled={!canSubmit}>
              {submitting ? "Creando…" : "Crear"}
            </Button>
          </DialogActions>
        </form>
      )}
    </Dialog>
  );
}
