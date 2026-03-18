import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getUser, updateUser } from '../api/user';

const ALL_ROLES = ['ADMIN', 'PROFESSIONAL', 'ADMINISTRATIVE', 'SUPERVISOR'];

const ROLE_LABELS: Record<string, string> = {
    ADMIN: 'Administrador',
    PROFESSIONAL: 'Profesional sanitario',
    ADMINISTRATIVE: 'Administrativo',
    SUPERVISOR: 'Supervisor',
};

export default function EditUserPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    const [form, setForm] = useState({
        fullName: '',
        email: '',
        password: '',
        serviceCode: '',
    });

    const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
    const [username, setUsername] = useState('');

    useEffect(() => {
        const load = async () => {
            try {
                const user = await getUser(Number(id));
                setForm({
                    fullName: user.fullName,
                    email: user.email,
                    password: '',
                    serviceCode: user.serviceCode || '',
                });
                setSelectedRoles([...user.roles]);
                setUsername(user.username);
            } catch {
                setError('Error al cargar el usuario');
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [id]);

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
        setSaving(true);
        setError('');
        try {
            await updateUser(Number(id), {
                fullName: form.fullName,
                email: form.email,
                password: form.password || undefined,
                roles: selectedRoles,
                serviceCode: form.serviceCode || undefined,
            });
            navigate('/users');
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al actualizar el usuario');
        } finally {
            setSaving(false);
        }
    };

    if (loading) return (
        <div className="min-h-screen flex items-center justify-center bg-gray-100">
            <p className="text-gray-400">Cargando usuario...</p>
        </div>
    );

    return (
        <div className="min-h-screen bg-gray-100">

            <nav className="bg-emerald-700 text-white px-6 py-4 flex items-center gap-3">
                <button
                    onClick={() => navigate('/users')}
                    className="text-emerald-300 hover:text-white text-sm transition-colors"
                >
                    ← Usuarios
                </button>
                <span className="text-emerald-500">|</span>
                <h1 className="font-bold">Editar Usuario — @{username}</h1>
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
                           focus:outline-none focus:ring-2 focus:ring-emerald-500"
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
                           focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Nueva contraseña
                            </label>
                            <input
                                type="password"
                                value={form.password}
                                onChange={e => setForm({ ...form, password: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                placeholder="Dejar vacío para no cambiar"
                                minLength={8}
                            />
                            <p className="text-xs text-gray-400 mt-1">
                                Solo se actualizará si introduces una nueva contraseña (mín. 8 caracteres)
                            </p>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Código de servicio
                            </label>
                            <input
                                type="text"
                                value={form.serviceCode}
                                onChange={e => setForm({ ...form, serviceCode: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                placeholder="Ej: CIR"
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
                            disabled={saving}
                            className="px-6 py-2 bg-emerald-700 text-white rounded-lg font-medium
                         hover:bg-emerald-600 disabled:opacity-50 transition-colors"
                        >
                            {saving ? 'Guardando...' : 'Guardar cambios'}
                        </button>
                    </div>
                </form>
            </main>
        </div>
    );
}
