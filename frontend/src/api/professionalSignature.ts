import client from './client';
import type { PenEvent } from '../hooks/useXPPenTablet';

export interface SignatureStatus {
    hasSignature: boolean;
    updatedAt: string | null;
    signatureMethod: 'TABLET' | 'CERTIFICATE';
}

export const getSignatureStatus = async (): Promise<SignatureStatus> => {
    const { data } = await client.get('/api/profile/signature');
    return data;
};

export const saveSignature = async (
    signatureImageBase64: string,
    events?: PenEvent[]
): Promise<void> => {
    await client.post('/api/profile/signature', { signatureImageBase64, events });
};

export const deleteSignature = async (): Promise<void> => {
    await client.delete('/api/profile/signature');
};

export const updateSignatureMethod = async (signatureMethod: 'TABLET' | 'CERTIFICATE'): Promise<void> => {
    await client.put('/api/profile/signature/method', { signatureMethod });
};