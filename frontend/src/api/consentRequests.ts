import client from './client';

export interface ConsentRequestResponse {
    id: number;
    nhc: string;
    episodeId: string;
    templateName: string;
    templateId: number;
    professionalName: string;
    channel: string;
    status: string;
    patientEmail: string;
    patientPhone: string;
    cancellationReason: string;
    createdAt: string;
    updatedAt: string;
}

export interface ConsentRequestDto {
    nhc: string;
    episodeId: string;
    templateId: number;
    channel: string;
    patientEmail: string;
    patientPhone: string;
}

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export const createRequest = async (
    dto: ConsentRequestDto
): Promise<ConsentRequestResponse> => {
    const { data } = await client.post('/api/consent-requests', dto);
    return data;
};

export const sendRequest = async (
    id: number
): Promise<ConsentRequestResponse> => {
    const { data } = await client.post(`/api/consent-requests/${id}/send`);
    return data;
};

export const cancelRequest = async (
    id: number,
    reason: string
): Promise<ConsentRequestResponse> => {
    const { data } = await client.post(`/api/consent-requests/${id}/cancel`, {
        reason,
    });
    return data;
};

export const getMyRequests = async (
    status?: string,
    page = 0,
    size = 20
): Promise<PageResponse<ConsentRequestResponse>> => {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    params.append('page', String(page));
    params.append('size', String(size));
    const { data } = await client.get(`/api/consent-requests/my?${params}`);
    return data;
};