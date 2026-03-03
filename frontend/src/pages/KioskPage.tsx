import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getPatientByNhc, getPatientByDni, type PatientDto } from '../api/his';
import { getMyRequests, type ConsentRequestResponse } from '../api/consentRequests';

export default function KioskPage() {
    const navigate = useNavigate();

    const [step, setStep] = useState<'search' | 'select' | 'sign'>('search');
    const [searchType, setSearchType] = useState<'nhc' | 'dni'>('nhc');
    const [searchValue, setSearchValue] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [patient, setPatient] = useState<PatientDto | null>(null);
    const [requests, setRequests] = useState<ConsentRequestResponse[]>([]);

    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const found = searchType === 'nhc'
                ? await getPatientByNhc(searchValue)
                : await getPatientByDni(searchValue);

            setPatient(found);

            // Busca solicitudes PENDING o SENT para este paciente
            const all = await getMyRequests(undefined, 0, 100);
            const forPatient = all.content.filter(
                r => r.nhc === found.nhc &&
                    ['PENDING', 'SENT'].includes(r.status) &&
                    r.channel === 'ONSITE'
            );

            if (forPatient.length === 0) {
                setError('No hay consentimientos pendientes de firma presencial para este paciente.');
                setLoading(false);
                return;
            }

            setRequests(forPatient);
            setStep('select');

        } catch (err: any) {
            if (err?.response?.status === 404) {
                setError('Paciente no encontrado');
            } else {
                setError('Error al buscar el paciente');
            }
        } finally {
            setLoading(false);
        }
    };

    const handleSelect = (request: ConsentRequestResponse) => {
        // Redirige al portal de firma con el canal presencial
        // El profesional habrá generado ya el token en la solicitud
        navigate(`/kiosk/${request.id}`);
    };

    const handleReset = () => {
        setStep('search');
        setSearchValue('');
        setPatient(null);
        setRequests([]);
        setError('');
    };

    return (
        <div className="min-h-screen bg-blue-950 flex items-center justify-center p-6">
            <div className="w-full max-w-lg">

                {/* Logo y título */}
                <div className="text-center mb-8">
                    <div className="w-16 h-16 bg-blue-800 rounded-2xl flex items-center
                          justify-center mx-auto mb-4">
                        <span className="text-3xl">🏥</span>
                    </div>
                    <h1 className="text-white text-2xl font-bold">
                        Consentimiento Informado
                    </h1>
                    <p className="text-blue-300 text-sm mt-1">
                        Consorci Hospitalari Provincial de Castelló
                    </p>
                </div>

                {/* ── Paso 1: Búsqueda ── */}
                {step === 'search' && (
                    <div className="bg-white rounded-2xl p-6 shadow-2xl">
                        <h2 className="font-bold text-gray-800 text-lg mb-6 text-center">
                            Identifíquese para firmar
                        </h2>

                        <form onSubmit={handleSearch} className="space-y-4">
                            <div className="flex gap-2">
                                <button
                                    type="button"
                                    onClick={() => setSearchType('nhc')}
                                    className={`flex-1 py-3 rounded-xl text-sm font-medium
                    transition-colors
                    ${searchType === 'nhc'
                                            ? 'bg-blue-900 text-white'
                                            : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
                                >
                                    NHC
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setSearchType('dni')}
                                    className={`flex-1 py-3 rounded-xl text-sm font-medium
                    transition-colors
                    ${searchType === 'dni'
                                            ? 'bg-blue-900 text-white'
                                            : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
                                >
                                    DNI
                                </button>
                            </div>

                            <input
                                type="text"
                                value={searchValue}
                                onChange={e => setSearchValue(e.target.value)}
                                placeholder={searchType === 'nhc'
                                    ? 'Número de historia clínica'
                                    : 'Número de DNI'}
                                className="w-full border-2 border-gray-200 rounded-xl px-4 py-4
                           text-xl text-center focus:outline-none
                           focus:border-blue-500 transition-colors"
                                autoFocus
                                required
                            />

                            {error && (
                                <div className="bg-red-50 border border-red-200 text-red-700
                                px-4 py-3 rounded-xl text-sm text-center">
                                    {error}
                                </div>
                            )}

                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full bg-blue-900 text-white py-4 rounded-xl
                           font-bold text-lg hover:bg-blue-800
                           disabled:opacity-50 transition-colors"
                            >
                                {loading ? 'Buscando...' : 'Buscar'}
                            </button>
                        </form>
                    </div>
                )}

                {/* ── Paso 2: Selección de consentimiento ── */}
                {step === 'select' && patient && (
                    <div className="bg-white rounded-2xl p-6 shadow-2xl">

                        <div className="text-center mb-6">
                            <p className="text-gray-500 text-sm">Bienvenido/a</p>
                            <h2 className="font-bold text-gray-800 text-xl">
                                {patient.firstName} {patient.lastName}
                            </h2>
                            <p className="text-gray-400 text-sm">NHC: {patient.nhc}</p>
                        </div>

                        <p className="text-gray-600 text-sm mb-4 text-center">
                            Tiene los siguientes consentimientos pendientes de firma:
                        </p>

                        <div className="space-y-3 mb-6">
                            {requests.map(req => (
                                <button
                                    key={req.id}
                                    onClick={() => handleSelect(req)}
                                    className="w-full border-2 border-gray-200 hover:border-blue-500
                             hover:bg-blue-50 rounded-xl p-4 text-left
                             transition-all"
                                >
                                    <p className="font-semibold text-gray-800">
                                        {req.templateName}
                                    </p>
                                    <p className="text-sm text-gray-500 mt-1">
                                        {req.episodeId} · {new Date(req.createdAt)
                                            .toLocaleDateString('es-ES')}
                                    </p>
                                </button>
                            ))}
                        </div>

                        <button
                            onClick={handleReset}
                            className="w-full border border-gray-300 text-gray-600 py-3
                         rounded-xl text-sm hover:bg-gray-50 transition-colors"
                        >
                            ← Volver
                        </button>
                    </div>
                )}

                {/* Pie */}
                <p className="text-blue-600 text-xs text-center mt-6">
                    Si necesita ayuda, diríjase al mostrador de atención al paciente
                </p>
            </div>
        </div>
    );
}