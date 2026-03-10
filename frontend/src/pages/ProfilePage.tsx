import { useEffect, useRef, useState, useCallback } from 'react';
import SignaturePad from 'signature_pad';
import { useNavigate } from 'react-router-dom';
import { useXPPenTablet, type PenEvent } from '../hooks/useXPPenTablet';
import {
    getSignatureStatus, saveSignature,
    deleteSignature, type SignatureStatus
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

    const navigate = useNavigate();

    useEffect(() => { loadStatus(); }, []);



    const loadStatus = async () => {
        try {
            const s = await getSignatureStatus();
            setStatus(s);
        } catch {
            setError('Error al cargar el estado de la firma');
        }
    };

    const handleSave = async () => {
        if (!canvasRef.current || !isSigned) return;
        setSaving(true);
        setError('');
        try {
            const imageBase64 = canvasRef.current.toDataURL('image/png');
            await saveSignature(imageBase64, penEvents);
            setMessage('Firma guardada correctamente');
            setMode('view');
            await loadStatus();
        } catch {
            setError('Error al guardar la firma');
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!window.confirm('¿Seguro que quieres eliminar tu firma?')) return;
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
        if (mode === 'draw' && canvasRef.current) {
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
    }, [mode]);

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
                    <h1 className="font-bold">Mi perfil</h1>
                </div>
            </nav>

            <main className="p-6 max-w-2xl mx-auto">

                {message && (
                    <div className="bg-green-50 border border-green-200 text-green-700
                          px-4 py-3 rounded-lg mb-4 text-sm flex justify-between">
                        <span>{message}</span>
                        <button onClick={() => setMessage('')} className="font-bold">✕</button>
                    </div>
                )}
                {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700
                          px-4 py-3 rounded-lg mb-4 text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                <div className="bg-white rounded-xl p-6 shadow-sm">
                    <h2 className="font-bold text-gray-800 text-lg mb-1">
                        Firma del profesional
                    </h2>
                    <p className="text-gray-500 text-sm mb-6">
                        Tu firma se incluirá automáticamente en el PDF de todos los
                        consentimientos que gestiones.
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
            </main>
        </div>
    );
}