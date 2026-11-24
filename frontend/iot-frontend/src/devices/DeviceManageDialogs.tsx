import { useState } from "react";
import type { FormEvent } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import type { Device } from "../types/api";
import { deleteDevice, renameDevice } from "./devicesApi";
import { parseApiError } from "../shared/apiErrors";

interface BaseProps {
  open: boolean;
  edgeId: string;
  device: Device;
  onClose: () => void;
}

export function EditDeviceNameDialog({
  open,
  edgeId,
  device,
  onClose,
  onSaved,
}: BaseProps & { onSaved: (device: Device) => void }) {
  const [name, setName] = useState(device.name);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function close() {
    if (saving) return;
    onClose();
    setName(device.name);
    setFieldError(null);
    setError(null);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setFieldError(null);
    setError(null);
    try {
      const updated = await renameDevice(edgeId, device.id, name.trim());
      setName(updated.name);
      onSaved(updated);
      onClose();
    } catch (err) {
      const parsed = parseApiError(err, "No se pudo cambiar el nombre.");
      setFieldError(parsed.fieldErrors.name ?? null);
      setError(parsed.message);
    } finally {
      setSaving(false);
    }
  }

  const trimmed = name.trim();
  const canSave = trimmed !== "" && trimmed !== device.name && !saving;

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="xs">
      <form onSubmit={submit} noValidate>
        <DialogTitle>Editar nombre</DialogTitle>
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
              error={fieldError !== null}
              helperText={fieldError}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={close} disabled={saving}>
            Cancelar
          </Button>
          <Button type="submit" variant="contained" disabled={!canSave}>
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function DeleteDeviceDialog({
  open,
  edgeId,
  device,
  onClose,
  onDeleted,
}: BaseProps & { onDeleted: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  function close() {
    if (deleting) return;
    onClose();
    setError(null);
  }

  async function confirm() {
    setDeleting(true);
    setError(null);
    try {
      await deleteDevice(edgeId, device.id);
      onDeleted();
    } catch (err) {
      setError(parseApiError(err, "No se pudo eliminar el dispositivo.").message ??
        "No se pudo eliminar el dispositivo.");
      setDeleting(false);
    }
  }

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="xs">
      <DialogTitle>¿Eliminar «{device.name}»?</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <DialogContentText>
            Se eliminará el dispositivo y <strong>todas sus lecturas</strong>, tanto en este Edge
            como en el Cloud. Esta acción no se puede deshacer.
          </DialogContentText>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={close} disabled={deleting}>
          Cancelar
        </Button>
        <Button color="error" variant="contained" onClick={confirm} disabled={deleting}>
          {deleting ? "Eliminando…" : "Eliminar"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
