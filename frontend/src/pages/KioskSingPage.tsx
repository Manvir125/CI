import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import SignaturePad from 'signature_pad';
import { getKioskToken, type ConsentRequestResponse } from '../api/consentRequests';
import { loadConsent, submitSignature, type PortalConsentDto } from '../api/portal';
import type { PatientDto } from '../api/his';
import { useXPPenTablet, type PenEvent } from '../hooks/useXPPenTablet';

type Step = 'loading' | 'read' | 'sign' | 'confirmed' | 'rejected' | 'error';

export default function KioskSignPage() {
    const { requestId } = useParams<{ requestId: string }>();
    const navigate = useNavigate();
    const location = useLocation();

    const navigationState = location.state as {
        patient?: PatientDto | null;
        request?: ConsentRequestResponse | null;
    } | null;
    const selectedPatient = navigationState?.patient ?? null;
    const selectedRequest = navigationState?.request ?? null;

    const [step, setStep] = useState<Step>('loading');
    const [token, setToken] = useState('');
    const [consent, setConsent] = useState<PortalConsentDto | null>(null);
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const [hasScrolled, setHasScrolled] = useState(false);
    const [readConfirmed, setReadConfirmed] = useState(false);
    const contentRef = useRef<HTMLDivElement>(null);

    const canvasRef = useRef<HTMLCanvasElement>(null);
    const sigPadRef = useRef<SignaturePad | null>(null);
    const [isSigned, setIsSigned] = useState(false);
    const [penEvents, setPenEvents] = useState<PenEvent[]>([]);

    const lastPtRef = useRef<{ x: number; y: number } | null>(null);
    const penDownRef = useRef(false);

    const handlePenEvent = useCallback((evt: PenEvent) => {
        setPenEvents(prev => [...prev, evt]);
        const canvas = canvasRef.current;
        if (!canvas || step !== 'sign') return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const rect = canvas.getBoundingClientRect();
        const cssX = (evt.x / evt.maxX) * rect.width;
        const cssY = (evt.y / evt.maxY) * rect.height;

        const pressure = evt.maxPressure > 0 ? evt.pressure / evt.maxPressure : 0.5;
        const minWidth = 1;
        const maxWidth = 6;
        const penWidth = minWidth + pressure * (maxWidth - minWidth);

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

    const [isRejecting, setIsRejecting] = useState(false);
    const [rejectReason, setRejectReason] = useState('');

    const fallbackPatientName = selectedPatient
        ? selectedPatient.fullName || `${selectedPatient.firstName} ${selectedPatient.lastName}`.trim()
        : '';
    const resolvedPatientName = consent?.patientName || fallbackPatientName || 'Paciente';
    const resolvedProcedureName = consent?.procedureName || selectedRequest?.templateName || 'Consentimiento';
    const resolvedProfessionalName = consent?.professionalName || selectedRequest?.professionalName || 'Profesional';

    useEffect(() => {
        const load = async () => {
            try {
                const rawToken = await getKioskToken(Number(requestId));
                setToken(rawToken);
                const data = await loadConsent(rawToken);
                setConsent(data);

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
    }, [step, xppenDevice.connected]);

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
        setPenEvents([]);
    };

    const handleContentScroll = (e: React.UIEvent<HTMLDivElement>) => {
        const el = e.currentTarget;
        if (el.scrollHeight - el.scrollTop <= el.clientHeight + 50) {
            setHasScrolled(true);
        }
    };

    const handleSign = async () => {
        if (!isSigned || !canvasRef.current) return;
        if (isRejecting && !rejectReason.trim()) return;

        setSubmitting(true);
        try {
            const imageBase64 = canvasRef.current.toDataURL('image/png');
            const confirmation = isRejecting ? 'REJECTED' : 'SIGNED';
            await submitSignature(token, imageBase64, readConfirmed, confirmation, isRejecting ? rejectReason : undefined, penEvents);

            if (isRejecting) {
                setStep('rejected');
            } else {
                setStep('confirmed');
            }
        } catch {
            setError('Error al enviar la firma');
        } finally {
            setSubmitting(false);
        }
    };

    if (step === 'loading') {
        return (
            <div className="page-loading">
                <div className="text-center">
                    <div className="w-12 h-12 border-4 border-[var(--green-strong)] border-t-transparent rounded-full animate-spin mx-auto mb-4" />
                    <p>Cargando documento...</p>
                </div>
            </div>
        );
    }

    if (step === 'error') {
        return (
            <div className="center-stage">
                <div className="soft-modal-card max-w-md w-full text-center">
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-rose-50 text-3xl">!</div>
                    <h1 className="text-2xl font-semibold text-[var(--text-main)]">No se pudo abrir el consentimiento</h1>
                    <p className="mt-3 text-sm text-[var(--text-soft)]">{error}</p>
                    <button
                        onClick={() => navigate('/kiosk')}
                        className="soft-button mt-6 w-full"
                    >
                        Volver al inicio
                    </button>
                </div>
            </div>
        );
    }

    if (step === 'confirmed') {
        return (
            <div className="center-stage">
                <div className="soft-modal-card max-w-md w-full text-center">
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-[var(--green-soft)] text-3xl text-[var(--green-strong)]">OK</div>
                    <h1 className="text-2xl font-semibold text-[var(--text-main)]">Consentimiento firmado</h1>
                    <p className="mt-3 text-sm text-[var(--text-soft)]">La firma ha sido registrada correctamente.</p>
                    <div className="soft-list-item mt-5 p-4 text-left text-sm text-[var(--text-soft)] space-y-2">
                        <p><strong>Procedimiento:</strong> {resolvedProcedureName}</p>
                        <p><strong>Profesional:</strong> {resolvedProfessionalName}</p>
                    </div>
                    <button
                        onClick={() => navigate('/kiosk')}
                        className="soft-button mt-6 w-full"
                    >
                        Finalizar
                    </button>
                </div>
            </div>
        );
    }

    if (step === 'rejected') {
        return (
            <div className="center-stage">
                <div className="soft-modal-card max-w-md w-full text-center">
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-amber-50 text-3xl text-amber-700">!</div>
                    <h1 className="text-2xl font-semibold text-[var(--text-main)]">Consentimiento rechazado</h1>
                    <p className="mt-3 text-sm text-[var(--text-soft)]">El rechazo ha sido registrado. El equipo medico ha sido notificado.</p>
                    <button
                        onClick={() => navigate('/kiosk')}
                        className="soft-button mt-6 w-full"
                    >
                        Finalizar
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="page-shell">
            <header className="app-topbar">
                <div className="app-topbar__brand">
                    <div className="app-topbar__mark">CH</div>
                    <div>
                        <p className="app-topbar__eyebrow">Kiosco Hospitalario</p>
                        <h1 className="app-topbar__title">Firma presencial asistida</h1>
                        <p className="app-topbar__subtitle">Lectura, aceptacion y firma del consentimiento</p>
                    </div>
                </div>
                <div className="app-topbar__actions">
                    <span className="app-pill">{resolvedPatientName}</span>
                    <button
                        onClick={() => navigate('/kiosk')}
                        className="soft-button-secondary"
                    >
                        Cancelar
                    </button>
                </div>
            </header>

            <main className="page-main max-w-5xl">
                {step === 'read' && consent && (
                    <div className="space-y-5">
                        <section className="page-hero-lite">
                            <div>
                                <p className="section-kicker">Documento preparado</p>
                                <h2 className="page-hero-lite__title">{consent.templateName}</h2>
                                <p className="page-hero-lite__text">
                                    Revise toda la informacion antes de confirmar. El sistema habilitara la siguiente accion cuando se haya alcanzado el final del documento.
                                </p>
                            </div>
                            <span className="soft-badge">Firma presencial</span>
                        </section>

                        <section className="soft-list-card">
                            <div className="grid gap-3 md:grid-cols-2 text-sm text-[var(--text-soft)]">
                                <div className="soft-list-item p-4">
                                    <p><strong>Paciente:</strong> {resolvedPatientName}</p>
                                    <p className="mt-1"><strong>Profesional:</strong> {resolvedProfessionalName}</p>
                                    <p className="mt-1"><strong>Servicio:</strong> {consent.serviceName}</p>
                                </div>
                                <div className="soft-list-item p-4">
                                    <p><strong>Procedimiento:</strong> {resolvedProcedureName}</p>
                                    <p className="mt-1"><strong>Fecha:</strong> {consent.episodeDate}</p>
                                    {selectedPatient?.nhc && <p className="mt-1"><strong>NHC:</strong> {selectedPatient.nhc}</p>}
                                </div>
                            </div>
                        </section>

                        <section className="soft-list-card overflow-hidden">
                            <div className="flex items-center justify-between gap-3 border-b border-[var(--line-soft)] bg-[var(--green-pale)] px-5 py-3">
                                <p className="font-medium text-[var(--green-strong)]">Lea el documento completo antes de continuar</p>
                                {!hasScrolled && <span className="soft-badge">Desplazate hasta el final</span>}
                            </div>

                            {consent.isGroup && consent.groupDocuments && consent.groupDocuments.length > 0 ? (
                                <div
                                    ref={contentRef}
                                    onScroll={handleContentScroll}
                                    className="p-5 overflow-y-auto max-w-none space-y-8"
                                    style={{ maxHeight: '45vh', fontSize: '16px', lineHeight: '1.7' }}
                                >
                                    {consent.groupDocuments.map((doc, idx) => (
                                        <div key={idx}>
                                            <div
                                                className="prose prose-sm max-w-none"
                                                dangerouslySetInnerHTML={{ __html: doc }}
                                            />
                                            {idx < (consent.groupDocuments?.length ?? 0) - 1 && (
                                                <hr className="my-8 border-t-2 border-dashed border-[var(--line-strong)]" />
                                            )}
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <div
                                    ref={contentRef}
                                    onScroll={handleContentScroll}
                                    className="p-5 overflow-y-auto prose prose-sm max-w-none"
                                    style={{ maxHeight: '45vh', fontSize: '16px', lineHeight: '1.7' }}
                                    dangerouslySetInnerHTML={{ __html: consent.contentHtml }}
                                />
                            )}

                            {!hasScrolled && (
                                <div className="surface-note surface-note--warn rounded-none border-x-0 border-b-0 text-center">
                                    Desplazate hasta el final para continuar.
                                </div>
                            )}
                        </section>

                        <section className={`soft-form-card transition-opacity ${hasScrolled ? 'opacity-100' : 'opacity-50'}`}>
                            <label className="flex items-start gap-3 cursor-pointer text-sm text-[var(--text-soft)] leading-relaxed">
                                <input
                                    type="checkbox"
                                    checked={readConfirmed}
                                    onChange={e => setReadConfirmed(e.target.checked)}
                                    disabled={!hasScrolled}
                                    className="mt-1 h-5 w-5 rounded"
                                />
                                <span>
                                    He leido y comprendido el contenido de este documento de consentimiento informado y he tenido la oportunidad de hacer preguntas sobre el procedimiento.
                                </span>
                            </label>
                        </section>

                        <div className="grid gap-3 md:grid-cols-2">
                            <button
                                onClick={() => {
                                    setIsRejecting(false);
                                    setStep('sign');
                                }}
                                disabled={!readConfirmed}
                                className="soft-button disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                Continuar para firmar
                            </button>
                            <button
                                onClick={() => {
                                    setIsRejecting(true);
                                    setStep('sign');
                                }}
                                className="soft-button-secondary border-rose-200 text-rose-700"
                            >
                                No deseo firmar este consentimiento
                            </button>
                        </div>
                    </div>
                )}

                {step === 'sign' && (
                    <div className="space-y-5">
                        <section className="page-hero-lite">
                            <div>
                                <p className="section-kicker">{isRejecting ? 'Rechazo' : 'Firma'}</p>
                                <h2 className="page-hero-lite__title">
                                    {isRejecting ? 'Registrar rechazo del documento' : 'Registrar firma del documento'}
                                </h2>
                                <p className="page-hero-lite__text">
                                    {isRejecting
                                        ? 'Debe firmar para dejar constancia de que ha declinado este consentimiento.'
                                        : 'Dibuja la firma en el recuadro inferior usando la tableta, el dedo o el raton.'}
                                </p>
                            </div>
                            <span className="soft-badge">{xppenDevice.connected ? 'Tableta conectada' : 'Modo tactil'}</span>
                        </section>

                        {isRejecting && (
                            <section className="soft-form-card">
                                <label className="block text-sm font-medium text-[var(--text-soft)] mb-2">
                                    Motivo del rechazo
                                </label>
                                <textarea
                                    value={rejectReason}
                                    onChange={e => setRejectReason(e.target.value)}
                                    rows={3}
                                    placeholder="Explique el motivo del rechazo..."
                                    className="w-full px-4 py-3 text-sm"
                                />
                            </section>
                        )}

                        {xppenState === 'open' && (
                            <div className={`surface-note ${xppenDevice.connected ? 'surface-note--success' : 'surface-note--warn'}`}>
                                {xppenDevice.connected
                                    ? `Tableta ${xppenDevice.product ?? 'XP Pen'} conectada. Puede firmar con el lapiz.`
                                    : 'Tableta desconectada. Puede firmar con el dedo o el raton.'}
                            </div>
                        )}

                        <section className="soft-list-card overflow-hidden">
                            <div className="flex items-center justify-between gap-3 border-b border-[var(--line-soft)] bg-[var(--green-pale)] px-4 py-3">
                                <span className="text-sm font-medium text-[var(--text-soft)]">Area de firma</span>
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
                                        setPenEvents([]);
                                    }}
                                    className="soft-subtle-button"
                                >
                                    Borrar
                                </button>
                            </div>
                            <canvas
                                ref={canvasRef}
                                className="w-full touch-none bg-white"
                                style={{ height: '220px', cursor: 'crosshair' }}
                            />
                            {!isSigned && (
                                <div className="border-t border-[var(--line-soft)] bg-slate-50 px-4 py-3 text-center text-xs text-[var(--text-faint)]">
                                    {xppenDevice.connected
                                        ? 'Firma en la tableta con el lapiz.'
                                        : 'Firma aqui con el dedo, raton o lapiz tactil.'}
                                </div>
                            )}
                        </section>

                        {error && (
                            <div className="surface-note surface-note--danger text-center">
                                {error}
                            </div>
                        )}

                        <div className="grid gap-3 md:grid-cols-2">
                            <button
                                onClick={handleSign}
                                disabled={!isSigned || submitting}
                                className="soft-button disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                {submitting ? 'Registrando firma...' : 'Confirmar y firmar'}
                            </button>
                            <button
                                onClick={() => setStep('read')}
                                disabled={submitting}
                                className="soft-button-secondary"
                            >
                                Volver al documento
                            </button>
                        </div>
                    </div>
                )}
            </main>
        </div>
    );
}
