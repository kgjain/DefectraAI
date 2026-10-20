# DEFECTRA AI — Architecture

## 1. System overview

```
┌─────────────────────────────┐        ┌──────────────────────────────────────┐
│  android-app (Java, M3)     │  HTTPS │  backend (Spring Boot 3.5, Java 21) │
│  Retrofit/OkHttp, ZXing     │ ─────► │  /api/v1/**  Spring Security (JWT)  │
│  scanner, WorkManager       │  JWT   │  Bean Validation, Flyway, JPA       │
│  (plain app-private prefs)  │        └───────┬──────────────┬───────────────┘
└─────────────────────────────┘                │              │
                                        JDBC/PG        HTTP (server-side only)
                                               │              │
                                     ┌─────────▼──────┐  ┌────▼──────────────────┐
                                     │  PostgreSQL 18 │  │ ml-service (FastAPI)  │
                                     │  + private file│  │ PyTorch compact CNN   │
                                     │  storage       │  │ (trained from scratch)│
                                     └────────────────┘  └───────────────────────┘
                                                │
                                     ┌──────────▼───────────────────────────────┐
                                     │ GenAI provider (OpenAI-compatible,       │
                                     │ configurable) — called ONLY by backend,  │
                                     │ grounded in retrieved authorized records │
                                     └──────────────────────────────────────────┘
```

**Rule of trust:** the Android app never talks to the ML service or the LLM
provider directly. Every AI call is proxied by the backend so that
authentication, authorization and record grounding cannot be bypassed.

## 2. Components

| Component   | Tech                                            | Port | Responsibility |
|-------------|--------------------------------------------------|------|----------------|
| android-app | Java 17, AGP 8.x, Material 3, Retrofit 2.11, OkHttp 4.12, ZXing scanner (journeyapps 4.3), WorkManager 2.9 (dashboard prewarm only); screens call Retrofit through `ApiClient` directly (no ViewModel/Repository layer) | — | Capture, upload, review, dashboards, role-aware navigation |
| backend     | Spring Boot 3.5.x, Spring Web/Security/Data JPA, Flyway, jjwt, springdoc | 8080 | REST API, RBAC, validation, business rules, audit, metrics, GenAI orchestration |
| ml-service  | Python 3.12, FastAPI, PyTorch 2.14, torchvision 0.29 (data loading only) | 8000 | `POST /predict` 6-class surface-defect classification (OK, SCRATCH, DENT, DISCOLORATION, CRACK, MISALIGNMENT) with heuristic fallback; `GET /health` |
| database    | PostgreSQL 18                                    | 5432 | Normalized relational records, referential integrity |
| storage     | Private local directory (S3-compatible ready)     | —    | Original images/evidence; only metadata in PG |

## 3. Key design decisions

1. **Model output ≠ human outcome.** `model_results` stores the raw prediction
   (class, score, score_type, model name/version, timing, status).
   `inspection_reviews` stores the human decision. They are separate tables and
   separate API resources; a review never mutates a model result.
2. **Distinct items, not photos.** Business metrics count
   `COUNT(DISTINCT item_key)` on `inspections` — multiple images or follow-ups
   of one physical item count once. `item_key` is a generated column
   (`upper(btrim(item_reference))`).
3. **Evidence is immutable.** Uploaded images are versioned rows
   (`is_current`, `version`, `replaced_by_id`); replacement adds a row, never
   overwrites. Draft inspections may be deleted; submitted records are archived
   or status-changed only.
4. **Idempotency.** `POST /inspections` accepts a client `idempotencyKey`;
   `idempotency_records` replays the stored response for duplicate requests.
   A partial unique index also blocks duplicate *initial* inspections per
   `(batch_id, item_key)`.
5. **Async ML processing.** `POST /images/{id}/process` returns `202` with an
   attempt id; a worker thread calls the ML service; every attempt (success or
   failure) is persisted in `processing_attempts` and can be retried. If the ML
   service is down the inspection stays intact and proceeds to manual review.
6. **GenAI grounding.** `POST /ai/*` → authenticate → authorize scope → query
   records with Spring Data → compute counts in SQL/Java → build an evidence
   JSON (only permitted rows) → prompt the configured provider → validate the
   answer for unknown identifiers → persist the report with the evidence
   snapshot in `ai_reports.grounding_json`. If `GENAI_PROVIDER=none` the API
   returns a clearly labelled *deterministic evidence summary* marked
   `generated=false, mode="dev-no-llm"` (never presented as an LLM answer).
7. **Explainable review prioritization (innovation).** `GET /review-queue`
   ranks work items with a transparent rule-based score (failed processing,
   missing/low-quality evidence, unreviewed prediction, reopened defect,
   overdue action, repeat confirmed issues on the same product family) and
   returns `reasons[]` plus the record links behind each factor. It is
   explicitly *not* machine learning.
8. **Single-tenant deployment isolation.** All records belong to the deploying
   organization; cross-organization isolation is enforced at deployment level
   (documented limitation in `documentation/limitations.md`).

## 4. Security model

- Stateless JWT access tokens (HS256, `APP_JWT_SECRET`, 30 min) + rotating
  refresh tokens stored **hashed** (SHA-256) in `refresh_tokens`.
- Passwords: BCrypt (strength 10).
- Method-level authorization with `@PreAuthorize` on every mutating endpoint;
  Spring Security request matchers as a second layer. Hiding UI controls is
  never the only check.
- Images downloadable only through `GET /api/v1/images/{id}` after the caller's
  role is checked against the inspection chain.
- Audit trail is append-only (PostgreSQL trigger rejects `UPDATE`/`DELETE`).
- Secrets only via environment variables; no keys in the APK, migrations or git.

## 5. Request lifecycle (inspection → evidence → AI → human → defect → action)

```
Inspector creates inspection (item_key, scope, observations)
  → uploads image (validated type/size, sha256, version 1)
  → POST /images/{id}/process  (202 + attempt)
      ml-service /predict  → model_results (class, score, score_type, model_version)
  → authorized review (confirm | correct+reason | manual | more evidence)
  → confirmed defect (severity, evidence links)  → corrective action (owner, due date)
  → supervisor performs work → POST /actions/{id}/reinspections (FOLLOW_UP inspection)
  → follow-up image + ML result + human review → defect CLOSED or REOPENED
  → Quality manager records batch decision (HOLD/RELEASE + reason)
  → every step appends audit_events; dashboard counts read only persisted rows
```

## 6. Module dependency map

```
backend:  config → security → common(error/audit/idempotency/storage)
          → auth → user → product → batch → inspection(+images,+ml) → review
          → defect → action → decision → dashboard → search → audit → ai
android:  api(network) → data(session) → report(pdf) → widget(chart)
          → worker(dashboard prewarm) → ui(screens)
ml-service: app(config/schemas/model/inference) ← training(prepare_dataset/train/evaluate)
```
