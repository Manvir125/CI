import axios from 'axios';

const client = axios.create({
    baseURL: 'http://localhost:8080',
    headers: {
        'Content-Type': 'application/json',
    },
});

// Añade el token JWT a cada petición si existe
client.interceptors.request.use(
    (config) => {
        try {
            const stored = localStorage.getItem('auth');
            if (stored) {
                const auth = JSON.parse(stored);
                if (auth?.token) {
                    config.headers.Authorization = `Bearer ${auth.token}`;
                }
            }
        } catch {
            // Si el JSON está corrupto lo ignoramos
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Gestiona errores de respuesta
client.interceptors.response.use(
    (response) => {
        console.log('Respuesta Axios:', response.status, response.config.url);
        return response;
    },
    (error) => {
        console.error('Error Axios:', error.response?.status, error.config?.url);
        return Promise.reject(error);
    }
);

export const getApiErrorMessage = (error: unknown, fallback: string): string => {
    if (axios.isAxiosError(error)) {
        const data = error.response?.data;
        if (typeof data === 'string' && data.trim()) {
            return data;
        }
        if (data && typeof data.message === 'string' && data.message.trim()) {
            return data.message;
        }
        if (typeof error.message === 'string' && error.message.trim()) {
            return error.message;
        }
    }
    if (error instanceof Error && error.message.trim()) {
        return error.message;
    }
    return fallback;
};

export default client;
