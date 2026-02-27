import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createUser } from '../api/user';

const ALL_ROLES = ['ADMIN', 'PROFESSIONAL', 'ADMINISTRATIVE', 'SUPERVISOR'];

const ROLE_LABELS: Record<string, string> = {
    ADMIN: 'Administrador',
    PROFESSIONAL: 'Profesional sanitario',
    ADMINISTRATIVE: 'Administrativo',
    SUPERVISOR: 'Supervisor',
};

export default function NewUserPage() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [form, setForm] = useState({
        username: '',
        fullName: '',
        email: '',
        password: '',
    });

    const [selectedRoles, setSelectedRoles] = useState<string[]>(['PROFESSIONAL']);

    const toggleRole = (role: string) => {
        setSelectedRoles(prev =>
            prev.includes(role) ? prev.filter(r => r !== role) : [...prev, role]
        );
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (selectedRoles.length === 0) {
            setError('Selecciona al menos un rol');
            return;
        }
        setLoading(true);
        setError('');
        try {
            await createUser({ ...form, roles: selectedRoles });
            navigate('/users');
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al crear el usuario');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-gray-100">

            <nav className="bg-blue-900 text-white px-6 py-4 flex items-center gap-3">
                <button
                    onClick={() => navigate('/users')}
                    className="text-blue-300 hover:text-white text-sm transition-colors"
                >
                    ← Usuarios
                </button>
                <span className="text-blue-500">|</span>
                <h1 className="font-bold">Nuevo Usuario</h1>
            </nav>

            <main className="p-6 max-w-lg mx-auto">
                <form onSubmit={handleSubmit} className="space-y-6">

                    {/* Datos personales */}
                    <div className="bg-white rounded-xl p-6 shadow-sm space-y-4">
                        <h2 className="font-semibold text-gray-800 text-lg">Datos personales</h2>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Nombre completo *
                            </label>
                            <input
                                type="text"
                                value={form.fullName}
                                onChange={e => setForm({ ...form, fullName: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
                                placeholder="Dra. Ana García López"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Usuario *
                            </label>
                            <input
                                type="text"
                                value={form.username}
                                onChange={e => setForm({ ...form, username: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
                                placeholder="ana.garcia"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Email *
                            </label>
                            <input
                                type="email"
                                value={form.email}
                                onChange={e => setForm({ ...form, email: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
                                placeholder="ana.garcia@chpc.es"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Contraseña *
                            </label>
                            <input
                                type="password"
                                value={form.password}
                                onChange={e => setForm({ ...form, password: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
                                placeholder="Mínimo 8 caracteres"
                                required
                                minLength={8}
                            />
                        </div>
                    </div>

                    {/* Roles */}
                    <div className="bg-white rounded-xl p-6 shadow-sm">
                        <h2 className="font-semibold text-gray-800 text-lg mb-4">
                            Roles asignados *
                        </h2>
                        <div className="space-y-3">
                            {ALL_ROLES.map(role => (
                                <label
                                    key={role}
                                    className="flex items-center gap-3 cursor-pointer p-3
                             border border-gray-200 rounded-lg hover:bg-gray-50
                             transition-colors"
                                >
                                    <input
                                        type="checkbox"
                                        checked={selectedRoles.includes(role)}
                                        onChange={() => toggleRole(role)}
                                        className="w-4 h-4 rounded"
                                    />
                                    <div>
                                        <p className="font-medium text-gray-800 text-sm">
                                            {ROLE_LABELS[role]}
                                        </p>
                                        <p className="text-gray-400 text-xs">{role}</p>
                                    </div>
                                </label>
                            ))}
                        </div>
                    </div>

                    {error && (
                        <div className="bg-red-50 border border-red-200 text-red-700
                            px-4 py-3 rounded-lg text-sm">
                            {error}
                        </div>
                    )}

                    <div className="flex gap-3 justify-end">
                        <button
                            type="button"
                            onClick={() => navigate('/users')}
                            className="px-6 py-2 border border-gray-300 rounded-lg text-gray-700
                         hover:bg-gray-50 transition-colors"
                        >
                            Cancelar
                        </button>
                        <button
                            type="submit"
                            disabled={loading}
                            className="px-6 py-2 bg-blue-900 text-white rounded-lg font-medium
                         hover:bg-blue-800 disabled:opacity-50 transition-colors"
                        >
                            {loading ? 'Creando...' : 'Crear usuario'}
                        </button>
                    </div>
                </form>
            </main>
        </div>
    );
}