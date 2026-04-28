import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { createTemplate, extractPdfToHtml } from '../api/templates';
import type { TemplateField } from '../types';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';

export default function NewTemplatePage() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [extracting, setExtracting] = useState(false);
    const [error, setError] = useState('');
    const fileInputRef = useRef<HTMLInputElement>(null);

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

    const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        setExtracting(true);
        setError('');
        try {
            const html = await extractPdfToHtml(file);
            setForm(prev => ({ 
                ...prev, 
                contentHtml: prev.contentHtml + (prev.contentHtml ? '\n\n' : '') + html 
            }));
        } catch {
            setError('Error al extraer texto del PDF');
        } finally {
            setExtracting(false);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
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
        <div className="page-shell">
            <nav className="app-topbar">
                <button
                    onClick={() => navigate('/templates')}
                    className="soft-button-ghost text-sm"
                >
                    ← Plantillas
                </button>
                <span className="text-emerald-200">|</span>
                <h1 className="font-bold">Nueva Plantilla</h1>
            </nav>

            <main className="page-main max-w-5xl space-y-6">
                <section className="page-hero-lite">
                    <div>
                        <p className="section-kicker">Nueva plantilla</p>
                        <h2 className="page-hero-lite__title">Diseña el consentimiento base con mejor legibilidad</h2>
                        <p className="page-hero-lite__text">
                            Organiza contenido, campos dinámicos e importación desde PDF dentro de un lienzo más limpio.
                        </p>
                    </div>
                </section>
                <form onSubmit={handleSubmit} className="space-y-6">

                    {/* Datos básicos */}
                    <div className="soft-form-card space-y-4">
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
                            <div className="flex justify-between items-center mb-1">
                                <label className="block text-sm font-medium text-gray-700">
                                    Contenido HTML *
                                </label>
                                <div>
                                    <input 
                                        type="file" 
                                        accept="application/pdf" 
                                        className="hidden" 
                                        ref={fileInputRef}
                                        onChange={handleFileUpload}
                                    />
                                    <button 
                                        type="button"
                                        onClick={() => fileInputRef.current?.click()}
                                        disabled={extracting}
                                        className="text-xs bg-emerald-50 text-emerald-700 px-3 py-1.5 rounded-lg hover:bg-emerald-100 disabled:opacity-50 transition-colors flex items-center gap-1 font-medium"
                                    >
                                        {extracting ? 'Extrayendo...' : '📄 Importar texto desde PDF'}
                                    </button>
                                </div>
                            </div>
                            <div className="flex flex-col gap-1">
                                <ReactQuill
                                    theme="snow"
                                    value={form.contentHtml}
                                    onChange={(val) => setForm({ ...form, contentHtml: val })}
                                    className="bg-white"
                                    style={{ minHeight: '300px' }}
                                />
                                <p className="text-xs text-gray-400 mt-1">
                                    Usa {'{{PATIENT_NAME}}'}, {'{{SERVICE}}'}, {'{{PROFESSIONAL_NAME}}'} como campos dinámicos
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Campos dinámicos */}
                    <div className="soft-form-card">
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
                        <div className="surface-note surface-note--danger text-sm">
                            {error}
                        </div>
                    )}

                    <div className="flex gap-3 justify-end">
                        <button
                            type="button"
                            onClick={() => navigate('/templates')}
                            className="soft-button-secondary"
                        >
                            Cancelar
                        </button>
                        <button
                            type="submit"
                            disabled={loading}
                            className="soft-button disabled:opacity-50"
                        >
                            {loading ? 'Guardando...' : 'Crear plantilla'}
                        </button>
                    </div>
                </form>
            </main>
        </div>
    );
}
