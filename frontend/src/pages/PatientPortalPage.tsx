import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import SignaturePad from 'signature_pad';
import {
    loadConsent, verifyCode, sendCode, resendCode,
    submitSignature, type PortalConsentDto
} from '../api/portal';

type Step = 'loading' | 'verify' | 'read' | 'sign' | 'confirmed' | 'rejected' | 'error';

export default function PatientPortalPage() {
    const { token } = useParams<{ token: string }>();

    const [step, setStep] = useState<Step>('loading');
    const [consent, setConsent] = useState<PortalConsentDto | null>(null);
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const [smsCode, setSmsCode] = useState('');
    const [verifyError, setVerifyError] = useState('');
    const [resending, setResending] = useState(false);
    const [resendCooldown, setResendCooldown] = useState(0);

    const [hasScrolled, setHasScrolled] = useState(false);
    const [readConfirmed, setReadConfirmed] = useState(false);
    const contentRef = useRef<HTMLDivElement>(null);

    const canvasRef = useRef<HTMLCanvasElement>(null);
    const sigPadRef = useRef<SignaturePad | null>(null);
    const [isSigned, setIsSigned] = useState(false);

    const [isRejecting, setIsRejecting] = useState(false);
    const [rejectReason, setRejectReason] = useState('');

    useEffect(() => {
        if (resendCooldown <= 0) return;
        const timer = setTimeout(() => setResendCooldown(c => c - 1), 1000);
        return () => clearTimeout(timer);
    }, [resendCooldown]);

    useEffect(() => {
        if (!token) { setStep('error'); return; }

        loadConsent(token)
            .then(async data => {
                setConsent(data);
                if (data.status === 'SIGNED') {
                    setStep('confirmed');
                } else if (data.status === 'REJECTED') {
                    setStep('rejected');
                } else {
                    await sendCode(token);
                    setResendCooldown(60);
                    setStep('verify');
                }
            })
            .catch(() => setStep('error'));
    }, [token]);

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
        }
    }, [step]);

    useEffect(() => {
        if (step === 'read' && contentRef.current) {
            const el = contentRef.current;
            const needsScroll = el.scrollHeight > el.clientHeight + 10;
            if (!needsScroll) {
                setHasScrolled(true);
            }
        }
    }, [step, consent]);

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
        const atBottom = el.scrollHeight - el.scrollTop <= el.clientHeight + 50;
        if (atBottom) setHasScrolled(true);
    };

    const handleVerify = async (e: React.FormEvent) => {
        e.preventDefault();
        setVerifyError('');
        setSubmitting(true);
        try {
            const result = await verifyCode(token!, smsCode);
            if (result.success) {
                setStep('read');
            } else {
                setVerifyError('Codigo incorrecto. Comprueba el SMS e intentalo de nuevo.');
                setSmsCode('');
            }
        } catch (err: any) {
            setStep('error');
            setError(err?.response?.data?.message || 'Error al verificar el codigo');
        } finally {
            setSubmitting(false);
        }
    };

    const handleResend = async () => {
        setResending(true);
        try {
            await resendCode(token!);
            setResendCooldown(60);
            setVerifyError('');
            setSmsCode('');
        } catch {
            setVerifyError('Error al reenviar el codigo');
        } finally {
            setResending(false);
        }
    };

    const handleSign = async () => {
        if (!sigPadRef.current || sigPadRef.current.isEmpty()) return;
        if (isRejecting && !rejectReason.trim()) return;

        setSubmitting(true);
        try {
            const imageBase64 = sigPadRef.current.toDataURL('image/png');
            const confirmation = isRejecting ? 'REJECTED' : 'SIGNED';
            await submitSignature(token!, imageBase64, readConfirmed, confirmation, isRejecting ? rejectReason : undefined);

            if (isRejecting) {
                setStep('rejected');
            } else {
                setStep('confirmed');
            }
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al enviar la firma');
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
                    <h1 className="text-2xl font-semibold text-[var(--text-main)]">Enlace no valido</h1>
                    <p className="mt-3 text-sm text-[var(--text-soft)]">
                        {error || 'Este enlace ha expirado o no es valido. Contacta con el hospital si crees que es un error.'}
                    </p>
                    <p className="mt-6 text-xs text-[var(--text-faint)]">CHPC · Tel. 964 25 94 00</p>
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
                    <p className="mt-3 text-sm text-[var(--text-soft)]">
                        Tu firma ha sido registrada correctamente. Recibiras una copia del documento en tu email.
                    </p>
                    <div className="soft-list-item mt-5 p-4 text-left text-sm text-[var(--text-soft)] space-y-2">
                        <p><strong>Procedimiento:</strong> {consent?.procedureName}</p>
                        <p><strong>Profesional:</strong> {consent?.professionalName}</p>
                        <p><strong>Servicio:</strong> {consent?.serviceName}</p>
                    </div>
                    <p className="mt-6 text-xs text-[var(--text-faint)]">Consorci Hospitalari Provincial de Castello</p>
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
                    <p className="mt-3 text-sm text-[var(--text-soft)]">
                        Has rechazado firmar este consentimiento. El equipo medico ha sido notificado y contactara contigo para explicarte el procedimiento.
                    </p>
                    <p className="mt-6 text-xs text-[var(--text-faint)]">Si tienes dudas llama al 964 25 94 00</p>
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
                        <p className="app-topbar__eyebrow">Portal del Paciente</p>
                        <h1 className="app-topbar__title">Consentimiento informado digital</h1>
                        <p className="app-topbar__subtitle">Validacion por SMS y firma remota segura</p>
                    </div>
                </div>
                <div className="app-topbar__actions">
                    {consent?.maskedPhone && <span className="app-pill">{consent.maskedPhone}</span>}
                </div>
            </header>

            <main className="page-main max-w-5xl">
                {step === 'verify' && consent && (
                    <section className="max-w-3xl mx-auto space-y-5">
                        <div className="request-hero">
                            <p className="section-kicker">Verificacion previa</p>
                            <h2 className="page-hero-lite__title">Confirma tu identidad antes de acceder al documento</h2>
                            <p className="page-hero-lite__text">
                                Hemos enviado un codigo de 6 digitos al telefono asociado al paciente. Introducelo para continuar con la lectura y firma.
                            </p>
                            <div className="request-hero__stats">
                                <div className="request-hero__stat">
                                    <span className="request-hero__value">{consent.maskedPhone}</span>
                                    <span className="request-hero__label">Telefono de verificacion</span>
                                </div>
                                <div className="request-hero__stat">
                                    <span className="request-hero__value">{resendCooldown > 0 ? `${resendCooldown}s` : 'Listo'}</span>
                                    <span className="request-hero__label">Estado de reenvio</span>
                                </div>
                            </div>
                        </div>

                        <div className="soft-form-card">
                            <form onSubmit={handleVerify} className="space-y-5">
                                <div>
                                    <label className="block text-sm font-medium text-[var(--text-soft)] mb-2 text-center">
                                        Introduce el codigo recibido
                                    </label>
                                    <input
                                        type="text"
                                        inputMode="numeric"
                                        pattern="[0-9]*"
                                        maxLength={6}
                                        value={smsCode}
                                        onChange={e => setSmsCode(e.target.value.replace(/\D/g, ''))}
                                        placeholder="_ _ _ _ _ _"
                                        className="w-full px-4 py-4 text-3xl text-center tracking-[0.5em] font-bold"
                                        required
                                        autoFocus
                                    />
                                </div>

                                {verifyError && (
                                    <div className="surface-note surface-note--danger text-center">
                                        {verifyError}
                                    </div>
                                )}

                                <button
                                    type="submit"
                                    disabled={submitting || smsCode.length !== 6}
                                    className="soft-button w-full disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    {submitting ? 'Verificando...' : 'Verificar codigo'}
                                </button>

                                <div className="text-center">
                                    <p className="text-sm text-[var(--text-soft)] mb-2">No has recibido el SMS?</p>
                                    {resendCooldown > 0 ? (
                                        <p className="text-sm text-[var(--text-faint)]">
                                            Puedes reenviar en {resendCooldown}s
                                        </p>
                                    ) : (
                                        <button
                                            type="button"
                                            onClick={handleResend}
                                            disabled={resending}
                                            className="soft-subtle-button disabled:opacity-50"
                                        >
                                            {resending ? 'Enviando...' : 'Reenviar codigo'}
                                        </button>
                                    )}
                                </div>
                            </form>

                            <p className="mt-6 text-center text-xs text-[var(--text-faint)]">
                                El codigo es valido durante 10 minutos. Si tienes problemas llama al 964 25 94 00.
                            </p>
                        </div>
                    </section>
                )}

                {step === 'read' && consent && (
                    <div className="space-y-5">
                        <section className="page-hero-lite">
                            <div>
                                <p className="section-kicker">Documento disponible</p>
                                <h2 className="page-hero-lite__title">{consent.templateName}</h2>
                                <p className="page-hero-lite__text">
                                    Lea la informacion completa antes de tomar una decision. Al llegar al final podra confirmar la lectura y avanzar a la firma.
                                </p>
                            </div>
                            <span className="soft-badge">Acceso remoto</span>
                        </section>

                        <section className="soft-list-card">
                            <div className="grid gap-3 md:grid-cols-2 text-sm text-[var(--text-soft)]">
                                <div className="soft-list-item p-4">
                                    <p><strong>Paciente:</strong> {consent.patientName}</p>
                                    <p className="mt-1"><strong>NHC:</strong> {consent.nhc}</p>
                                    <p className="mt-1"><strong>Profesional:</strong> {consent.professionalName}</p>
                                </div>
                                <div className="soft-list-item p-4">
                                    <p><strong>Servicio:</strong> {consent.serviceName}</p>
                                    <p className="mt-1"><strong>Fecha:</strong> {consent.episodeDate}</p>
                                    <p className="mt-1"><strong>Telefono:</strong> {consent.maskedPhone}</p>
                                </div>
                            </div>
                        </section>

                        <section className="soft-list-card overflow-hidden">
                            <div className="flex items-center justify-between gap-3 border-b border-[var(--line-soft)] bg-[var(--green-pale)] px-5 py-3">
                                <p className="font-medium text-[var(--green-strong)]">Lea el documento completo antes de continuar</p>
                                {!hasScrolled && <span className="soft-badge">Pendiente de lectura</span>}
                            </div>

                            {consent.isGroup && consent.groupDocuments && consent.groupDocuments.length > 0 ? (
                                <div
                                    ref={contentRef}
                                    onScroll={handleContentScroll}
                                    className="p-5 overflow-y-auto max-w-none space-y-8"
                                    style={{ maxHeight: '50vh', fontSize: '16px', lineHeight: '1.7' }}
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
                                    style={{ maxHeight: '50vh', fontSize: '16px', lineHeight: '1.7' }}
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
                                    {isRejecting ? 'Registrar rechazo del consentimiento' : 'Firmar el documento'}
                                </h2>
                                <p className="page-hero-lite__text">
                                    {isRejecting
                                        ? 'Si no desea continuar, puede dejar constancia del rechazo con una explicacion breve y su firma.'
                                        : 'Dibuja tu firma en el recuadro inferior usando el dedo o un lapiz tactil.'}
                                </p>
                            </div>
                            <span className="soft-badge">{consent?.patientName}</span>
                        </section>

                        {isRejecting && (
                            <section className="soft-form-card">
                                <label className="block text-sm font-medium text-[var(--text-soft)] mb-2">
                                    Explica brevemente el motivo del rechazo
                                </label>
                                <textarea
                                    value={rejectReason}
                                    onChange={e => setRejectReason(e.target.value)}
                                    rows={3}
                                    placeholder="Ej: No estoy de acuerdo con los riesgos expuestos..."
                                    className="w-full px-4 py-3 text-sm"
                                />
                            </section>
                        )}

                        <section className="soft-list-card overflow-hidden">
                            <div className="flex items-center justify-between gap-3 border-b border-[var(--line-soft)] bg-[var(--green-pale)] px-4 py-3">
                                <span className="text-sm font-medium text-[var(--text-soft)]">Area de firma</span>
                                <button
                                    onClick={() => {
                                        sigPadRef.current?.clear();
                                        setIsSigned(false);
                                    }}
                                    className="soft-subtle-button"
                                >
                                    Borrar
                                </button>
                            </div>
                            <canvas
                                ref={canvasRef}
                                className="w-full touch-none bg-white"
                                style={{ height: '200px', cursor: 'crosshair' }}
                            />
                            {!isSigned && (
                                <div className="border-t border-[var(--line-soft)] bg-slate-50 px-4 py-3 text-center text-xs text-[var(--text-faint)]">
                                    Firma aqui con el dedo o lapiz tactil.
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
                                {submitting ? 'Enviando firma...' : 'Confirmar y firmar'}
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
