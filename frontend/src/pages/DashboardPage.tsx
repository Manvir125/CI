import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function DashboardPage() {
    const { user, logoutUser, hasRole } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logoutUser();
        navigate('/login');
    };

    return (
        <div className="min-h-screen bg-gray-100">

            {/* Barra superior */}
            <nav className="bg-emerald-700 text-white px-6 py-4 flex justify-between items-center">
                <div>
                    <h1 className="font-bold text-lg">CI Digital — CHPC</h1>
                    <p className="text-emerald-300 text-xs">
                        Gestión de Consentimientos Informados
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
                        className="bg-emerald-600 hover:bg-emerald-500 px-3 py-1 rounded text-sm
                       transition-colors"
                    >
                        Cerrar sesión
                    </button>
                </div>
            </nav>

            {/* Contenido */}
            <main className="p-6 max-w-6xl mx-auto">
                <h2 className="text-xl font-bold text-gray-800 mb-6">
                    Panel principal
                </h2>

                {/* Tarjetas */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    <div
                        onClick={() => navigate('/profile')}
                        className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                        cursor-pointer hover:shadow-md hover:border-blue-300
                        transition-all">
                        <div className="text-3xl mb-3">✍️</div>
                        <h3 className="font-semibold text-gray-800">Mi firma</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Gestionar mi firma para los consentimientos
                        </p>
                    </div>

                    {/* Solicitudes — todos los roles */}
                    <div
                        onClick={() => navigate('/requests')}
                        className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                       cursor-pointer hover:shadow-md hover:border-emerald-300
                       transition-all">
                        <div className="text-3xl mb-3">📤</div>
                        <h3 className="font-semibold text-gray-800">Solicitudes</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Gestionar consentimientos y envíos
                        </p>
                    </div>

                    {/* Plantillas — ADMIN, ADMINISTRATIVE, SUPERVISOR */}
                    {(hasRole('ADMIN') || hasRole('ADMINISTRATIVE') || hasRole('SUPERVISOR')) && (
                        <div
                            onClick={() => navigate('/templates')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                         cursor-pointer hover:shadow-md hover:border-emerald-300
                         transition-all">
                            <div className="text-3xl mb-3">📋</div>
                            <h3 className="font-semibold text-gray-800">Plantillas</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Gestionar plantillas de consentimiento
                            </p>
                        </div>
                    )}

                    {/* Usuarios — solo ADMIN */}
                    {hasRole('ADMIN') && (
                        <div
                            onClick={() => navigate('/users')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                         cursor-pointer hover:shadow-md hover:border-emerald-300
                         transition-all">
                            <div className="text-3xl mb-3">👥</div>
                            <h3 className="font-semibold text-gray-800">Usuarios</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Gestionar usuarios y roles
                            </p>
                        </div>
                    )}

                    {/* Firma presencial — ADMIN, PROFESSIONAL, ADMINISTRATIVE */}
                    {(hasRole('ADMIN') || hasRole('PROFESSIONAL') || hasRole('ADMINISTRATIVE')) && (
                        <div
                            onClick={() => window.open('/kiosk', '_blank')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                         cursor-pointer hover:shadow-md hover:border-emerald-300
                         transition-all">
                            <div className="text-3xl mb-3">🖊️</div>
                            <h3 className="font-semibold text-gray-800">Firma presencial</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Modo kiosco para firma en el centro
                            </p>
                        </div>
                    )}

                    {/* Auditoría — ADMIN, SUPERVISOR */}
                    {(hasRole('ADMIN') || hasRole('SUPERVISOR')) && (
                        <div
                            onClick={() => navigate('/audit')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                         cursor-pointer hover:shadow-md hover:border-emerald-300
                         transition-all">
                            <div className="text-3xl mb-3">🔍</div>
                            <h3 className="font-semibold text-gray-800">Auditoría</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Registro de actividad del sistema
                            </p>
                        </div>
                    )}

                    {/* Firmas pendientes — PROFESSIONAL */}
                    {hasRole('PROFESSIONAL') && (
                        <div
                            onClick={() => navigate('/pending-signatures')}
                            className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                         cursor-pointer hover:shadow-md hover:border-emerald-300
                         transition-all">
                            <div className="text-3xl mb-3">⏳</div>
                            <h3 className="font-semibold text-gray-800">Firmas pendientes</h3>
                            <p className="text-gray-500 text-sm mt-1">
                                Firmar consentimientos pendientes
                            </p>
                        </div>
                    )}

                </div>
            </main >
        </div >
    );
}