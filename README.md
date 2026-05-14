# CI Digital

Aplicacion para la gestion de consentimientos informados digitales del CHPC. Permite crear plantillas de consentimiento, generar solicitudes asociadas a pacientes/episodios, recoger firmas presenciales o remotas, generar PDF, registrar auditoria y consultar informacion del HIS mediante integracion real o mock.

## Modulos del repositorio

```text
.
├── backend/        API Spring Boot, seguridad, persistencia, PDF, firma y auditoria
├── frontend/       Aplicacion React/Vite servida por Nginx en Docker
├── his-mock/       Mock WireMock del HIS para desarrollo local
├── xppen-bridge/   Puente WebSocket local para tableta XP-Pen
├── docker-compose.yml
└── .env            Configuracion local opcional
```

## Stack tecnico

- Backend: Java 17, Spring Boot 3.4, Spring Security, JPA, Flyway, PostgreSQL, iText, Twilio, LDAP.
- Frontend: React 19, TypeScript, Vite, pnpm, Tailwind/CSS, React Router, React Quill.
- Base de datos: PostgreSQL 15.
- HIS mock: WireMock.
- Firma con tableta: aplicacion .NET `xppen-bridge` con WebSocket en `ws://localhost:5100`.

## Arranque rapido con Docker

Desde la raiz del proyecto:

```powershell
docker compose up --build
```

Servicios principales:

| Servicio | URL/puerto por defecto | Descripcion |
| --- | --- | --- |
| Frontend | http://localhost:5173 | Interfaz web |
| Backend | http://localhost:8080 | API REST |
| Backend mTLS | https://localhost:8444 | Endpoints con certificado cuando aplica |
| PostgreSQL | localhost:5434 | Base de datos local |
| HIS mock | http://localhost:9090 | HIS simulado |

El usuario inicial se crea por Flyway:

```text
usuario: admin
password: Admin1234!
rol: ADMIN
```

> Nota de seguridad: `docker-compose.yml` contiene valores por defecto para desarrollo. En entornos reales deben sobrescribirse en `.env` o en el gestor de secretos correspondiente, especialmente `JWT_SECRET`, credenciales LDAP, SMTP, Twilio y claves/certificados.

## Desarrollo local

En Windows, si el proyecto esta en una ruta de red UNC, usa la unidad mapeada `W:\` para evitar problemas con herramientas que invocan `cmd.exe`.

### Frontend

```powershell
cd W:\frontend
corepack enable
corepack pnpm install
corepack pnpm run dev
```

Comandos utiles:

```powershell
corepack pnpm run build
corepack pnpm run lint
corepack pnpm run preview
```

La API esta configurada en `frontend/src/api/client.ts` con `http://localhost:8080`.

### Backend

```powershell
cd W:\backend
.\mvnw.cmd spring-boot:run
```

Tests:

```powershell
.\mvnw.cmd test
.\mvnw.cmd test -Dtest=TemplateServiceTest
```

El backend usa `backend/src/main/resources/application.yml` y variables de entorno para sobrescribir configuracion.

### HIS mock

Con Docker Compose se levanta automaticamente. Si se necesita solo el mock:

```powershell
docker compose up his-mock
```

Los mappings y respuestas estan en:

```text
his-mock/mappings/
his-mock/__files/
```

### XP-Pen Bridge

El puente local expone `ws://localhost:5100` para que el frontend reciba eventos de la tableta.

Publicar ejecutable:

```powershell
cd W:\xppen-bridge
.\scripts\Publish-XPPenBridge.ps1
```

Instalar arranque al iniciar sesion:

```powershell
.\scripts\Install-XPPenBridgeStartupTask.ps1
```

Mas detalle en `xppen-bridge/README.md`.

## Variables de entorno principales

El proyecto puede arrancar con valores por defecto, pero estas variables son las mas relevantes:

| Variable | Uso |
| --- | --- |
| `POSTGRES_PORT` | Puerto local de PostgreSQL, por defecto `5434` |
| `BACKEND_PORT` | Puerto HTTP del backend, por defecto `8080` |
| `BACKEND_MTLS_PORT` | Puerto mTLS del backend, por defecto `8444` |
| `FRONTEND_PORT` | Puerto del frontend Docker, por defecto `5173` |
| `APP_BASE_URL` | URL publica del frontend usada en enlaces de firma |
| `APP_PUBLIC_API_BASE_URL` | URL publica del backend para descargas/enlaces |
| `JWT_SECRET` | Secreto de firma JWT |
| `JWT_EXPIRATION_MS` | Duracion del token de sesion |
| `HIS_BASE_URL` | URL de integracion HIS o mock |
| `HIS_DOCUMENT_EXPORT_*` | Exportacion de documentos al HIS/ruta compartida |
| `APIKEWAN_*` | Integracion con ApiKewan |
| `LDAP_*` | Integracion con Active Directory |
| `MAIL_*` | Envio de correo |
| `TWILIO_*` | Envio de SMS |
| `SIGNATURES_PATH` | Ruta de almacenamiento de firmas |
| `PDF_PATH` | Ruta de almacenamiento de PDF |

## Roles y permisos

| Rol | Capacidades principales |
| --- | --- |
| `ADMIN` | Gestion global: usuarios, plantillas, solicitudes, auditoria |
| `ADMINISTRATIVE` | Gestion operativa de solicitudes y plantillas |
| `PROFESSIONAL` | Citas/agendas, solicitudes, firma profesional, favorita de plantillas de su servicio |
| `SUPERVISOR` | Consulta de auditoria y acceso de supervision |

En plantillas:

- `ADMIN` y `ADMINISTRATIVE` ven todas las plantillas y pueden crear, editar, duplicar y gestionar.
- `PROFESSIONAL` ve la pagina de plantillas en modo limitado: solo plantillas de su servicio y solo puede elegir favorita.
- La plantilla favorita se preselecciona y aparece la primera en la pantalla de creacion de solicitud.

## Flujos principales

### Inicio de sesion

El frontend autentica contra `POST /api/auth/login`. El backend devuelve un JWT y los datos del usuario, incluyendo roles, servicio y metodo de firma.

### Gestion de plantillas

Las plantillas contienen HTML, version, servicio, procedimiento y campos dinamicos. Al editar una plantilla se crea una nueva version activa y se desactiva la anterior.

Campos dinamicos habituales:

```text
{{PATIENT_NAME}}
{{SERVICE}}
{{PROFESSIONAL_NAME}}
{{NHS_NUMBER}}
{{PATIENT_PHONE}}
{{PATIENT_EMAIL}}
```

### Creacion de solicitud

1. Se busca o precarga un paciente/cita desde el HIS.
2. Se cargan plantillas disponibles.
3. La favorita del usuario aparece primero y se preselecciona si coincide con el filtro.
4. Se elige consentimiento principal y opcionales.
5. Se genera la solicitud, se firma presencialmente o se envia al paciente.

### Firma presencial

El profesional crea la solicitud y abre la firma en kiosco/pantalla presencial. Si hay tableta XP-Pen, el navegador se comunica con `xppen-bridge` en `ws://localhost:5100`.

### Firma remota

El paciente accede mediante token, valida codigo si aplica, firma y se genera el PDF final.

### Auditoria

El sistema registra acciones relevantes en `audit_log`: creacion, envio, firma, cambios de plantillas, gestion de favoritos, accesos y errores relevantes.

## API principal

Prefijo backend: `http://localhost:8080/api`.

| Area | Rutas |
| --- | --- |
| Auth | `/auth/login`, `/auth/me` |
| Usuarios | `/users`, `/users/active-professionals` |
| Plantillas | `/templates`, `/templates/{id}`, `/templates/{id}/favorite`, `/templates/extract-pdf` |
| Solicitudes | `/consent-requests`, `/consent-requests/my`, `/consent-requests/{id}/pdf` |
| Grupos | `/consent-groups`, `/consent-groups/pending-my-signature` |
| HIS | `/his/patients/...`, `/his/episodes/...`, `/his/agendas/...` |
| Portal paciente | `/patient/sign/{token}` |
| Firma profesional | `/profile/signature` |
| Auditoria | `/audit`, `/audit/export/csv` |

## Base de datos y migraciones

Flyway ejecuta automaticamente las migraciones de:

```text
backend/src/main/resources/db/migration/
```

Incluyen usuarios/roles, plantillas, auditoria, solicitudes, grupos de consentimiento, eventos de firma, favoritos de plantilla y snapshots HIS.

Los datos persistentes de Docker se guardan en volumenes:

```text
postgres_data
backend_signatures
backend_pdfs
```

## Certificados, mTLS y firma

El backend incluye configuracion para:

- keystore de aplicacion: `app.keystore-*`
- truststore mTLS: `app.mtls-truststore-*`
- ApiKewan truststore/private key: `APIKEWAN_*`
- firma profesional por tableta o certificado

Las rutas por defecto estan preparadas para desarrollo. En produccion deben montarse certificados reales y protegerse fuera del repositorio.

## Exportacion HIS

La exportacion documental esta controlada por:

```text
HIS_DOCUMENT_EXPORT_ENABLED
HIS_DOCUMENT_EXPORT_PATH
HIS_DOCUMENT_TYPE_CODE
```

En Docker se monta:

```text
Z:/autoasociar -> /app/his-export
```

Si no existe esa unidad/ruta en el equipo, ajusta el volumen en `docker-compose.yml` o desactiva la exportacion.

## Comandos habituales

Arrancar todo:

```powershell
docker compose up --build
```

Parar contenedores:

```powershell
docker compose down
```

Parar y eliminar volumenes locales:

```powershell
docker compose down -v
```

Build frontend:

```powershell
cd W:\frontend
corepack pnpm run build
```

Tests backend:

```powershell
cd W:\backend
.\mvnw.cmd test
```

Logs:

```powershell
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

## Notas de troubleshooting

- Si `pnpm` o Maven dicen que no encuentran `package.json`/`pom.xml` y aparece `C:\Windows`, probablemente estas ejecutando desde una ruta UNC. Usa `W:\frontend` o `W:\backend`.
- Si el frontend carga pero no autentica, revisa que el backend este en `http://localhost:8080`.
- Si no aparecen citas/agendas, revisa `APIKEWAN_ENABLED`, `HIS_BASE_URL` y los logs de backend.
- Si no se generan PDF o firmas, revisa permisos de escritura en `PDF_PATH` y `SIGNATURES_PATH`.
- Si falla la exportacion documental, valida que exista la ruta configurada en `HIS_DOCUMENT_EXPORT_PATH`.
- Si la tableta no responde, comprueba que `xppen-bridge` este ejecutandose y escuchando en `ws://localhost:5100`.

