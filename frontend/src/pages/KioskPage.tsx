import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { PatientDto } from '../api/his';
import { searchKioskRequests, type ConsentRequestResponse } from '../api/consentRequests';

export default function KioskPage() {
    const navigate = useNavigate();

    const [step, setStep] = useState<'search' | 'select'>('search');
    const [searchType, setSearchType] = useState<'sip' | 'dni'>('sip');
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
            const normalizedSearchValue = searchType === 'dni'
                ? searchValue.trim().toUpperCase()
                : searchValue.trim();

            const result = await searchKioskRequests({
                sip: searchType === 'sip' ? normalizedSearchValue : undefined,
                dni: searchType === 'dni' ? normalizedSearchValue : undefined,
            });
            const resolvedPatient = result.patient ?? null;
            const patientRequests = result.requests ?? [];
            setPatient(resolvedPatient);

            if (patientRequests.length === 0) {
                setRequests([]);
                setError(`No hay consentimientos pendientes de firma presencial para ese ${searchType.toUpperCase()}.`);
                return;
            }

            setRequests(patientRequests);
            setStep('select');
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al buscar el paciente o sus solicitudes');
        } finally {
            setLoading(false);
        }
    };

    const handleSelect = (request: ConsentRequestResponse) => {
        navigate(`/kiosk/${request.id}`, { state: { patient, request } });
    };

    const handleReset = () => {
        setStep('search');
        setSearchValue('');
        setPatient(null);
        setRequests([]);
        setError('');
    };

    return (
        <div className="min-h-screen bg-emerald-950 flex items-center justify-center p-6">
            <div className="w-full max-w-lg">
                <div className="text-center mb-8">
                    <div className="w-16 h-16 bg-emerald-800 rounded-2xl flex items-center justify-center mx-auto mb-4">
                        <span className="text-3xl">🏥</span>
                    </div>
                    <h1 className="text-white text-2xl font-bold">
                        Consentimiento Informado
                    </h1>
                    <p className="text-emerald-300 text-sm mt-1">
                        Consorci Hospitalari Provincial de Castello
                    </p>
                </div>

                {step === 'search' && (
                    <div className="bg-white rounded-2xl p-6 shadow-2xl">
                        <h2 className="font-bold text-gray-800 text-lg mb-6 text-center">
                            Identifiquese para firmar
                        </h2>

                        <form onSubmit={handleSearch} className="space-y-4">
                            <div className="flex gap-2">
                                <button
                                    type="button"
                                    onClick={() => setSearchType('sip')}
                                    className={`flex-1 py-3 rounded-xl text-sm font-medium transition-colors ${searchType === 'sip'
                                        ? 'bg-emerald-900 text-white'
                                        : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
                                >
                                    SIP
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setSearchType('dni')}
                                    className={`flex-1 py-3 rounded-xl text-sm font-medium transition-colors ${searchType === 'dni'
                                        ? 'bg-emerald-900 text-white'
                                        : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
                                >
                                    DNI
                                </button>
                            </div>

                            <input
                                type="text"
                                value={searchValue}
                                onChange={e => setSearchValue(e.target.value)}
                                placeholder={searchType === 'sip'
                                    ? 'Numero SIP'
                                    : 'Numero de DNI'}
                                className="w-full border-2 border-gray-200 rounded-xl px-4 py-4 text-xl text-center focus:outline-none focus:border-emerald-500 transition-colors"
                                autoFocus
                                required
                            />

                            {error && (
                                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm text-center">
                                    {error}
                                </div>
                            )}

                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full bg-emerald-900 text-white py-4 rounded-xl font-bold text-lg hover:bg-emerald-800 disabled:opacity-50 transition-colors"
                            >
                                {loading ? 'Buscando...' : 'Buscar'}
                            </button>
                        </form>
                    </div>
                )}

                {step === 'select' && (
                    <div className="bg-white rounded-2xl p-6 shadow-2xl">
                        <div className="text-center mb-6">
                            <p className="text-gray-500 text-sm">Bienvenido/a</p>
                            {patient ? (
                                <>
                                    <h2 className="font-bold text-gray-800 text-xl">
                                        {patient.fullName || `${patient.firstName} ${patient.lastName}`.trim()}
                                    </h2>
                                    <div className="mt-2 space-y-1 text-sm text-gray-400">
                                        <p>NHC: {patient.nhc}</p>
                                        {patient.sip && <p>SIP: {patient.sip}</p>}
                                        <p>DNI: {patient.dni}</p>
                                        {patient.birthDate && <p>Nacimiento: {patient.birthDate}</p>}
                                        {patient.phone && <p>Telefono: {patient.phone}</p>}
                                        {patient.email && <p>Email: {patient.email}</p>}
                                    </div>
                                </>
                            ) : (
                                <div className="mt-2 space-y-1 text-sm text-gray-400">
                                    <p>Identificador: {searchValue.trim()}</p>
                                    <p>No se han podido recuperar los datos demograficos del paciente desde las solicitudes guardadas.</p>
                                </div>
                            )}
                        </div>

                        <p className="text-gray-600 text-sm mb-4 text-center">
                            Tiene los siguientes consentimientos pendientes de firma:
                        </p>

                        <div className="space-y-3 mb-6">
                            {requests.map(req => (
                                <button
                                    key={req.id}
                                    onClick={() => handleSelect(req)}
                                    className="w-full border-2 border-gray-200 hover:border-emerald-500 hover:bg-emerald-50 rounded-xl p-4 text-left transition-all"
                                >
                                    <p className="font-semibold text-gray-800">
                                        {req.templateName}
                                    </p>
                                    <p className="text-sm text-gray-500 mt-1">
                                        Episodio {req.episodeId} · {new Date(req.createdAt).toLocaleDateString('es-ES')}
                                    </p>
                                    <div className="mt-2 flex flex-wrap gap-2 text-xs text-gray-500">
                                        <span>Canal: {req.channel}</span>
                                        <span>Estado: {req.status}</span>
                                        {req.responsibleService && <span>Servicio: {req.responsibleService}</span>}
                                        {req.professionalName && <span>Solicitado por: {req.professionalName}</span>}
                                    </div>
                                </button>
                            ))}
                        </div>

                        <button
                            onClick={handleReset}
                            className="w-full border border-gray-300 text-gray-600 py-3 rounded-xl text-sm hover:bg-gray-50 transition-colors"
                        >
                            Volver
                        </button>
                    </div>
                )}

                <p className="text-emerald-600 text-xs text-center mt-6">
                    Si necesita ayuda, dirijase al mostrador de atencion al paciente
                </p>
            </div>
        </div>
    );
}
