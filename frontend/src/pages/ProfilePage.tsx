import { useEffect, useRef, useState, useCallback } from 'react';
import SignaturePad from 'signature_pad';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useXPPenTablet, type PenEvent } from '../hooks/useXPPenTablet';
import {
    getSignatureStatus, saveSignature,
    deleteSignature, updateSignatureMethod, type SignatureStatus
} from '../api/professionalSignature';

export default function ProfilePage() {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const sigPadRef = useRef<SignaturePad | null>(null);

    const [status, setStatus] = useState<SignatureStatus | null>(null);
    const [mode, setMode] = useState<'view' | 'draw'>('view');
    const [isSigned, setIsSigned] = useState(false);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const [penEvents, setPenEvents] = useState<PenEvent[]>([]);

    // Método de firma local antes de guardar
    const [selectedMethod, setSelectedMethod] = useState<'TABLET' | 'CERTIFICATE'>('TABLET');

    const navigate = useNavigate();
    const { user, loginUser } = useAuth();

    useEffect(() => { loadStatus(); }, []);



    const loadStatus = async () => {
        try {
            const s = await getSignatureStatus();
            setStatus(s);
            if (s.signatureMethod) {
                setSelectedMethod(s.signatureMethod);
            }
        } catch {
            setError('Error al cargar el estado de la firma');
        }
    };

    const handleSaveMethod = async () => {
        if (!status) return;
        if (selectedMethod === status.signatureMethod) return;

        setSaving(true);
        setError('');
        try {
            await updateSignatureMethod(selectedMethod);
            if (user) {
                loginUser({ ...user, signatureMethod: selectedMethod });
            }
            setMessage(`Preferencia actualizada a: ${selectedMethod === 'TABLET' ? 'Firma de Tableta' : 'Certificado Digital'}`);
            await loadStatus();
        } catch {
            setError('Error al actualizar la preferencia de firma');
        } finally {
            setSaving(false);
        }
    };

    const handleSave = async () => {
        if (!canvasRef.current || !isSigned) return;
        setSaving(true);
        setError('');
        try {
            const imageBase64 = canvasRef.current.toDataURL('image/png');
            await saveSignature(imageBase64, penEvents);
            setMessage('Firma de tableta guardada correctamente');
            setMode('view');
            await loadStatus();
        } catch {
            setError('Error al guardar la firma');
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!window.confirm('¿Seguro que quieres eliminar tu firma de tableta guardada?')) return;
        try {
            await deleteSignature();
            setMessage('Firma eliminada');
            await loadStatus();
        } catch {
            setError('Error al eliminar la firma');
        }
    };



    // XP Pen tablet — last point for line drawing
    const lastPtRef = useRef<{ x: number; y: number } | null>(null);
    const penDownRef = useRef(false);

    // Handler for pen events from the XP Pen bridge
    const handlePenEvent = useCallback((evt: PenEvent) => {
        setPenEvents(prev => [...prev, evt]);
        const canvas = canvasRef.current;
        if (!canvas || mode !== 'draw') return;
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
    }, [mode]);

    const { device: xppenDevice, wsState: xppenState } = useXPPenTablet({
        onPenEvent: handlePenEvent,
        enabled: true,
    });
    // ── Inicializa SignaturePad ─────────────────────────────────────────────
    useEffect(() => {
        if (mode === 'draw' && canvasRef.current && selectedMethod === 'TABLET') {
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
    }, [mode, selectedMethod]);

    // ── Toggle signature_pad on/off and block global gestures ──────────
    useEffect(() => {
        const preventGlobal = (e: TouchEvent) => {
            if (e.touches.length > 1 || (e.type === 'touchmove' && mode === 'draw')) {
                e.preventDefault();
            }
        };

        document.body.style.touchAction = 'none';
        document.addEventListener('touchstart', preventGlobal as any, { passive: false });
        document.addEventListener('touchmove', preventGlobal as any, { passive: false });

        if (sigPadRef.current && mode === 'draw') {
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
    }, [xppenDevice.connected, mode]);

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
                    <h1 className="font-bold">Mi perfil</h1>
                </div>
            </nav>

            <main className="page-main max-w-3xl space-y-6">
                <section className="page-hero-lite">
                    <div>
                        <p className="section-kicker">Perfil</p>
                        <h2 className="page-hero-lite__title">Firma profesional y preferencias personales</h2>
                        <p className="page-hero-lite__text">
                            Ajusta tu método de firma y mantén tu trazo o certificado en un entorno más claro y menos denso.
                        </p>
                    </div>
                </section>

                {message && (
                    <div className="surface-note surface-note--success text-sm flex justify-between">
                        <span>{message}</span>
                        <button onClick={() => setMessage('')} className="font-bold">✕</button>
                    </div>
                )}
                {error && (
                    <div className="surface-note surface-note--danger text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                {/* Sección: Método de Firma */}
                <div className="soft-form-card">
                    <h2 className="font-bold text-gray-800 text-lg mb-1">
                        Preferencia de firma
                    </h2>
                    <p className="text-gray-500 text-sm mb-6">
                        Elige cómo quieres firmar los consentimientos informados.
                    </p>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                        {/* Opción Tableta */}
                        <label className={`
                            relative flex cursor-pointer rounded-xl border p-4 shadow-sm focus:outline-none 
                            ${selectedMethod === 'TABLET' ? 'border-emerald-600 ring-1 ring-emerald-600 bg-emerald-50' : 'border-gray-300 hover:border-emerald-300'}
                        `}>
                            <input
                                type="radio"
                                name="signatureMethod"
                                value="TABLET"
                                className="sr-only"
                                checked={selectedMethod === 'TABLET'}
                                onChange={() => setSelectedMethod('TABLET')}
                            />
                            <div className="flex w-full items-center justify-between">
                                <div className="flex items-center">
                                    <div className="text-sm">
                                        <p className={`font-medium ${selectedMethod === 'TABLET' ? 'text-emerald-900' : 'text-gray-900'}`}>
                                            Firma en Tableta
                                        </p>
                                        <div className={`mt-1 text-xs ${selectedMethod === 'TABLET' ? 'text-emerald-700' : 'text-gray-500'}`}>
                                            Dibuja tu firma una vez y se incrustará como imagen en el PDF.
                                        </div>
                                    </div>
                                </div>
                                <div className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${selectedMethod === 'TABLET' ? 'border-emerald-600 bg-emerald-600 text-white' : 'border-gray-300'
                                    }`}>
                                    {selectedMethod === 'TABLET' && <span className="h-2 w-2 rounded-full bg-white"></span>}
                                </div>
                            </div>
                        </label>

                        {/* Opción Certificado Digital */}
                        <label className={`
                            relative flex cursor-pointer rounded-xl border p-4 shadow-sm focus:outline-none 
                            ${selectedMethod === 'CERTIFICATE' ? 'border-emerald-600 ring-1 ring-emerald-600 bg-emerald-50' : 'border-gray-300 hover:border-emerald-300'}
                        `}>
                            <input
                                type="radio"
                                name="signatureMethod"
                                value="CERTIFICATE"
                                className="sr-only"
                                checked={selectedMethod === 'CERTIFICATE'}
                                onChange={() => setSelectedMethod('CERTIFICATE')}
                            />
                            <div className="flex w-full items-center justify-between">
                                <div className="flex items-center">
                                    <div className="text-sm">
                                        <p className={`font-medium ${selectedMethod === 'CERTIFICATE' ? 'text-emerald-900' : 'text-gray-900'}`}>
                                            Certificado Digital
                                        </p>
                                        <div className={`mt-1 text-xs ${selectedMethod === 'CERTIFICATE' ? 'text-emerald-700' : 'text-gray-500'}`}>
                                            Usa tu certificado personal (ej. CERES). Se solicitará validación SSL mTLS al firmar.
                                        </div>
                                    </div>
                                </div>
                                <div className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${selectedMethod === 'CERTIFICATE' ? 'border-emerald-600 bg-emerald-600 text-white' : 'border-gray-300'
                                    }`}>
                                    {selectedMethod === 'CERTIFICATE' && <span className="h-2 w-2 rounded-full bg-white"></span>}
                                </div>
                            </div>
                        </label>
                    </div>

                    {status?.signatureMethod !== selectedMethod && (
                        <div className="flex justify-end">
                            <button
                                onClick={handleSaveMethod}
                                disabled={saving}
                                className="bg-emerald-600 text-white px-5 py-2.5 rounded-lg
                                         font-medium hover:bg-emerald-700 focus:ring-4 focus:ring-emerald-200
                                         disabled:opacity-50 transition-all text-sm"
                            >
                                {saving ? 'Guardando...' : 'Guardar preferencia'}
                            </button>
                        </div>
                    )}
                </div>

                {/* Sección: Configuración del método seleccionado */}
                {selectedMethod === 'TABLET' && (
                    <div className="soft-form-card">
                        <h2 className="font-bold text-gray-800 text-lg mb-1">
                            Firma de Tableta
                        </h2>
                        <p className="text-gray-500 text-sm mb-6">
                            Configura el trazo de tu firma que se usará en los documentos.
                        </p>

                        {/* Estado actual */}
                        <div className={`flex items-center gap-3 p-4 rounded-xl mb-6
                              ${status?.hasSignature
                                ? 'bg-green-50 border border-green-200'
                                : 'bg-yellow-50 border border-yellow-200'}`}>
                            <span className="text-2xl">
                                {status?.hasSignature ? '✅' : '⚠️'}
                            </span>
                            <div>
                                <p className={`font-medium text-sm
                    ${status?.hasSignature
                                        ? 'text-green-700' : 'text-yellow-700'}`}>
                                    {status?.hasSignature
                                        ? 'Tienes una firma registrada'
                                        : 'No tienes firma registrada'}
                                </p>
                                {status?.hasSignature && status.updatedAt && (
                                    <p className="text-green-600 text-xs mt-0.5">
                                        Actualizada el{' '}
                                        {new Date(status.updatedAt).toLocaleDateString('es-ES', {
                                            day: '2-digit', month: '2-digit', year: 'numeric',
                                            hour: '2-digit', minute: '2-digit'
                                        })}
                                    </p>
                                )}
                            </div>
                        </div>

                        {/* Modo visualización */}
                        {mode === 'view' && (
                            <div className="flex gap-3">
                                <button
                                    onClick={() => { setMode('draw'); setIsSigned(false); }}
                                    className="flex-1 bg-blue-900 text-white py-3 rounded-xl
                               font-medium hover:bg-blue-800 transition-colors"
                                >
                                    {status?.hasSignature ? 'Actualizar firma' : 'Añadir firma'}
                                </button>
                                {status?.hasSignature && (
                                    <button
                                        onClick={handleDelete}
                                        className="px-4 py-3 border border-red-300 text-red-600
                                 rounded-xl hover:bg-red-50 transition-colors"
                                    >
                                        Eliminar
                                    </button>
                                )}
                            </div>
                        )}

                        {/* Modo dibujo */}
                        {mode === 'draw' && (
                            <div className="space-y-4">
                                <div className="border-2 border-gray-200 rounded-xl overflow-hidden">
                                    <div className="bg-gray-50 px-4 py-2 border-b border-gray-200
                                    flex justify-between items-center">
                                        <span className="text-sm text-gray-500">
                                            Dibuja tu firma
                                        </span>
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
                                        <button
                                            onClick={() => {
                                                sigPadRef.current?.clear();
                                                setIsSigned(false);
                                                setPenEvents([]);
                                            }}
                                            className="text-sm text-blue-600 hover:text-blue-800"
                                        >
                                            Borrar
                                        </button>
                                    </div>
                                    <canvas
                                        ref={canvasRef}
                                        className="w-full touch-none bg-white"
                                        style={{ height: '180px', cursor: 'crosshair' }}
                                    />
                                    {!isSigned && (
                                        <div className="bg-gray-50 px-4 py-2 border-t border-gray-200
                                      text-center">
                                            <p className="text-gray-400 text-xs">
                                                Firma aquí con el ratón o dedo
                                            </p>
                                        </div>
                                    )}
                                </div>

                                <div className="flex gap-3">
                                    <button
                                        onClick={() => setMode('view')}
                                        className="flex-1 border border-gray-300 text-gray-600
                                 py-3 rounded-xl hover:bg-gray-50 transition-colors"
                                    >
                                        Cancelar
                                    </button>
                                    <button
                                        onClick={handleSave}
                                        disabled={!isSigned || saving}
                                        className="flex-1 bg-blue-900 text-white py-3 rounded-xl
                                 font-medium hover:bg-blue-800
                                 disabled:opacity-50 transition-colors"
                                    >
                                        {saving ? 'Guardando...' : 'Guardar firma'}
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {selectedMethod === 'CERTIFICATE' && (
                    <div className="soft-form-card border border-blue-100">
                        <div className="flex gap-4 items-start">
                            <div className="bg-blue-50 text-blue-600 p-3 rounded-full shrink-0">
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                                </svg>
                            </div>
                            <div>
                                <h3 className="font-bold text-gray-800 text-lg mb-2">Todo listo para firmar</h3>
                                <p className="text-gray-600 text-sm mb-4">
                                    Al usar el certificado digital, el sistema solicitará que selecciones tu certificado (como la tarjeta CERES o tu certificado personal importado) en el momento de firmar un documento.
                                </p>
                                <div className="bg-gray-50 p-4 rounded-lg text-sm text-gray-500 border border-gray-200">
                                    <p><strong>Asegúrate de:</strong></p>
                                    <ul className="list-disc ml-5 mt-2 space-y-1">
                                        <li>Tener tu certificado digital instalado en este navegador/sistema operativo.</li>
                                        <li>Si usas tarjeta física (ej. CERES), tenerla insertada en el lector antes de firmar.</li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </main>
        </div>
    );
}
