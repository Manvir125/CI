import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import StatusBadge from '../components/StatusBadge';
import {
    getMyRequests, cancelRequest,
    type ConsentRequestResponse, sendRequest, downloadPdf
} from '../api/consentRequests';

const STATUSES = [
    { value: '', label: 'Todas' },
    { value: 'PENDING', label: 'Pendientes' },
    { value: 'SENT', label: 'Enviadas' },
    { value: 'SIGNED', label: 'Firmadas' },
    { value: 'REJECTED', label: 'Rechazadas' },
    { value: 'EXPIRED', label: 'Expiradas' },
    { value: 'CANCELLED', label: 'Canceladas' },
];

export default function RequestsPage() {
    const navigate = useNavigate();

    const [requests, setRequests] = useState<ConsentRequestResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [totalPages, setTotalPages] = useState(0);
    const [page, setPage] = useState(0);

    // Modal de cancelación
    const [cancelId, setCancelId] = useState<number | null>(null);
    const [cancelReason, setCancelReason] = useState('');
    const [cancelling, setCancelling] = useState(false);

    useEffect(() => {
        loadRequests();
    }, [statusFilter, page]);

    const loadRequests = async () => {
        setLoading(true);
        try {
            const result = await getMyRequests(statusFilter || undefined, page, 15);
            setRequests(result.content);
            setTotalPages(result.totalPages);
        } catch {
            setError('Error al cargar las solicitudes');
        } finally {
            setLoading(false);
        }
    };

    const handleSend = async (id: number) => {
        try {
            await sendRequest(id);
            loadRequests();
        } catch {
            setError('Error al enviar el enlace');
        }
    };

    const handleCancel = async () => {
        if (!cancelId || !cancelReason.trim()) return;
        setCancelling(true);
        try {
            await cancelRequest(cancelId, cancelReason);
            setCancelId(null);
            setCancelReason('');
            loadRequests();
        } catch {
            setError('Error al cancelar la solicitud');
        } finally {
            setCancelling(false);
        }
    };
    const handleDownloadPdf = async (id: number) => {
        try {
            await downloadPdf(id);
        } catch {
            setError('Error al descargar el PDF');
        }
    };

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
                    <span className="text-blue-500">|</span>
                    <h1 className="font-bold">Gestión de Solicitudes</h1>
                </div>
                <button
                    onClick={() => navigate('/requests/new')}
                    className="bg-green-600 hover:bg-green-500 px-4 py-2 rounded-lg
                       text-sm font-medium transition-colors"
                >
                    + Nueva solicitud
                </button>
            </nav>

            <main className="p-6 max-w-6xl mx-auto">
                {/* Filtros por estado */}
                <div className="flex gap-2 mb-6 flex-wrap">
                    {STATUSES.map(s => (
                        <button
                            key={s.value}
                            onClick={() => { setStatusFilter(s.value); setPage(0); }}
                            className={`px-4 py-1.5 rounded-full text-sm font-medium
                          transition-colors border
                ${statusFilter === s.value
                                    ? 'bg-emerald-700 text-white border-emerald-700'
                                    : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'}`}
                        >
                            {s.label}
                        </button>
                    ))}
                </div>

                {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700
                          px-4 py-3 rounded-lg mb-4 text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                {loading ? (
                    <div className="text-center py-16 text-gray-400">
                        Cargando solicitudes...
                    </div>
                ) : requests.length === 0 ? (
                    <div className="bg-white rounded-xl p-16 text-center shadow-sm">
                        <p className="text-gray-400 text-lg mb-4">
                            No hay solicitudes
                            {statusFilter ? ` con estado "${statusFilter}"` : ''}
                        </p>
                        <button
                            onClick={() => navigate('/requests/new')}
                            className="bg-emerald-700 text-white px-6 py-2 rounded-lg
                         hover:bg-emerald-600 transition-colors"
                        >
                            Crear primera solicitud
                        </button>
                    </div>
                ) : (
                    <>
                        <div className="space-y-3">
                            {requests.map(req => (
                                <div
                                    key={req.id}
                                    className="bg-white rounded-xl p-5 shadow-sm border border-gray-200
                             hover:shadow-md transition-shadow"
                                >
                                    <div className="flex justify-between items-start">
                                        <div className="flex-1">
                                            <div className="flex items-center gap-3 mb-1">
                                                <span className="font-semibold text-gray-800">
                                                    {req.templateName}
                                                </span>
                                                <StatusBadge status={req.status} />
                                                <span className={`text-xs px-2 py-0.5 rounded-full
                          ${req.channel === 'REMOTE'
                                                        ? 'bg-purple-100 text-purple-700'
                                                        : 'bg-orange-100 text-orange-700'}`}>
                                                    {req.channel === 'REMOTE' ? '📱 Remota' : '🖊️ Presencial'}
                                                </span>
                                            </div>
                                            <div className="flex gap-4 text-sm text-gray-500">
                                                <span>NHC: <strong>{req.nhc}</strong></span>
                                                <span>Episodio: <strong>{req.episodeId}</strong></span>
                                                {req.patientEmail && (
                                                    <span>✉️ {req.patientEmail}</span>
                                                )}
                                                <span>
                                                    📅 {new Date(req.createdAt).toLocaleDateString('es-ES', {
                                                        day: '2-digit', month: '2-digit', year: 'numeric',
                                                        hour: '2-digit', minute: '2-digit'
                                                    })}
                                                </span>
                                            </div>
                                            {req.cancellationReason && (
                                                <p className="text-xs text-red-500 mt-1">
                                                    Motivo: {req.cancellationReason}
                                                </p>
                                            )}
                                        </div>

                                        {/* Acciones */}
                                        <div className="flex gap-2 ml-4">
                                            {req.status === 'SIGNED' && (
                                                <button
                                                    onClick={() => handleDownloadPdf(req.id)}
                                                    className="bg-teal-50 hover:bg-teal-100 text-teal-700
                                                    px-3 py-1.5 rounded-lg text-xs transition-colors"
                                                >
                                                    📄 PDF
                                                </button>
                                            )}
                                            {req.status === 'PENDING' && req.channel === 'REMOTE' && (
                                                <button
                                                    onClick={() => handleSend(req.id)}
                                                    className="bg-emerald-50 hover:bg-emerald-100 text-emerald-700
                                                    px-3 py-1.5 rounded-lg text-xs transition-colors"
                                                >
                                                    Enviar enlace
                                                </button>
                                            )}
                                            {req.status === 'SENT' && req.channel === 'REMOTE' && (
                                                <button
                                                    onClick={() => handleSend(req.id)}
                                                    className="bg-gray-100 hover:bg-gray-200 text-gray-700
                                                    px-3 py-1.5 rounded-lg text-xs transition-colors"
                                                >
                                                    Reenviar
                                                </button>
                                            )}
                                            {['PENDING', 'SENT'].includes(req.status) && (
                                                <button
                                                    onClick={() => setCancelId(req.id)}
                                                    className="bg-red-50 hover:bg-red-100 text-red-600
                                                    px-3 py-1.5 rounded-lg text-xs transition-colors"
                                                >
                                                    Cancelar
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ))}
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
                    </>
                )}
            </main>

            {/* Modal de cancelación */}
            {cancelId && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center
                        justify-center z-50 p-4">
                    <div className="bg-white rounded-xl p-6 w-full max-w-md shadow-xl">
                        <h3 className="font-bold text-gray-800 text-lg mb-4">
                            Cancelar solicitud
                        </h3>
                        <p className="text-gray-600 text-sm mb-4">
                            Indica el motivo de la cancelación. Este campo es obligatorio
                            y quedará registrado en el log de auditoría.
                        </p>
                        <textarea
                            value={cancelReason}
                            onChange={e => setCancelReason(e.target.value)}
                            rows={3}
                            placeholder="Motivo de la cancelación..."
                            className="w-full border border-gray-300 rounded-lg px-3 py-2
                         focus:outline-none focus:ring-2 focus:ring-blue-500
                         text-sm mb-4"
                        />
                        <div className="flex gap-3 justify-end">
                            <button
                                onClick={() => { setCancelId(null); setCancelReason(''); }}
                                className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700
                           hover:bg-gray-50 transition-colors text-sm"
                            >
                                Volver
                            </button>
                            <button
                                onClick={handleCancel}
                                disabled={cancelling || !cancelReason.trim()}
                                className="px-4 py-2 bg-red-600 text-white rounded-lg text-sm
                           hover:bg-red-500 disabled:opacity-50 transition-colors"
                            >
                                {cancelling ? 'Cancelando...' : 'Confirmar cancelación'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}