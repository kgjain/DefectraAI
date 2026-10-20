package com.qualityinspect.android.ui;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.qualityinspect.android.data.SessionManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DefectDetailActivity extends AppCompatActivity {

    private long id;
    private TextView tvDetail, tvHistory;
    private LinearLayout llButtons;
    private DefectDetail detail;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        setTitle("Defect");
        session = ((com.qualityinspect.android.QualityInspectApp) getApplication()).getSession();
        id = getIntent().getLongExtra("id", -1);
        tvDetail = findViewById(R.id.tv_detail);
        tvHistory = findViewById(R.id.tv_history);
        llButtons = findViewById(R.id.ll_buttons);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (id > 0) load();
    }

    private void load() {
        ApiClient.get().defect(id).enqueue(new Callback<DefectDetail>() {
            @Override public void onResponse(Call<DefectDetail> c, Response<DefectDetail> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    tvDetail.setText("Error: HTTP " + r.code());
                    return;
                }
                detail = r.body();
                Defect d = detail.defect;
                tvDetail.setText("Code: " + d.code + "\nSeverity: " + d.severity
                        + "\nStatus: " + d.status + "\nReview: " + d.reviewStatus
                        + "\nCategory: " + nvl(d.category)
                        + "\nItem: " + nvl(d.itemKey)
                        + "\nDescription: " + nvl(d.description)
                        + "\nBatch: " + nvl(d.batchCode)
                        + "\nEvidence images: " + (detail.evidence == null ? 0 : detail.evidence.size())
                        + "\nActions: " + (detail.actions == null ? 0 : detail.actions.size())
                        + (d.closureReason != null ? "\nClosure reason: " + d.closureReason : ""));
                if (detail.actions != null) {
                    for (ActionSummary a : detail.actions) {
                        tvDetail.append("\n  → " + a.code + " [" + a.status + "] " + a.title);
                    }
                }
                renderHistory();
                renderButtons(d);
            }
            @Override public void onFailure(Call<DefectDetail> c, Throwable t) { tvDetail.setText("Error: " + t.getMessage()); }
        });
    }

    private void renderHistory() {
        if (detail.history == null || detail.history.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (AuditEvent e : detail.history) {
            sb.append(e.action).append(" · ").append(nvl(e.actor))
              .append(" · ").append(nvl(e.occurredAt)).append("\n");
        }
        tvHistory.setText(sb.toString().trim());
        findViewById(R.id.card_history).setVisibility(android.view.View.VISIBLE);
    }

    private void renderButtons(Defect d) {
        llButtons.removeAllViews();
        boolean canMutate = session.isQualityManager();
        boolean open = "OPEN".equals(d.status) || "REOPENED".equals(d.status);
        boolean closed = "CLOSED".equals(d.status);
        if (canMutate && open) llButtons.addView(button("Close defect", v -> closeReopen("CLOSED")));
        if (canMutate && closed) llButtons.addView(button("Reopen defect", v -> closeReopen("REOPENED")));
        if (canMutate) llButtons.addView(button("Create corrective action", v -> createAction()));
    }

    private void closeReopen(String target) {
        TextView et = new TextView(this);
        et.setPadding(48, 24, 48, 0);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Reason (required)");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(target.equals("CLOSED") ? "Close defect" : "Reopen defect")
                .setView(wrap)
                .setPositiveButton("Submit", (dialog, which) -> {
                    DefectUpdateRequest req = new DefectUpdateRequest();
                    req.status = target;
                    req.reason = input.getText().toString().trim();
                    ApiClient.get().updateDefect(id, req).enqueue(new Callback<Defect>() {
                        @Override public void onResponse(Call<Defect> c, Response<Defect> r) {
                            Toast.makeText(DefectDetailActivity.this,
                                    r.isSuccessful() ? "Defect " + target.toLowerCase() : err(r),
                                    Toast.LENGTH_LONG).show();
                            load();
                        }
                        @Override public void onFailure(Call<Defect> c, Throwable t) {
                            Toast.makeText(DefectDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createAction() {
        if (detail == null || detail.defect == null) return;
        final long defectId = detail.defect.id;
        ApiClient.get().actionOwners(null).enqueue(new Callback<List<OwnerOption>>() {
            @Override public void onResponse(Call<List<OwnerOption>> c, Response<List<OwnerOption>> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                    Toast.makeText(DefectDetailActivity.this, "No owners available", Toast.LENGTH_SHORT).show();
                    return;
                }
                final List<OwnerOption> owners = r.body();
                String[] labels = new String[owners.size()];
                for (int i = 0; i < owners.size(); i++) {
                    labels[i] = owners.get(i).displayName + " (" + owners.get(i).username + ")";
                }
                final int[] picked = {0};
                final android.widget.EditText title = new android.widget.EditText(DefectDetailActivity.this);
                title.setHint("Action title (required)");
                final android.widget.EditText target = new android.widget.EditText(DefectDetailActivity.this);
                target.setHint("Target date (YYYY-MM-DD)");
                target.setText(java.time.LocalDate.now().plusDays(7).toString());
                LinearLayout wrap = new LinearLayout(DefectDetailActivity.this);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setPadding(48, 24, 48, 0);
                wrap.addView(title);
                wrap.addView(target);
                new AlertDialog.Builder(DefectDetailActivity.this)
                        .setTitle("New corrective action")
                        .setView(wrap)
                        .setSingleChoiceItems(labels, 0, (dialog, which) -> picked[0] = which)
                        .setPositiveButton("Create", (dialog, which) -> {
                            ActionCreateRequest req = new ActionCreateRequest();
                            req.defectId = defectId;
                            req.title = title.getText().toString().trim();
                            req.ownerId = owners.get(picked[0]).id;
                            req.targetDate = target.getText().toString().trim();
                            ApiClient.get().createAction(req).enqueue(new Callback<Action>() {
                                @Override public void onResponse(Call<Action> c, Response<Action> r) {
                                    Toast.makeText(DefectDetailActivity.this,
                                            r.isSuccessful() ? "Action created: " + r.body().code : err(r),
                                            Toast.LENGTH_LONG).show();
                                    load();
                                }
                                @Override public void onFailure(Call<Action> c, Throwable t) {
                                    Toast.makeText(DefectDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            @Override public void onFailure(Call<List<OwnerOption>> c, Throwable t) {
                Toast.makeText(DefectDetailActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Button button(String label, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.setOnClickListener(l);
        return b;
    }

    static String err(Response<?> r) {
        try {
            String body = r.errorBody() != null ? r.errorBody().string() : "";
            com.google.gson.Gson g = new com.google.gson.Gson();
            ApiError e = g.fromJson(body, ApiError.class);
            if (e != null && e.message != null) {
                if (e.fieldErrors != null && !e.fieldErrors.isEmpty()) {
                    StringBuilder sb = new StringBuilder(e.message);
                    for (ApiError.FieldError fe : e.fieldErrors) sb.append("\n").append(fe.field).append(": ").append(fe.message);
                    return sb.toString();
                }
                return e.message;
            }
        } catch (Exception ignored) {}
        return "HTTP " + r.code();
    }

    static String nvl(String s) { return s == null ? "—" : s; }
}
