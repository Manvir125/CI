import { createContext, useCallback, useContext, useState, useEffect, type ReactNode } from 'react';
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

    const loginUser = useCallback((userData: AuthUser) => {
        setUser(userData);
        localStorage.setItem('auth', JSON.stringify(userData));
    }, []);

    const logoutUser = useCallback(() => {
        setUser(null);
        localStorage.removeItem('auth');
    }, []);

    const updateSessionUser = useCallback((partialUser: Partial<AuthUser>) => {
        setUser(currentUser => {
            if (!currentUser) return currentUser;
            const updatedUser = { ...currentUser, ...partialUser };
            localStorage.setItem('auth', JSON.stringify(updatedUser));
            return updatedUser;
        });
    }, []);

    const hasRole = useCallback((role: string): boolean => {
        return user?.roles.includes(role) ?? false;
    }, [user?.roles]);

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
