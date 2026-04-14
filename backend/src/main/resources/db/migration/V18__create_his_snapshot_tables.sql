CREATE TABLE his_patients (
    nhc         VARCHAR(20) PRIMARY KEY,
    sip         VARCHAR(30),
    dni         VARCHAR(30),
    full_name   VARCHAR(200) NOT NULL,
    first_name  VARCHAR(100),
    last_name   VARCHAR(150),
    birth_date  DATE,
    gender      VARCHAR(20),
    email       VARCHAR(200),
    phone       VARCHAR(30),
    address     VARCHAR(255),
    blood_type  VARCHAR(10),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_his_patients_sip
    ON his_patients(sip)
    WHERE sip IS NOT NULL;

CREATE INDEX idx_his_patients_dni
    ON his_patients(dni);

CREATE TABLE his_patient_allergies (
    patient_nhc VARCHAR(20)  NOT NULL REFERENCES his_patients(nhc) ON DELETE CASCADE,
    allergy     VARCHAR(100) NOT NULL,
    PRIMARY KEY (patient_nhc, allergy)
);

CREATE TABLE his_professionals (
    professional_id VARCHAR(50) PRIMARY KEY,
    full_name       VARCHAR(200),
    sip             VARCHAR(30),
    dni             VARCHAR(30),
    specialty_code  VARCHAR(50),
    specialty_name  VARCHAR(200),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_his_professionals_sip
    ON his_professionals(sip)
    WHERE sip IS NOT NULL;

CREATE INDEX idx_his_professionals_specialty_code
    ON his_professionals(specialty_code);

CREATE TABLE his_agendas (
    agenda_id       VARCHAR(50) PRIMARY KEY,
    professional_id VARCHAR(50) REFERENCES his_professionals(professional_id),
    name            VARCHAR(200) NOT NULL,
    service_code    VARCHAR(50),
    service_name    VARCHAR(200),
    status          VARCHAR(30),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_his_agendas_professional
    ON his_agendas(professional_id);

CREATE INDEX idx_his_agendas_service_code
    ON his_agendas(service_code);

CREATE TABLE his_episodes (
    episode_id               VARCHAR(50) PRIMARY KEY,
    nhc                      VARCHAR(20) REFERENCES his_patients(nhc),
    agenda_id                VARCHAR(50) REFERENCES his_agendas(agenda_id),
    professional_id          VARCHAR(50) REFERENCES his_professionals(professional_id),
    service_code             VARCHAR(50),
    service_name             VARCHAR(200),
    procedure_code           VARCHAR(50),
    procedure_name           VARCHAR(200),
    episode_date             DATE,
    admission_date           DATE,
    expected_discharge_date  DATE,
    ward                     VARCHAR(100),
    bed                      VARCHAR(50),
    attending_physician      VARCHAR(200),
    status                   VARCHAR(30),
    priority                 VARCHAR(30),
    diagnosis_summary        VARCHAR(500),
    icd10_code               VARCHAR(50),
    updated_at               TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_his_episodes_nhc
    ON his_episodes(nhc);

CREATE INDEX idx_his_episodes_agenda
    ON his_episodes(agenda_id);

CREATE INDEX idx_his_episodes_professional
    ON his_episodes(professional_id);

CREATE TABLE his_agenda_appointments (
    episode_id        VARCHAR(50) PRIMARY KEY REFERENCES his_episodes(episode_id) ON DELETE CASCADE,
    nhc               VARCHAR(20) REFERENCES his_patients(nhc),
    agenda_id         VARCHAR(50) REFERENCES his_agendas(agenda_id),
    professional_id   VARCHAR(50) REFERENCES his_professionals(professional_id),
    appointment_date  DATE,
    start_time        TIME,
    end_time          TIME,
    prestation        VARCHAR(200),
    status            VARCHAR(30),
    updated_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_his_appointments_agenda_date
    ON his_agenda_appointments(agenda_id, appointment_date, start_time);

CREATE INDEX idx_his_appointments_professional
    ON his_agenda_appointments(professional_id);

CREATE TABLE his_episode_diagnoses (
    id              BIGSERIAL PRIMARY KEY,
    episode_id      VARCHAR(50)  NOT NULL REFERENCES his_episodes(episode_id) ON DELETE CASCADE,
    diagnosis_code  VARCHAR(50),
    diagnosis_name  VARCHAR(255) NOT NULL,
    diagnosis_type  VARCHAR(50),
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_his_episode_diagnoses_episode
    ON his_episode_diagnoses(episode_id);
