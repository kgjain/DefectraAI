# DEFECTRA AI

Full-stack defect-tracking system: Android (Java) capture app, Spring Boot
backend, PostgreSQL, PyTorch/FastAPI ML classifier, and a server-side GenAI
report assistant.

```
android-app ──HTTPS/JWT──▶ backend ──JDBC──▶ PostgreSQL 18
                              │
                              ├──HTTP──▶ ml-service (FastAPI + PyTorch)
                              └──HTTPS─▶ GenAI provider (optional, grounded)
```

**Trust rule:** the APK never talks to the ML service or LLM directly. Every
AI call is proxied by the backend so authentication, authorization, and record
grounding cannot be bypassed.

## What is included

| Piece | Location | Status |
|-------|----------|--------|
| Android app (Java 17, Material 3, Retrofit, ZXing scanner, WorkManager) | `android-app/` | `app-debug.apk` builds (9.0 MB) |
| Backend (Spring Boot 3.5, JWT, Flyway, RBAC, audit, idempotency) | `backend/` | Green build, 78 tests pass |
| ML service (FastAPI, PyTorch compact CNN trained from scratch) | `ml-service/` | Trained model v0.1.0, 8/8 pytest pass |
| Database schema + idempotent DB creation | `database/` | Flyway V1–V6 |
| Architecture, DB schema, OpenAPI | `documentation/` | Complete |
| Setup, demo script, limitations | `documentation/` | Complete |

## Quick start (development)

Prerequisites: JDK 21, Maven (or the wrapper), PostgreSQL 18, Python 3.12,
Android SDK (for APK builds only).

```bash
# 1. Databases (idempotent)
psql -U postgres -f database/create_databases.sql

# 2. ML service
cd ml-service
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# 3. Backend
cd backend
APP_BOOTSTRAP_ENABLED=true APP_BOOTSTRAP_PASSWORD=ChangeMe!qm1 ./mvnw spring-boot:run

# 4. Android APK
cd android-app
./gradlew assembleDebug
```

First run: `POST /api/v1/auth/bootstrap` creates the single initial
QUALITY_MANAGER. Login with the field `username` (not email).

Full environment walkthrough: [documentation/setup.md](documentation/setup.md)
Live demo script: [documentation/demo-script.md](documentation/demo-script.md)
Honest metrics and limits: [documentation/limitations.md](documentation/limitations.md)
Innovation (explainable review prioritization): [documentation/innovation.md](documentation/innovation.md)

## Key API facts

- Base path: `/api/v1`. OpenAPI: `documentation/openapi.yaml` (Swagger UI at
  `/swagger-ui.html` when the backend is up).
- Login returns `accessToken` (JWT, HS256). Send as `Authorization: Bearer …`.
- Duplicate-submit protection: send an `Idempotency-Key` header on POSTs;
  the same key + payload replays the first response, a different payload gets 409.
- Roles: `INSPECTOR`, `SUPERVISOR`, `QUALITY_MANAGER`. Role checks are
  server-side (`@PreAuthorize`); UI hiding is never the only control.
- Audit trail (`/api/v1/audit-events`) is append-only and QM-only.
- AI reports in `dev-no-llm` mode are deterministic database summaries —
  explicitly labelled, **not** generative answers.

## Tests

```bash
cd backend && ./mvnw test        # 78 tests (unit + integration: RBAC, registration/OTP,
                                 # idempotency replay, GenAI scenarios, review-queue priority,
                                 # AI endpoint security)
cd ml-service && pytest          # 8 tests
```

## Security notes

- `.env` is git-ignored; `.env.example` contains no real secrets.
- JWT secret must be ≥ 32 chars; refresh tokens are stored only as SHA-256 hashes.
- Image evidence lives in a private local directory; only metadata is in PG.
- Bootstrap is single-use and must be disabled in production
  (`APP_BOOTSTRAP_ENABLED=false`).
# DefectraAI
