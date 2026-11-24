import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { EdgeProvider } from './session/EdgeProvider';
import { ProtectedRoute, RequireSession } from './shared/ProtectedRoute';
import { AppLayout } from './shared/AppLayout';
import { LoginPage } from './auth/LoginPage';
import { RegisterPage } from './auth/RegisterPage';
import { DevicesPage } from './devices/DevicesPage';
import { DeviceDetailPage } from './devices/DeviceDetailPage';
import { DashboardPage } from './dashboard/DashboardPage';
import { ProfilePage } from './profile/ProfilePage';
import { EdgeDetailPage } from './edges/EdgeDetailPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <EdgeProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route element={<RequireSession backend="devices" />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/devices" element={<DevicesPage />} />
                  <Route path="/devices/:id" element={<DeviceDetailPage />} />
                </Route>
                <Route element={<RequireSession backend="cloud" />}>
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route path="/edges/:edgeId" element={<EdgeDetailPage />} />
                </Route>
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </EdgeProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
