import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { login } from '../api/auth';

export default function LoginPage() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const { loginUser, isAuthenticated, loading: authLoading } = useAuth();
    const navigate = useNavigate();

    useEffect(() => {
        if (!authLoading && isAuthenticated) {
            navigate('/dashboard');
        }
    }, [isAuthenticated, authLoading, navigate]);

    if (authLoading) {
        return (
            <div className="page-loading">
                <p className="text-gray-400">Cargando...</p>
            </div>
        );
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const data = await login(username, password);
            loginUser({
                id: data.id,
                token: data.token,
                username: data.username,
                fullName: data.fullName,
                email: data.email,
                dni: data.dni,
                roles: data.roles,
                serviceCode: data.serviceCode,
                serviceName: data.serviceName,
                signatureMethod: data.signatureMethod,
            });
        } catch {
            setError('Usuario o contraseña incorrectos');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-shell">
            <div className="login-card">
                <div className="text-center mb-8">
                    <div className="login-mark">CI</div>
                    <h1 className="text-2xl font-bold text-gray-800">
                        Consentimientos Informados
                    </h1>
                    <p className="text-gray-500 text-sm mt-1">
                        Consorci Hospitalari Provincial de Castelló
                    </p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Usuario
                        </label>
                        <input
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            className="w-full border border-gray-300 rounded-lg px-3 py-2
                         focus:outline-none focus:ring-2 focus:ring-emerald-500"
                            placeholder="usuario.corporativo"
                            required
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Contraseña
                        </label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            className="w-full border border-gray-300 rounded-lg px-3 py-2
                         focus:outline-none focus:ring-2 focus:ring-emerald-500"
                            placeholder="••••••••"
                            required
                        />
                    </div>

                    {error && (
                        <div className="surface-note surface-note--danger text-sm">
                            {error}
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={loading}
                        className="soft-button w-full disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {loading ? 'Accediendo...' : 'Iniciar sesión'}
                    </button>
                </form>
            </div>
        </div>
    );
}
