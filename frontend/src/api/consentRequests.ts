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
    groupId: number;
    responsibleService: string;
    professionalSigned: boolean;
    professionalSignerName: string;
    professionalSignedAt: string;
    observations?: string;
    dynamicFields?: Record<string, string>;

}

export interface ConsentRequestDto {
    nhc: string;
    episodeId: string;
    templateId: number;
    channel: string;
    patientEmail: string;
    patientPhone: string;
    observations?: string;
    dynamicFields?: Record<string, string>;
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

export const downloadPdf = async (id: number): Promise<void> => {
    const response = await client.get(`/api/consent-requests/${id}/pdf`, {
        responseType: 'blob',
    });
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `consentimiento_${id}.pdf`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
};

export const getKioskToken = async (id: number): Promise<string> => {
    const { data } = await client.post(`/api/consent-requests/${id}/kiosk-token`);
    return data.token;
};

export interface ConsentGroupDto {
    nhc: string;
    episodeId: string;
    patientEmail: string;
    patientPhone: string;
    items: {
        templateId: number;
        responsibleService: string;
        channel: string;
        observations?: string;
        dynamicFields?: Record<string, string>;
    }[];
}

export const createGroup = async (
    dto: ConsentGroupDto
): Promise<any> => {
    const { data } = await client.post('/api/consent-groups', dto);
    return data;
};

export const getPendingMySignature = async (): Promise<ConsentRequestResponse[]> => {
    const { data } = await client.get('/api/consent-groups/pending-my-signature');
    return data;
};

export const professionalSign = async (requestId: number): Promise<void> => {
    await client.post(`/api/consent-groups/requests/${requestId}/professional-sign`);
};