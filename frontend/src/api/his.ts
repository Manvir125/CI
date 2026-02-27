import client from './client';

export interface PatientDto {
    nhc: string;
    dni: string;
    firstName: string;
    lastName: string;
    birthDate: string;
    gender: string;
    email: string;
    phone: string;
    address: string;
    bloodType: string;
    allergies: string[];
    active: boolean;
}

export interface EpisodeDto {
    episodeId: string;
    nhc: string;
    serviceCode: string;
    serviceName: string;
    procedureCode: string;
    procedureName: string;
    episodeDate: string;
    admissionDate: string;
    expectedDischargeDate: string | null;
    ward: string;
    bed: string | null;
    attendingPhysician: string;
    status: string;
    priority: string;
}

export const getPatientByNhc = async (nhc: string): Promise<PatientDto> => {
    const { data } = await client.get(`/api/his/patients/nhc/${nhc}`);
    return data;
};

export const getPatientByDni = async (dni: string): Promise<PatientDto> => {
    const { data } = await client.get(`/api/his/patients/dni/${dni}`);
    return data;
};

export const searchPatients = async (q: string): Promise<PatientDto[]> => {
    const { data } = await client.get(`/api/his/patients/search?q=${q}`);
    return data;
};

export const getActiveEpisodes = async (nhc: string): Promise<EpisodeDto[]> => {
    const { data } = await client.get(`/api/his/patients/${nhc}/episodes`);
    return data;
};