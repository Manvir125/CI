import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getPatientByNhc, getPatientByDni,
    getActiveEpisodes, type PatientDto, type EpisodeDto
} from '../api/his';
import { getTemplates } from '../api/templates';
import { createRequest, sendRequest } from '../api/consentRequests';
import type { Template } from '../types';

type Step = 'search' | 'episodes' | 'configure';

export default function NewRequestPage() {
    const navigate = useNavigate();

    const [step, setStep] = useState<Step>('search');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    // Datos del flujo
    const [searchType, setSearchType] = useState<'nhc' | 'dni'>('nhc');
    const [searchValue, setSearchValue] = useState('');
    const [patient, setPatient] = useState<PatientDto | null>(null);
    const [episodes, setEpisodes] = useState<EpisodeDto[]>([]);
    const [selectedEpisode, setSelectedEpisode] = useState<EpisodeDto | null>(null);
    const [templates, setTemplates] = useState<Template[]>([]);
    const [selectedTemplate, setSelectedTemplate] = useState<number | null>(null);
    const [channel, setChannel] = useState<'REMOTE' | 'ONSITE'>('REMOTE');
    const [patientEmail, setPatientEmail] = useState('');
    const [patientPhone, setPatientPhone] = useState('');
    const [sendNow, setSendNow] = useState(true);

    // ── Paso 1: Buscar paciente ──────────────────────────────────────────────
    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const found = searchType === 'nhc'
                ? await getPatientByNhc(searchValue)
                : await getPatientByDni(searchValue);

            setPatient(found);
            setPatientEmail(found.email ?? '');
            setPatientPhone(found.phone ?? '');

            const eps = await getActiveEpisodes(found.nhc);
            setEpisodes(eps);
            setStep('episodes');

        } catch (err: any) {
            if (err?.response?.status === 404) {
                setError('Paciente no encontrado. Verifica el número introducido.');
            } else {
                setError('Error al conectar con el HIS. Inténtalo de nuevo.');
            }
        } finally {
            setLoading(false);
        }
    };

    // ── Paso 2: Seleccionar episodio ─────────────────────────────────────────
    const handleSelectEpisode = async (episode: EpisodeDto) => {
        setSelectedEpisode(episode);
        setLoading(true);
        try {
            const allTemplates = await getTemplates();
            // Filtra plantillas que coincidan con el servicio del episodio
            const relevant = allTemplates.filter(
                t => !t.serviceCode || t.serviceCode === episode.serviceCode
            );
            setTemplates(relevant.length > 0 ? relevant : allTemplates);
            setStep('configure');
        } catch {
            setError('Error al cargar las plantillas');
        } finally {
            setLoading(false);
        }
    };

    // ── Paso 3: Crear y enviar solicitud ─────────────────────────────────────
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedTemplate) {
            setError('Selecciona una plantilla');
            return;
        }
        setLoading(true);
        setError('');
        try {
            const created = await createRequest({
                nhc: patient!.nhc,
                episodeId: selectedEpisode!.episodeId,
                templateId: selectedTemplate,
                channel,
                patientEmail,
                patientPhone,
            });

            if (sendNow && channel === 'REMOTE') {
                await sendRequest(created.id);
            }

            navigate('/requests');
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al crear la solicitud');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-gray-100">
            {/* Navbar */}
            <nav className="bg-emerald-700 text-white px-6 py-4 flex justify-between items-center">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/requests')}
                        className="text-emerald-300 hover:text-white text-sm transition-colors"
                    >
                        ← Solicitudes
                    </button>
                    <span className="text-emerald-500">|</span>
                    <h1 className="font-bold">Nueva Solicitud de Consentimiento</h1>
                </div>
            </nav>

            <main className="p-6 max-w-3xl mx-auto">

                {/* Indicador de pasos */}
                <div className="flex items-center mb-8">
                    {(['search', 'episodes', 'configure'] as Step[]).map((s, i) => {
                        const labels = ['Buscar paciente', 'Seleccionar episodio', 'Configurar envío'];
                        const isActive = step === s;
                        const isCompleted = ['search', 'episodes', 'configure']
                            .indexOf(step) > i;
                        return (
                            <div key={s} className="flex items-center">
                                <div className={`flex items-center gap-2 px-3 py-1 rounded-full text-sm
                  ${isActive ? 'bg-emerald-700 text-white font-medium' :
                                        isCompleted ? 'bg-green-100 text-green-700' :
                                            'bg-gray-200 text-gray-500'}`}>
                                    <span>{i + 1}</span>
                                    <span className="hidden sm:block">{labels[i]}</span>
                                </div>
                                {i < 2 && (
                                    <div className={`h-0.5 w-8 mx-1
                    ${isCompleted ? 'bg-green-400' : 'bg-gray-300'}`} />
                                )}
                            </div>
                        );
                    })}
                </div>

                {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700
                          px-4 py-3 rounded-lg mb-4 text-sm flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">✕</button>
                    </div>
                )}

                {/* ── PASO 1: Búsqueda ── */}
                {step === 'search' && (
                    <div className="bg-white rounded-xl p-6 shadow-sm">
                        <h2 className="font-semibold text-gray-800 text-lg mb-6">
                            Buscar paciente
                        </h2>
                        <form onSubmit={handleSearch} className="space-y-4">
                            <div className="flex gap-3">
                                <button
                                    type="button"
                                    onClick={() => setSearchType('nhc')}
                                    className={`flex-1 py-2 rounded-lg text-sm font-medium border
                    transition-colors
                    ${searchType === 'nhc'
                                            ? 'bg-emerald-700 text-white border-emerald-700'
                                            : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'}`}
                                >
                                    Buscar por NHC
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setSearchType('dni')}
                                    className={`flex-1 py-2 rounded-lg text-sm font-medium border
                    transition-colors
                    ${searchType === 'dni'
                                            ? 'bg-emerald-700 text-white border-emerald-700'
                                            : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'}`}
                                >
                                    Buscar por DNI
                                </button>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    {searchType === 'nhc' ? 'Número de Historia Clínica' : 'DNI del paciente'}
                                </label>
                                <div className="flex gap-2">
                                    <input
                                        type="text"
                                        value={searchValue}
                                        onChange={e => setSearchValue(e.target.value)}
                                        placeholder={searchType === 'nhc' ? '10045623' : '12345678A'}
                                        className="flex-1 border border-gray-300 rounded-lg px-3 py-2
                               focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                        required
                                    />
                                    <button
                                        type="submit"
                                        disabled={loading}
                                        className="bg-emerald-900 text-white px-6 py-2 rounded-lg
                               hover:bg-emerald-800 disabled:opacity-50 transition-colors"
                                    >
                                        {loading ? '...' : 'Buscar'}
                                    </button>
                                </div>
                            </div>
                        </form>
                    </div>
                )}

                {/* ── PASO 2: Episodios ── */}
                {step === 'episodes' && patient && (
                    <div className="space-y-4">

                        {/* Ficha del paciente */}
                        <div className="bg-white rounded-xl p-5 shadow-sm border-l-4
                            border-emerald-700">
                            <div className="flex justify-between items-start">
                                <div>
                                    <h3 className="font-bold text-gray-800 text-lg">
                                        {patient.firstName} {patient.lastName}
                                    </h3>
                                    <div className="flex gap-4 text-sm text-gray-500 mt-1">
                                        <span>NHC: <strong>{patient.nhc}</strong></span>
                                        <span>DNI: <strong>{patient.dni}</strong></span>
                                        <span>Nacimiento: <strong>{patient.birthDate}</strong></span>
                                    </div>
                                    {patient.allergies?.length > 0 && (
                                        <div className="mt-2 flex gap-1">
                                            {patient.allergies.map(a => (
                                                <span key={a}
                                                    className="bg-red-100 text-red-700 text-xs px-2 py-0.5
                                     rounded-full">
                                                    ⚠️ {a}
                                                </span>
                                            ))}
                                        </div>
                                    )}
                                </div>
                                <button
                                    onClick={() => { setStep('search'); setPatient(null); }}
                                    className="text-gray-400 hover:text-gray-600 text-sm"
                                >
                                    Cambiar
                                </button>
                            </div>
                        </div>

                        {/* Episodios activos */}
                        <div className="bg-white rounded-xl p-6 shadow-sm">
                            <h2 className="font-semibold text-gray-800 text-lg mb-4">
                                Episodios activos ({episodes.length})
                            </h2>

                            {episodes.length === 0 ? (
                                <p className="text-gray-400 text-center py-8">
                                    No hay episodios activos para este paciente
                                </p>
                            ) : (
                                <div className="space-y-3">
                                    {episodes.map(ep => (
                                        <div
                                            key={ep.episodeId}
                                            onClick={() => handleSelectEpisode(ep)}
                                            className="border border-gray-200 rounded-lg p-4 cursor-pointer
                                 hover:border-emerald-400 hover:bg-emerald-50 transition-all"
                                        >
                                            <div className="flex justify-between items-start">
                                                <div>
                                                    <p className="font-medium text-gray-800">
                                                        {ep.procedureName}
                                                    </p>
                                                    <div className="flex gap-3 text-sm text-gray-500 mt-1">
                                                        <span>🏥 {ep.serviceName}</span>
                                                        <span>📅 {ep.episodeDate}</span>
                                                        {ep.ward && <span>🛏️ {ep.ward}</span>}
                                                        {ep.bed && <span>Cama: {ep.bed}</span>}
                                                    </div>
                                                    <p className="text-xs text-gray-400 mt-1">
                                                        ID: {ep.episodeId} · {ep.attendingPhysician}
                                                    </p>
                                                </div>
                                                <span className="bg-emerald-100 text-emerald-700 text-xs
                                         px-2 py-1 rounded-full">
                                                    {ep.priority}
                                                </span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {/* ── PASO 3: Configurar envío ── */}
                {step === 'configure' && patient && selectedEpisode && (
                    <form onSubmit={handleSubmit} className="space-y-4">

                        {/* Resumen */}
                        <div className="bg-white rounded-xl p-5 shadow-sm">
                            <h2 className="font-semibold text-gray-800 text-lg mb-3">
                                Resumen de la solicitud
                            </h2>
                            <div className="grid grid-cols-2 gap-3 text-sm">
                                <div>
                                    <p className="text-gray-500">Paciente</p>
                                    <p className="font-medium">
                                        {patient.firstName} {patient.lastName}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-gray-500">NHC</p>
                                    <p className="font-medium">{patient.nhc}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">Procedimiento</p>
                                    <p className="font-medium">{selectedEpisode.procedureName}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">Servicio</p>
                                    <p className="font-medium">{selectedEpisode.serviceName}</p>
                                </div>
                            </div>
                        </div>

                        {/* Plantilla */}
                        <div className="bg-white rounded-xl p-6 shadow-sm space-y-3">
                            <h2 className="font-semibold text-gray-800 text-lg">
                                Plantilla de consentimiento *
                            </h2>
                            {templates.map(t => (
                                <label
                                    key={t.id}
                                    className={`flex items-start gap-3 p-3 border rounded-lg
                               cursor-pointer transition-all
                    ${selectedTemplate === t.id
                                            ? 'border-emerald-500 bg-emerald-50'
                                            : 'border-gray-200 hover:bg-gray-50'}`}
                                >
                                    <input
                                        type="radio"
                                        name="template"
                                        value={t.id}
                                        checked={selectedTemplate === t.id}
                                        onChange={() => setSelectedTemplate(t.id)}
                                        className="mt-0.5"
                                    />
                                    <div>
                                        <p className="font-medium text-gray-800 text-sm">{t.name}</p>
                                        <p className="text-gray-400 text-xs mt-0.5">
                                            v{t.version}
                                            {t.serviceCode && ` · ${t.serviceCode}`}
                                        </p>
                                    </div>
                                </label>
                            ))}
                        </div>

                        {/* Canal y datos de contacto */}
                        <div className="bg-white rounded-xl p-6 shadow-sm space-y-4">
                            <h2 className="font-semibold text-gray-800 text-lg">
                                Canal de firma
                            </h2>

                            <div className="grid grid-cols-2 gap-3">
                                <label
                                    className={`flex items-center gap-3 p-4 border rounded-lg
                               cursor-pointer transition-all
                    ${channel === 'REMOTE'
                                            ? 'border-emerald-500 bg-emerald-50'
                                            : 'border-gray-200 hover:bg-gray-50'}`}
                                >
                                    <input
                                        type="radio"
                                        name="channel"
                                        value="REMOTE"
                                        checked={channel === 'REMOTE'}
                                        onChange={() => setChannel('REMOTE')}
                                    />
                                    <div>
                                        <p className="font-medium text-sm">📱 Firma remota</p>
                                        <p className="text-xs text-gray-500">
                                            El paciente firma desde su dispositivo
                                        </p>
                                    </div>
                                </label>
                                <label
                                    className={`flex items-center gap-3 p-4 border rounded-lg
                               cursor-pointer transition-all
                    ${channel === 'ONSITE'
                                            ? 'border-emerald-500 bg-emerald-50'
                                            : 'border-gray-200 hover:bg-gray-50'}`}
                                >
                                    <input
                                        type="radio"
                                        name="channel"
                                        value="ONSITE"
                                        checked={channel === 'ONSITE'}
                                        onChange={() => setChannel('ONSITE')}
                                    />
                                    <div>
                                        <p className="font-medium text-sm">🖊️ Firma presencial</p>
                                        <p className="text-xs text-gray-500">
                                            El paciente firma en el centro
                                        </p>
                                    </div>
                                </label>
                            </div>

                            {channel === 'REMOTE' && (
                                <div className="space-y-3 pt-2">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">
                                            Email del paciente *
                                        </label>
                                        <input
                                            type="email"
                                            value={patientEmail}
                                            onChange={e => setPatientEmail(e.target.value)}
                                            className="w-full border border-gray-300 rounded-lg px-3 py-2
                                 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                            required={channel === 'REMOTE'}
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">
                                            Teléfono (opcional, para SMS)
                                        </label>
                                        <input
                                            type="tel"
                                            value={patientPhone}
                                            onChange={e => setPatientPhone(e.target.value)}
                                            className="w-full border border-gray-300 rounded-lg px-3 py-2
                                 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                            placeholder="666123456"
                                        />
                                    </div>

                                    <label className="flex items-center gap-3 cursor-pointer
                                    p-3 bg-emerald-50 rounded-lg border border-emerald-200">
                                        <input
                                            type="checkbox"
                                            checked={sendNow}
                                            onChange={e => setSendNow(e.target.checked)}
                                            className="w-4 h-4"
                                        />
                                        <span className="text-sm text-emerald-800">
                                            Enviar el enlace al paciente ahora por email
                                        </span>
                                    </label>
                                </div>
                            )}
                        </div>

                        <div className="flex gap-3 justify-end">
                            <button
                                type="button"
                                onClick={() => setStep('episodes')}
                                className="px-6 py-2 border border-gray-300 rounded-lg text-gray-700
                           hover:bg-gray-50 transition-colors"
                            >
                                Atrás
                            </button>
                            <button
                                type="submit"
                                disabled={loading}
                                className="px-6 py-2 bg-emerald-700 text-white rounded-lg font-medium
                           hover:bg-emerald-600 disabled:opacity-50 transition-colors"
                            >
                                {loading ? 'Creando...' :
                                    channel === 'REMOTE' && sendNow
                                        ? 'Crear y enviar al paciente'
                                        : 'Crear solicitud'}
                            </button>
                        </div>
                    </form>
                )}
            </main>
        </div>
    );
}