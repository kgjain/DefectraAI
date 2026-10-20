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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActionDetailActivity extends AppCompatActivity {

    private static final Map<String, String[]> NEXT_STATES = Map.of(
            "OPEN", new String[]{"IN_PROGRESS", "CANCELLED"},
            "IN_PROGRESS", new String[]{"AWAITING_REINSPECTION", "COMPLETED", "CANCELLED"},
            "AWAITING_REINSPECTION", new String[]{"COMPLETED", "IN_PROGRESS"});

    private long id;
    private TextView tvDetail, tvHistory;
    private LinearLayout llButtons;
    private ActionDetail detail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        setTitle("Corrective Action");
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
        ApiClient.get().action(id).enqueue(new Callback<ActionDetail>() {
            @Override public void onResponse(Call<ActionDetail> c, Response<ActionDetail> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    tvDetail.setText("Error: HTTP " + r.code());
                    return;
                }
                detail = r.body();
                Action a = detail.action;
                tvDetail.setText("Code: " + a.code + "\nTitle: " + nvl(a.title)
                        + "\nStatus: " + a.status + (a.overdue ? " (OVERDUE)" : "")
                        + "\nOwner: " + nvl(a.ownerName)
                        + "\nTarget: " + nvl(a.targetDate)
                        + "\nDefect: " + nvl(a.defectCode)
                        + "\n\nDescription: " + nvl(detail.description)
                        + "\n\nWork notes: " + nvl(detail.workNotes)
                        + "\nCompletion: " + nvl(detail.completionDetails)
                        + (detail.statusReason != null ? "\nStatus reason: " + detail.statusReason : "")
                        + "\nFollow-ups: " + (detail.followUpInspections == null ? 0 : detail.followUpInspections.size()));
                if (detail.followUpInspections != null) {
                    for (Inspection fu : detail.followUpInspections) {
                        tvDetail.append("\n  → " + nvl(fu.itemReference) + " [" + fu.status + "]");
                    }
                }
                renderHistory();
                renderButtons(a);
            }
            @Override public void onFailure(Call<ActionDetail> c, Throwable t) { tvDetail.setText("Error: " + t.getMessage()); }
        });
    }

    private void renderHistory() {
        if (detail.history == null || detail.history.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (AuditEvent e : detail.history) {
            sb.append(e.action).append(" · ").append(DefectDetailActivity.nvl(e.actor))
              .append(" · ").append(DefectDetailActivity.nvl(e.occurredAt)).append("\n");
        }
        tvHistory.setText(sb.toString().trim());
        findViewById(R.id.card_history).setVisibility(android.view.View.VISIBLE);
    }

    private void renderButtons(Action a) {
        llButtons.removeAllViews();
        String[] next = NEXT_STATES.get(a.status);
        if (next != null && next.length > 0) {
            llButtons.addView(button("Update status…", v -> pickStatus(next)));
        }
        llButtons.addView(button("Add work notes…", v -> editNotes(false)));
        if (!"COMPLETED".equals(a.status) && !"CANCELLED".equals(a.status)) {
            llButtons.addView(button("Request follow-up inspection…", v -> followUp()));
        }
    }

    private void pickStatus(String[] options) {
        final String[] chosen = {options[0]};
        new AlertDialog.Builder(this)
                .setTitle("New status")
                .setSingleChoiceItems(options, 0, (d, w) -> chosen[0] = options[w])
                .setPositiveButton("Continue", (d, w) -> {
                    if ("COMPLETED".equals(chosen[0])) editNotes(true);
                    else if ("CANCELLED".equals(chosen[0])) cancelWithReason();
                    else patchStatus(chosen[0], null, null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cancelWithReason() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Cancellation reason (required)");
        new AlertDialog.Builder(this)
                .setTitle("Cancel action")
                .setView(input)
                .setPositiveButton("Cancel action", (d, w) ->
                        patchStatus("CANCELLED", null, input.getText().toString().trim()))
                .setNegativeButton("Back", null)
                .show();
    }

    private void editNotes(boolean completing) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(completing ? "Completion details (required)" : "Work notes");
        input.setSingleLine(false);
        input.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle(completing ? "Complete action" : "Work notes")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (completing) patchStatus("COMPLETED", text, null);
                    else patchNotes(text);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void patchStatus(String status, String completionDetails, String reason) {
        ActionUpdateRequest req = new ActionUpdateRequest();
        req.status = status;
        req.completionDetails = completionDetails;
        req.reason = reason;
        send(req);
    }

    private void patchNotes(String notes) {
        ActionUpdateRequest req = new ActionUpdateRequest();
        req.workNotes = notes;
        send(req);
    }

    private void send(ActionUpdateRequest req) {
        ApiClient.get().updateAction(id, req).enqueue(new Callback<Action>() {
            @Override public void onResponse(Call<Action> c, Response<Action> r) {
                Toast.makeText(ActionDetailActivity.this,
                        r.isSuccessful() ? "Updated" : DefectDetailActivity.err(r), Toast.LENGTH_LONG).show();
                load();
            }
            @Override public void onFailure(Call<Action> c, Throwable t) {
                Toast.makeText(ActionDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void followUp() {
        final android.widget.EditText item = new android.widget.EditText(this);
        item.setHint("Item reference (required)");
        final android.widget.EditText obs = new android.widget.EditText(this);
        obs.setHint("Observations (required)");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);
        wrap.addView(item);
        wrap.addView(obs);
        new AlertDialog.Builder(this)
                .setTitle("Follow-up inspection")
                .setView(wrap)
                .setPositiveButton("Create", (d, w) -> {
                    FollowUpRequest req = new FollowUpRequest(
                            item.getText().toString().trim(), "ITEM", obs.getText().toString().trim());
                    ApiClient.get().createFollowUp(id, req).enqueue(new Callback<Inspection>() {
                        @Override public void onResponse(Call<Inspection> c, Response<Inspection> r) {
                            Toast.makeText(ActionDetailActivity.this,
                                    r.isSuccessful() ? "Follow-up inspection created" : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                            load();
                        }
                        @Override public void onFailure(Call<Inspection> c, Throwable t) {
                            Toast.makeText(ActionDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Button button(String label, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.setOnClickListener(l);
        return b;
    }

    private static String nvl(String s) { return DefectDetailActivity.nvl(s); }
}
