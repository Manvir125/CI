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
        // Solo redirige al login si NO estamos ya en el login
        if (error.response?.status === 401 &&
            !window.location.pathname.includes('/login')) {
            localStorage.removeItem('auth');
            window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

export default client;