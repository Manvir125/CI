import client from './client';

export interface PatientDto {
    nhc: string;
    sip?: string | null;
    dni: string;
    fullName?: string | null;
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

export interface ProfessionalDto {
    professionalId: string;
    sip?: string | null;
    dni?: string | null;
    fullName: string;
    specialtyCode?: string | null;
    specialtyName?: string | null;
}

export interface AgendaDto {
    agendaId: string;
    name: string;
    serviceCode?: string | null;
    serviceName?: string | null;
    status?: string | null;
    professional?: ProfessionalDto | null;
}

export interface AgendaAppointmentDto {
    episodeId: string;
    nhc: string;
    agendaId: string;
    professionalId: string;
    appointmentDate: string;
    startTime: string;
    endTime: string;
    prestation: string;
    status: string;
    patient?: PatientDto | null;
    agenda?: AgendaDto | null;
    professional?: ProfessionalDto | null;
}

export interface EpisodeDiagnosisDto {
    diagnosisCode?: string | null;
    diagnosisName: string;
    diagnosisType?: string | null;
    primary?: boolean | null;
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
    diagnosis?: string | null;
    icd10Code?: string | null;
    patient?: PatientDto | null;
    professional?: ProfessionalDto | null;
    agenda?: AgendaDto | null;
    appointment?: AgendaAppointmentDto | null;
    diagnoses?: EpisodeDiagnosisDto[];
}

export const getPatientByNhc = async (nhc: string): Promise<PatientDto> => {
    const { data } = await client.get(`/api/his/patients/nhc/${nhc}`);
    return data;
};

export const getPatientByDni = async (dni: string): Promise<PatientDto> => {
    const { data } = await client.get(`/api/his/patients/dni/${dni}`);
    return data;
};

export const getPatientBySip = async (sip: string): Promise<PatientDto> => {
    const { data } = await client.get(`/api/his/patients/sip/${sip}`);
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

export const getEpisode = async (episodeId: string): Promise<EpisodeDto> => {
    const { data } = await client.get(`/api/his/episodes/${episodeId}`);
    return data;
};

export const getProfessionalAgendas = async (professionalId: string): Promise<AgendaDto[]> => {
    const { data } = await client.get(`/api/his/professionals/${professionalId}/agendas`);
    return data;
};

export const getServiceAgendas = async (serviceCode: string): Promise<AgendaDto[]> => {
    const { data } = await client.get(`/api/his/services/${serviceCode}/agendas`);
    return data;
};

export const getAgendaAppointments = async (agendaId: string): Promise<AgendaAppointmentDto[]> => {
    const { data } = await client.get(`/api/his/agendas/${agendaId}/appointments`);
    return data;
};
