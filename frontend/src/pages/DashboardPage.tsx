import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getAgendaAppointments, getServiceAgendas, type AgendaAppointmentDto, type AgendaDto } from '../api/his';
import { getApiErrorMessage } from '../api/client';
import { getServiceDisplayName } from '../utils/serviceDisplay';

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

export default function DashboardPage() {
    const { user, logoutUser, hasRole, updateSessionUser } = useAuth();
    const navigate = useNavigate();

    const [agendas, setAgendas] = useState<AgendaDto[]>([]);
    const [selectedAgenda, setSelectedAgenda] = useState<AgendaDto | null>(null);
    const [appointments, setAppointments] = useState<AgendaAppointmentDto[]>([]);
    const [loadingAgendas, setLoadingAgendas] = useState(false);
    const [loadingAppointments, setLoadingAppointments] = useState(false);
    const [agendaError, setAgendaError] = useState('');

    const isProfessional = hasRole('PROFESSIONAL');
    const currentSpecialtyLabel = getServiceDisplayName(user?.serviceName, user?.serviceCode);

    useEffect(() => {
        if (!isProfessional || !user?.dni) {
            return;
        }

        const loadAgendas = async () => {
            setLoadingAgendas(true);
            setAgendaError('');
            try {
                const data = await getServiceAgendas(user.serviceCode || 'self');
                setAgendas(data);
                setSelectedAgenda(data[0] ?? null);

                const specialtyCode = data[0]?.professional?.specialtyCode;
                const specialtyName = data[0]?.professional?.specialtyName;
                if (
                    (specialtyCode && specialtyCode !== user.serviceCode) ||
                    (specialtyName && specialtyName !== user.serviceName)
                ) {
                    updateSessionUser({
                        serviceCode: specialtyCode || user.serviceCode,
                        serviceName: specialtyName || user.serviceName,
                    });
                }
            } catch (error) {
                setAgendaError(getApiErrorMessage(
                    error,
                    'No se han podido cargar las agendas del profesional.'
                ));
                setAgendas([]);
                setSelectedAgenda(null);
            } finally {
                setLoadingAgendas(false);
            }
        };

        loadAgendas();
    }, [isProfessional, updateSessionUser, user?.dni, user?.serviceCode]);

    useEffect(() => {
        if (!selectedAgenda) {
            setAppointments([]);
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
    }, [selectedAgenda]);

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
                            Gestion de consentimientos con una vista clara y tranquila
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
                        Cerrar sesion
                    </button>
                </div>
            </nav>

            <main className="page-main space-y-6">
                <section className="dashboard-hero">
                    <div className="flex items-start justify-between gap-6 flex-wrap">
                        <div className="max-w-2xl">
                            <p className="section-kicker">Panel principal</p>
                            <h2 className="text-3xl font-semibold tracking-tight text-slate-800">
                                Un panel mas limpio para iniciar consentimientos sin friccion
                            </h2>
                            <p className="mt-3 text-sm leading-6 text-slate-500">
                                {isProfessional
                                    ? 'Selecciona una agenda, revisa las citas del dia y entra al flujo de solicitud desde un entorno visual mas calmado.'
                                    : 'Accede a las areas clave del sistema desde una interfaz mas ligera y ordenada.'}
                            </p>
                        </div>
                        {isProfessional && (
                            <button
                                onClick={() => navigate('/requests')}
                                className="soft-button-secondary text-sm"
                            >
                                Ver todas las solicitudes
                            </button>
                        )}
                    </div>
                    <div className="dashboard-hero__stats">
                        <div className="dashboard-hero__stat">
                            <span className="dashboard-hero__value">{agendas.length}</span>
                            <span className="dashboard-hero__label">Agendas detectadas</span>
                        </div>
                        <div className="dashboard-hero__stat">
                            <span className="dashboard-hero__value">{appointments.length}</span>
                            <span className="dashboard-hero__label">Citas en la vista</span>
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
                                    Agendas disponibles para iniciar consentimientos.
                                </p>
                            </div>
                            <div className="p-4 space-y-3">
                                {user?.serviceCode && !user?.dni && (
                                    <div className="surface-note surface-note--warn">
                                        Tu usuario no tiene DNI configurado y no puede consultar citas en ApiKewan.
                                    </div>
                                )}
                                {!user?.serviceName && user?.dni && (
                                    <div className="surface-note surface-note--info">
                                        La especialidad se actualizara automaticamente desde ApiKewan al cargar las agendas.
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
                                        No hay agendas disponibles para este profesional hoy.
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
                                        {selectedAgenda ? selectedAgenda.name : 'Selecciona una agenda'}
                                    </h3>
                                    <p className="text-sm text-gray-500 mt-1">
                                        Elige una cita para ir directamente a crear la solicitud de consentimiento.
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
                                        Selecciona una agenda para ver sus citas.
                                    </div>
                                ) : loadingAppointments ? (
                                    <div className="text-sm text-gray-400 py-12 text-center">
                                        Cargando citas...
                                    </div>
                                ) : appointments.length === 0 ? (
                                    <div className="text-sm text-gray-400 py-12 text-center">
                                        No hay citas para esta agenda hoy.
                                    </div>
                                ) : (
                                    <div className="space-y-3">
                                        {appointments.map(appointment => (
                                            <div
                                                key={appointment.episodeId}
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
                            Gestionar mi firma para los consentimientos
                        </p>
                    </div>

                    <div
                        onClick={() => navigate('/requests')}
                        className="dashboard-action-card">
                        <div className="dashboard-action-card__icon mb-4">Solic</div>
                        <h3 className="font-semibold text-gray-800">Solicitudes</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Gestionar consentimientos y envios
                        </p>
                    </div>

                    {(hasRole('ADMIN') || hasRole('ADMINISTRATIVE') || hasRole('SUPERVISOR')) && (
                        <div
                            onClick={() => navigate('/templates')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Temp</div>
                            <h3 className="font-semibold text-gray-800">Plantillas</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Gestionar plantillas de consentimiento
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
                                Gestionar usuarios y roles
                            </p>
                        </div>
                    )}

                    {(hasRole('ADMIN') || hasRole('PROFESSIONAL') || hasRole('ADMINISTRATIVE')) && (
                        <div
                            onClick={() => window.open('/kiosk', '_blank')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Kiosk</div>
                            <h3 className="font-semibold text-gray-800">Firma presencial</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Modo kiosco para firma en el centro
                            </p>
                        </div>
                    )}

                    {(hasRole('ADMIN') || hasRole('SUPERVISOR')) && (
                        <div
                            onClick={() => navigate('/audit')}
                            className="dashboard-action-card">
                            <div className="dashboard-action-card__icon mb-4">Audit</div>
                            <h3 className="font-semibold text-gray-800">Auditoria</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Registro de actividad del sistema
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
                                Firmar consentimientos pendientes
                            </p>
                        </div>
                    )}
                </div>
            </main>
        </div>
    );
}
