# Demo Script

End-to-end walkthrough of the core flow using the REST API (the Android app
drives the same endpoints). Run backend + ML service first (see `setup.md`).
All calls are PowerShell snippets; `$token` from the setup login step.

## 1. Create a product (QM only)

```powershell
$h = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/products -Headers $h `
  -ContentType application/json `
  -Body '{"code":"SKU-A","name":"Widget A","category":"PARTS","specifications":{"material":"steel"}}'
```

An INSPECTOR token gets 403 here — RBAC is enforced server-side.

## 2. Create a batch for the product

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/batches -Headers $h `
  -ContentType application/json `
  -Body '{"code":"BATCH-2026-001","productId":1,"productionDate":"2026-10-12","quantityProduced":100}'
```

## 3. Start an inspection and attach evidence

```powershell
$insp = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/inspections -Headers $h `
  -ContentType application/json `
  -Body '{"batchId":1,"itemReference":"ITEM-0001","evidenceScope":"FINAL","observations":"Initial visual check"}'

# Upload an image (multipart)
$form = @{ file = Get-Item D:\Defect\.tools\test-image.png }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/inspections/$($insp.id)/images" -Headers $h -Form $form

# Submit the inspection
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/inspections/$($insp.id)/submit" -Headers $h
```

## 4. Run ML classification

```powershell
# Replace 1 with the real image id from the upload response
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/images/1/process -Headers $h

# Poll until COMPLETED (async; runs after DB commit)
Start-Sleep -Seconds 3
Invoke-RestMethod -Uri http://localhost:8080/api/v1/images/1/results -Headers $h
```

Expected: `aiState: COMPLETED`, `modelResult` with a predicted class
(OK / SCRATCH / DENT / …) and per-class probabilities.

## 5. Human review of the model result

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/inspections/1/reviews -Headers $h `
  -ContentType application/json `
  -Body '{"decision":"CONFIRM","confirmedOutcome":"POTENTIALLY_DEFECTIVE","reason":"Visible scratch on edge"}'
```

A review is required before a defect can be created — the model never creates
defects on its own.

## 6. Create a defect (only after a confirmed review)

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/defects -Headers $h `
  -ContentType application/json `
  -Body '{"inspectionId":1,"defectClass":"SCRATCH","severity":"MEDIUM","description":"Scratch on widget edge"}'
```

## 7. Open a corrective action

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/actions -Headers $h `
  -ContentType application/json `
  -Body '{"defectId":1,"actionType":"CONTAINMENT","ownerId":2,"dueDate":"2026-10-20","description":"Contain affected lot"}'
```

Actions follow a state machine: OPEN → IN_PROGRESS → AWAITING_REINSPECTION →
COMPLETED (or CANCELLED). Transitions are validated; invalid jumps return 409.

## 8. Batch decision (QM only)

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/batches/1/decisions -Headers $h `
  -ContentType application/json `
  -Body '{"decision":"HOLD","reason":"Confirmed defect in lot; containment in progress"}'
```

Valid decisions: `HOLD`, `RELEASE`, `CONDITIONAL_RELEASE`.

## 9. Dashboards, review queue, audit

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/v1/dashboard/summary -Headers $h
Invoke-RestMethod -Uri http://localhost:8080/api/v1/dashboard/trends -Headers $h
Invoke-RestMethod -Uri http://localhost:8080/api/v1/review-queue -Headers $h
Invoke-RestMethod -Uri http://localhost:8080/api/v1/audit-events -Headers $h   # QM only
```

`confirmedDefectRate` on the summary is computed from confirmed human reviews,
not raw model predictions.

## 10. GenAI assistant (dev-no-llm mode)

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/ai/reports/batch-summary -Headers $h `
  -ContentType application/json -Body '{"batchId":1}'

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/ai/questions -Headers $h `
  -ContentType application/json -Body '{"question":"What is the biggest quality risk in batch BATCH-2026-001?"}'
```

With `GENAI_PROVIDER=none` the response is a deterministic evidence summary
labelled `[DEVELOPMENT MODE - no LLM configured]` with `generated: false` and
`mode: dev-no-llm`. It is **not** a generative answer. Set
`GENAI_PROVIDER=openai-compatible` + `GENAI_API_KEY` for real grounded LLM
output; cited record IDs not present in the authorized evidence are redacted.

## 11. Idempotency

```powershell
$k = @{ Authorization = "Bearer $token"; "Idempotency-Key" = "demo-123" }
# Same key + same payload → replays the first response
# Same key + different payload → 409 Conflict
```

## 12. Android app flow

Install `app-debug.apk`. The app performs the same flow:
Login → Dashboard → New Inspection (camera/gallery) → upload → submit →
Review Queue (confirm/reject model results) → Defects/Actions → Audit (QM).
WorkManager pre-warms the dashboard summary in the background; there is no
offline capture queue (documented limitation — uploads need connectivity).
