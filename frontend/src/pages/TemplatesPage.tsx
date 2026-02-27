import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
    getTemplates, deactivateTemplate, duplicateTemplate
} from '../api/templates';
import type { Template } from '../types';

export default function TemplatesPage() {
    const [templates, setTemplates] = useState<Template[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const { hasRole } = useAuth();
    const navigate = useNavigate();
    const canManage = hasRole('ADMIN') || hasRole('ADMINISTRATIVE');

    useEffect(() => {
        loadTemplates();
    }, []);

    const loadTemplates = async () => {
        try {
            const data = await getTemplates();
            setTemplates(data);
        } catch {
            setError('Error al cargar las plantillas');
        } finally {
            setLoading(false);
        }
    };

    const handleDeactivate = async (id: number) => {
        if (!confirm('¿Desactivar esta plantilla?')) return;
        try {
            await deactivateTemplate(id);
            setTemplates(templates.filter(t => t.id !== id));
        } catch {
            setError('Error al desactivar la plantilla');
        }
    };

    const handleDuplicate = async (id: number) => {
        try {
            const copy = await duplicateTemplate(id);
            setTemplates([...templates, copy]);
        } catch {
            setError('Error al duplicar la plantilla');
        }
    };

    if (loading) return (
        <div className="min-h-screen flex items-center justify-center">
            <p className="text-gray-500">Cargando plantillas...</p>
        </div>
    );

    return (
        <div className="min-h-screen bg-gray-100">

            {/* Barra superior */}
            <nav className="bg-emerald-700 text-white px-6 py-4 flex justify-between items-center">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/dashboard')}
                        className="text-emerald-300 hover:text-white text-sm transition-colors"
                    >
                        ← Dashboard
                    </button>
                    <span className="text-emerald-500">|</span>
                    <h1 className="font-bold">Plantillas de Consentimiento</h1>
                </div>
                {canManage && (
                    <button
                        onClick={() => navigate('/templates/new')}
                        className="bg-green-600 hover:bg-green-500 px-4 py-2 rounded-lg 
                       text-sm font-medium transition-colors"
                    >
                        + Nueva plantilla
                    </button>
                )}
            </nav>

            <main className="p-6 max-w-6xl mx-auto">

                {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700 
                          px-4 py-3 rounded-lg mb-4 text-sm">
                        {error}
                    </div>
                )}

                {templates.length === 0 ? (
                    <div className="bg-white rounded-xl p-12 text-center shadow-sm">
                        <p className="text-gray-400 text-lg">No hay plantillas activas</p>
                        {canManage && (
                            <button
                                onClick={() => navigate('/templates/new')}
                                className="mt-4 bg-emerald-700 text-white px-6 py-2 rounded-lg
                           hover:bg-emerald-600 transition-colors"
                            >
                                Crear primera plantilla
                            </button>
                        )}
                    </div>
                ) : (
                    <div className="grid gap-4">
                        {templates.map(template => (
                            <div
                                key={template.id}
                                className="bg-white rounded-xl p-5 shadow-sm border border-gray-200
                           hover:shadow-md transition-shadow"
                            >
                                <div className="flex justify-between items-start">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-3 mb-1">
                                            <h3 className="font-semibold text-gray-800 text-lg">
                                                {template.name}
                                            </h3>
                                            <span className="bg-emerald-100 text-emerald-700 text-xs 
                                       px-2 py-0.5 rounded-full">
                                                v{template.version}
                                            </span>
                                        </div>
                                        <div className="flex gap-4 text-sm text-gray-500">
                                            {template.serviceCode && (
                                                <span>🏥 {template.serviceCode}</span>
                                            )}
                                            {template.procedureCode && (
                                                <span>🔬 {template.procedureCode}</span>
                                            )}
                                            <span>👤 {template.createdByName}</span>
                                            <span>
                                                📅 {new Date(template.createdAt).toLocaleDateString('es-ES')}
                                            </span>
                                        </div>
                                    </div>

                                    {/* Acciones */}
                                    {canManage && (
                                        <div className="flex gap-2 ml-4">
                                            <button
                                                onClick={() => navigate(`/templates/${template.id}/edit`)}
                                                className="bg-emerald-50 hover:bg-emerald-100 text-emerald-700
                                                px-3 py-1.5 rounded-lg text-sm transition-colors"
                                            >
                                                Editar
                                            </button>
                                            <button
                                                onClick={() => handleDuplicate(template.id)}
                                                className="bg-gray-100 hover:bg-gray-200 text-gray-700 
                                   px-3 py-1.5 rounded-lg text-sm transition-colors"
                                            >
                                                Duplicar
                                            </button>
                                            <button
                                                onClick={() => handleDeactivate(template.id)}
                                                className="bg-red-50 hover:bg-red-100 text-red-600 
                                   px-3 py-1.5 rounded-lg text-sm transition-colors"
                                            >
                                                Desactivar
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </main>
        </div>
    );
}