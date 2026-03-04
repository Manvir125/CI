import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getUsers, updateUserRoles, activateUser,
    deactivateUser, deleteUser, type UserResponse
} from '../api/user';

const ALL_ROLES = ['ADMIN', 'PROFESSIONAL', 'ADMINISTRATIVE', 'SUPERVISOR'];

const ROLE_LABELS: Record<string, { label: string; color: string }> = {
    ADMIN: { label: 'Admin', color: 'bg-red-100 text-red-700' },
    PROFESSIONAL: { label: 'Profesional', color: 'bg-blue-100 text-blue-700' },
    ADMINISTRATIVE: { label: 'Administrativo', color: 'bg-green-100 text-green-700' },
    SUPERVISOR: { label: 'Supervisor', color: 'bg-purple-100 text-purple-700' },
};

export default function UsersPage() {
    const [users, setUsers] = useState<UserResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [editingId, setEditingId] = useState<number | null>(null);
    const [editRoles, setEditRoles] = useState<string[]>([]);
    const [savingId, setSavingId] = useState<number | null>(null);

    const navigate = useNavigate();

    useEffect(() => { loadUsers(); }, []);

    const loadUsers = async () => {
        try {
            setUsers(await getUsers());
        } catch {
            setError('Error al cargar los usuarios');
        } finally {
            setLoading(false);
        }
    };

    // Abre el editor de roles para un usuario
    const startEditRoles = (user: UserResponse) => {
        setEditingId(user.id);
        setEditRoles([...user.roles]);
    };

    const toggleRole = (role: string) => {
        setEditRoles(prev =>
            prev.includes(role) ? prev.filter(r => r !== role) : [...prev, role]
        );
    };

    const saveRoles = async (id: number) => {
        if (editRoles.length === 0) {
            setError('El usuario debe tener al menos un rol');
            return;
        }
        setSavingId(id);
        try {
            const updated = await updateUserRoles(id, editRoles);
            setUsers(users.map(u => u.id === id ? updated : u));
            setEditingId(null);
        } catch {
            setError('Error al actualizar los roles');
        } finally {
            setSavingId(null);
        }
    };

    const handleToggleActive = async (user: UserResponse) => {
        const action = user.isActive ? deactivateUser : activateUser;
        const label = user.isActive ? 'desactivar' : 'activar';
        if (!confirm(`¿${label} a ${user.fullName}?`)) return;
        try {
            const updated = await action(user.id);
            setUsers(users.map(u => u.id === user.id ? updated : u));
        } catch (err: any) {
            setError(err?.response?.data?.message || `Error al ${label} el usuario`);
        }
    };

    const handleDelete = async (user: UserResponse) => {
        if (!confirm(`¿Eliminar permanentemente a ${user.fullName}?`)) return;
        try {
            await deleteUser(user.id);
            setUsers(users.filter(u => u.id !== user.id));
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al eliminar el usuario');
        }
    };

    if (loading) return (
        <div className="min-h-screen flex items-center justify-center bg-gray-100">
            <p className="text-gray-400">Cargando usuarios...</p>
        </div>
    );

    return (
        <div className="min-h-screen bg-gray-100">

            {/* Navbar */}
            <nav className="bg-emerald-700 text-white px-6 py-4 flex justify-between items-center">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/dashboard')}
                        className="text-emerald-300 hover:text-white text-sm transition-colors"
                    >
                        ← Dashboard
                    </button>
                    <span className="text-emerald-500">|</span>
                    <h1 className="font-bold">Gestión de Usuarios</h1>
                </div>
                <button
                    onClick={() => navigate('/users/new')}
                    className="bg-green-600 hover:bg-green-500 px-4 py-2 rounded-lg
                     text-sm font-medium transition-colors"
                >
                    + Nuevo usuario
                </button>
            </nav>

            <main className="p-6 max-w-6xl mx-auto">

                {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700
                          px-4 py-3 rounded-lg mb-4 text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                <div className="bg-white rounded-xl shadow-sm overflow-hidden">
                    <table className="w-full">
                        <thead>
                            <tr className="bg-gray-50 border-b border-gray-200">
                                <th className="text-left px-4 py-3 text-sm font-semibold text-gray-600">
                                    Usuario
                                </th>
                                <th className="text-left px-4 py-3 text-sm font-semibold text-gray-600">
                                    Email
                                </th>
                                <th className="text-left px-4 py-3 text-sm font-semibold text-gray-600">
                                    Roles
                                </th>
                                <th className="text-left px-4 py-3 text-sm font-semibold text-gray-600">
                                    Estado
                                </th>
                                <th className="text-left px-4 py-3 text-sm font-semibold text-gray-600">
                                    Último acceso
                                </th>
                                <th className="px-4 py-3"></th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {users.map(user => (
                                <tr key={user.id} className="hover:bg-gray-50 transition-colors">

                                    {/* Nombre y username */}
                                    <td className="px-4 py-3">
                                        <p className="font-medium text-gray-800">{user.fullName}</p>
                                        <p className="text-gray-400 text-xs">@{user.username}</p>
                                    </td>

                                    {/* Email */}
                                    <td className="px-4 py-3 text-sm text-gray-600">
                                        {user.email}
                                    </td>

                                    {/* Roles — modo edición o modo visualización */}
                                    <td className="px-4 py-3">
                                        {editingId === user.id ? (
                                            <div className="space-y-1">
                                                {ALL_ROLES.map(role => (
                                                    <label
                                                        key={role}
                                                        className="flex items-center gap-2 text-sm cursor-pointer"
                                                    >
                                                        <input
                                                            type="checkbox"
                                                            checked={editRoles.includes(role)}
                                                            onChange={() => toggleRole(role)}
                                                            className="rounded"
                                                        />
                                                        <span className={`px-2 py-0.5 rounded-full text-xs font-medium
                                            ${ROLE_LABELS[role].color}`}>
                                                            {ROLE_LABELS[role].label}
                                                        </span>
                                                    </label>
                                                ))}
                                            </div>
                                        ) : (
                                            <div className="flex flex-wrap gap-1">
                                                {user.roles.map(role => (
                                                    <span
                                                        key={role}
                                                        className={`px-2 py-0.5 rounded-full text-xs font-medium
                                       ${ROLE_LABELS[role]?.color ?? 'bg-gray-100 text-gray-600'}`}
                                                    >
                                                        {ROLE_LABELS[role]?.label ?? role}
                                                    </span>
                                                ))}
                                            </div>
                                        )}
                                    </td>

                                    {/* Estado */}
                                    <td className="px-4 py-3">
                                        <span className={`px-2 py-1 rounded-full text-xs font-medium
                      ${user.isActive
                                                ? 'bg-green-100 text-green-700'
                                                : 'bg-gray-100 text-gray-500'}`}>
                                            {user.isActive ? 'Activo' : 'Inactivo'}
                                        </span>
                                    </td>

                                    {/* Último acceso */}
                                    <td className="px-4 py-3 text-sm text-gray-500">
                                        {user.lastLogin
                                            ? new Date(user.lastLogin).toLocaleDateString('es-ES', {
                                                day: '2-digit', month: '2-digit', year: 'numeric',
                                                hour: '2-digit', minute: '2-digit'
                                            })
                                            : 'Nunca'}
                                    </td>

                                    {/* Acciones */}
                                    <td className="px-4 py-3">
                                        <div className="flex gap-2 justify-end">
                                            {editingId === user.id ? (
                                                <>
                                                    <button
                                                        onClick={() => saveRoles(user.id)}
                                                        disabled={savingId === user.id}
                                                        className="bg-emerald-700 hover:bg-emerald-600 text-white
                                       px-3 py-1.5 rounded-lg text-xs font-medium
                                       disabled:opacity-50 transition-colors"
                                                    >
                                                        {savingId === user.id ? 'Guardando...' : 'Guardar roles'}
                                                    </button>
                                                    <button
                                                        onClick={() => setEditingId(null)}
                                                        className="bg-gray-100 hover:bg-gray-200 text-gray-700
                                       px-3 py-1.5 rounded-lg text-xs transition-colors"
                                                    >
                                                        Cancelar
                                                    </button>
                                                </>
                                            ) : (
                                                <>
                                                    <button
                                                        onClick={() => startEditRoles(user)}
                                                        className="bg-emerald-50 hover:bg-emerald-100 text-emerald-700
                                       px-3 py-1.5 rounded-lg text-xs transition-colors"
                                                    >
                                                        Roles
                                                    </button>
                                                    <button
                                                        onClick={() => handleToggleActive(user)}
                                                        className={`px-3 py-1.5 rounded-lg text-xs transition-colors
                              ${user.isActive
                                                                ? 'bg-amber-50 hover:bg-amber-100 text-amber-700'
                                                                : 'bg-green-50 hover:bg-green-100 text-green-700'}`}
                                                    >
                                                        {user.isActive ? 'Desactivar' : 'Activar'}
                                                    </button>
                                                    <button
                                                        onClick={() => handleDelete(user)}
                                                        className="bg-red-50 hover:bg-red-100 text-red-600
                                       px-3 py-1.5 rounded-lg text-xs transition-colors"
                                                    >
                                                        Eliminar
                                                    </button>
                                                </>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    {users.length === 0 && (
                        <div className="text-center py-12 text-gray-400">
                            No hay usuarios registrados
                        </div>
                    )}
                </div>
            </main>
        </div>
    );
}