import client from './client';
import type { LoginResponse } from '../types';

export const login = async (
    username: string,
    password: string
): Promise<LoginResponse> => {
    const response = await client.post<LoginResponse>('/api/auth/login', {
        username,
        password,
    });
    return response.data;
};

export const getCurrentUser = async (): Promise<LoginResponse> => {
    const response = await client.get<LoginResponse>('/api/auth/me');
    return response.data;
};
