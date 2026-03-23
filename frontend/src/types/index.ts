// Respuesta del login
export interface LoginResponse {
    token: string;
    username: string;
    fullName: string;
    roles: string[];
    expiresInMs: number;
    serviceCode?: string;
}

// Usuario autenticado en el contexto
export interface AuthUser {
    token: string;
    username: string;
    fullName: string;
    roles: string[];
    serviceCode?: string;
}

// Campo dinámico de plantilla
export interface TemplateField {
    id?: number;
    fieldKey: string;
    fieldLabel: string;
    fieldType: string;
    required: boolean;
    defaultValue?: string;
}

// Plantilla de consentimiento
export interface Template {
    id: number;
    name: string;
    serviceCode: string;
    procedureCode: string;
    contentHtml: string;
    version: number;
    isActive: boolean;
    createdByName: string;
    createdAt: string;
    fields: TemplateField[];
}

// Petición para crear plantilla
export interface TemplateRequest {
    name: string;
    serviceCode: string;
    procedureCode: string;
    contentHtml: string;
    fields: TemplateField[];
}