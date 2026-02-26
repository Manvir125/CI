-- ROLES
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(30)  NOT NULL UNIQUE,
    description VARCHAR(200)
);

INSERT INTO roles (type, description) VALUES
    ('ADMIN',          'Administrador del sistema'),
    ('PROFESSIONAL',   'Profesional sanitario'),
    ('ADMINISTRATIVE', 'Personal administrativo'),
    ('SUPERVISOR',     'Supervisor y auditor');

-- USUARIOS
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    full_name     VARCHAR(200) NOT NULL,
    email         VARCHAR(200) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login    TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- RELACIÓN USUARIO ↔ ROL (muchos a muchos)
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Usuario admin por defecto (password: Admin1234!)
-- El hash corresponde a bcrypt con coste 12
INSERT INTO users (username, full_name, email, password_hash) VALUES
    ('admin', 'Administrador Sistema', 'admin@chpc.es',
     '$2a$12$1AGjH1Gvu4PUrElGzGHEUO.Q7g0bz2J9oU8TafhudW9uaM2ZvGLfq');

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.username = 'admin' AND r.type = 'ADMIN';