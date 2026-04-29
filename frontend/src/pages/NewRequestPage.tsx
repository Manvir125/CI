import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';
import {
    getPatientByNhc, getPatientByDni,
    getActiveEpisodes, getEpisode, type PatientDto, type EpisodeDto, type AgendaAppointmentDto
} from '../api/his';
import { getTemplates } from '../api/templates';
import { sendRequest, createGroup } from '../api/consentRequests';
import { getSignatureStatus } from '../api/professionalSignature';
import { getActiveProfessionals, type UserResponse } from '../api/user';
import type { Template } from '../types';
import { useAuth } from '../context/AuthContext';
import { getServiceDisplayName } from '../utils/serviceDisplay';

type Step = 'search' | 'episodes' | 'configure';

const firstNonEmpty = (...values: Array<string | null | undefined>) =>
    values.find(value => typeof value === 'string' && value.trim().length > 0)?.trim() ?? '';

const normalizeIdentifier = (value?: string | null) => firstNonEmpty(value).toLowerCase();

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/i;
const GENERIC_EMAIL_LOCAL_PARTS = new Set([
    'noemail',
    'sinemail',
    'sincorreo',
    'correo',
    'email',
    'test',
    'noreply'
]);
const GENERIC_EMAIL_DOMAIN_MARKERS = [
    'email.com',
    'example.',
    'noemail',
    'nomail',
    'mail.local',
    'correo.local',
    'invalid'
];

const normalizePatientEmail = (value?: string | null) => {
    const normalized = firstNonEmpty(value);
    if (!normalized) {
        return '';
    }

    const loweredEmail = normalized.toLowerCase();
    if (!EMAIL_PATTERN.test(loweredEmail)) {
        return '';
    }

    const [localPart = '', domain = ''] = loweredEmail.split('@');
    if (GENERIC_EMAIL_LOCAL_PARTS.has(localPart)) {
        return '';
    }
    if (GENERIC_EMAIL_DOMAIN_MARKERS.some(marker => domain.includes(marker))) {
        return '';
    }

    return normalized;
};

const matchesServiceIdentifier = (
    templateServiceIdentifier?: string | null,
    serviceCode?: string | null,
    serviceName?: string | null
) => {
    const templateValue = normalizeIdentifier(templateServiceIdentifier);
    if (!templateValue) {
        return false;
    }

    return templateValue === normalizeIdentifier(serviceCode)
        || templateValue === normalizeIdentifier(serviceName);
};

const mergePatientData = (
    base: PatientDto | null | undefined,
    override: PatientDto | null | undefined
): PatientDto | null => {
    if (!base && !override) {
        return null;
    }

    const allergies = override?.allergies?.length
        ? override.allergies
        : (base?.allergies ?? []);

    return {
        nhc: firstNonEmpty(override?.nhc, base?.nhc),
        dni: firstNonEmpty(override?.dni, base?.dni),
        firstName: firstNonEmpty(override?.firstName, base?.firstName),
        lastName: firstNonEmpty(override?.lastName, base?.lastName),
        birthDate: firstNonEmpty(override?.birthDate, base?.birthDate),
        gender: firstNonEmpty(override?.gender, base?.gender),
        email: firstNonEmpty(override?.email, base?.email),
        phone: firstNonEmpty(override?.phone, base?.phone),
        address: firstNonEmpty(override?.address, base?.address),
        bloodType: firstNonEmpty(override?.bloodType, base?.bloodType),
        fullName: firstNonEmpty(override?.fullName, base?.fullName) || null,
        sip: firstNonEmpty(override?.sip, base?.sip) || null,
        allergies,
        active: override?.active ?? base?.active ?? true
    };
};

const normalizeEpisode = (
    episode: EpisodeDto,
    fallbackPatient?: PatientDto | null
): EpisodeDto => {
    const mergedPatient = mergePatientData(
        fallbackPatient,
        episode.patient ?? episode.appointment?.patient ?? null
    );

    const professional = episode.professional ?? episode.appointment?.professional ?? null;
    const agenda = episode.agenda ?? episode.appointment?.agenda ?? null;

    return {
        ...episode,
        nhc: firstNonEmpty(episode.nhc, mergedPatient?.nhc),
        serviceCode: firstNonEmpty(episode.serviceCode, agenda?.serviceCode),
        serviceName: firstNonEmpty(episode.serviceName, agenda?.serviceName),
        procedureCode: firstNonEmpty(episode.procedureCode),
        procedureName: firstNonEmpty(episode.procedureName, episode.appointment?.prestation),
        attendingPhysician: firstNonEmpty(
            episode.attendingPhysician,
            professional?.fullName,
            episode.appointment?.professional?.fullName
        ),
        diagnosis: firstNonEmpty(
            episode.diagnosis,
            episode.diagnoses?.find(diagnosis => diagnosis.primary)?.diagnosisName,
            episode.diagnoses?.[0]?.diagnosisName
        ) || null,
        patient: mergedPatient,
        professional,
        agenda,
        appointment: episode.appointment
            ? {
                ...episode.appointment,
                nhc: firstNonEmpty(episode.appointment.nhc, mergedPatient?.nhc),
                patient: mergePatientData(mergedPatient, episode.appointment.patient ?? null),
                agenda: episode.appointment.agenda ?? agenda,
                professional: episode.appointment.professional ?? professional
            }
            : null
    };
};

const buildEpisodeFromAppointment = (appointment: AgendaAppointmentDto): EpisodeDto =>
({
    episodeId: appointment.episodeId,
    nhc: appointment.nhc,
    serviceCode: firstNonEmpty(
        appointment.agenda?.serviceCode,
        appointment.professional?.specialtyCode
    ),
    serviceName: firstNonEmpty(
        appointment.agenda?.serviceName,
        appointment.professional?.specialtyName
    ),
    procedureCode: '',
    procedureName: firstNonEmpty(appointment.prestation, 'Consulta'),
    episodeDate: appointment.appointmentDate,
    admissionDate: appointment.appointmentDate,
    expectedDischargeDate: null,
    ward: '',
    bed: null,
    attendingPhysician: firstNonEmpty(appointment.professional?.fullName),
    status: appointment.status,
    priority: '',
    diagnosis: null,
    icd10Code: null,
    patient: appointment.patient ?? null,
    professional: appointment.professional ?? null,
    agenda: appointment.agenda ?? null,
    appointment,
    diagnoses: []
});

export default function NewRequestPage() {
    const { user } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams] = useSearchParams();
    const preselectedEpisodeId = searchParams.get('episodeId');
    const preselectedNhc = searchParams.get('nhc');
    const startedFromAgenda = Boolean(preselectedEpisodeId);
    const agendaSelection = (location.state as { appointment?: AgendaAppointmentDto } | null)?.appointment ?? null;

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
    const [mainTemplateId, setMainTemplateId] = useState<number | null>(null);
    const [secondaryTemplateIds, setSecondaryTemplateIds] = useState<number[]>([]);
    const [observationsMap, setObservationsMap] = useState<Record<number, string>>({});
    const [customTemplateMap, setCustomTemplateMap] = useState<Record<number, string>>({});
    const [editingTemplateId, setEditingTemplateId] = useState<number | null>(null);
    const [assignedProfessionalMap, setAssignedProfessionalMap] = useState<Record<number, number | null>>({});
    const [professionalSearchMap, setProfessionalSearchMap] = useState<Record<number, string>>({});
    const [activeProfessionals, setActiveProfessionals] = useState<UserResponse[]>([]);
    const [channel, setChannel] = useState<'REMOTE' | 'ONSITE'>('REMOTE');
    const [patientEmail, setPatientEmail] = useState('');
    const [patientPhone, setPatientPhone] = useState('');
    const [sendNow, setSendNow] = useState(true);
    const [hasSignature, setHasSignature] = useState(false);
    const currentUserServiceLabel = getServiceDisplayName(user?.serviceName, user?.serviceCode);
    const selectedEpisodeServiceLabel = getServiceDisplayName(
        selectedEpisode?.serviceName,
        selectedEpisode?.agenda?.serviceName || selectedEpisode?.serviceCode
    );
    const episodeServiceCode = firstNonEmpty(
        selectedEpisode?.serviceCode,
        selectedEpisode?.agenda?.serviceCode
    );
    const episodeServiceName = firstNonEmpty(
        selectedEpisode?.serviceName,
        selectedEpisode?.agenda?.serviceName
    );
    const userServiceCode = firstNonEmpty(user?.serviceCode);
    const userServiceName = firstNonEmpty(user?.serviceName);

    const resolveServiceLabel = (serviceCode?: string | null, preferredName?: string | null) => {
        const directLabel = getServiceDisplayName(preferredName, undefined);
        if (directLabel) {
            return directLabel;
        }

        const normalizedCode = firstNonEmpty(serviceCode);
        if (!normalizedCode) {
            return '';
        }

        if (selectedEpisode?.serviceCode?.toLowerCase() === normalizedCode.toLowerCase()) {
            return getServiceDisplayName(selectedEpisode?.serviceName, normalizedCode);
        }

        if (user?.serviceCode?.toLowerCase() === normalizedCode.toLowerCase()) {
            return getServiceDisplayName(user?.serviceName, normalizedCode);
        }

        const professionalMatch = activeProfessionals.find(professional =>
            professional.serviceCode?.toLowerCase() === normalizedCode.toLowerCase()
            && professional.serviceName
        );

        return getServiceDisplayName(professionalMatch?.serviceName, normalizedCode);
    };

    const matchesSelectedEpisodeService = (templateServiceIdentifier?: string | null) =>
        matchesServiceIdentifier(templateServiceIdentifier, episodeServiceCode, episodeServiceName);

    const matchesCurrentUserService = (templateServiceIdentifier?: string | null) =>
        matchesServiceIdentifier(templateServiceIdentifier, userServiceCode, userServiceName);

    const templateMatchesPrimaryFilter = (template: Template) => {
        if (!firstNonEmpty(template.serviceCode)) {
            return true;
        }

        if (episodeServiceCode || episodeServiceName) {
            return matchesSelectedEpisodeService(template.serviceCode);
        }

        if (userServiceCode || userServiceName) {
            return matchesCurrentUserService(template.serviceCode);
        }

        return true;
    };

    const resolveResponsibleService = (template?: Template | null) => {
        const templateServiceIdentifier = firstNonEmpty(template?.serviceCode);

        if (matchesSelectedEpisodeService(templateServiceIdentifier)) {
            return episodeServiceCode || templateServiceIdentifier;
        }

        if (matchesCurrentUserService(templateServiceIdentifier)) {
            return userServiceCode || templateServiceIdentifier;
        }

        return templateServiceIdentifier || episodeServiceCode;
    };

    const patientDni = firstNonEmpty(selectedEpisode?.patient?.dni, patient?.dni);
    const patientSip = firstNonEmpty(selectedEpisode?.patient?.sip, patient?.sip);

    const resetConfiguration = () => {
        setMainTemplateId(null);
        setSecondaryTemplateIds([]);
        setObservationsMap({});
        setCustomTemplateMap({});
        setEditingTemplateId(null);
        setAssignedProfessionalMap({});
        setProfessionalSearchMap({});
    };

    const loadTemplatesForConfiguration = async () => {
        const allTemplates = await getTemplates();
        setTemplates(allTemplates);
        setStep('configure');
    };

    useEffect(() => {
        getSignatureStatus().then(status => {
            setHasSignature(status.hasSignature);
        }).catch(err => console.error("Error fetching signature status", err));

        getActiveProfessionals()
            .then(setActiveProfessionals)
            .catch(err => console.error('Error fetching active professionals', err));
    }, []);

    useEffect(() => {
        if (channel === 'ONSITE') {
            setSendNow(false);
        }
    }, [channel]);

    useEffect(() => {
        if (!preselectedEpisodeId) {
            return;
        }

        let cancelled = false;

        const preloadFromAgenda = async () => {
            setLoading(true);
            setError('');
            try {
                const appointmentEpisode = agendaSelection
                    ? normalizeEpisode(
                        buildEpisodeFromAppointment(agendaSelection),
                        agendaSelection.patient ?? null
                    )
                    : null;

                const rawEpisode = await getEpisode(preselectedEpisodeId);
                const lookupNhc = firstNonEmpty(
                    appointmentEpisode?.nhc,
                    preselectedNhc,
                    rawEpisode.nhc
                );
                const patientFromHis = rawEpisode.patient
                    ?? rawEpisode.appointment?.patient
                    ?? appointmentEpisode?.patient
                    ?? (lookupNhc ? await getPatientByNhc(lookupNhc) : null);

                const normalizedRemoteEpisode = normalizeEpisode(rawEpisode, patientFromHis);
                const selectedFromAgenda = appointmentEpisode
                    && (
                        normalizedRemoteEpisode.episodeId !== appointmentEpisode.episodeId
                        || normalizedRemoteEpisode.nhc !== appointmentEpisode.nhc
                    )
                    ? normalizeEpisode({
                        ...normalizedRemoteEpisode,
                        episodeId: appointmentEpisode.episodeId,
                        nhc: appointmentEpisode.nhc,
                        serviceCode: firstNonEmpty(appointmentEpisode.serviceCode, normalizedRemoteEpisode.serviceCode),
                        serviceName: firstNonEmpty(appointmentEpisode.serviceName, normalizedRemoteEpisode.serviceName),
                        procedureName: firstNonEmpty(appointmentEpisode.procedureName, normalizedRemoteEpisode.procedureName),
                        episodeDate: firstNonEmpty(appointmentEpisode.episodeDate, normalizedRemoteEpisode.episodeDate),
                        admissionDate: firstNonEmpty(appointmentEpisode.admissionDate, normalizedRemoteEpisode.admissionDate),
                        attendingPhysician: firstNonEmpty(appointmentEpisode.attendingPhysician, normalizedRemoteEpisode.attendingPhysician),
                        status: firstNonEmpty(appointmentEpisode.status, normalizedRemoteEpisode.status),
                        patient: mergePatientData(normalizedRemoteEpisode.patient, appointmentEpisode.patient),
                        professional: appointmentEpisode.professional ?? normalizedRemoteEpisode.professional,
                        agenda: appointmentEpisode.agenda ?? normalizedRemoteEpisode.agenda,
                        appointment: appointmentEpisode.appointment ?? normalizedRemoteEpisode.appointment
                    }, appointmentEpisode.patient)
                    : normalizedRemoteEpisode;

                const episode = selectedFromAgenda;
                const resolvedPatient = mergePatientData(patientFromHis, episode.patient);

                if (cancelled) {
                    return;
                }

                setPatient(resolvedPatient);
                setSelectedEpisode(episode);
                setPatientEmail(normalizePatientEmail(resolvedPatient?.email));
                setPatientPhone(resolvedPatient?.phone ?? '');
                resetConfiguration();
                await loadTemplatesForConfiguration();
            } catch {
                if (!cancelled) {
                    setError('No se ha podido cargar la cita seleccionada. Puedes buscar el paciente manualmente.');
                    setStep('search');
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };

        preloadFromAgenda();

        return () => {
            cancelled = true;
        };
    }, [preselectedEpisodeId]);

    // Paso 1: Buscar paciente
    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const found = searchType === 'nhc'
                ? await getPatientByNhc(searchValue)
                : await getPatientByDni(searchValue);

            const resolvedPatient = mergePatientData(found, found);
            setPatient(resolvedPatient);
            setPatientEmail(normalizePatientEmail(resolvedPatient?.email));
            setPatientPhone(resolvedPatient?.phone ?? '');

            const eps = await getActiveEpisodes(found.nhc);
            setEpisodes(eps.map(ep => normalizeEpisode(ep, resolvedPatient)));
            setStep('episodes');

        } catch (err: any) {
            if (err?.response?.status === 404) {
                setError('Paciente no encontrado. Verifica el numero introducido.');
            } else {
                setError('Error al conectar con el HIS. Intentalo de nuevo.');
            }
        } finally {
            setLoading(false);
        }
    };

    // Paso 2: Seleccionar episodio
    const handleSelectEpisode = async (episode: EpisodeDto) => {
        setLoading(true);
        setError('');
        try {
            const detailedEpisode = normalizeEpisode(await getEpisode(episode.episodeId), patient);
            setSelectedEpisode(detailedEpisode);
            setPatient(prev => mergePatientData(prev, detailedEpisode.patient));
            setPatientEmail(current => current || normalizePatientEmail(detailedEpisode.patient?.email));
            setPatientPhone(current => current || detailedEpisode.patient?.phone || '');
            resetConfiguration();
            await loadTemplatesForConfiguration();
        } catch {
            setError('Error al cargar las plantillas');
        } finally {
            setLoading(false);
        }
    };

    const toggleSecondary = (id: number) => {
        setSecondaryTemplateIds(prev =>
            prev.includes(id)
                ? prev.filter(tid => tid !== id)
                : [...prev, id]
        );
    };

    // Paso 3: Crear y enviar solicitud
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!mainTemplateId) {
            setError('Debes seleccionar un consentimiento principal');
            return;
        }
        setLoading(true);
        setError('');
        try {
            // Unificamos el principal y los secundarios (filtrando si el principal se repite en secundarios)
            const allSelectedIds = [
                mainTemplateId,
                ...secondaryTemplateIds.filter(id => id !== mainTemplateId)
            ];
            const normalizedPatientEmail = channel === 'REMOTE'
                ? normalizePatientEmail(patientEmail)
                : '';

            if (channel === 'REMOTE' && !normalizedPatientEmail) {
                setError('Introduzca un correo electrónico válido para la firma remota.');
                setLoading(false);
                return;
            }

            const groupData = {
                nhc: selectedEpisode!.nhc || patient!.nhc,
                episodeId: selectedEpisode!.episodeId,
                patientEmail: normalizedPatientEmail,
                patientPhone,
                patientDni,
                patientSip,
                items: allSelectedIds.map(id => {
                    const t = templates.find(temp => temp.id === id);
                    return {
                        templateId: id,
                        responsibleService: resolveResponsibleService(t),
                        assignedProfessionalId: assignedProfessionalMap[id] ?? null,
                        channel,
                        autoSign: id === mainTemplateId,
                        observations: observationsMap[id] || '',
                        customTemplateHtml: customTemplateMap[id] || t?.contentHtml || ''
                    };
                })
            };

            // La plantilla principal se firma siempre por el profesional que crea la solicitud.
            const willAutoSign = allSelectedIds.some(id => {
                if (id === mainTemplateId) {
                    return true;
                }

                const assignedProfessionalId = assignedProfessionalMap[id];
                if (assignedProfessionalId) {
                    const assignedProfessional = activeProfessionals.find(
                        professional => professional.id === assignedProfessionalId
                    );
                    return user?.id === assignedProfessionalId ||
                        (!!assignedProfessional && assignedProfessional.username === user?.username);
                }

                const t = templates.find(temp => temp.id === id);
                const respService = resolveResponsibleService(t);
                return matchesCurrentUserService(respService);
            });

            if (willAutoSign && user?.signatureMethod !== 'CERTIFICATE' && !hasSignature) {
                setError('No tienes una firma predeterminada configurada para auto-firmar. Ve a tu perfil para configurar una firma con tableta.');
                setLoading(false);
                return;
            }

            // Si auto-firma y su metodo es certificado, obligamos al request mTLS
            const useMtls = willAutoSign && user?.signatureMethod === 'CERTIFICATE';

            const createdGroup = await createGroup(groupData, useMtls);

            if (sendNow && channel === 'REMOTE' && createdGroup.requests?.length > 0) {
                // Se envia el primer consentimiento del grupo para que el acceso al portal
                // muestre todos los consentimientos vinculados.
                await sendRequest(createdGroup.requests[0].id);
            }

            navigate('/requests');
        } catch (err: any) {
            setError(err?.response?.data?.message || 'Error al crear la solicitud');
        } finally {
            setLoading(false);
        }
    };

    const getFilteredProfessionals = (templateId: number) => {
        const search = (professionalSearchMap[templateId] || '').trim().toLowerCase();
        if (!search) {
            return activeProfessionals;
        }

        return activeProfessionals.filter(professional =>
            professional.fullName.toLowerCase().includes(search) ||
            professional.username.toLowerCase().includes(search) ||
            (professional.serviceName || '').toLowerCase().includes(search) ||
            (professional.serviceCode || '').toLowerCase().includes(search)
        );
    };

    return (
        <div className="new-request-page page-shell">
            {/* Navbar */}
            <nav className="app-topbar">
                <div className="app-topbar__brand">
                    <div className="app-topbar__mark">CI</div>
                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => navigate(startedFromAgenda ? '/dashboard' : '/requests')}
                            className="soft-button-ghost text-sm"
                        >
                            {startedFromAgenda ? 'Volver al dashboard' : 'Volver a solicitudes'}
                        </button>
                        <div>
                            <p className="app-topbar__eyebrow">Solicitud</p>
                            <h1 className="app-topbar__title">Nueva solicitud de consentimiento</h1>
                        </div>
                    </div>
                </div>
            </nav>

            <main className="page-main max-w-6xl">
                <section className="request-hero mb-6">
                    <div className="flex items-start justify-between gap-6 flex-wrap">
                        <div className="max-w-2xl">
                            <p className="section-kicker">Solicitud guiada</p>
                            <h2 className="text-3xl font-semibold tracking-tight text-slate-800">
                                Prepare el consentimiento del paciente
                            </h2>
                            <p className="mt-3 text-sm leading-6 text-slate-500">
                                Busque al paciente, seleccione el episodio y configure la firma.
                            </p>
                        </div>
                    </div>
                    <div className="request-hero__stats">
                        <div className="request-hero__stat">
                            <span className="request-hero__value">{patient ? '1' : '0'}</span>
                            <span className="request-hero__label">Paciente seleccionado</span>
                        </div>
                        <div className="request-hero__stat">
                            <span className="request-hero__value">{episodes.length}</span>
                            <span className="request-hero__label">Episodios cargados</span>
                        </div>
                        <div className="request-hero__stat">
                            <span className="request-hero__value">{templates.length}</span>
                            <span className="request-hero__label">Plantillas disponibles</span>
                        </div>
                    </div>
                </section>

                {/* Indicador de pasos */}
                <div className="step-strip">
                    {(['search', 'episodes', 'configure'] as Step[]).map((s, i) => {
                        const labels = ['Buscar paciente', 'Seleccionar episodio', 'Configurar envío'];
                        const isActive = step === s;
                        const isCompleted = ['search', 'episodes', 'configure']
                            .indexOf(step) > i;
                        return (
                            <div key={s} className="flex items-center">
                                <div className={`step-pill text-sm ${isActive ? 'step-pill--active font-medium' :
                                    isCompleted ? 'step-pill--done' :
                                        ''}`}>
                                    <span>{i + 1}</span>
                                    <span className="hidden sm:block">{labels[i]}</span>
                                </div>
                                {i < 2 && (
                                    <div className="step-link mx-2" />
                                )}
                            </div>
                        );
                    })}
                </div>

                {error && (
                    <div className="surface-note surface-note--danger mb-4 flex justify-between gap-3">
                        <span>{error}</span>
                        <button onClick={() => setError('')} className="font-bold">x</button>
                    </div>
                )}

                {/* PASO 1: Busqueda */}
                {step === 'search' && (
                    <div className="pastel-card p-6">
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
                                    {searchType === 'nhc' ? 'Número de historia clínica' : 'DNI del paciente'}
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
                                        className="soft-button disabled:opacity-50"
                                    >
                                        {loading ? '...' : 'Buscar'}
                                    </button>
                                </div>
                            </div>
                        </form>
                    </div>
                )}

                {/* PASO 2: Episodios */}
                {step === 'episodes' && patient && (
                    <div className="space-y-4">

                        {/* Ficha del paciente */}
                        <div className="pastel-card p-5 border-l-4 border-emerald-300">
                            <div className="flex justify-between items-start">
                                <div>
                                    <h3 className="font-bold text-gray-800 text-lg">
                                        {patient.fullName || `${patient.firstName} ${patient.lastName}`.trim()}
                                    </h3>
                                    <div className="flex gap-4 text-sm text-gray-500 mt-1">
                                        <span>NHC: <strong>{patient.nhc}</strong></span>
                                        {patient.sip && <span>SIP: <strong>{patient.sip}</strong></span>}
                                        <span>DNI: <strong>{patient.dni}</strong></span>
                                        <span>Nacimiento: <strong>{patient.birthDate}</strong></span>
                                    </div>
                                    {patient.allergies?.length > 0 && (
                                        <div className="mt-2 flex gap-1">
                                            {patient.allergies.map(a => (
                                                <span key={a}
                                                    className="bg-red-100 text-red-700 text-xs px-2 py-0.5
                                     rounded-full">
                                                    Alerta: {a}
                                                </span>
                                            ))}
                                        </div>
                                    )}
                                </div>
                                <button
                                    onClick={() => { setStep('search'); setPatient(null); }}
                                    className="text-gray-400 hover:text-emerald-700 text-sm"
                                >
                                    Cambiar
                                </button>
                            </div>
                        </div>

                        {/* Episodios activos */}
                        <div className="pastel-card p-6">
                            <h2 className="font-semibold text-gray-800 text-lg mb-4">
                                Episodios activos ({episodes.length})
                            </h2>

                            {episodes.length === 0 ? (
                                <p className="text-gray-400 text-center py-8">
                                    No hay episodios activos para este paciente.
                                </p>
                            ) : (
                                <div className="space-y-3">
                                    {episodes.map(ep => (
                                        <div
                                            key={ep.episodeId}
                                            onClick={() => handleSelectEpisode(ep)}
                                            className="border border-emerald-100/80 bg-white/70 rounded-2xl p-4 cursor-pointer
                                 hover:border-emerald-300 hover:bg-emerald-50/55 transition-all"
                                        >
                                            <div className="flex justify-between items-start">
                                                <div>
                                                    <p className="font-medium text-gray-800">
                                                        {ep.procedureName || 'Procedimiento no informado'}
                                                    </p>
                                                    <div className="flex gap-3 text-sm text-gray-500 mt-1">
                                                        <span>Servicio: {ep.serviceName}</span>
                                                        <span>Fecha: {ep.episodeDate}</span>
                                                        {ep.ward && <span>Sala: {ep.ward}</span>}
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

                {/* PASO 3: Configurar envío */}
                {step === 'configure' && patient && selectedEpisode && (
                    <form onSubmit={handleSubmit} className="space-y-4">

                        {/* Resumen */}
                        <div className="pastel-card p-5">
                            <h2 className="font-semibold text-gray-800 text-lg mb-3">
                                Resumen de la solicitud
                            </h2>
                            <div className="grid grid-cols-2 gap-3 text-sm">
                                <div>
                                    <p className="text-gray-500">Paciente</p>
                                    <p className="font-medium">
                                        {patient.fullName || `${patient.firstName} ${patient.lastName}`.trim()}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-gray-500">NHC</p>
                                    <p className="font-medium">{patient.nhc}</p>
                                </div>
                                {patientSip && (
                                    <div>
                                        <p className="text-gray-500">SIP</p>
                                        <p className="font-medium">{patientSip}</p>
                                    </div>
                                )}
                                {patientDni && (
                                    <div>
                                        <p className="text-gray-500">DNI</p>
                                        <p className="font-medium">{patientDni}</p>
                                    </div>
                                )}
                                <div>
                                    <p className="text-gray-500">Procedimiento</p>
                                    <p className="font-medium">{selectedEpisode.procedureName || 'No informado'}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">Servicio</p>
                                    <p className="font-medium">{selectedEpisodeServiceLabel || 'No informado'}</p>
                                </div>
                                {(selectedEpisode.attendingPhysician || selectedEpisode.professional?.fullName) && (
                                    <div>
                                        <p className="text-gray-500">Profesional</p>
                                        <p className="font-medium">
                                            {selectedEpisode.attendingPhysician || selectedEpisode.professional?.fullName}
                                        </p>
                                    </div>
                                )}
                                {selectedEpisode.agenda && (
                                    <div>
                                        <p className="text-gray-500">Agenda</p>
                                        <p className="font-medium">{selectedEpisode.agenda.name}</p>
                                    </div>
                                )}
                                {selectedEpisode.appointment && (
                                    <div>
                                        <p className="text-gray-500">Cita</p>
                                        <p className="font-medium">
                                            {selectedEpisode.appointment.appointmentDate} · {selectedEpisode.appointment.startTime} - {selectedEpisode.appointment.endTime}
                                        </p>
                                    </div>
                                )}
                                {selectedEpisode.diagnoses && selectedEpisode.diagnoses.length > 0 && (
                                    <div className="col-span-2">
                                        <p className="text-gray-500">Diagnosticos HIS</p>
                                        <p className="font-medium">
                                            {selectedEpisode.diagnoses
                                                .map(diagnosis => diagnosis.diagnosisName)
                                                .filter(Boolean)
                                                .join(' · ')}
                                        </p>
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Plantilla Principal */}
                        <div className="pastel-card p-6 space-y-3">
                            <h2 className="font-semibold text-gray-800 text-lg">
                                Consentimiento principal *
                            </h2>
                            <p className="text-sm text-gray-500 mb-2">
                                {currentUserServiceLabel ? (
                                    <>Se muestran las plantillas de su especialidad.</>
                                ) : (
                                    <>Se muestran las plantillas del servicio {selectedEpisodeServiceLabel || 'seleccionado'}.</>
                                )}
                            </p>
                            <div className="space-y-2">
                                {templates
                                    .filter(templateMatchesPrimaryFilter)
                                    .map(t => (
                                        <div key={t.id} className={`border rounded-lg overflow-hidden transition-all ${mainTemplateId === t.id ? 'border-emerald-500' : 'border-gray-200'}`}>
                                            <label
                                                className={`flex items-start gap-3 p-3 cursor-pointer ${mainTemplateId === t.id ? 'bg-emerald-50' : 'hover:bg-gray-50'}`}
                                            >
                                                <input
                                                    type="radio"
                                                    name="mainTemplate"
                                                    checked={mainTemplateId === t.id}
                                                    onChange={() => setMainTemplateId(t.id)}
                                                    className="mt-0.5"
                                                />
                                                <div>
                                                    <p className="font-medium text-gray-800 text-sm">{t.name}</p>
                                                    <p className="text-gray-400 text-xs mt-0.5">
                                                        v{t.version}
                                                        {t.serviceCode && ` · ${resolveServiceLabel(t.serviceCode)}`}
                                                    </p>
                                                </div>
                                            </label>
                                            {mainTemplateId === t.id && (
                                                <div className="mt-2 pl-10 pr-3 space-y-3 pb-4">
                                                    <div>
                                                        <label className="block text-xs font-medium text-gray-700 mb-1">Observaciones para el PDF</label>
                                                        <textarea
                                                            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                                            rows={2}
                                                            placeholder="Añada observaciones si es necesario."
                                                            value={observationsMap[t.id] || ''}
                                                            onChange={e => setObservationsMap(prev => ({ ...prev, [t.id]: e.target.value }))}
                                                        />
                                                    </div>
                                                    <div className="pt-2">
                                                        {editingTemplateId === t.id ? (
                                                            <div className="template-editor-shell border border-emerald-200 rounded-2xl p-3 mt-2 shadow-sm">
                                                                <div className="flex justify-between items-center mb-2">
                                                                    <label className="block text-sm font-semibold text-gray-800">
                                                                        Editando plantilla
                                                                    </label>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => setEditingTemplateId(null)}
                                                                        className="text-xs bg-emerald-100 text-emerald-700 px-3 py-1 rounded-md hover:bg-emerald-200 font-medium transition-colors"
                                                                    >
                                                                        Finalizar edición
                                                                    </button>
                                                                </div>
                                                                <div className="bg-white border border-gray-300 rounded overflow-hidden">
                                                                    <ReactQuill
                                                                        theme="snow"
                                                                        value={customTemplateMap[t.id] ?? t.contentHtml ?? ''}
                                                                        onChange={(content) => {
                                                                            setCustomTemplateMap(prev => ({
                                                                                ...prev, [t.id]: content
                                                                            }))
                                                                        }}
                                                                    />
                                                                </div>
                                                            </div>
                                                        ) : (
                                                            <button
                                                                type="button"
                                                                onClick={() => setEditingTemplateId(t.id)}
                                                                className="soft-button-ghost mt-1 w-full text-sm flex items-center justify-center gap-2"
                                                            >
                                                                <span>Editar</span>
                                                                {customTemplateMap[t.id] ? "Editar plantilla modificada" : "Personalizar plantilla"}
                                                            </button>
                                                        )}
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                            </div>
                        </div>

                        {/* Plantillas Secundarias */}
                        <div className="pastel-card p-6 space-y-3">
                            <h2 className="font-semibold text-gray-800 text-lg">
                                Consentimientos adicionales
                            </h2>
                            <p className="text-sm text-gray-500 mb-2">
                                Añada otros consentimientos si procede.
                            </p>
                            <div className="space-y-2">
                                {templates
                                    .filter(t => t.id !== mainTemplateId)
                                    .map(t => (
                                        <div key={t.id} className={`border rounded-lg overflow-hidden transition-all ${secondaryTemplateIds.includes(t.id) ? 'border-emerald-500' : 'border-gray-200'}`}>
                                            <label
                                                className={`flex items-start gap-3 p-3 cursor-pointer ${secondaryTemplateIds.includes(t.id) ? 'bg-emerald-50' : 'hover:bg-gray-50'}`}
                                            >
                                                <input
                                                    type="checkbox"
                                                    checked={secondaryTemplateIds.includes(t.id)}
                                                    onChange={() => toggleSecondary(t.id)}
                                                    className="mt-0.5"
                                                />
                                                <div>
                                                    <p className="font-medium text-gray-800 text-sm">{t.name}</p>
                                                    <p className="text-gray-400 text-xs mt-0.5">
                                                        v{t.version}
                                                        {t.serviceCode && ` · ${resolveServiceLabel(t.serviceCode)}`}
                                                    </p>
                                                </div>
                                            </label>
                                            {secondaryTemplateIds.includes(t.id) && (
                                                <div className="mt-2 pl-10 pr-3 space-y-3 pb-4">
                                                    <div className="rounded-lg border border-gray-200 bg-gray-50 p-3">
                                                        <label className="block text-xs font-medium text-gray-700 mb-1">
                                                            Profesional asignado
                                                        </label>
                                                        <p className="text-xs text-gray-500 mb-2">
                                                            Asigne un profesional concreto o deje que se derive por especialidad.
                                                        </p>
                                                        <input
                                                            type="text"
                                                            value={professionalSearchMap[t.id] || ''}
                                                            onChange={e => setProfessionalSearchMap(prev => ({ ...prev, [t.id]: e.target.value }))}
                                                            placeholder="Buscar por nombre, usuario o especialidad"
                                                            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                                        />
                                                        <select
                                                            value={assignedProfessionalMap[t.id] ?? ''}
                                                            onChange={e => setAssignedProfessionalMap(prev => ({
                                                                ...prev,
                                                                [t.id]: e.target.value ? Number(e.target.value) : null
                                                            }))}
                                                            className="mt-2 w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                                        >
                                                            <option value="">Sin asignación específica</option>
                                                            {getFilteredProfessionals(t.id).map(professional => (
                                                                <option key={professional.id} value={professional.id}>
                                                                    {professional.fullName}
                                                                    {resolveServiceLabel(professional.serviceCode, professional.serviceName)
                                                                        ? ` · ${resolveServiceLabel(professional.serviceCode, professional.serviceName)}`
                                                                        : ''}
                                                                </option>
                                                            ))}
                                                        </select>
                                                    </div>
                                                    <div>
                                                        <label className="block text-xs font-medium text-gray-700 mb-1">Observaciones para el PDF</label>
                                                        <textarea
                                                            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                                            rows={2}
                                                            placeholder="Añada observaciones si es necesario."
                                                            value={observationsMap[t.id] || ''}
                                                            onChange={e => setObservationsMap(prev => ({ ...prev, [t.id]: e.target.value }))}
                                                        />
                                                    </div>
                                                    <div className="pt-2">
                                                        {editingTemplateId === t.id ? (
                                                            <div className="template-editor-shell border border-emerald-200 rounded-2xl p-3 mt-2 shadow-sm">
                                                                <div className="flex justify-between items-center mb-2">
                                                                    <label className="block text-sm font-semibold text-gray-800">
                                                                        Editando plantilla
                                                                    </label>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => setEditingTemplateId(null)}
                                                                        className="text-xs bg-emerald-100 text-emerald-700 px-3 py-1 rounded-md hover:bg-emerald-200 font-medium transition-colors"
                                                                    >
                                                                        Finalizar edición
                                                                    </button>
                                                                </div>
                                                                <div className="bg-white border border-gray-300 rounded overflow-hidden">
                                                                    <ReactQuill
                                                                        theme="snow"
                                                                        value={customTemplateMap[t.id] ?? t.contentHtml ?? ''}
                                                                        onChange={(content) => {
                                                                            setCustomTemplateMap(prev => ({
                                                                                ...prev, [t.id]: content
                                                                            }))
                                                                        }}
                                                                    />
                                                                </div>
                                                            </div>
                                                        ) : (
                                                            <button
                                                                type="button"
                                                                onClick={() => setEditingTemplateId(t.id)}
                                                                className="soft-button-ghost mt-1 w-full text-sm flex items-center justify-center gap-2"
                                                            >
                                                                <span>Editar</span>
                                                                {customTemplateMap[t.id] ? "Editar plantilla modificada" : "Personalizar plantilla"}
                                                            </button>
                                                        )}
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                            </div>
                        </div>

                        {/* Canal y datos de contacto */}
                        <div className="pastel-card p-6 space-y-4">
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
                                        <p className="font-medium text-sm">Firma remota</p>
                                        <p className="text-xs text-gray-500">
                                            Enlace para firma fuera del centro
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
                                        <p className="font-medium text-sm">Firma presencial</p>
                                        <p className="text-xs text-gray-500">
                                            Firma en el centro
                                        </p>
                                    </div>
                                </label>
                            </div>

                            {channel === 'REMOTE' && (
                                <div className="space-y-3 pt-2">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">
                                            Correo electrónico del paciente *
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
                                            Teléfono para SMS (opcional)
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
                                            Enviar ahora el enlace al paciente
                                        </span>
                                    </label>
                                </div>
                            )}
                        </div>

                        <div className="flex gap-3 justify-end">
                            <button
                                type="button"
                                onClick={() => startedFromAgenda ? navigate('/dashboard') : setStep('episodes')}
                                className="soft-button-secondary"
                            >
                                {startedFromAgenda ? 'Volver al dashboard' : 'Atrás'}
                            </button>
                            <button
                                type="submit"
                                disabled={loading}
                                className="soft-button disabled:opacity-50"
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

