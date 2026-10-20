# Innovation: Explainable Review Prioritization (Smart Review Queue)

**Decision:** the approved innovation for this project is a *transparent,
rule-based* ordering of the human review workload — not machine learning.
This document explains what it does, why it is built this way, and the test
evidence behind it.

## The problem

An inspection queue grows faster than reviewers can clear it. In a flat
queue the items that most need attention — AI processing that failed, an
item with no evidence photo, a defect that was reopened after a failed
follow-up, a product family with repeated confirmed issues — are buried
under routine OK predictions. Reviewers either process FIFO (risk items
wait) or guess (no audit trail for the choice).

## What the system does

`GET /api/v1/review-queue?limit=N` returns submitted inspections ordered by
a priority score, highest first. Every item carries a `reasons[]` array with
the exact factors that produced its score, so the ordering is auditable
item-by-item — a reviewer (or an auditor) can verify every point against the
underlying records. The queue is surfaced in the Android app's Review Queue
screen and is readable by any authenticated role.

Implementation: `backend/.../dashboard/DashboardService.reviewQueue()`
(there is deliberately no ML model, training data, or opaque ranking step
involved).

## Scoring factors (hand-set, deterministic)

| Factor | Weight | Fires when |
|--------|-------:|------------|
| `FAILED_PROCESSING` | 40 | The item has ≥1 failed ML attempt and no model result — AI could not process it at all |
| `NO_EVIDENCE` | 35 | The submitted inspection has no current evidence image |
| `REOPENED_DEFECT` | 35 | A defect on this item was reopened after a corrective-action follow-up |
| `UNREVIEWED_PREDICTION` | 25 | A model prediction exists and no human review has recorded yet |
| `REPEAT_ISSUE` | 20 | ≥2 confirmed defects on the same product family in the last 30 days |
| `POOR_EVIDENCE` | 15 | At least one evidence file is under 20 KB and may be insufficient |
| `OVERDUE_ACTION` | 15 | The item's batch has overdue corrective actions |
| `NEGATIVE_PREDICTION` | 10 | The model's predicted class is anything other than `OK` (the model is 6-class: OK, SCRATCH, DENT, DISCOLORATION, CRACK, MISALIGNMENT) |

Scores simply add up (max observed is well above 100 for compounding
problems). Candidates are `SUBMITTED` inspections (up to 60 scanned); the
`limit` parameter is clamped to 1–50. The queue is sorted by descending
`priorityScore`.

Each `reasons[]` entry is `{factor, weight, detail}` where `detail` cites the
concrete records (attempt counts, file sizes, model name/version, defect
counts) — nothing in the score is hidden behind a model explanation.

## Why rule-based instead of ML (the deliberate choice)

1. **Explainability is the criterion.** Every point maps to a verifiable
   database record. A learned ranker would need a separate explanation
   mechanism and training labels that this deployment does not have.
2. **Honesty.** The only labeled data available is the synthetic image
   dataset behind the 6-class classifier (see `limitations.md`); training a
   ranker on it would produce theater, not signal. The system must never
   pretend a smarter capability exists.
3. **Deterministic demos and tests.** The same records always produce the
   same order and the same reasons, which is what makes the integration
   test below able to assert exact weights.

The prioritizer is explicitly *not* machine learning, and the API/docs never
describe it as such.

## Test evidence

`backend/src/test/java/.../ReviewQueuePriorityIntegrationTest.java`
(1 test, part of the 78-test suite) seeds two submitted inspections on one
batch:

- item A: evidence image present, ML attempt **FAILED**, no model result →
  score 40 (`FAILED_PROCESSING`),
- item B: healthy image with a stored **CRACK** prediction awaiting review
  → score 35 (`UNREVIEWED_PREDICTION` 25 + `NEGATIVE_PREDICTION` 10).

It asserts: both appear; A ranks above B; A's `FAILED_PROCESSING` reason
carries weight 40; B exposes `UNREVIEWED_PREDICTION` (25) and
`NEGATIVE_PREDICTION` (10, with `CRACK` named in the detail); scores are
non-increasing across the queue. (An earlier version of the factor checked
for a `POTENTIALLY_DEFECTIVE` label the 6-class model never emits; the test
now pins the real behavior with an actual model class.)

## Honest limits

- The weights are engineering judgment, not tuned against reviewer
  outcomes; tuning them from real triage data is future work.
- The candidate scan window is the 60 most recent submitted inspections
  before ranking/limit — an extremely large backlog pages through that
  window rather than re-scoring all history.
- Factors cover evidence/processing/workflow signals only; there is no
  image-content-based risk scoring beyond what the (weak, synthetic-data)
  classifier already reports as a normal model result.
