import axios from 'axios';
import type { PenEvent } from '../hooks/useXPPenTablet';

const portalClient = axios.create({
    baseURL: 'http://localhost:8080',
    headers: { 'Content-Type': 'application/json' },
});

export interface PortalConsentDto {
    requestId: number;
    nhc: string;
    patientName: string;
    professionalName: string;
    serviceName: string;
    procedureName: string;
    templateName: string;
    contentHtml: string;
    episodeDate: string;
    expiresAt: string;
    status: string;
    maskedPhone: string;
    isGroup?: boolean;
    groupDocuments?: string[];
    groupRequestIds?: number[];
}

export const loadConsent = async (token: string): Promise<PortalConsentDto> => {
    const { data } = await portalClient.get(`/api/patient/sign/${token}`);
    return data;
};

export const verifyCode = async (
    token: string,
    code: string
): Promise<{ success: boolean; message: string }> => {
    const { data } = await portalClient.post(
        `/api/patient/sign/${token}/verify`,
        { code }
    );
    return data;
};

export const sendCode = async (token: string): Promise<void> => {
    await portalClient.post(`/api/patient/sign/${token}/send-code`);
};

export const resendCode = async (token: string): Promise<void> => {
    await portalClient.post(`/api/patient/sign/${token}/resend-code`);
};

export const submitSignature = async (
    token: string,
    signatureImageBase64: string,
    readCheckConfirmed: boolean,
    confirmation: 'SIGNED' | 'REJECTED',
    rejectionReason?: string,
    events?: PenEvent[]
): Promise<{ status: string; message: string }> => {
    const { data } = await portalClient.post(
        `/api/patient/sign/${token}/submit`,
        { signatureImageBase64, readCheckConfirmed, confirmation, rejectionReason, events }
    );
    return data;
};
