import client from './client';

export interface SignatureStatus {
    hasSignature: boolean;
    updatedAt: string | null;
}

export const getSignatureStatus = async (): Promise<SignatureStatus> => {
    const { data } = await client.get('/api/profile/signature');
    return data;
};

export const saveSignature = async (
    signatureImageBase64: string
): Promise<void> => {
    await client.post('/api/profile/signature', { signatureImageBase64 });
};

export const deleteSignature = async (): Promise<void> => {
    await client.delete('/api/profile/signature');
};