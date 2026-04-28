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
        <div className="page-shell">
            {/* Navbar */}
            <nav className="app-topbar">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/dashboard')}
                        className="soft-button-ghost text-sm"
                    >
                        ← Dashboard
                    </button>
                    <span className="text-emerald-200">|</span>
                    <h1 className="font-bold">Firmas pendientes</h1>
                </div>
            </nav>
            <main className="page-main max-w-5xl space-y-6">
                <section className="page-hero-lite">
                    <div>
                        <p className="section-kicker">Pendientes</p>
                        <h2 className="page-hero-lite__title">Firmas pendientes de mi servicio</h2>
                        <p className="page-hero-lite__text">
                            Revisa los consentimientos ya firmados por el paciente y completa tu firma profesional desde una vista más reposada.
                        </p>
                    </div>
                </section>

                {success && (
                    <div className="surface-note surface-note--success mb-4 text-sm flex justify-between">
                        <span>{success}</span>
                        <button onClick={() => setSuccess('')} className="font-bold">✕</button>
                    </div>
                )}
                {error && (
                    <div className="surface-note surface-note--danger mb-4 text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                {loading ? (
                    <div className="text-center py-16 text-gray-400">
                        Cargando...
                    </div>
                ) : requests.length === 0 ? (
                    <div className="soft-empty">
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
                                className="soft-list-card soft-list-item p-5"
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
                                            className="ml-4 soft-button text-sm disabled:opacity-50 flex gap-2 items-center"
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
