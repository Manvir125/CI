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
        <div className="page-shell">
            <header className="app-topbar">
                <div className="app-topbar__brand">
                    <div className="app-topbar__mark">CH</div>
                    <div>
                        <p className="app-topbar__eyebrow">Firma Presencial</p>
                        <h1 className="app-topbar__title">Consentimiento informado</h1>
                        <p className="app-topbar__subtitle">Acceso guiado para pacientes en kiosco</p>
                    </div>
                </div>
                <div className="app-topbar__actions">
                    <span className="app-pill">Hospital de Castellon</span>
                </div>
            </header>

            <main className="page-main">
                <section className="request-hero">
                    <p className="section-kicker">Acceso asistido</p>
                    <h2 className="page-hero-lite__title">Busca el consentimiento pendiente para continuar con la firma presencial</h2>
                    <p className="page-hero-lite__text">
                        El paciente puede identificarse mediante SIP o DNI. Solo mostraremos solicitudes pendientes de firma y preparadas para este canal.
                    </p>
                    <div className="request-hero__stats">
                        <div className="request-hero__stat">
                            <span className="request-hero__value">{searchType.toUpperCase()}</span>
                            <span className="request-hero__label">Metodo de busqueda activo</span>
                        </div>
                        <div className="request-hero__stat">
                            <span className="request-hero__value">{requests.length}</span>
                            <span className="request-hero__label">Solicitudes encontradas</span>
                        </div>
                    </div>
                </section>

                {step === 'search' && (
                    <section className="soft-form-card mt-6 max-w-3xl mx-auto">
                        <div className="mb-6 text-center">
                            <p className="section-kicker">Identificacion</p>
                            <h3 className="text-2xl font-semibold text-[var(--text-main)]">Identifiquese para firmar</h3>
                            <p className="mt-2 text-sm text-[var(--text-soft)]">
                                Introduzca el identificador del paciente para localizar sus consentimientos pendientes.
                            </p>
                        </div>

                        <form onSubmit={handleSearch} className="space-y-5">
                            <div className="soft-filter-strip justify-center">
                                <button
                                    type="button"
                                    onClick={() => setSearchType('sip')}
                                    className={`soft-pill-button min-w-32 ${searchType === 'sip' ? 'soft-pill-button--active' : ''}`}
                                >
                                    Buscar por SIP
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setSearchType('dni')}
                                    className={`soft-pill-button min-w-32 ${searchType === 'dni' ? 'soft-pill-button--active' : ''}`}
                                >
                                    Buscar por DNI
                                </button>
                            </div>

                            <div className="max-w-xl mx-auto">
                                <label className="block text-sm font-medium text-[var(--text-soft)] mb-2 text-center">
                                    {searchType === 'sip' ? 'Numero SIP' : 'Numero de DNI'}
                                </label>
                                <input
                                    type="text"
                                    value={searchValue}
                                    onChange={e => setSearchValue(e.target.value)}
                                    placeholder={searchType === 'sip' ? 'Numero SIP' : 'Numero de DNI'}
                                    className="w-full px-5 py-4 text-2xl text-center"
                                    autoFocus
                                    required
                                />
                            </div>

                            {error && (
                                <div className="surface-note surface-note--danger text-center">
                                    {error}
                                </div>
                            )}

                            <div className="flex justify-center">
                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="soft-button min-w-56 disabled:opacity-60 disabled:cursor-not-allowed"
                                >
                                    {loading ? 'Buscando...' : 'Buscar paciente'}
                                </button>
                            </div>
                        </form>
                    </section>
                )}

                {step === 'select' && (
                    <section className="mt-6 max-w-4xl mx-auto space-y-5">
                        <div className="pastel-card p-6">
                            <p className="section-kicker">Paciente identificado</p>
                            {patient ? (
                                <>
                                    <h3 className="text-2xl font-semibold text-[var(--text-main)]">
                                        {patient.fullName || `${patient.firstName} ${patient.lastName}`.trim()}
                                    </h3>
                                    <div className="mt-4 grid gap-3 sm:grid-cols-2 text-sm text-[var(--text-soft)]">
                                        <div className="soft-list-item p-4">
                                            <p><strong>NHC:</strong> {patient.nhc}</p>
                                            {patient.sip && <p className="mt-1"><strong>SIP:</strong> {patient.sip}</p>}
                                            <p className="mt-1"><strong>DNI:</strong> {patient.dni}</p>
                                        </div>
                                        <div className="soft-list-item p-4">
                                            {patient.birthDate && <p><strong>Nacimiento:</strong> {patient.birthDate}</p>}
                                            {patient.phone && <p className="mt-1"><strong>Telefono:</strong> {patient.phone}</p>}
                                            {patient.email && <p className="mt-1"><strong>Email:</strong> {patient.email}</p>}
                                        </div>
                                    </div>
                                </>
                            ) : (
                                <div className="surface-note surface-note--warn mt-3">
                                    Identificador: {searchValue.trim()}. No se han podido recuperar los datos demograficos del paciente desde las solicitudes guardadas.
                                </div>
                            )}
                        </div>

                        <div className="soft-list-card">
                            <div className="flex items-center justify-between gap-3 flex-wrap mb-5">
                                <div>
                                    <p className="section-kicker">Pendientes de firma</p>
                                    <h3 className="text-xl font-semibold text-[var(--text-main)]">
                                        Selecciona el consentimiento que quieres abrir
                                    </h3>
                                </div>
                                <span className="soft-badge">{requests.length} disponibles</span>
                            </div>

                            <div className="space-y-3">
                                {requests.map(req => (
                                    <button
                                        key={req.id}
                                        onClick={() => handleSelect(req)}
                                        className="soft-list-item w-full p-5 text-left"
                                    >
                                        <div className="flex items-start justify-between gap-4 flex-wrap">
                                            <div>
                                                <p className="text-lg font-semibold text-[var(--text-main)]">
                                                    {req.templateName}
                                                </p>
                                                <p className="mt-1 text-sm text-[var(--text-soft)]">
                                                    Episodio {req.episodeId} · {new Date(req.createdAt).toLocaleDateString('es-ES')}
                                                </p>
                                            </div>
                                            <span className="soft-badge">{req.status}</span>
                                        </div>
                                        <div className="mt-3 flex flex-wrap gap-2 text-xs text-[var(--text-soft)]">
                                            <span className="app-pill">Canal: {req.channel}</span>
                                            {req.responsibleService && <span className="app-pill">Servicio: {req.responsibleService}</span>}
                                            {req.professionalName && <span className="app-pill">Solicitado por: {req.professionalName}</span>}
                                        </div>
                                    </button>
                                ))}
                            </div>

                            <div className="mt-5 flex justify-center">
                                <button
                                    onClick={handleReset}
                                    className="soft-button-secondary min-w-44"
                                >
                                    Volver
                                </button>
                            </div>
                        </div>
                    </section>
                )}

                <p className="mt-6 text-center text-sm text-[var(--text-faint)]">
                    Si necesita ayuda, dirijase al mostrador de atencion al paciente.
                </p>
            </main>
        </div>
    );
}
