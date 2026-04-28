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
        <div className="page-loading">
            <p className="text-gray-500">Cargando plantillas...</p>
        </div>
    );

    return (
        <div className="page-shell">

            {/* Barra superior */}
            <nav className="app-topbar">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/dashboard')}
                        className="soft-button-ghost text-sm"
                    >
                        ← Dashboard
                    </button>
                    <span className="text-emerald-200">|</span>
                    <h1 className="font-bold">Plantillas de Consentimiento</h1>
                </div>
                {canManage && (
                    <button
                        onClick={() => navigate('/templates/new')}
                        className="soft-button text-sm"
                    >
                        + Nueva plantilla
                    </button>
                )}
            </nav>

            <main className="page-main space-y-6">
                <section className="page-hero-lite">
                    <div>
                        <p className="section-kicker">Biblioteca</p>
                        <h2 className="page-hero-lite__title">Plantillas listas para reutilizar y versionar</h2>
                        <p className="page-hero-lite__text">
                            Mantén el catálogo ordenado, duplica contenido y evoluciona versiones con una presentación más clara.
                        </p>
                    </div>
                </section>

                {error && (
                    <div className="surface-note surface-note--danger mb-4 text-sm">
                        {error}
                    </div>
                )}

                {templates.length === 0 ? (
                    <div className="soft-empty">
                        <p className="text-gray-400 text-lg">No hay plantillas activas</p>
                        {canManage && (
                            <button
                                onClick={() => navigate('/templates/new')}
                                className="soft-button mt-4"
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
                                className="soft-list-card soft-list-item p-5"
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
                                                className="soft-subtle-button text-sm"
                                            >
                                                Editar
                                            </button>
                                            <button
                                                onClick={() => handleDuplicate(template.id)}
                                                className="soft-button-secondary text-sm px-3 py-1.5"
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
