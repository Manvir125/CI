import client from './client';
import type { PatientDto } from './his';

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
    assignedProfessionalId?: number | null;
    assignedProfessionalName?: string | null;
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
    patientDni?: string;
    patientSip?: string;
    observations?: string;
    dynamicFields?: Record<string, string>;
    customTemplateHtml?: string;
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
    patientDni?: string;
    patientSip?: string;
    items: {
        templateId: number;
        responsibleService: string;
        assignedProfessionalId?: number | null;
        channel: string;
        autoSign?: boolean;
        observations?: string;
        dynamicFields?: Record<string, string>;
        customTemplateHtml?: string;
    }[];
}

export interface KioskPatientSearchResponse {
    patient: PatientDto | null;
    requests: ConsentRequestResponse[];
}

export const createGroup = async (
    dto: ConsentGroupDto,
    useMtls: boolean = false
): Promise<any> => {
    if (useMtls) {
        const mtlsUrl = `https://${window.location.hostname}:8444`;
        
        let token = '';
        try {
            const stored = localStorage.getItem('auth');
            if (stored) {
                const auth = JSON.parse(stored);
                token = auth?.token || '';
            }
        } catch (e) {
            console.error('Error parsing auth token', e);
        }

        const response = await fetch(`${mtlsUrl}/api/consent-groups`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(dto)
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: 'Error en firma con certificado' }));
            throw { response: { data: errorData, status: response.status } };
        }
        return await response.json();
    }

    const { data } = await client.post('/api/consent-groups', dto);
    return data;
};

export const getPendingMySignature = async (): Promise<ConsentRequestResponse[]> => {
    const { data } = await client.get('/api/consent-groups/pending-my-signature');
    return data;
};

export const searchKioskRequests = async (params: {
    sip?: string;
    dni?: string;
}): Promise<KioskPatientSearchResponse> => {
    const query = new URLSearchParams();
    if (params.sip) query.append('sip', params.sip);
    if (params.dni) query.append('dni', params.dni);
    const { data } = await client.get(`/api/consent-requests/kiosk/search?${query.toString()}`);
    return data;
};

export const professionalSign = async (requestId: number): Promise<void> => {
    await client.post(`/api/consent-groups/requests/${requestId}/professional-sign`);
};

export const professionalSignWithCert = async (requestId: number): Promise<void> => {
    // LLamar al puerto 8444 para requerir mTLS (fuerza HTTPS)
    const mtlsUrl = `https://${window.location.hostname}:8444`;
    
    // Extraer el token del objeto 'auth' en localStorage (mismo patrón que api/client.ts)
    let token = '';
    try {
        const stored = localStorage.getItem('auth');
        if (stored) {
            const auth = JSON.parse(stored);
            token = auth?.token || '';
        }
    } catch (e) {
        console.error('Error parsing auth token', e);
    }

    const response = await fetch(`${mtlsUrl}/api/consent-groups/requests/${requestId}/professional-sign-certificate`, {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`
        }
    });

    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Error al firmar con certificado digital');
    }
};
