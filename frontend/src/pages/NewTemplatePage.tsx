import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createTemplate } from '../api/templates';
import type { TemplateField } from '../types';

export default function NewTemplatePage() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [form, setForm] = useState({
        name: '',
        serviceCode: '',
        procedureCode: '',
        contentHtml: '',
    });

    const [fields, setFields] = useState<TemplateField[]>([]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');
        try {
            await createTemplate({ ...form, fields });
            navigate('/templates');
        } catch {
            setError('Error al crear la plantilla');
        } finally {
            setLoading(false);
        }
    };

    const addField = () => {
        setFields([...fields, {
            fieldKey: '', fieldLabel: '', fieldType: 'TEXT', required: true
        }]);
    };

    const updateField = (index: number, key: keyof TemplateField, value: string | boolean) => {
        const updated = [...fields];
        (updated[index] as any)[key] = value;
        setFields(updated);
    };

    const removeField = (index: number) => {
        setFields(fields.filter((_, i) => i !== index));
    };

    return (
        <div className="min-h-screen bg-gray-100">
            <nav className="bg-emerald-700 text-white px-6 py-4 flex items-center gap-3">
                <button
                    onClick={() => navigate('/templates')}
                    className="text-emerald-300 hover:text-white text-sm transition-colors"
                >
                    ← Plantillas
                </button>
                <span className="text-emerald-500">|</span>
                <h1 className="font-bold">Nueva Plantilla</h1>
            </nav>

            <main className="p-6 max-w-5xl mx-auto">
                <form onSubmit={handleSubmit} className="space-y-6">

                    {/* Datos básicos */}
                    <div className="bg-white rounded-xl p-6 shadow-sm space-y-4">
                        <h2 className="font-semibold text-gray-800 text-lg">Datos básicos</h2>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Nombre *
                            </label>
                            <input
                                type="text"
                                value={form.name}
                                onChange={e => setForm({ ...form, name: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                required
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Código de servicio
                                </label>
                                <input
                                    type="text"
                                    value={form.serviceCode}
                                    onChange={e => setForm({ ...form, serviceCode: e.target.value })}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2
                             focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                    placeholder="CIR, MED, ONC..."
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Código de procedimiento
                                </label>
                                <input
                                    type="text"
                                    value={form.procedureCode}
                                    onChange={e => setForm({ ...form, procedureCode: e.target.value })}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2
                             focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                    placeholder="PROC-001..."
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Contenido HTML *
                            </label>
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                                <div className="flex flex-col gap-1">
                                    <textarea
                                        value={form.contentHtml}
                                        onChange={e => setForm({ ...form, contentHtml: e.target.value })}
                                        rows={14}
                                        className="w-full border border-gray-300 rounded-lg px-3 py-2
                           focus:outline-none focus:ring-2 focus:ring-emerald-500
                           font-mono text-sm whitespace-pre"
                                        placeholder="<h2>Consentimiento Informado</h2>&#10;<p>Yo, {{PATIENT_NAME}}...</p>"
                                        required
                                    />
                                    <p className="text-xs text-gray-400 mt-1">
                                        Usa {'{{PATIENT_NAME}}'}, {'{{SERVICE}}'}, {'{{PROFESSIONAL_NAME}}'} como campos dinámicos
                                    </p>
                                </div>
                                <div className="border border-gray-300 rounded-lg p-4 bg-white overflow-y-auto min-h-[300px] max-h-[400px]">
                                    <div className="text-xs font-semibold text-gray-500 mb-3 uppercase tracking-wider border-b pb-2">Vista Previa</div>
                                    <div 
                                        className="prose prose-sm max-w-none text-gray-800"
                                        dangerouslySetInnerHTML={{ __html: form.contentHtml || '<p class="text-gray-400 italic">La vista previa aparecerá aquí...</p>' }}
                                    />
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Campos dinámicos */}
                    <div className="bg-white rounded-xl p-6 shadow-sm">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="font-semibold text-gray-800 text-lg">
                                Campos dinámicos
                            </h2>
                            <button
                                type="button"
                                onClick={addField}
                                className="bg-emerald-50 hover:bg-emerald-100 text-emerald-700 px-3 py-1.5
                           rounded-lg text-sm transition-colors"
                            >
                                + Añadir campo
                            </button>
                        </div>

                        {fields.length === 0 && (
                            <p className="text-gray-400 text-sm text-center py-4">
                                No hay campos dinámicos. Pulsa "Añadir campo" para agregar uno.
                            </p>
                        )}

                        {fields.map((field, index) => (
                            <div
                                key={index}
                                className="border border-gray-200 rounded-lg p-4 mb-3 
                           grid grid-cols-2 gap-3"
                            >
                                <input
                                    type="text"
                                    placeholder="Clave (ej: PATIENT_NAME)"
                                    value={field.fieldKey}
                                    onChange={e => updateField(index, 'fieldKey', e.target.value)}
                                    className="border border-gray-300 rounded px-3 py-1.5 text-sm
                             focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                />
                                <input
                                    type="text"
                                    placeholder="Etiqueta (ej: Nombre del paciente)"
                                    value={field.fieldLabel}
                                    onChange={e => updateField(index, 'fieldLabel', e.target.value)}
                                    className="border border-gray-300 rounded px-3 py-1.5 text-sm
                             focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                />
                                <select
                                    value={field.fieldType}
                                    onChange={e => updateField(index, 'fieldType', e.target.value)}
                                    className="border border-gray-300 rounded px-3 py-1.5 text-sm
                             focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                >
                                    <option value="TEXT">Texto libre</option>
                                    <option value="NHS_NUMBER">Número de la Seguridad Social</option>
                                    <option value="PATIENT_PHONE">Teléfono del paciente</option>
                                    <option value="PATIENT_EMAIL">Correo electrónico del paciente</option>
                                    <option value="PATIENT_NAME">Nombre paciente</option>
                                    <option value="PROFESSIONAL_NAME">Nombre profesional</option>
                                    <option value="SERVICE">Servicio</option>
                                    <option value="PROCEDURE">Procedimiento</option>
                                    <option value="DATE">Fecha</option>
                                </select>
                                <div className="flex items-center justify-between">
                                    <label className="flex items-center gap-2 text-sm text-gray-600">
                                        <input
                                            type="checkbox"
                                            checked={field.required}
                                            onChange={e => updateField(index, 'required', e.target.checked)}
                                        />
                                        Obligatorio
                                    </label>
                                    <button
                                        type="button"
                                        onClick={() => removeField(index)}
                                        className="text-red-500 hover:text-red-700 text-sm transition-colors"
                                    >
                                        Eliminar
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>

                    {error && (
                        <div className="bg-red-50 border border-red-200 text-red-700 
                            px-4 py-3 rounded-lg text-sm">
                            {error}
                        </div>
                    )}

                    <div className="flex gap-3 justify-end">
                        <button
                            type="button"
                            onClick={() => navigate('/templates')}
                            className="px-6 py-2 border border-gray-300 rounded-lg text-gray-700
                         hover:bg-gray-50 transition-colors"
                        >
                            Cancelar
                        </button>
                        <button
                            type="submit"
                            disabled={loading}
                            className="px-6 py-2 bg-emerald-700 text-white rounded-lg font-medium
                         hover:bg-emerald-600 disabled:opacity-50 transition-colors"
                        >
                            {loading ? 'Guardando...' : 'Crear plantilla'}
                        </button>
                    </div>
                </form>
            </main>
        </div>
    );
}