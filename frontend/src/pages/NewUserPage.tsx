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
        serviceCode: '',
        dni: '',
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
            await createUser({
                ...form,
                roles: selectedRoles,
                dni: form.dni || undefined,
                serviceCode: form.serviceCode || undefined,
            });
            navigate('/users');
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al crear el usuario');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="page-shell">
            <nav className="app-topbar">
                <button
                    onClick={() => navigate('/users')}
                    className="soft-button-ghost text-sm"
                >
                    ← Usuarios
                </button>
                <span className="text-emerald-200">|</span>
                <h1 className="font-bold">Nuevo Usuario</h1>
            </nav>

            <main className="page-main max-w-3xl space-y-6">
                <section className="page-hero-lite">
                    <div>
                        <p className="section-kicker">Alta de usuario</p>
                        <h2 className="page-hero-lite__title">Configura acceso, identidad y roles</h2>
                        <p className="page-hero-lite__text">
                            Prepara el perfil profesional con un formulario más claro y una jerarquía visual más suave.
                        </p>
                    </div>
                </section>
                <form onSubmit={handleSubmit} className="space-y-6">
                    <div className="soft-form-card space-y-4">
                        <h2 className="font-semibold text-gray-800 text-lg">Datos personales</h2>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Nombre completo *
                            </label>
                            <input
                                type="text"
                                value={form.fullName}
                                onChange={e => setForm({ ...form, fullName: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                placeholder="Dra. Ana Garcia Lopez"
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
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
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
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
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
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                placeholder="Minimo 8 caracteres"
                                required
                                minLength={8}
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                DNI profesional
                            </label>
                            <input
                                type="text"
                                value={form.dni}
                                onChange={e => setForm({ ...form, dni: e.target.value.toUpperCase() })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                placeholder="Ej: 48581393B"
                            />
                            <p className="text-xs text-gray-400 mt-1">
                                Necesario para consultar las citas del profesional en ApiKewan.
                            </p>
                        </div>

                        {form.dni ? (
                            <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-4">
                                <p className="text-sm font-medium text-blue-900">Especialidad sincronizada con ApiKewan</p>
                                <p className="text-xs text-blue-700 mt-2">
                                    Si el DNI existe en ApiKewan, la especialidad se completara automaticamente al entrar en el dashboard.
                                </p>
                            </div>
                        ) : (
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Codigo interno del servicio
                                </label>
                                <input
                                    type="text"
                                    value={form.serviceCode}
                                    onChange={e => setForm({ ...form, serviceCode: e.target.value })}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                    placeholder="Ej: 138"
                                />
                            </div>
                        )}
                    </div>

                    <div className="soft-form-card">
                        <h2 className="font-semibold text-gray-800 text-lg mb-4">
                            Roles asignados *
                        </h2>
                        <div className="space-y-3">
                            {ALL_ROLES.map(role => (
                                <label
                                    key={role}
                                    className="flex items-center gap-3 cursor-pointer p-3 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
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
                        <div className="surface-note surface-note--danger text-sm">
                            {error}
                        </div>
                    )}

                    <div className="flex gap-3 justify-end">
                        <button
                            type="button"
                            onClick={() => navigate('/users')}
                            className="soft-button-secondary"
                        >
                            Cancelar
                        </button>
                        <button
                            type="submit"
                            disabled={loading}
                            className="soft-button disabled:opacity-50"
                        >
                            {loading ? 'Creando...' : 'Crear usuario'}
                        </button>
                    </div>
                </form>
            </main>
        </div>
    );
}
