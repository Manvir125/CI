import client from './client';
import type { Template, TemplateRequest } from '../types';

export const getTemplates = async (): Promise<Template[]> => {
    const response = await client.get<Template[]>('/api/templates');
    return response.data;
};

export const getTemplateById = async (id: number): Promise<Template> => {
    const response = await client.get<Template>(`/api/templates/${id}`);
    return response.data;
};

export const createTemplate = async (
    data: TemplateRequest
): Promise<Template> => {
    const response = await client.post<Template>('/api/templates', data);
    return response.data;
};

export const deactivateTemplate = async (id: number): Promise<void> => {
    await client.delete(`/api/templates/${id}`);
};

export const duplicateTemplate = async (id: number): Promise<Template> => {
    const response = await client.post<Template>(`/api/templates/${id}/duplicate`);
    return response.data;
};