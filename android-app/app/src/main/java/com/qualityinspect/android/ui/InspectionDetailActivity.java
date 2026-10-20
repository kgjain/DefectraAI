package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InspectionDetailActivity extends AppCompatActivity {

    private long id;
    private TextView tvDetail, tvAi;
    private Button btnProcess, btnReview, btnCreateDefect;
    private InspectionDetail detail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspection_detail);
        setTitle("Inspection");
        id = getIntent().getLongExtra("id", -1);
        tvDetail = findViewById(R.id.tv_detail);
        tvAi = findViewById(R.id.tv_ai);
        btnProcess = findViewById(R.id.btn_process);
        btnReview = findViewById(R.id.btn_review);
        btnCreateDefect = findViewById(R.id.btn_create_defect);
        btnProcess.setOnClickListener(v -> process());
        btnReview.setOnClickListener(v -> showReview());
        btnCreateDefect.setOnClickListener(v -> createDefect());
        findViewById(R.id.btn_ai_full).setOnClickListener(v -> {
            Intent i = new Intent(this, AiAnalysisActivity.class);
            i.putExtra("inspectionId", id);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (id > 0) load();
    }

    private void load() {
        ApiClient.get().inspection(id).enqueue(new Callback<InspectionDetail>() {
            @Override public void onResponse(Call<InspectionDetail> c, Response<InspectionDetail> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    tvDetail.setText("Error: HTTP " + r.code());
                    return;
                }
                detail = r.body();
                Inspection ins = detail.inspection;
                tvDetail.setText("Item: " + ins.itemReference
                        + "\nScope: " + ins.evidenceScope
                        + "\nStatus: " + ins.status
                        + "\nBatch: " + ins.batchCode
                        + "\nAI state: " + ins.aiState
                        + "\nReview state: " + ins.reviewState
                        + "\nObservations: " + ins.observations);
                StringBuilder ai = new StringBuilder("AI results:\n");
                if (detail.modelResults != null) {
                    for (ModelResult mr : detail.modelResults) {
                        ai.append("  ").append(mr.predictedClass)
                          .append(" (").append(mr.score).append(", ")
                          .append(mr.scoreType).append(") via ")
                          .append(mr.modelName).append("\n");
                    }
                }
                if (detail.attempts != null) {
                    for (ProcessingAttempt pa : detail.attempts) {
                        ai.append("  attempt ").append(pa.attemptNo).append(": ")
                          .append(pa.status);
                        if (pa.errorDetails != null) ai.append(" — ").append(pa.errorDetails);
                        ai.append("\n");
                    }
                }
                tvAi.setText(ai.toString());
                boolean submitted = "SUBMITTED".equals(ins.status);
                btnProcess.setEnabled(submitted);
                btnReview.setEnabled(submitted);
                btnCreateDefect.setVisibility(hasConfirmedDefectReview() ?
                        android.view.View.VISIBLE : android.view.View.GONE);
            }
            @Override public void onFailure(Call<InspectionDetail> c, Throwable t) {
                tvDetail.setText("Error: " + t.getMessage());
            }
        });
    }

    private boolean hasConfirmedDefectReview() {
        if (detail == null || detail.reviews == null) return false;
        for (Review rev : detail.reviews) {
            if ("CONFIRM".equals(rev.decision) && "POTENTIALLY_DEFECTIVE".equals(rev.confirmedOutcome)) {
                return true;
            }
        }
        return false;
    }

    private void process() {
        if (detail == null || detail.images == null || detail.images.isEmpty()) {
            Toast.makeText(this, "No image to process", Toast.LENGTH_SHORT).show();
            return;
        }
        long imageId = detail.images.get(0).id;
        ApiClient.get().processImage(imageId).enqueue(new Callback<ProcessingAttempt>() {
            @Override public void onResponse(Call<ProcessingAttempt> c, Response<ProcessingAttempt> r) {
                Toast.makeText(InspectionDetailActivity.this,
                        r.isSuccessful() ? "Processing started" : DefectDetailActivity.err(r),
                        Toast.LENGTH_SHORT).show();
                load();
            }
            @Override public void onFailure(Call<ProcessingAttempt> c, Throwable t) {
                Toast.makeText(InspectionDetailActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Full 4-option review flow backed by the server-side decision rules. */
    private void showReview() {
        final String[] decisions = {"CONFIRM defect", "CORRECT to GOOD", "MANUAL review required", "REQUEST more evidence"};
        final int[] picked = {0};
        new AlertDialog.Builder(this)
                .setTitle("Record review")
                .setSingleChoiceItems(decisions, 0, (d, w) -> picked[0] = w)
                .setPositiveButton("Continue", (d, w) -> {
                    switch (picked[0]) {
                        case 0 -> submitReview("CONFIRM", "POTENTIALLY_DEFECTIVE", null);
                        case 1 -> submitReview("CORRECT", "GOOD", null);
                        case 2 -> submitReview("MANUAL_REQUIRED", "NEEDS_MANUAL", null);
                        case 3 -> submitReview("EVIDENCE_REQUESTED", null, "Additional evidence requested from the floor");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitReview(String decision, String outcome, String defaultNote) {
        final EditText et = new EditText(this);
        boolean needsNote = "EVIDENCE_REQUESTED".equals(decision);
        et.setHint(needsNote ? "What evidence is needed? (required)"
                : "Reason (required for CONFIRM/CORRECT)");
        if (defaultNote != null) et.setText(defaultNote);
        new AlertDialog.Builder(this)
                .setTitle(decision)
                .setView(et)
                .setPositiveButton("Submit", (d, w) -> {
                    ReviewRequest req = new ReviewRequest();
                    req.decision = decision;
                    req.confirmedOutcome = outcome;
                    String text = et.getText().toString().trim();
                    if (needsNote) req.evidenceRequestedNote = text;
                    else req.reason = text;
                    ApiClient.get().reviewInspection(id, req).enqueue(new Callback<Review>() {
                        @Override public void onResponse(Call<Review> c, Response<Review> r) {
                            Toast.makeText(InspectionDetailActivity.this,
                                    r.isSuccessful() ? "Review recorded: " + decision : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                            load();
                        }
                        @Override public void onFailure(Call<Review> c, Throwable t) {
                            Toast.makeText(InspectionDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createDefect() {
        final String[] severities = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
        final int[] picked = {1};
        final EditText desc = new EditText(this);
        desc.setHint("Description (required)");
        final EditText category = new EditText(this);
        category.setHint("Category (optional)");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);
        wrap.addView(desc);
        wrap.addView(category);
        new AlertDialog.Builder(this)
                .setTitle("Confirm defect")
                .setView(wrap)
                .setSingleChoiceItems(severities, picked[0], (d, w) -> picked[0] = w)
                .setPositiveButton("Create", (d, w) -> {
                    DefectCreateRequest req = new DefectCreateRequest();
                    req.inspectionId = id;
                    req.description = desc.getText().toString().trim();
                    req.severity = severities[picked[0]];
                    req.category = category.getText().toString().trim();
                    ApiClient.get().createDefect(req).enqueue(new Callback<Defect>() {
                        @Override public void onResponse(Call<Defect> c, Response<Defect> r) {
                            Toast.makeText(InspectionDetailActivity.this,
                                    r.isSuccessful() ? "Defect created: " + r.body().code : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                        }
                        @Override public void onFailure(Call<Defect> c, Throwable t) {
                            Toast.makeText(InspectionDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
