import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import SignaturePad from 'signature_pad';
import { getKioskToken } from '../api/consentRequests';
import { loadConsent, submitSignature, type PortalConsentDto } from '../api/portal';
import { useXPPenTablet, type PenEvent } from '../hooks/useXPPenTablet';

type Step = 'loading' | 'read' | 'sign' | 'confirmed' | 'rejected' | 'error';

export default function KioskSignPage() {
    const { requestId } = useParams<{ requestId: string }>();
    const navigate = useNavigate();

    const [step, setStep] = useState<Step>('loading');
    const [token, setToken] = useState('');
    const [consent, setConsent] = useState<PortalConsentDto | null>(null);
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // Lectura
    const [hasScrolled, setHasScrolled] = useState(false);
    const [readConfirmed, setReadConfirmed] = useState(false);
    const contentRef = useRef<HTMLDivElement>(null);

    // Firma
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const sigPadRef = useRef<SignaturePad | null>(null);
    const [isSigned, setIsSigned] = useState(false);

    // XP Pen tablet — last point for line drawing
    const lastPtRef = useRef<{ x: number; y: number } | null>(null);
    const penDownRef = useRef(false);

    // Handler for pen events from the XP Pen bridge
    const handlePenEvent = useCallback((evt: PenEvent) => {
        const canvas = canvasRef.current;
        if (!canvas || step !== 'sign') return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const rect = canvas.getBoundingClientRect();
        const cssX = (evt.x / evt.maxX) * rect.width;
        const cssY = (evt.y / evt.maxY) * rect.height;

        const pressure = evt.maxPressure > 0 ? evt.pressure / evt.maxPressure : 0.5;
        const penWidth = 1 + pressure * 3;

        switch (evt.status) {
            case 'Down':
                penDownRef.current = true;
                lastPtRef.current = { x: cssX, y: cssY };
                break;
            case 'Move':
                if (penDownRef.current && lastPtRef.current) {
                    ctx.save();
                    ctx.strokeStyle = 'rgb(0,0,0)';
                    ctx.lineWidth = penWidth;
                    ctx.lineCap = 'round';
                    ctx.lineJoin = 'round';
                    ctx.beginPath();
                    ctx.moveTo(lastPtRef.current.x, lastPtRef.current.y);
                    ctx.lineTo(cssX, cssY);
                    ctx.stroke();
                    ctx.restore();
                    lastPtRef.current = { x: cssX, y: cssY };
                    setIsSigned(true);
                }
                break;
            case 'Up':
            case 'Leave':
                penDownRef.current = false;
                lastPtRef.current = null;
                break;
        }
    }, [step]);

    const { device: xppenDevice, wsState: xppenState } = useXPPenTablet({
        onPenEvent: handlePenEvent,
        enabled: true,
    });

    // Rechazo
    const [showRejectModal, setShowRejectModal] = useState(false);
    const [rejectReason, setRejectReason] = useState('');

    // ── Carga el token de kiosco y luego el consentimiento ─────────────────
    useEffect(() => {
        const load = async () => {
            try {
                const rawToken = await getKioskToken(Number(requestId));
                setToken(rawToken);
                const data = await loadConsent(rawToken);
                setConsent(data);

                // Verifica si el contenido necesita scroll
                setTimeout(() => {
                    if (contentRef.current) {
                        const el = contentRef.current;
                        if (el.scrollHeight <= el.clientHeight + 10) {
                            setHasScrolled(true);
                        }
                    }
                }, 100);

                setStep('read');
            } catch {
                setStep('error');
                setError('Error al cargar el consentimiento');
            }
        };
        load();
    }, [requestId]);

    // ── Inicializa SignaturePad ─────────────────────────────────────────────
    useEffect(() => {
        if (step === 'sign' && canvasRef.current) {
            sigPadRef.current = new SignaturePad(canvasRef.current, {
                backgroundColor: 'rgb(255,255,255)',
                penColor: 'rgb(0,0,0)',
                minWidth: 1,
                maxWidth: 3,
            });
            resizeCanvas();
            sigPadRef.current.addEventListener('endStroke', () => {
                setIsSigned(!sigPadRef.current?.isEmpty());
            });

            if (xppenDevice.connected) {
                sigPadRef.current.off();
            }
        }
    }, [step]);

    // ── Toggle signature_pad on/off and block global gestures ──────────
    useEffect(() => {
        const preventGlobal = (e: TouchEvent) => {
            if (e.touches.length > 1 || (e.type === 'touchmove' && step === 'sign')) {
                e.preventDefault();
            }
        };

        document.body.style.touchAction = 'none';
        document.addEventListener('touchstart', preventGlobal as any, { passive: false });
        document.addEventListener('touchmove', preventGlobal as any, { passive: false });

        if (sigPadRef.current && step === 'sign') {
            if (xppenDevice.connected) {
                sigPadRef.current.off();
            } else {
                sigPadRef.current.on();
            }
        }

        return () => {
            document.body.style.touchAction = '';
            document.removeEventListener('touchstart', preventGlobal as any);
            document.removeEventListener('touchmove', preventGlobal as any);
        };
    }, [xppenDevice.connected, step]);

    // ── Detecta scroll al cambiar al paso read ─────────────────────────────
    useEffect(() => {
        if (step === 'read' && contentRef.current) {
            const el = contentRef.current;
            if (el.scrollHeight <= el.clientHeight + 10) {
                setHasScrolled(true);
            }
        }
    }, [step]);

    const resizeCanvas = () => {
        if (!canvasRef.current || !sigPadRef.current) return;
        const ratio = Math.max(window.devicePixelRatio || 1, 1);
        const canvas = canvasRef.current;
        canvas.width = canvas.offsetWidth * ratio;
        canvas.height = canvas.offsetHeight * ratio;
        canvas.getContext('2d')?.scale(ratio, ratio);
        sigPadRef.current.clear();
        setIsSigned(false);
    };

    const handleContentScroll = (e: React.UIEvent<HTMLDivElement>) => {
        const el = e.currentTarget;
        if (el.scrollHeight - el.scrollTop <= el.clientHeight + 50) {
            setHasScrolled(true);
        }
    };

    const handleSign = async () => {
        if (!isSigned || !canvasRef.current) return;
        setSubmitting(true);
        try {
            const imageBase64 = canvasRef.current.toDataURL('image/png');
            await submitSignature(token, imageBase64, readConfirmed, 'SIGNED');
            setStep('confirmed');
        } catch {
            setError('Error al enviar la firma');
        } finally {
            setSubmitting(false);
        }
    };

    const handleReject = async () => {
        if (!rejectReason.trim()) return;
        setSubmitting(true);
        try {
            await submitSignature(token, '', false, 'REJECTED', rejectReason);
            setStep('rejected');
        } catch {
            setError('Error al rechazar el consentimiento');
        } finally {
            setSubmitting(false);
            setShowRejectModal(false);
        }
    };

    // ── Pantallas de estado ────────────────────────────────────────────────
    if (step === 'loading') return (
        <div className="min-h-screen bg-emerald-950 flex items-center justify-center">
            <div className="text-center">
                <div className="w-12 h-12 border-4 border-white border-t-transparent
                        rounded-full animate-spin mx-auto mb-4" />
                <p className="text-emerald-300">Cargando documento...</p>
            </div>
        </div>
    );

    if (step === 'error') return (
        <div className="min-h-screen bg-emerald-950 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl p-8 max-w-md w-full text-center">
                <div className="text-5xl mb-4">❌</div>
                <h1 className="text-xl font-bold text-gray-800 mb-3">Error</h1>
                <p className="text-gray-500 text-sm mb-6">{error}</p>
                <button
                    onClick={() => navigate('/kiosk')}
                    className="w-full bg-emerald-900 text-white py-3 rounded-xl font-medium"
                >
                    Volver al inicio
                </button>
            </div>
        </div>
    );

    if (step === 'confirmed') return (
        <div className="min-h-screen bg-emerald-950 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl p-8 max-w-md w-full text-center">
                <div className="w-16 h-16 bg-green-100 rounded-full flex items-center
                        justify-center mx-auto mb-4">
                    <span className="text-3xl">✅</span>
                </div>
                <h1 className="text-xl font-bold text-gray-800 mb-2">
                    Consentimiento firmado
                </h1>
                <p className="text-gray-500 text-sm mb-6">
                    La firma ha sido registrada correctamente.
                </p>
                <div className="bg-gray-50 rounded-xl p-4 text-left text-sm space-y-2 mb-6">
                    <p className="text-gray-600">
                        <span className="font-medium">Procedimiento:</span>{' '}
                        {consent?.procedureName}
                    </p>
                    <p className="text-gray-600">
                        <span className="font-medium">Profesional:</span>{' '}
                        {consent?.professionalName}
                    </p>
                </div>
                <button
                    onClick={() => navigate('/kiosk')}
                    className="w-full bg-emerald-900 text-white py-3 rounded-xl
                     font-medium hover:bg-emerald-800 transition-colors"
                >
                    Finalizar
                </button>
            </div>
        </div>
    );

    if (step === 'rejected') return (
        <div className="min-h-screen bg-emerald-950 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl p-8 max-w-md w-full text-center">
                <div className="w-16 h-16 bg-orange-100 rounded-full flex items-center
                        justify-center mx-auto mb-4">
                    <span className="text-3xl">⚠️</span>
                </div>
                <h1 className="text-xl font-bold text-gray-800 mb-2">
                    Consentimiento rechazado
                </h1>
                <p className="text-gray-500 text-sm mb-6">
                    El rechazo ha sido registrado. El equipo médico ha sido notificado.
                </p>
                <button
                    onClick={() => navigate('/kiosk')}
                    className="w-full bg-emerald-900 text-white py-3 rounded-xl
                     font-medium hover:bg-emerald-800 transition-colors"
                >
                    Finalizar
                </button>
            </div>
        </div>
    );

    return (
        <div className="min-h-screen bg-emerald-950">

            {/* Cabecera */}
            <header className="bg-emerald-900 text-white px-4 py-4 sticky top-0 z-10
                         flex items-center justify-between">
                <div>
                    <p className="text-emerald-300 text-xs">
                        Consorci Hospitalari Provincial de Castelló
                    </p>
                    <h1 className="font-bold text-sm">
                        Consentimiento Informado — Firma Presencial
                    </h1>
                </div>
                <button
                    onClick={() => navigate('/kiosk')}
                    className="text-emerald-300 hover:text-white text-sm transition-colors"
                >
                    ✕ Cancelar
                </button>
            </header>

            <main className="max-w-2xl mx-auto p-4 pb-32">

                {/* ── PASO: Lectura ── */}
                {step === 'read' && consent && (
                    <div className="mt-4 space-y-4">

                        {/* Info del procedimiento */}
                        <div className="bg-white rounded-2xl p-5 shadow-sm">
                            <h2 className="font-bold text-gray-800 text-lg mb-3">
                                {consent.templateName}
                            </h2>
                            <div className="space-y-1 text-sm text-gray-600">
                                <p>👤 Paciente: <strong>{consent.patientName}</strong></p>
                                <p>👨‍⚕️ Profesional: <strong>{consent.professionalName}</strong></p>
                                <p>🏥 Servicio: <strong>{consent.serviceName}</strong></p>
                                <p>📅 Fecha: <strong>{consent.episodeDate}</strong></p>
                            </div>
                        </div>

                        {/* Documento */}
                        <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
                            <div className="bg-emerald-50 px-5 py-3 border-b border-emerald-100">
                                <p className="text-emerald-800 text-sm font-medium">
                                    📄 Lea el documento completo antes de continuar
                                </p>
                            </div>
                            <div
                                ref={contentRef}
                                onScroll={handleContentScroll}
                                className="p-5 overflow-y-auto prose prose-sm max-w-none"
                                style={{ maxHeight: '45vh', fontSize: '16px', lineHeight: '1.7' }}
                                dangerouslySetInnerHTML={{ __html: consent.contentHtml }}
                            />
                            {!hasScrolled && (
                                <div className="bg-amber-50 px-5 py-3 border-t border-amber-100
                                text-center">
                                    <p className="text-amber-700 text-xs">
                                        ↓ Desplázate hasta el final para continuar
                                    </p>
                                </div>
                            )}
                        </div>

                        {/* Checkbox */}
                        <div className={`bg-white rounded-2xl p-5 shadow-sm transition-opacity
                            ${hasScrolled ? 'opacity-100' : 'opacity-40'}`}>
                            <label className="flex items-start gap-3 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={readConfirmed}
                                    onChange={e => setReadConfirmed(e.target.checked)}
                                    disabled={!hasScrolled}
                                    className="w-5 h-5 mt-0.5 rounded"
                                />
                                <span className="text-sm text-gray-700 leading-relaxed">
                                    He leído y comprendido el contenido de este documento de
                                    consentimiento informado y he tenido la oportunidad de
                                    hacer preguntas sobre el procedimiento.
                                </span>
                            </label>
                        </div>

                        <div className="space-y-3">
                            <button
                                onClick={() => setStep('sign')}
                                disabled={!readConfirmed}
                                className="w-full bg-emerald-900 text-white py-4 rounded-xl
                           font-semibold text-base hover:bg-emerald-800
                           disabled:opacity-40 disabled:cursor-not-allowed
                           transition-colors"
                            >
                                Continuar para firmar
                            </button>
                            <button
                                onClick={() => setShowRejectModal(true)}
                                className="w-full bg-red-500 border border-red-500 text-white
                           py-4 rounded-xl font-medium text-base hover:bg-red-600
                           transition-colors"
                            >
                                No deseo firmar este consentimiento
                            </button>
                        </div>
                    </div>
                )}

                {/* ── PASO: Firma ── */}
                {step === 'sign' && (
                    <div className="mt-4 space-y-4">

                        <div className="bg-white rounded-2xl p-5 shadow-sm text-center">
                            <h2 className="font-bold text-gray-800 text-xl mb-2">
                                Firma el documento
                            </h2>
                            <p className="text-gray-500 text-sm">
                                Dibuja tu firma en el recuadro inferior.
                            </p>
                        </div>

                        {/* Tablet status indicator */}
                        {xppenState === 'open' && (
                            <div className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm ${xppenDevice.connected
                                ? 'bg-green-50 text-green-700 border border-green-200'
                                : 'bg-amber-50 text-amber-700 border border-amber-200'
                                }`}>
                                <span className={`w-2 h-2 rounded-full ${xppenDevice.connected ? 'bg-green-500' : 'bg-amber-500'
                                    }`} />
                                {xppenDevice.connected
                                    ? `✏️ Tableta ${xppenDevice.product ?? 'XP Pen'} conectada — firma con el lápiz`
                                    : '⚠️ Tableta desconectada — puedes firmar con el dedo o ratón'
                                }
                            </div>
                        )}

                        {/* Canvas */}
                        <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
                            <div className="bg-gray-50 px-4 py-2 border-b border-gray-200
                              flex justify-between items-center">
                                <span className="text-sm text-gray-500">Área de firma</span>
                                <button
                                    onClick={() => {
                                        sigPadRef.current?.clear();
                                        const canvas = canvasRef.current;
                                        if (canvas) {
                                            const ctx = canvas.getContext('2d');
                                            if (ctx) {
                                                ctx.fillStyle = 'rgb(255,255,255)';
                                                ctx.fillRect(0, 0, canvas.width, canvas.height);
                                            }
                                        }
                                        setIsSigned(false);
                                    }}
                                    className="text-sm text-emerald-600 hover:text-emerald-800"
                                >
                                    Borrar
                                </button>
                            </div>
                            <canvas
                                ref={canvasRef}
                                className="w-full touch-none"
                                style={{ height: '220px', cursor: 'crosshair' }}
                            />
                            {!isSigned && (
                                <div className="bg-gray-50 px-4 py-2 border-t border-gray-200
                                text-center">
                                    <p className="text-gray-400 text-xs">
                                        {xppenDevice.connected
                                            ? 'Firma en la tableta con el lápiz'
                                            : 'Firma aquí con el dedo, ratón o lápiz táctil'
                                        }
                                    </p>
                                </div>
                            )}
                        </div>

                        {error && (
                            <div className="bg-red-50 border border-red-200 text-red-700
                              px-4 py-3 rounded-xl text-sm text-center">
                                {error}
                            </div>
                        )}

                        <div className="space-y-3">
                            <button
                                onClick={handleSign}
                                disabled={!isSigned || submitting}
                                className="w-full bg-green-600 text-white py-4 rounded-xl
                           font-semibold text-base hover:bg-green-500
                           disabled:opacity-40 disabled:cursor-not-allowed
                           transition-colors"
                            >
                                {submitting ? 'Registrando firma...' : '✅ Confirmar y firmar'}
                            </button>
                            <button
                                onClick={() => setStep('read')}
                                disabled={submitting}
                                className="w-full bg-white border border-gray-300 text-gray-600
                           py-3 rounded-xl text-sm hover:bg-gray-50
                           transition-colors"
                            >
                                ← Volver al documento
                            </button>
                        </div>
                    </div>
                )}
            </main>

            {/* Modal de rechazo */}
            {showRejectModal && (
                <div className="fixed inset-0 bg-black bg-opacity-60 flex items-end
                        justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-xl mb-4">
                        <h3 className="font-bold text-gray-800 text-lg mb-2">
                            ¿Por qué no deseas firmar?
                        </h3>
                        <p className="text-gray-500 text-sm mb-4">
                            Explica brevemente el motivo.
                        </p>
                        <textarea
                            value={rejectReason}
                            onChange={e => setRejectReason(e.target.value)}
                            rows={3}
                            placeholder="Motivo del rechazo..."
                            className="w-full border border-gray-300 rounded-xl px-4 py-3
                         text-sm focus:outline-none focus:ring-2
                         focus:ring-emerald-500 mb-4"
                        />
                        <div className="space-y-2">
                            <button
                                onClick={handleReject}
                                disabled={submitting || !rejectReason.trim()}
                                className="w-full bg-red-600 text-white py-3 rounded-xl
                           font-medium disabled:opacity-50 transition-colors"
                            >
                                {submitting ? 'Enviando...' : 'Confirmar rechazo'}
                            </button>
                            <button
                                onClick={() => setShowRejectModal(false)}
                                className="w-full bg-gray-100 text-gray-700 py-3 rounded-xl
                           font-medium hover:bg-gray-200 transition-colors"
                            >
                                Cancelar
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}