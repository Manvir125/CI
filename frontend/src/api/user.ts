import client from './client';

export interface UserResponse {
    id: number;
    username: string;
    fullName: string;
    email: string;
    isActive: boolean;
    roles: string[];
    lastLogin: string;
    createdAt: string;
    serviceCode?: string;
}

export interface UserRequest {
    username: string;
    fullName: string;
    email: string;
    password: string;
    roles: string[];
    serviceCode?: string;
}

export const getUsers = async (): Promise<UserResponse[]> => {
    const { data } = await client.get('/api/users');
    return data;
};

export const createUser = async (req: UserRequest): Promise<UserResponse> => {
    const { data } = await client.post('/api/users', req);
    return data;
};

export const updateUserRoles = async (
    id: number,
    roles: string[]
): Promise<UserResponse> => {
    const { data } = await client.put(`/api/users/${id}/roles`, { roles });
    return data;
};

export const activateUser = async (id: number): Promise<UserResponse> => {
    const { data } = await client.put(`/api/users/${id}/activate`);
    return data;
};

export const deactivateUser = async (id: number): Promise<UserResponse> => {
    const { data } = await client.put(`/api/users/${id}/deactivate`);
    return data;
};

export const deleteUser = async (id: number): Promise<void> => {
    await client.delete(`/api/users/${id}`);
};