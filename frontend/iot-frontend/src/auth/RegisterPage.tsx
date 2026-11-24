import { useState } from 'react';
import type { FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Container,
  Link,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import { Link as RouterLink, Navigate, useNavigate } from 'react-router-dom';
import { apiErrorMessage } from '../shared/errorMessages';
import { useAuth } from './AuthContext';

function validate(name: string, email: string, username: string, password: string): string | null {
  if (name.trim().length < 2) return 'El nombre debe tener al menos 2 caracteres.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Introduce un email válido.';
  if (!/^[a-zA-Z0-9_]{3,64}$/.test(username))
    return 'El usuario admite 3-64 letras, dígitos o guiones bajos.';
  if (password.length < 8) return 'La contraseña debe tener al menos 8 caracteres.';
  return null;
}

export function RegisterPage() {
  const { isAuthenticated, isInitializing, register } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validate(name, email, username, password);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await register({ name: name.trim(), email: email.trim(), username: username.trim(), password });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(apiErrorMessage(err, 'Error inesperado al crear la cuenta.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Container maxWidth="xs" sx={{ mt: 8 }}>
      <Paper elevation={3} sx={{ p: 4 }}>
        <Typography variant="h5" component="h1" gutterBottom>
          Crear cuenta
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Regístrate para gestionar tus dispositivos IoT.
        </Typography>
        <Box component="form" onSubmit={handleSubmit} noValidate>
          <TextField
            label="Nombre"
            value={name}
            onChange={(e) => setName(e.target.value)}
            fullWidth
            margin="normal"
            autoComplete="name"
            autoFocus
            required
          />
          <TextField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            fullWidth
            margin="normal"
            autoComplete="email"
            required
          />
          <TextField
            label="Usuario"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            fullWidth
            margin="normal"
            autoComplete="username"
            required
          />
          <TextField
            label="Contraseña"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            fullWidth
            margin="normal"
            autoComplete="new-password"
            helperText="Mínimo 8 caracteres."
            required
          />
          {error && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {error}
            </Alert>
          )}
          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            sx={{ mt: 3 }}
            disabled={
              submitting || name === '' || email === '' || username === '' || password === ''
            }
          >
            {submitting ? <CircularProgress size={24} color="inherit" /> : 'Crear cuenta'}
          </Button>
          <Typography variant="body2" align="center" sx={{ mt: 2 }}>
            ¿Ya tienes cuenta?{' '}
            <Link component={RouterLink} to="/login">
              Inicia sesión
            </Link>
          </Typography>
        </Box>
      </Paper>
    </Container>
  );
}
