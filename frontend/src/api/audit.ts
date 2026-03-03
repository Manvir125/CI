import client from './client';

export interface AuditLogResponse {
    id: number;
    timestampUtc: string;
    actorId: string;
    action: string;
    entityType: string;
    entityId: number;
    ipAddress: string;
    success: boolean;
    detailJson: string;
}

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
}

export const getAuditLogs = async (
    params: {
        actorId?: string;
        action?: string;
        from?: string;
        to?: string;
        page?: number;
        size?: number;
    }
): Promise<PageResponse<AuditLogResponse>> => {
    const p = new URLSearchParams();
    if (params.actorId) p.append('actorId', params.actorId);
    if (params.action) p.append('action', params.action);
    if (params.from) p.append('from', params.from);
    if (params.to) p.append('to', params.to);
    p.append('page', String(params.page ?? 0));
    p.append('size', String(params.size ?? 50));
    const { data } = await client.get(`/api/audit?${p}`);
    return data;
};

export const exportAuditCsv = async (from?: string, to?: string): Promise<void> => {
    const p = new URLSearchParams();
    if (from) p.append('from', from);
    if (to) p.append('to', to);
    const response = await client.get(`/api/audit/export/csv?${p}`, {
        responseType: 'blob',
    });
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `auditoria_chpc_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
};