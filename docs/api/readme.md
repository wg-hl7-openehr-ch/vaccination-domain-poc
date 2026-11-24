# API-Dokumentation

Die BFF-Dienste (Backend for Frontend) stellen die APIs für die Frontends bereit.
Alle Anfragen laufen über den Nginx-Reverse-Proxy (`localhost:3003`), der CORS-Probleme vermeidet.

Authentifizierung: JWT aus dem IAM Mock via `Authorization: Bearer <token>`.
Siehe [Architektur – Gateway & Authentifizierung](../architecture/architecture.md#4-gateway--authentifizierung) für Mock-Benutzer und JWT-Details.

---

## Producer API (Arzt-Cockpit)

Backend-Dienst: `bff-producer-server:9112/bff-producer-server/api/`
Nginx-Route: `/api/bff-producer/*` → `bff-producer-server:9112`

| Methode | Pfad | Beschreibung | DTO (OpenAPI) |
|---|---|---|---|
| GET | `/patients` | Liste aller Patienten abrufen | `PatientDto` → [patient.yaml](producer-api/patient.yaml) |
| GET | `/vaccinations?personId=` | Impfungen eines Patienten abrufen | `VaccinationDto[]` → [vaccination.yaml](producer-api/vaccination.yaml) |
| POST | `/immunizations?personId=` | Neue Impfung erfassen (Body: `ImmunizationCreateDto`) | [immunization.yaml](producer-api/immunization.yaml) |
| PUT | `/immunizations/{id}?personId=` | Impfung korrigieren (Body: `ImmunizationUpdateDto`) | [immunization.yaml](producer-api/immunization.yaml) |

Felder und Typen sind in den verlinkten OpenAPI-Spezifikationen definiert.

---

## Consumer API (Patienten-Impfpass)

Backend-Dienst: `bff-consumer:8001`
Nginx-Route: `/api/bff-consumer/*` → `bff-consumer:8001`

| Methode | Pfad | Beschreibung | DTO (OpenAPI) |
|---|---|---|---|
| GET | `/patients/{patientId}` | Patientendossier mit Impfungen abrufen | `PatientDossierDto` → [vaccination-dossier.yaml](consumer-api/vaccination-dossier.yaml) |
| GET | `/vaccinations/?personId=` | Impfungen eines Patienten abrufen | `VaccinationDto[]` |
| GET | `/healthz` | Health-Check (Fhir-Status) | — |

---

## OpenAPI-Spezifikationen

| API | Datei |
|---|---|
| Producer – Patienten | [producer-api/patient.yaml](producer-api/patient.yaml) |
| Producer – Impfungen (Read) | [producer-api/vaccination.yaml](producer-api/vaccination.yaml) |
| Producer – Impfungen (Write) | [producer-api/immunization.yaml](producer-api/immunization.yaml) |
| Consumer – Dossier | [consumer-api/vaccination-dossier.yaml](consumer-api/vaccination-dossier.yaml) |

> **Hinweis:** Swagger-UI ist für den BFF-Producer unter `http://localhost:9112/bff-producer-server/swagger-ui.html` verfügbar (nur direkt, nicht via Nginx).
