import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import StatusBadge from '../components/StatusBadge';
import {
    getPendingMySignature, professionalSign,
    type ConsentRequestResponse
} from '../api/consentRequests';
import { professionalSignWithCert } from '../api/consentRequests';
import { getSignatureStatus } from '../api/professionalSignature';

export default function PendingSignaturesPage() {
    const [requests, setRequests] = useState<ConsentRequestResponse[]>([]);
    const [signatureMethod, setSignatureMethod] = useState<'TABLET' | 'CERTIFICATE'>('TABLET');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [signing, setSigning] = useState<number | null>(null);
    const [success, setSuccess] = useState('');
    const [hasSignature, setHasSignature] = useState(false);

    useEffect(() => { 
        loadPending();
        loadSignatureMethod();
    }, []);

    const navigate = useNavigate();

    const loadSignatureMethod = async () => {
        try {
            const status = await getSignatureStatus();
            if (status.signatureMethod) {
                setSignatureMethod(status.signatureMethod);
            }
            setHasSignature(status.hasSignature);
        } catch (e) {
            console.error("Error al cargar la preferencia de firma", e);
        }
    }

    const loadPending = async () => {
        setLoading(true);
        try {
            setRequests(await getPendingMySignature());
        } catch (e: any) {
            setError(e?.response?.data?.message ||
                'Error al cargar las firmas pendientes');
        } finally {
            setLoading(false);
        }
    };

    const handleSign = async (id: number) => {
        if (signatureMethod === 'TABLET' && !hasSignature) {
            setError('No tienes una firma predeterminada configurada. Ve a tu perfil para configurar una firma con tableta.');
            return;
        }

        setSigning(id);
        setError('');
        try {
            if (signatureMethod === 'CERTIFICATE') {
                await professionalSignWithCert(id);
            } else {
                await professionalSign(id);
            }
            setSuccess('Consentimiento firmado correctamente');
            await loadPending();
        } catch (e: any) {
            setError(e.message || e?.response?.data?.message || 'Error al firmar');
        } finally {
            setSigning(null);
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
                    <span className="text-emerald-500">|</span>
                    <h1 className="font-bold">Firmas pendientes</h1>
                </div>
            </nav>
            <main className="p-6 max-w-4xl mx-auto">
                <div className="mb-6">
                    <h2 className="text-xl font-bold text-gray-800">
                        Firmas pendientes de mi servicio
                    </h2>
                    <p className="text-gray-500 text-sm mt-1">
                        Consentimientos firmados por el paciente que requieren
                        tu firma como responsable del servicio.
                    </p>
                </div>

                {success && (
                    <div className="bg-green-50 border border-green-200 text-green-700
                          px-4 py-3 rounded-lg mb-4 text-sm flex justify-between">
                        <span>{success}</span>
                        <button onClick={() => setSuccess('')} className="font-bold">✕</button>
                    </div>
                )}
                {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700
                          px-4 py-3 rounded-lg mb-4 text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                {loading ? (
                    <div className="text-center py-16 text-gray-400">
                        Cargando...
                    </div>
                ) : requests.length === 0 ? (
                    <div className="bg-white rounded-xl p-16 text-center shadow-sm">
                        <p className="text-4xl mb-4">✅</p>
                        <p className="text-gray-500">
                            No tienes consentimientos pendientes de firma
                        </p>
                    </div>
                ) : (
                    <div className="space-y-4">
                        {requests.map(req => (
                            <div
                                key={req.id}
                                className="bg-white rounded-xl p-5 shadow-sm border
                           border-gray-200"
                            >
                                <div className="flex justify-between items-start">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-3 mb-2">
                                            <span className="font-semibold text-gray-800">
                                                {req.templateName}
                                            </span>
                                            <StatusBadge status={req.status} />
                                            {req.professionalSigned ? (
                                                <span className="bg-green-100 text-green-700 text-xs
                                         px-2 py-0.5 rounded-full">
                                                    ✓ Firmado por ti
                                                </span>
                                            ) : (
                                                <span className="bg-orange-100 text-orange-700 text-xs
                                         px-2 py-0.5 rounded-full">
                                                    ⏳ Pendiente tu firma
                                                </span>
                                            )}
                                        </div>

                                        <div className="flex gap-4 text-sm text-gray-500">
                                            <span>NHC: <strong>{req.nhc}</strong></span>
                                            <span>Episodio: <strong>{req.episodeId}</strong></span>
                                            <span>
                                                Servicio responsable:{' '}
                                                <strong>{req.responsibleService}</strong>
                                            </span>
                                            {req.assignedProfessionalName && (
                                                <span>
                                                    Asignado a:{' '}
                                                    <strong>{req.assignedProfessionalName}</strong>
                                                </span>
                                            )}
                                        </div>

                                        <p className="text-xs text-gray-400 mt-1">
                                            Firmado por el paciente el{' '}
                                            {new Date(req.updatedAt).toLocaleDateString('es-ES', {
                                                day: '2-digit', month: '2-digit', year: 'numeric',
                                                hour: '2-digit', minute: '2-digit'
                                            })}
                                        </p>
                                    </div>

                                    {!req.professionalSigned && (
                                        <button
                                            onClick={() => handleSign(req.id)}
                                            disabled={signing === req.id}
                                            className="ml-4 bg-emerald-700 text-white px-5 py-2
                                 rounded-lg text-sm font-medium
                                 hover:bg-emerald-800 disabled:opacity-50
                                 transition-colors flex gap-2 items-center"
                                        >
                                            {signing === req.id ? 'Firmando...' : 
                                                signatureMethod === 'CERTIFICATE' ? '📄 Firmar con Certificado' : '✍️ Firmar con Tableta'}
                                        </button>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </main>
        </div>
    );
}
