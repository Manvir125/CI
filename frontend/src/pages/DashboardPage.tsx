import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getCurrentUser } from '../api/auth';
import { getAgendaAppointments, getAppointmentsByDate, getProfessionalAgendas, type AgendaAppointmentDto, type AgendaDto } from '../api/his';
import { getApiErrorMessage } from '../api/client';
import { getServiceDisplayName } from '../utils/serviceDisplay';

const formatLocalDate = (date: Date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

const sortAppointmentsByTime = (items: AgendaAppointmentDto[]) =>
    [...items].sort((left, right) => {
        const leftKey = [
            left.appointmentDate || '9999-12-31',
            left.startTime || '23:59:59',
            left.endTime || '23:59:59',
            left.episodeId || ''
        ].join('|');
        const rightKey = [
            right.appointmentDate || '9999-12-31',
            right.startTime || '23:59:59',
            right.endTime || '23:59:59',
            right.episodeId || ''
        ].join('|');
        return leftKey.localeCompare(rightKey);
    });

const getAppointmentAgendaId = (appointment: AgendaAppointmentDto) =>
    appointment.agendaId || appointment.agenda?.agendaId || 'SIN_AGENDA';

const buildAgendasFromAppointments = (items: AgendaAppointmentDto[]): AgendaDto[] => {
    const agendasById = new Map<string, AgendaDto>();

    items.forEach(appointment => {
        const agendaId = getAppointmentAgendaId(appointment);
        if (agendasById.has(agendaId)) {
            return;
        }

        agendasById.set(agendaId, appointment.agenda ?? {
            agendaId,
            name: agendaId === 'SIN_AGENDA' ? 'Sin agenda informada' : agendaId,
            serviceCode: appointment.professional?.specialtyCode ?? null,
            serviceName: appointment.professional?.specialtyName ?? null,
            status: null,
            professional: appointment.professional ?? null
        });
    });

    return Array.from(agendasById.values());
};

export default function DashboardPage() {
    const { user, logoutUser, hasRole, updateSessionUser } = useAuth();
    const navigate = useNavigate();

    const [agendas, setAgendas] = useState<AgendaDto[]>([]);
    const [selectedAgenda, setSelectedAgenda] = useState<AgendaDto | null>(null);
    const [appointments, setAppointments] = useState<AgendaAppointmentDto[]>([]);
    const [dateAppointments, setDateAppointments] = useState<AgendaAppointmentDto[]>([]);
    const [selectedDate, setSelectedDate] = useState(formatLocalDate(new Date()));
    const [loadingAgendas, setLoadingAgendas] = useState(false);
    const [loadingAppointments, setLoadingAppointments] = useState(false);
    const [agendaError, setAgendaError] = useState('');

    const isProfessional = hasRole('PROFESSIONAL');
    const currentSpecialtyLabel = getServiceDisplayName(user?.serviceName, user?.serviceCode);
    const today = formatLocalDate(new Date());
    const isTodaySelected = selectedDate === today;

    useEffect(() => {
        if (!isProfessional || !user?.dni) {
            return;
        }

        const professionalDni = user.dni;

        const loadAgendas = async () => {
            setLoadingAgendas(true);
            setLoadingAppointments(!isTodaySelected);
            setAgendaError('');
            try {
                if (isTodaySelected) {
                    setDateAppointments([]);
                    const data = await getProfessionalAgendas(professionalDni);
                    setAgendas(data);
                    setSelectedAgenda(data[0] ?? null);
                } else {
                    const data = sortAppointmentsByTime(await getAppointmentsByDate(selectedDate));
                    const dateAgendas = buildAgendasFromAppointments(data);
                    setDateAppointments(data);
                    setAgendas(dateAgendas);
                    setSelectedAgenda(dateAgendas[0] ?? null);
                    setAppointments(dateAgendas[0]
                        ? data.filter(appointment => getAppointmentAgendaId(appointment) === dateAgendas[0].agendaId)
                        : data);
                }

                const refreshedUser = await getCurrentUser();
                updateSessionUser({
                    fullName: refreshedUser.fullName,
                    email: refreshedUser.email,
                    dni: refreshedUser.dni,
                    roles: [...refreshedUser.roles],
                    serviceCode: refreshedUser.serviceCode,
                    serviceName: refreshedUser.serviceName,
                    signatureMethod: refreshedUser.signatureMethod
                });
            } catch (error) {
                setAgendas([]);
                setSelectedAgenda(null);
                setAppointments([]);
                setDateAppointments([]);
                setAgendaError(getApiErrorMessage(
                    error,
                    isTodaySelected
                        ? 'No se han podido cargar las agendas del profesional.'
                        : 'No se han podido cargar las citas de la fecha seleccionada.'
                ));
            } finally {
                setLoadingAgendas(false);
                setLoadingAppointments(false);
            }
        };

        loadAgendas();
    }, [isProfessional, isTodaySelected, selectedDate, updateSessionUser, user?.dni, user?.serviceCode]);

    useEffect(() => {
        if (!selectedAgenda) {
            setAppointments(isTodaySelected ? [] : dateAppointments);
            return;
        }

        if (!isTodaySelected) {
            setAppointments(dateAppointments.filter(
                appointment => getAppointmentAgendaId(appointment) === selectedAgenda.agendaId
            ));
            return;
        }

        const loadAppointments = async () => {
            setLoadingAppointments(true);
            setAgendaError('');
            try {
                const data = await getAgendaAppointments(selectedAgenda.agendaId);
                setAppointments(sortAppointmentsByTime(data));
            } catch (error) {
                setAgendaError(getApiErrorMessage(
                    error,
                    'No se han podido cargar las citas de la agenda seleccionada.'
                ));
                setAppointments([]);
            } finally {
                setLoadingAppointments(false);
            }
        };

        loadAppointments();
    }, [dateAppointments, isTodaySelected, selectedAgenda]);

    const handleLogout = () => {
        logoutUser();
        navigate('/login');
    };

    const handleCreateConsent = (appointment: AgendaAppointmentDto) => {
        navigate(
            `/requests/new?episodeId=${encodeURIComponent(appointment.episodeId)}&agendaId=${encodeURIComponent(appointment.agendaId)}&nhc=${encodeURIComponent(appointment.nhc)}`,
            { state: { appointment } }
        );
    };

    return (
        <div className="dashboard-page page-shell">

            <nav className="app-topbar">
                <div className="app-topbar__brand">
                    <div className="app-topbar__mark">CI</div>
                    <div>
                        <p className="app-topbar__eyebrow">CHPC</p>
                        <h1 className="app-topbar__title">CI Digital</h1>
                        <p className="app-topbar__subtitle">
                            Gestión de consentimientos informados
                        </p>
                    </div>
                </div>
                <div className="app-topbar__actions">
                    <span className="app-pill">
                        {user?.fullName}
                        <span className="status-soft">{user?.roles[0]}</span>
                    </span>
                    <button
                        onClick={handleLogout}
                        className="soft-button-secondary text-sm"
                    >
                        Cerrar sesión
                    </button>
                </div>
            </nav>

            <main className="page-main space-y-6">
                <section className="dashboard-hero">
                    <div className="flex items-start justify-between gap-6 flex-wrap">
                        <div className="max-w-2xl">
                            <p className="section-kicker">Panel principal</p>
                            <h2 className="text-3xl font-semibold tracking-tight text-slate-800">
                                Gestión diaria de consentimientos
                            </h2>
                            <p className="mt-3 text-sm leading-6 text-slate-500">
                                {isProfessional
                                    ? 'Seleccione una agenda, revise las citas del día e inicie la solicitud correspondiente.'
                                    : 'Acceda a las áreas principales del sistema.'}
                            </p>
                        </div>
                        {isProfessional && (
                            <div className="flex items-end gap-3 flex-wrap">
                                <label className="text-sm text-slate-600">
                                    <span className="block text-xs font-medium text-slate-500 mb-1">Fecha de citas</span>
                                    <input
                                        type="date"
                                        value={selectedDate}
                                        onChange={event => setSelectedDate(event.target.value || today)}
                                        className="border border-emerald-100 rounded-lg px-3 py-2 bg-white/80 text-slate-700 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                    />
                                </label>
                                {!isTodaySelected && (
                                    <button
                                        type="button"
                                        onClick={() => setSelectedDate(today)}
                                        className="soft-button-secondary text-sm"
                                    >
                                        Volver a hoy
                                    </button>
                                )}
                                <button
                                    onClick={() => navigate('/requests')}
                                    className="soft-button-secondary text-sm"
                                >
                                    Ver todas las solicitudes
                                </button>
                            </div>
                        )}
                    </div>
                    <div className="dashboard-hero__stats">
                        <div className="dashboard-hero__stat">
                            <span className="dashboard-hero__value">{agendas.length}</span>
                            <span className="dashboard-hero__label">Agendas disponibles</span>
                        </div>
                        <div className="dashboard-hero__stat">
                            <span className="dashboard-hero__value">{appointments.length}</span>
                            <span className="dashboard-hero__label">Citas cargadas</span>
                        </div>
                        <div className="dashboard-hero__stat">
                            <span className="dashboard-hero__value">{currentSpecialtyLabel || 'Sin dato'}</span>
                            <span className="dashboard-hero__label">Especialidad activa</span>
                        </div>
                    </div>
                </section>

                {isProfessional && (
                    <section className="grid grid-cols-1 xl:grid-cols-[320px_minmax(0,1fr)] gap-6">
                        <div className="pastel-panel overflow-hidden">
                            <div className="px-5 py-4 border-b border-emerald-100/70 bg-gradient-to-r from-emerald-50 to-white">
                                <p className="section-kicker">Especialidad</p>
                                <h3 className="text-lg font-semibold mt-1 text-slate-800">
                                    {currentSpecialtyLabel || 'Sin especialidad asignada'}
                                </h3>
                                <p className="text-sm text-slate-500 mt-1">
                                    {isTodaySelected
                                        ? 'Agendas disponibles para crear solicitudes.'
                                        : `Agendas con citas el ${selectedDate}.`}
                                </p>
                            </div>
                            <div className="p-4 space-y-3">
                                {user?.serviceCode && !user?.dni && (
                                    <div className="surface-note surface-note--warn">
                                        Su usuario no tiene DNI configurado y no puede consultar citas en ApiKewan.
                                    </div>
                                )}
                                {!user?.serviceName && user?.dni && (
                                    <div className="surface-note surface-note--info">
                                        La especialidad se actualizará automáticamente desde ApiKewan al cargar las agendas.
                                    </div>
                                )}
                                {agendaError && (
                                    <div className="surface-note surface-note--danger">
                                        {agendaError}
                                    </div>
                                )}
                                {loadingAgendas ? (
                                    <div className="text-sm text-gray-400 py-6 text-center">
                                        Cargando agendas...
                                    </div>
                                ) : agendas.length === 0 ? (
                                    <div className="text-sm text-gray-400 py-6 text-center">
                                        {isTodaySelected
                                            ? 'No hay agendas disponibles para este profesional.'
                                            : 'No hay citas para este profesional en la fecha seleccionada.'}
                                    </div>
                                ) : (
                                    agendas.map(agenda => {
                                        const isSelected = selectedAgenda?.agendaId === agenda.agendaId;
                                        return (
                                            <button
                                                key={agenda.agendaId}
                                                type="button"
                                                onClick={() => setSelectedAgenda(agenda)}
                                                className={`w-full text-left rounded-2xl border px-4 py-4 transition-all ${isSelected
                                                    ? 'border-emerald-300 bg-emerald-50/70 shadow-sm'
                                                    : 'border-emerald-100/80 bg-white/70 hover:border-emerald-200 hover:bg-emerald-50/40'
                                                    }`}
                                            >
                                                <div className="flex items-start justify-between gap-3">
                                                    <div>
                                                        <p className="font-semibold text-gray-800">{agenda.name}</p>
                                                        <p className="text-sm text-gray-500 mt-1">
                                                            {agenda.professional?.fullName || 'Profesional no informado'}
                                                        </p>
                                                    </div>
                                                    <span className={`text-xs px-2 py-1 rounded-full ${agenda.status === 'ACTIVE'
                                                        ? 'bg-emerald-100 text-emerald-700'
                                                        : 'bg-slate-100 text-slate-500'
                                                        }`}>
                                                        {agenda.status || 'SIN ESTADO'}
                                                    </span>
                                                </div>
                                                {getServiceDisplayName(agenda.serviceName, agenda.serviceCode) && (
                                                    <div className="mt-3 flex flex-wrap gap-2 text-xs text-gray-500">
                                                        <span>Servicio: {getServiceDisplayName(agenda.serviceName, agenda.serviceCode)}</span>
                                                    </div>
                                                )}
                                            </button>
                                        );
                                    })
                                )}
                            </div>
                        </div>

                        <div className="pastel-panel overflow-hidden">
                            <div className="px-5 py-4 border-b border-emerald-100/70 flex items-center justify-between gap-4 flex-wrap">
                                <div>
                                    <p className="section-kicker">Citas</p>
                                    <h3 className="text-lg font-semibold text-gray-800 mt-1">
                                        {selectedAgenda ? selectedAgenda.name : 'Seleccione una agenda'}
                                    </h3>
                                    <p className="text-sm text-gray-500 mt-1">
                                        Seleccione una cita para crear la solicitud de consentimiento.
                                        {!isTodaySelected ? ` Fecha consultada: ${selectedDate}.` : ''}
                                    </p>
                                </div>
                                {selectedAgenda && (
                                    <div className="text-sm text-gray-500">
                                        {getServiceDisplayName(
                                            selectedAgenda.professional?.specialtyName,
                                            selectedAgenda.serviceName || selectedAgenda.serviceCode
                                        )}
                                    </div>
                                )}
                            </div>

                            <div className="p-4">
                                {!selectedAgenda ? (
                                    <div className="text-sm text-gray-400 py-12 text-center">
                                        Seleccione una agenda para ver sus citas.
                                    </div>
                                ) : loadingAppointments ? (
                                    <div className="text-sm text-gray-400 py-12 text-center">
                                        Cargando citas...
                                    </div>
                                ) : appointments.length === 0 ? (
                                    <div className="text-sm text-gray-400 py-12 text-center">
                                        {isTodaySelected
                                            ? 'No hay citas para esta agenda hoy.'
                                            : 'No hay citas para esta agenda en la fecha seleccionada.'}
                                    </div>
                                ) : (
                                    <div className="space-y-3">
                                        {appointments.map(appointment => (
                                            <div
                                                key={`${appointment.episodeId || appointment.nhc}-${appointment.agendaId}-${appointment.appointmentDate}-${appointment.startTime}`}
                                                className="rounded-2xl border border-emerald-100/80 bg-white/75 p-4 hover:border-emerald-300 hover:bg-emerald-50/45 transition-colors"
                                            >
                                                <div className="flex items-start justify-between gap-4 flex-wrap">
                                                    <div className="space-y-2">
                                                        <div>
                                                            <p className="font-semibold text-gray-800">
                                                                {appointment.patient?.fullName
                                                                    || `${appointment.patient?.firstName || ''} ${appointment.patient?.lastName || ''}`.trim()
                                                                    || `Paciente ${appointment.nhc}`}
                                                            </p>
                                                            <p className="text-sm text-gray-500 mt-1">
                                                                NHC {appointment.nhc}
                                                                {appointment.patient?.sip ? ` · SIP ${appointment.patient.sip}` : ''}
                                                                {appointment.patient?.dni ? ` · DNI ${appointment.patient.dni}` : ''}
                                                            </p>
                                                        </div>
                                                        <div className="flex flex-wrap gap-3 text-sm text-gray-600">
                                                            <span>{appointment.appointmentDate}</span>
                                                            <span>{appointment.startTime} - {appointment.endTime}</span>
                                                            <span>{appointment.prestation}</span>
                                                            <span>{appointment.status}</span>
                                                        </div>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        onClick={() => handleCreateConsent(appointment)}
                                                        className="soft-button text-sm"
                                                    >
                                                        Crear consentimiento
                                                    </button>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    </section>
                )}

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    <div
                        onClick={() => navigate('/profile')}
                        className="dashboard-action-card">
                        <div className="dashboard-action-card__icon mb-4">Firma</div>
                        <h3 className="font-semibold text-gray-800">Mi firma</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Configurar la firma profesional.
                        </p>
                    </div>

                    <div
                        onClick={() => navigate('/requests')}
                        className="dashboard-action-card">
                        <div className="dashboard-action-card__icon mb-4">Solic</div>
                        <h3 className="font-semibold text-gray-800">Solicitudes</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Consultar y gestionar solicitudes.
                        </p>
                    </div>

                    {(hasRole('ADMIN') || hasRole('ADMINISTRATIVE') || hasRole('SUPERVISOR') || hasRole('PROFESSIONAL')) && (
                        <div
                            onClick={() => navigate('/templates')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Temp</div>
                            <h3 className="font-semibold text-gray-800">Plantillas</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                {hasRole('PROFESSIONAL') && !hasRole('ADMIN') && !hasRole('ADMINISTRATIVE')
                                    ? 'Elegir la plantilla favorita de tu servicio.'
                                    : 'Mantener las plantillas disponibles.'}
                            </p>
                        </div>
                    )}

                    {hasRole('ADMIN') && (
                        <div
                            onClick={() => navigate('/users')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Users</div>
                            <h3 className="font-semibold text-gray-800">Usuarios</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Administrar usuarios y permisos.
                            </p>
                        </div>
                    )}

                    {(hasRole('ADMIN') || hasRole('PROFESSIONAL') || hasRole('ADMINISTRATIVE')) && (
                        <div
                            onClick={() => navigate('/requests/new')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Kiosk</div>
                            <h3 className="font-semibold text-gray-800">Firma presencial</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Crear una solicitud y abrir la firma en el centro.
                            </p>
                        </div>
                    )}

                    {(hasRole('ADMIN') || hasRole('SUPERVISOR')) && (
                        <div
                            onClick={() => navigate('/audit')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Audit</div>
                            <h3 className="font-semibold text-gray-800">Auditoría</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Revisar la actividad del sistema.
                            </p>
                        </div>
                    )}

                    {hasRole('PROFESSIONAL') && (
                        <div
                            onClick={() => navigate('/pending-signatures')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Pend</div>
                            <h3 className="font-semibold text-gray-800">Firmas pendientes</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Revisar y firmar solicitudes pendientes.
                            </p>
                        </div>
                    )}
                </div>
            </main>
        </div>
    );
}
