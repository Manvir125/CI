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

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Ruta raíz redirige al dashboard */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />

          {/* Pública */}
          <Route path="/login" element={<LoginPage />} />

          {/* Protegidas */}
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <DashboardPage />
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
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}