import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import type { AuthUser } from '../types';

interface AuthContextType {
    user: AuthUser | null;
    isAuthenticated: boolean;
    loading: boolean;
    loginUser: (userData: AuthUser) => void;
    logoutUser: () => void;
    updateSessionUser: (partialUser: Partial<AuthUser>) => void;
    hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        try {
            const stored = localStorage.getItem('auth');
            if (stored) {
                setUser(JSON.parse(stored));
            }
        } catch {
            localStorage.removeItem('auth');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (loading) {
            return;
        }

        if (user) {
            localStorage.setItem('auth', JSON.stringify(user));
        } else {
            localStorage.removeItem('auth');
        }
    }, [loading, user]);

    const loginUser = (userData: AuthUser) => {
        setUser(userData);
    };

    const logoutUser = () => {
        setUser(null);
    };

    const updateSessionUser = (partialUser: Partial<AuthUser>) => {
        if (!user) return;
        const updatedUser = { ...user, ...partialUser };
        setUser(updatedUser);
    };

    const hasRole = (role: string): boolean => {
        return user?.roles.includes(role) ?? false;
    };

    return (
        <AuthContext.Provider value={{
            user,
            isAuthenticated: !!user,
            loading,
            loginUser,
            logoutUser,
            updateSessionUser,
            hasRole
        }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth debe usarse dentro de AuthProvider');
    }
    return context;
}
