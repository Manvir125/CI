import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getAgendaAppointments, getServiceAgendas, type AgendaAppointmentDto, type AgendaDto } from '../api/his';
import { getApiErrorMessage } from '../api/client';

export default function DashboardPage() {
    const { user, logoutUser, hasRole } = useAuth();
    const navigate = useNavigate();

    const [agendas, setAgendas] = useState<AgendaDto[]>([]);
    const [selectedAgenda, setSelectedAgenda] = useState<AgendaDto | null>(null);
    const [appointments, setAppointments] = useState<AgendaAppointmentDto[]>([]);
    const [loadingAgendas, setLoadingAgendas] = useState(false);
    const [loadingAppointments, setLoadingAppointments] = useState(false);
    const [agendaError, setAgendaError] = useState('');

    const isProfessional = hasRole('PROFESSIONAL');

    useEffect(() => {
        if (!isProfessional || !user?.serviceCode || !user?.dni) {
            return;
        }

        const loadAgendas = async () => {
            setLoadingAgendas(true);
            setAgendaError('');
            try {
                const data = await getServiceAgendas(user.serviceCode!);
                setAgendas(data);
                setSelectedAgenda(data[0] ?? null);
            } catch (error) {
                setAgendaError(getApiErrorMessage(
                    error,
                    'No se han podido cargar las agendas de tu especialidad.'
                ));
                setAgendas([]);
                setSelectedAgenda(null);
            } finally {
                setLoadingAgendas(false);
            }
        };

        loadAgendas();
    }, [isProfessional, user?.serviceCode, user?.dni]);

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
                setAppointments(data);
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
        <div className="min-h-screen bg-gray-100">

            <nav className="bg-emerald-700 text-white px-6 py-4 flex justify-between items-center">
                <div>
                    <h1 className="font-bold text-lg">CI Digital - CHPC</h1>
                    <p className="text-emerald-300 text-xs">
                        Gestion de Consentimientos Informados
                    </p>
                </div>
                <div className="flex items-center gap-4">
                    <span className="text-sm">
                        {user?.fullName}
                        <span className="ml-2 bg-emerald-600 text-xs px-2 py-0.5 rounded-full">
                            {user?.roles[0]}
                        </span>
                    </span>
                    <button
                        onClick={handleLogout}
                        className="bg-emerald-600 hover:bg-emerald-500 px-3 py-1 rounded text-sm transition-colors"
                    >
                        Cerrar sesion
                    </button>
                </div>
            </nav>

            <main className="p-6 max-w-7xl mx-auto space-y-6">
                <div className="flex items-center justify-between gap-4 flex-wrap">
                    <div>
                        <h2 className="text-xl font-bold text-gray-800">
                            Panel principal
                        </h2>
                        {isProfessional && user?.serviceCode && (
                            <p className="text-sm text-gray-500 mt-1">
                                Flujo rapido para profesionales: selecciona agenda, luego cita y crea el consentimiento.
                            </p>
                        )}
                    </div>
                    {isProfessional && (
                        <button
                            onClick={() => navigate('/requests')}
                            className="bg-white border border-gray-300 text-gray-700 px-4 py-2 rounded-lg text-sm hover:bg-gray-50 transition-colors"
                        >
                            Ver todas las solicitudes
                        </button>
                    )}
                </div>

                {isProfessional && (
                    <section className="grid grid-cols-1 xl:grid-cols-[320px_minmax(0,1fr)] gap-6">
                        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden">
                            <div className="px-5 py-4 border-b border-gray-100 bg-gradient-to-r from-emerald-700 to-emerald-600 text-white">
                                <p className="text-xs uppercase tracking-[0.2em] text-emerald-100">Especialidad</p>
                                <h3 className="text-lg font-semibold mt-1">
                                    {user?.serviceCode || 'Sin servicio asignado'}
                                </h3>
                                <p className="text-sm text-emerald-100 mt-1">
                                    Agendas disponibles para iniciar consentimientos.
                                </p>
                            </div>
                            <div className="p-4 space-y-3">
                                {!user?.serviceCode && (
                                    <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                                        Tu usuario no tiene especialidad o servicio asignado.
                                    </div>
                                )}
                                {user?.serviceCode && !user?.dni && (
                                    <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                                        Tu usuario no tiene DNI configurado y no puede consultar citas en ApiKewan.
                                    </div>
                                )}
                                {agendaError && (
                                    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
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
                                                    ? 'border-emerald-500 bg-emerald-50 shadow-sm'
                                                    : 'border-gray-200 hover:border-emerald-300 hover:bg-gray-50'
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
                                                        : 'bg-gray-200 text-gray-600'
                                                        }`}>
                                                        {agenda.status || 'SIN ESTADO'}
                                                    </span>
                                                </div>
                                                <div className="mt-3 flex flex-wrap gap-2 text-xs text-gray-500">
                                                    {agenda.serviceCode && <span>Codigo: {agenda.serviceCode}</span>}
                                                    {agenda.serviceName && <span>Servicio: {agenda.serviceName}</span>}
                                                </div>
                                            </button>
                                        );
                                    })
                                )}
                            </div>
                        </div>

                        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden">
                            <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between gap-4 flex-wrap">
                                <div>
                                    <p className="text-xs uppercase tracking-[0.2em] text-gray-400">Citas</p>
                                    <h3 className="text-lg font-semibold text-gray-800 mt-1">
                                        {selectedAgenda ? selectedAgenda.name : 'Selecciona una agenda'}
                                    </h3>
                                    <p className="text-sm text-gray-500 mt-1">
                                        Elige una cita para ir directamente a crear la solicitud de consentimiento.
                                    </p>
                                </div>
                                {selectedAgenda && (
                                    <div className="text-sm text-gray-500">
                                        {selectedAgenda.professional?.specialtyName || selectedAgenda.serviceName}
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
                                                className="rounded-2xl border border-gray-200 p-4 hover:border-emerald-300 transition-colors"
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
                                                        className="bg-emerald-700 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-emerald-600 transition-colors"
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
                        className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 cursor-pointer hover:shadow-md hover:border-blue-300 transition-all">
                        <div className="text-3xl mb-3">Firma</div>
                        <h3 className="font-semibold text-gray-800">Mi firma</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Gestionar mi firma para los consentimientos
                        </p>
                    </div>

                    <div
                        onClick={() => navigate('/requests')}
                        className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 cursor-pointer hover:shadow-md hover:border-emerald-300 transition-all">
                        <div className="text-3xl mb-3">Solicitudes</div>
                        <h3 className="font-semibold text-gray-800">Solicitudes</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Gestionar consentimientos y envios
                        </p>
                    </div>

                    {(hasRole('ADMIN') || hasRole('ADMINISTRATIVE') || hasRole('SUPERVISOR')) && (
                        <div
                            onClick={() => navigate('/templates')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 cursor-pointer hover:shadow-md hover:border-emerald-300 transition-all">
                            <div className="text-3xl mb-3">Plantillas</div>
                            <h3 className="font-semibold text-gray-800">Plantillas</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Gestionar plantillas de consentimiento
                            </p>
                        </div>
                    )}

                    {hasRole('ADMIN') && (
                        <div
                            onClick={() => navigate('/users')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 cursor-pointer hover:shadow-md hover:border-emerald-300 transition-all">
                            <div className="text-3xl mb-3">Usuarios</div>
                            <h3 className="font-semibold text-gray-800">Usuarios</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Gestionar usuarios y roles
                            </p>
                        </div>
                    )}

                    {(hasRole('ADMIN') || hasRole('PROFESSIONAL') || hasRole('ADMINISTRATIVE')) && (
                        <div
                            onClick={() => window.open('/kiosk', '_blank')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 cursor-pointer hover:shadow-md hover:border-emerald-300 transition-all">
                            <div className="text-3xl mb-3">Kiosco</div>
                            <h3 className="font-semibold text-gray-800">Firma presencial</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Modo kiosco para firma en el centro
                            </p>
                        </div>
                    )}

                    {(hasRole('ADMIN') || hasRole('SUPERVISOR')) && (
                        <div
                            onClick={() => navigate('/audit')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 cursor-pointer hover:shadow-md hover:border-emerald-300 transition-all">
                            <div className="text-3xl mb-3">Auditoria</div>
                            <h3 className="font-semibold text-gray-800">Auditoria</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Registro de actividad del sistema
                            </p>
                        </div>
                    )}

                    {hasRole('PROFESSIONAL') && (
                        <div
                            onClick={() => navigate('/pending-signatures')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 cursor-pointer hover:shadow-md hover:border-emerald-300 transition-all">
                            <div className="text-3xl mb-3">Pendientes</div>
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
