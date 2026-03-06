import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import TemplatesPage from './pages/TemplatesPage';
import NewTemplatePage from './pages/NewTemplatePage';
import EditTemplatePage from './pages/EditTemplatePage';
import NewUserPage from './pages/NewUserPage';
import UsersPage from './pages/UserPage';
import RequestsPage from './pages/RequestPage';
import NewRequestPage from './pages/NewRequestPage';
import PatientPortalPage from './pages/PatientPortalPage';
import AuditPage from './pages/AuditPage';
import KioskPage from './pages/KioskPage';
import KioskSignPage from './pages/KioskSingPage';
import ProfilePage from './pages/ProfilePage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Ruta raíz redirige al dashboard */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />

          {/* Pública */}
          <Route path="/login" element={<LoginPage />} />

          <Route path="/sign/:token" element={<PatientPortalPage />} />

          {/* Protegidas */}
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          } />
          <Route path="/audit" element={
            <ProtectedRoute requiredRole="SUPERVISOR">
              <AuditPage />
            </ProtectedRoute>
          } />
          <Route path="/templates" element={
            <ProtectedRoute>
              <TemplatesPage />
            </ProtectedRoute>
          } />
          <Route path="/templates/new" element={
            <ProtectedRoute requiredRole="ADMIN">
              <NewTemplatePage />
            </ProtectedRoute>
          } />
          <Route path="/kiosk" element={
            <ProtectedRoute>
              <KioskPage />
            </ProtectedRoute>
          } />
          <Route path="/kiosk/:requestId" element={
            <ProtectedRoute>
              <KioskSignPage />
            </ProtectedRoute>
          } />
          <Route path="/profile" element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          } />

          {/* Sin autorización */}
          <Route path="/unauthorized" element={
            <div className="min-h-screen flex items-center justify-center">
              <p className="text-gray-500">No tienes permiso para ver esta página.</p>
            </div>
          } />
          <Route path="/templates/:id/edit" element={
            <ProtectedRoute requiredRole="ADMIN">
              <EditTemplatePage />
            </ProtectedRoute>
          } />
          <Route path="/users" element={
            <ProtectedRoute requiredRole="ADMIN">
              <UsersPage />
            </ProtectedRoute>
          } />
          <Route path="/users/new" element={
            <ProtectedRoute requiredRole="ADMIN">
              <NewUserPage />
            </ProtectedRoute>
          } />
          <Route path="/requests" element={
            <ProtectedRoute>
              <RequestsPage />
            </ProtectedRoute>
          } />
          <Route path="/requests/new" element={
            <ProtectedRoute>
              <NewRequestPage />
            </ProtectedRoute>
          } />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}