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

    // Verificación de identidad
    const [smsCode, setSmsCode] = useState('');
    const [verifyError, setVerifyError] = useState('');
    const [resending, setResending] = useState(false);
    const [resendCooldown, setResendCooldown] = useState(0);
    // Lectura del documento
    const [hasScrolled, setHasScrolled] = useState(false);
    const [readConfirmed, setReadConfirmed] = useState(false);
    const contentRef = useRef<HTMLDivElement>(null);

    // Firma
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const sigPadRef = useRef<SignaturePad | null>(null);
    const [isSigned, setIsSigned] = useState(false);

    // Rechazo
    const [isRejecting, setIsRejecting] = useState(false);
    const [rejectReason, setRejectReason] = useState('');

    useEffect(() => {
        if (resendCooldown <= 0) return;
        const timer = setTimeout(() => setResendCooldown(c => c - 1), 1000);
        return () => clearTimeout(timer);
    }, [resendCooldown]);

    // ── Carga el consentimiento al entrar ───────────────────────────────────
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
                    // Envía el SMS solo una vez tras cargar el documento
                    await sendCode(token);
                    setResendCooldown(60);
                    setStep('verify');
                }
            })
            .catch(() => setStep('error'));
    }, []); // ← dependencias vacías, solo se ejecuta una vez

    // ── Inicializa SignaturePad cuando el canvas está listo ─────────────────
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

    // Detecta si el contenido necesita scroll o ya cabe entero
    useEffect(() => {
        if (step === 'read' && contentRef.current) {
            const el = contentRef.current;
            // Si el contenido es más pequeño que el contenedor no hace falta scroll
            const needsScroll = el.scrollHeight > el.clientHeight + 10;
            if (!needsScroll) {
                setHasScrolled(true);
            }
        }
    }, [step, consent]);

    // Redimensiona el canvas para que ocupe todo el ancho
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

    // Detecta cuando el paciente ha llegado al final del documento
    const handleContentScroll = (e: React.UIEvent<HTMLDivElement>) => {
        const el = e.currentTarget;
        const atBottom = el.scrollHeight - el.scrollTop <= el.clientHeight + 50;
        if (atBottom) setHasScrolled(true);
    };

    // ── Handlers ────────────────────────────────────────────────────────────
    const handleVerify = async (e: React.FormEvent) => {
        e.preventDefault();
        setVerifyError('');
        setSubmitting(true);
        try {
            const result = await verifyCode(token!, smsCode);
            if (result.success) {
                setStep('read');
            } else {
                setVerifyError('Código incorrecto. Comprueba el SMS e inténtalo de nuevo.');
                setSmsCode('');
            }
        } catch (err: any) {
            setStep('error');
            setError(err?.response?.data?.message || 'Error al verificar el código');
        } finally {
            setSubmitting(false);
        }
    };

    const handleResend = async () => {
        setResending(true);
        try {
            await resendCode(token!);
            setResendCooldown(60); // 60 segundos hasta poder reenviar de nuevo
            setVerifyError('');
            setSmsCode('');
        } catch {
            setVerifyError('Error al reenviar el código');
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


    // ── Renders por paso ─────────────────────────────────────────────────────

    if (step === 'loading') return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
            <div className="text-center">
                <div className="w-12 h-12 border-4 border-emerald-700 border-t-transparent
                        rounded-full animate-spin mx-auto mb-4" />
                <p className="text-gray-500">Cargando documento...</p>
            </div>
        </div>
    );

    if (step === 'error') return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl p-8 max-w-md w-full text-center shadow-lg">
                <div className="text-5xl mb-4">❌</div>
                <h1 className="text-xl font-bold text-gray-800 mb-3">
                    Enlace no válido
                </h1>
                <p className="text-gray-500 text-sm">
                    {error || 'Este enlace ha expirado o no es válido. Contacta con el hospital si crees que es un error.'}
                </p>
                <p className="text-gray-400 text-xs mt-6">
                    CHPC · Tel. 964 25 94 00
                </p>
            </div>
        </div>
    );

    if (step === 'confirmed') return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl p-8 max-w-md w-full text-center shadow-lg">
                <div className="w-16 h-16 bg-green-100 rounded-full flex items-center
                        justify-center mx-auto mb-4">
                    <span className="text-3xl">✅</span>
                </div>
                <h1 className="text-xl font-bold text-gray-800 mb-2">
                    Consentimiento firmado
                </h1>
                <p className="text-gray-500 text-sm mb-6">
                    Tu firma ha sido registrada correctamente. Recibirás una copia
                    del documento en tu email.
                </p>
                <div className="bg-gray-50 rounded-xl p-4 text-left text-sm space-y-2">
                    <p className="text-gray-600">
                        <span className="font-medium">Procedimiento:</span>{' '}
                        {consent?.procedureName}
                    </p>
                    <p className="text-gray-600">
                        <span className="font-medium">Profesional:</span>{' '}
                        {consent?.professionalName}
                    </p>
                    <p className="text-gray-600">
                        <span className="font-medium">Servicio:</span>{' '}
                        {consent?.serviceName}
                    </p>
                </div>
                <p className="text-gray-400 text-xs mt-6">
                    Consorci Hospitalari Provincial de Castelló
                </p>
            </div>
        </div>
    );

    if (step === 'rejected') return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl p-8 max-w-md w-full text-center shadow-lg">
                <div className="w-16 h-16 bg-orange-100 rounded-full flex items-center
                        justify-center mx-auto mb-4">
                    <span className="text-3xl">⚠️</span>
                </div>
                <h1 className="text-xl font-bold text-gray-800 mb-2">
                    Consentimiento rechazado
                </h1>
                <p className="text-gray-500 text-sm mb-4">
                    Has rechazado firmar este consentimiento. El equipo médico
                    ha sido notificado y contactará contigo para explicarte el procedimiento.
                </p>
                <p className="text-gray-400 text-xs mt-6">
                    Si tienes dudas llama al <strong>964 25 94 00</strong>
                </p>
            </div>
        </div>
    );

    return (
        <div className="min-h-screen bg-gray-50">

            {/* Cabecera del portal */}
            <header className="bg-emerald-700 text-white px-4 py-4 sticky top-0 z-10">
                <div className="max-w-2xl mx-auto">
                    <p className="text-emerald-300 text-xs">
                        Consorci Hospitalari Provincial de Castelló
                    </p>
                    <h1 className="font-bold text-sm mt-0.5">
                        Consentimiento Informado Digital
                    </h1>
                </div>
            </header>

            <main className="max-w-2xl mx-auto p-4 pb-32">

                {/* ── PASO: Verificación de identidad ── */}
                {step === 'verify' && consent && (
                    <div className="bg-white rounded-2xl shadow-sm p-6 mt-4">

                        <div className="text-center mb-6">
                            <div className="w-14 h-14 bg-emerald-100 rounded-full flex items-center
                      justify-center mx-auto mb-3">
                                <span className="text-2xl">📱</span>
                            </div>
                            <h2 className="font-bold text-gray-800 text-xl">
                                Verificación por SMS
                            </h2>
                            <p className="text-gray-500 text-sm mt-2">
                                Hemos enviado un código de 6 dígitos al número
                            </p>
                            <p className="font-bold text-gray-800 text-lg mt-1">
                                {consent.maskedPhone}
                            </p>
                        </div>

                        <form onSubmit={handleVerify} className="space-y-4">

                            {/* Input del código */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700
                          mb-2 text-center">
                                    Introduce el código recibido
                                </label>
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    pattern="[0-9]*"
                                    maxLength={6}
                                    value={smsCode}
                                    onChange={e => setSmsCode(e.target.value.replace(/\D/g, ''))}
                                    placeholder="_ _ _ _ _ _"
                                    className="w-full border-2 border-gray-300 rounded-xl px-4 py-4
                     text-3xl text-center tracking-[0.5em] font-bold
                     focus:outline-none focus:border-blue-500 transition-colors"
                                    required
                                    autoFocus
                                />
                            </div>

                            {verifyError && (
                                <div className="bg-red-50 border border-red-200 text-red-700
                        px-4 py-3 rounded-xl text-sm text-center">
                                    {verifyError}
                                </div>
                            )}

                            <button
                                type="submit"
                                disabled={submitting || smsCode.length !== 6}
                                className="w-full bg-emerald-700 text-white py-4 rounded-xl
                   font-semibold text-base hover:bg-emerald-800
                   disabled:opacity-50 transition-colors"
                            >
                                {submitting ? 'Verificando...' : 'Verificar código'}
                            </button>

                            {/* Reenviar código */}
                            <div className="text-center pt-2">
                                <p className="text-gray-500 text-sm mb-2">
                                    ¿No has recibido el SMS?
                                </p>
                                {resendCooldown > 0 ? (
                                    <p className="text-gray-400 text-sm">
                                        Puedes reenviar en {resendCooldown}s
                                    </p>
                                ) : (
                                    <button
                                        type="button"
                                        onClick={handleResend}
                                        disabled={resending}
                                        className="text-emerald-600 hover:text-emerald-800 text-sm
                       font-medium underline disabled:opacity-50"
                                    >
                                        {resending ? 'Enviando...' : 'Reenviar código'}
                                    </button>
                                )}
                            </div>

                        </form>

                        {/* Aviso de validez */}
                        <p className="text-gray-400 text-xs text-center mt-6">
                            El código es válido durante 10 minutos.
                            Si tienes problemas llama al <strong>964 25 94 00</strong>
                        </p>
                    </div>
                )}

                {/* ── PASO: Lectura del documento ── */}
                {step === 'read' && consent && (
                    <div className="mt-4 space-y-4">

                        {/* Info del paciente y procedimiento */}
                        <div className="bg-white rounded-2xl p-5 shadow-sm">
                            <h2 className="font-bold text-gray-800 text-lg mb-3">
                                {consent.templateName}
                            </h2>
                            <div className="space-y-1 text-sm text-gray-600">
                                <p>👤 Paciente: <strong>{consent.patientName}</strong></p>
                                <p>NHC: <strong>{consent.nhc}</strong></p>
                                <p>👨‍⚕️ Profesional: <strong>{consent.professionalName}</strong></p>
                                <p>🏥 Servicio: <strong>{consent.serviceName}</strong></p>
                                <p>📅 Fecha: <strong>{consent.episodeDate}</strong></p>
                            </div>
                        </div>

                        {/* Contenido del documento */}
                        <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
                            <div className="bg-emerald-50 px-5 py-3 border-b border-emerald-100">
                                <p className="text-emerald-800 text-sm font-medium">
                                    📄 Lea el documento completo antes de continuar
                                </p>
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
                                            {idx < consent.groupDocuments!.length - 1 && (
                                                <hr className="my-8 border-t-2 border-emerald-200 border-dashed" />
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
                                <div className="bg-amber-50 px-5 py-3 border-t border-amber-100
                                text-center">
                                    <p className="text-amber-700 text-xs">
                                        ↓ Desplázate hasta el final para continuar
                                    </p>
                                </div>
                            )}
                        </div>

                        {/* Checkbox de confirmación */}
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

                        {/* Botones de acción */}
                        <div className="space-y-3">
                            <button
                                onClick={() => {
                                    setIsRejecting(false);
                                    setStep('sign');
                                }}
                                disabled={!readConfirmed}
                                className="w-full bg-emerald-700 text-white py-4 rounded-xl
                           font-semibold text-base hover:bg-emerald-800
                           disabled:opacity-40 disabled:cursor-not-allowed
                           transition-colors"
                            >
                                Continuar para firmar
                            </button>
                            <button
                                onClick={() => {
                                    setIsRejecting(true);
                                    setStep('sign');
                                }}
                                className="w-full bg-white border border-red-300 text-red-600
                           py-4 rounded-xl font-medium text-base
                           hover:bg-red-50 transition-colors"
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
                            <h2 className={`font-bold text-xl mb-2 ${isRejecting ? 'text-red-600' : 'text-gray-800'}`}>
                                {isRejecting ? 'Rechazo del documento' : 'Firma del documento'}
                            </h2>
                            <p className="text-gray-500 text-sm">
                                {isRejecting 
                                    ? 'Debe firmar para dejar constancia de que ha declinado este consentimiento.' 
                                    : 'Dibuja tu firma en el recuadro inferior usando el dedo o un lápiz táctil.'}
                            </p>
                        </div>

                        {isRejecting && (
                            <div className="bg-white rounded-2xl p-5 shadow-sm">
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Explica brevemente el motivo del rechazo:
                                </label>
                                <textarea
                                    value={rejectReason}
                                    onChange={e => setRejectReason(e.target.value)}
                                    rows={3}
                                    placeholder="Ej: No estoy de acuerdo con los riesgos expuestos..."
                                    className="w-full border border-gray-300 rounded-xl px-4 py-3
                                         text-sm focus:outline-none focus:ring-2
                                         focus:ring-red-500"
                                />
                            </div>
                        )}

                        {/* Canvas de firma */}
                        <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
                            <div className="bg-gray-50 px-4 py-2 border-b border-gray-200
                              flex justify-between items-center">
                                <span className="text-sm text-gray-500">Área de firma</span>
                                <button
                                    onClick={() => {
                                        sigPadRef.current?.clear();
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
                                style={{ height: '200px', cursor: 'crosshair' }}
                            />
                            {!isSigned && (
                                <div className="bg-gray-50 px-4 py-2 border-t border-gray-200
                                text-center">
                                    <p className="text-gray-400 text-xs">
                                        Firma aquí con el dedo o lápiz
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
                                {submitting ? 'Enviando firma...' : '✅ Confirmar y firmar'}
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
        </div>
    );
}
