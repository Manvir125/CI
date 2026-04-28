import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getAuditLogs, exportAuditCsv, type AuditLogResponse
} from '../api/audit';

const ACTION_COLORS: Record<string, string> = {
    USER_LOGIN: 'bg-emerald-100 text-emerald-700',
    USER_CREATED: 'bg-green-100 text-green-700',
    USER_DELETED: 'bg-red-100 text-red-700',
    USER_ROLES_UPDATED: 'bg-purple-100 text-purple-700',
    USER_ACTIVATED: 'bg-green-100 text-green-700',
    USER_DEACTIVATED: 'bg-gray-100 text-gray-600',
    REQUEST_CREATED: 'bg-emerald-100 text-emerald-700',
    CONSENT_SIGNED: 'bg-green-100 text-green-700',
    CONSENT_REJECTED: 'bg-red-100 text-red-700',
    CONSENT_CANCELLED: 'bg-orange-100 text-orange-700',
    LINK_SENT: 'bg-teal-100 text-teal-700',
    PORTAL_ACCESSED: 'bg-yellow-100 text-yellow-700',
    IDENTITY_VERIFIED: 'bg-purple-100 text-purple-700',
};

export default function AuditPage() {
    const navigate = useNavigate();

    const [logs, setLogs] = useState<AuditLogResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Filtros
    const [filterActor, setFilterActor] = useState('');
    const [filterAction, setFilterAction] = useState('');
    const [filterFrom, setFilterFrom] = useState('');
    const [filterTo, setFilterTo] = useState('');

    // Fila expandida para ver el detailJson
    const [expandedId, setExpandedId] = useState<number | null>(null);

    const [exporting, setExporting] = useState(false);

    useEffect(() => { loadLogs(); }, [page]);

    const loadLogs = async () => {
        setLoading(true);
        try {
            const result = await getAuditLogs({
                actorId: filterActor || undefined,
                action: filterAction || undefined,
                from: filterFrom ? filterFrom + ':00' : undefined,
                to: filterTo ? filterTo + ':00' : undefined,
                page,
                size: 50,
            });
            setLogs(result.content);
            setTotalPages(result.totalPages);
            setTotalElements(result.totalElements);
        } catch {
            setError('Error al cargar los logs de auditoría');
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        loadLogs();
    };

    const handleExport = async () => {
        setExporting(true);
        try {
            await exportAuditCsv(
                filterFrom ? filterFrom + ':00' : undefined,
                filterTo ? filterTo + ':00' : undefined
            );
        } catch {
            setError('Error al exportar el CSV');
        } finally {
            setExporting(false);
        }
    };

    return (
        <div className="page-shell">
            <nav className="app-topbar">
                <button
                    onClick={() => navigate('/dashboard')}
                    className="soft-button-ghost text-sm transition-colors"
                >
                    ← Dashboard
                </button>
                <div>
                    <h1 className="font-bold text-lg">Auditoría</h1>
                    <p className="text-emerald-300 text-xs">
                        CI Digital — CHPC
                    </p>
                </div>
                <div className="flex items-center gap-4">
                    <button
                        onClick={handleExport}
                        disabled={exporting}
                        className="bg-green-600 hover:bg-green-500 disabled:opacity-50
                       px-4 py-2 rounded-lg text-sm font-medium transition-colors"
                    >
                        {exporting ? 'Exportando...' : '⬇️ Exportar CSV'}
                    </button>
                </div>
            </nav>

            <main className="page-main space-y-6">
                <section className="page-hero-lite">
                    <div>
                        <p className="section-kicker">Control</p>
                        <h2 className="page-hero-lite__title">Actividad del sistema con filtros y detalle</h2>
                        <p className="page-hero-lite__text">
                            Navega por eventos, filtra actores y exporta evidencia con una lectura más ligera.
                        </p>
                    </div>
                </section>

                {/* Filtros */}
                <form
                    onSubmit={handleSearch}
                    className="soft-form-card mb-6"
                >
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        <div>
                            <label className="block text-xs font-medium text-gray-600 mb-1">
                                Actor
                            </label>
                            <input
                                type="text"
                                value={filterActor}
                                onChange={e => setFilterActor(e.target.value)}
                                placeholder="admin, p.martinez..."
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           text-sm focus:outline-none focus:ring-2
                           focus:ring-emerald-500"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-gray-600 mb-1">
                                Acción
                            </label>
                            <select
                                value={filterAction}
                                onChange={e => setFilterAction(e.target.value)}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           text-sm focus:outline-none focus:ring-2
                           focus:ring-emerald-500"
                            >
                                <option value="">Todas</option>
                                <option value="USER_LOGIN">Login</option>
                                <option value="USER_CREATED">Usuario creado</option>
                                <option value="USER_ROLES_UPDATED">Roles actualizados</option>
                                <option value="REQUEST_CREATED">Solicitud creada</option>
                                <option value="LINK_SENT">Enlace enviado</option>
                                <option value="PORTAL_ACCESSED">Portal accedido</option>
                                <option value="IDENTITY_VERIFIED">Identidad verificada</option>
                                <option value="CONSENT_SIGNED">Consentimiento firmado</option>
                                <option value="CONSENT_REJECTED">Consentimiento rechazado</option>
                                <option value="CONSENT_CANCELLED">Consentimiento cancelado</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-gray-600 mb-1">
                                Desde
                            </label>
                            <input
                                type="datetime-local"
                                value={filterFrom}
                                onChange={e => setFilterFrom(e.target.value)}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           text-sm focus:outline-none focus:ring-2
                           focus:ring-emerald-500"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-gray-600 mb-1">
                                Hasta
                            </label>
                            <input
                                type="datetime-local"
                                value={filterTo}
                                onChange={e => setFilterTo(e.target.value)}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           text-sm focus:outline-none focus:ring-2
                           focus:ring-emerald-500"
                            />
                        </div>
                    </div>
                    <div className="flex justify-between items-center mt-4">
                        <p className="text-sm text-gray-500">
                            {totalElements} registros encontrados
                        </p>
                        <div className="flex gap-2">
                            <button
                                type="button"
                                onClick={() => {
                                    setFilterActor('');
                                    setFilterAction('');
                                    setFilterFrom('');
                                    setFilterTo('');
                                    setPage(0);
                                }}
                                className="soft-button-secondary text-sm px-4 py-2"
                            >
                                Limpiar
                            </button>
                            <button
                                type="submit"
                                className="soft-button text-sm px-4 py-2"
                            >
                                Filtrar
                            </button>
                        </div>
                    </div>
                </form>

                {error && (
                    <div className="surface-note surface-note--danger mb-4 text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                {/* Tabla */}
                <div className="soft-table-card overflow-hidden">
                    <table className="w-full text-sm">
                        <thead>
                            <tr>
                                <th className="text-left px-4 py-3 font-semibold text-gray-600
                               text-xs">
                                    Timestamp
                                </th>
                                <th className="text-left px-4 py-3 font-semibold text-gray-600
                               text-xs">
                                    Actor
                                </th>
                                <th className="text-left px-4 py-3 font-semibold text-gray-600
                               text-xs">
                                    Acción
                                </th>
                                <th className="text-left px-4 py-3 font-semibold text-gray-600
                               text-xs">
                                    Entidad
                                </th>
                                <th className="text-left px-4 py-3 font-semibold text-gray-600
                               text-xs">
                                    IP
                                </th>
                                <th className="text-left px-4 py-3 font-semibold text-gray-600
                               text-xs">
                                    Estado
                                </th>
                                <th className="px-4 py-3"></th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {loading ? (
                                <tr>
                                    <td colSpan={7} className="text-center py-12 text-gray-400">
                                        Cargando...
                                    </td>
                                </tr>
                            ) : logs.length === 0 ? (
                                <tr>
                                    <td colSpan={7} className="text-center py-12 text-gray-400">
                                        No hay registros
                                    </td>
                                </tr>
                            ) : logs.map(log => (
                                <>
                                    <tr
                                        key={log.id}
                                        className="hover:bg-gray-50 transition-colors"
                                    >
                                        <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                                            {new Date(log.timestampUtc).toLocaleString('es-ES')}
                                        </td>
                                        <td className="px-4 py-3 font-mono text-xs text-gray-700">
                                            {log.actorId}
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className={`px-2 py-0.5 rounded-full text-xs
                                        font-medium
                        ${ACTION_COLORS[log.action] ??
                                                'bg-gray-100 text-gray-600'}`}>
                                                {log.action}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-xs text-gray-500">
                                            {log.entityType && (
                                                <span>
                                                    {log.entityType}
                                                    {log.entityId && (
                                                        <span className="font-medium"> #{log.entityId}</span>
                                                    )}
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 font-mono text-xs text-gray-400">
                                            {log.ipAddress}
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className={`px-2 py-0.5 rounded-full text-xs
                        ${log.success
                                                    ? 'bg-green-100 text-green-700'
                                                    : 'bg-red-100 text-red-700'}`}>
                                                {log.success ? '✓' : '✗'}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3">
                                            {log.detailJson && (
                                                <button
                                                    onClick={() => setExpandedId(
                                                        expandedId === log.id ? null : log.id)}
                                                    className="text-emerald-500 hover:text-emerald-700
                                     text-xs transition-colors"
                                                >
                                                    {expandedId === log.id ? 'Ocultar' : 'Detalle'}
                                                </button>
                                            )}
                                        </td>
                                    </tr>

                                    {/* Fila expandida con detailJson */}
                                    {expandedId === log.id && log.detailJson && (
                                        <tr key={`${log.id}-detail`}
                                            className="bg-gray-50">
                                            <td colSpan={7} className="px-4 py-3">
                                                <pre className="text-xs text-gray-600 bg-gray-100
                                        rounded-lg p-3 overflow-x-auto">
                                                    {JSON.stringify(
                                                        JSON.parse(log.detailJson), null, 2)}
                                                </pre>
                                            </td>
                                        </tr>
                                    )}
                                </>
                            ))}
                        </tbody>
                    </table>
                </div>

                {/* Paginación */}
                {totalPages > 1 && (
                    <div className="flex justify-center gap-2 mt-6">
                        <button
                            disabled={page === 0}
                            onClick={() => setPage(p => p - 1)}
                            className="px-4 py-2 border border-gray-300 rounded-lg text-sm
                         disabled:opacity-40 hover:bg-gray-50 transition-colors"
                        >
                            ← Anterior
                        </button>
                        <span className="px-4 py-2 text-sm text-gray-600">
                            Página {page + 1} de {totalPages}
                        </span>
                        <button
                            disabled={page >= totalPages - 1}
                            onClick={() => setPage(p => p + 1)}
                            className="px-4 py-2 border border-gray-300 rounded-lg text-sm
                         disabled:opacity-40 hover:bg-gray-50 transition-colors"
                        >
                            Siguiente →
                        </button>
                    </div>
                )}
            </main>
        </div>
    );
}
