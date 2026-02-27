import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface Props {
    title: string;
    backTo?: string;
    backLabel?: string;
    actions?: React.ReactNode;
}

export default function Navbar({ title, backTo, backLabel, actions }: Props) {
    const { user, logoutUser } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logoutUser();
        navigate('/login');
    };

    return (
        <nav className="bg-blue-900 text-white px-6 py-4 flex justify-between items-center">
            <div className="flex items-center gap-3">
                {backTo && (
                    <>
                        <button
                            onClick={() => navigate(backTo)}
                            className="text-blue-300 hover:text-white text-sm transition-colors"
                        >
                            ← {backLabel ?? 'Volver'}
                        </button>
                        <span className="text-blue-500">|</span>
                    </>
                )}
                <div>
                    <h1 className="font-bold">{title}</h1>
                    {!backTo && (
                        <p className="text-blue-300 text-xs">
                            Gestión de Consentimientos Informados
                        </p>
                    )}
                </div>
            </div>

            <div className="flex items-center gap-3">
                {actions}
                {!backTo && (
                    <div className="flex items-center gap-3">
                        <span className="text-sm hidden md:block">
                            {user?.fullName}
                            <span className="ml-2 bg-blue-700 text-xs px-2 py-0.5 rounded-full">
                                {user?.roles[0]}
                            </span>
                        </span>
                        <button
                            onClick={handleLogout}
                            className="bg-blue-700 hover:bg-blue-600 px-3 py-1 rounded
                         text-sm transition-colors"
                        >
                            Cerrar sesión
                        </button>
                    </div>
                )}
            </div>
        </nav>
    );
}