import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function DashboardPage() {
    const { user, logoutUser } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logoutUser();
        navigate('/login');
    };

    return (
        <div className="min-h-screen bg-gray-100">

            {/* Barra superior */}
            <nav className="bg-blue-900 text-white px-6 py-4 flex justify-between items-center">
                <div>
                    <h1 className="font-bold text-lg">CI Digital — CHPC</h1>
                    <p className="text-blue-300 text-xs">
                        Gestión de Consentimientos Informados
                    </p>
                </div>
                <div className="flex items-center gap-4">
                    <span className="text-sm">
                        {user?.fullName}
                        <span className="ml-2 bg-blue-700 text-xs px-2 py-0.5 rounded-full">
                            {user?.roles[0]}
                        </span>
                    </span>
                    <button
                        onClick={handleLogout}
                        className="bg-blue-700 hover:bg-blue-600 px-3 py-1 rounded text-sm
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

                {/* Tarjetas de acceso rápido */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div
                        onClick={() => navigate('/templates')}
                        className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                       cursor-pointer hover:shadow-md hover:border-blue-300
                       transition-all"
                    >
                        <div className="text-3xl mb-3">📋</div>
                        <h3 className="font-semibold text-gray-800">Plantillas</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Gestionar plantillas de consentimiento
                        </p>
                    </div>

                    <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                          opacity-50 cursor-not-allowed">
                        <div className="text-3xl mb-3">📤</div>
                        <h3 className="font-semibold text-gray-800">Solicitudes</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Disponible en Sprint 2
                        </p>
                    </div>

                    <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                          opacity-50 cursor-not-allowed">
                        <div className="text-3xl mb-3">📊</div>
                        <h3 className="font-semibold text-gray-800">Auditoría</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Disponible en Sprint 5
                        </p>
                    </div>
                    <div
                        onClick={() => navigate('/users')}
                        className="bg-white rounded-xl p-6 shadow-sm border border-gray-200
                        cursor-pointer hover:shadow-md hover:border-blue-300
                        transition-all"
                    >
                        <div className="text-3xl mb-3">👥</div>
                        <h3 className="font-semibold text-gray-800">Usuarios</h3>
                        <p className="text-gray-500 text-sm mt-1">
                            Gestionar usuarios y roles
                        </p>
                    </div>
                </div>
            </main>
        </div>
    );
}