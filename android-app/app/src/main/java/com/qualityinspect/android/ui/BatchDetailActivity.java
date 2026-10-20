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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BatchDetailActivity extends AppCompatActivity {

    private long id;
    private SessionManager session;
    private TextView tvDetail;
    private Batch current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        setTitle("Batch");
        session = ((com.qualityinspect.android.QualityInspectApp) getApplication()).getSession();
        id = getIntent().getLongExtra("id", -1);
        tvDetail = findViewById(R.id.tv_detail);
        if (session.isQualityManager()) {
            LinearLayout ll = findViewById(R.id.ll_buttons);
            Button b = new Button(this);
            b.setText("Record batch decision…");
            b.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            b.setOnClickListener(v -> decide());
            ll.addView(b);

            Button edit = new Button(this);
            edit.setText("Edit batch…");
            edit.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            edit.setOnClickListener(v -> editBatch());
            ll.addView(edit);

            Button archive = new Button(this);
            archive.setText("Archive batch");
            archive.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            archive.setOnClickListener(v -> confirmArchive());
            ll.addView(archive);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (id > 0) {
            load();
            loadQuality();
        }
    }

    private void load() {
        ApiClient.get().batch(id).enqueue(new Callback<Batch>() {
            @Override public void onResponse(Call<Batch> c, Response<Batch> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    tvDetail.setText("Error: HTTP " + r.code());
                    return;
                }
                current = r.body();
                tvDetail.setText("Code: " + current.code + "\nProduct: " + nvl(current.productName)
                        + "\nProduction date: " + nvl(current.productionDate)
                        + "\nQuantity: " + current.quantityProduced
                        + "\nLine: " + nvl(current.productionLine) + "\nSupplier: " + nvl(current.supplier)
                        + "\nStatus: " + current.status + "\nDecision: " + nvl(current.currentDecision));
            }
            @Override public void onFailure(Call<Batch> c, Throwable t) { tvDetail.setText("Error: " + t.getMessage()); }
        });
    }

    private void loadQuality() {
        ApiClient.get().batchQuality(id).enqueue(new Callback<BatchQuality>() {
            @Override public void onResponse(Call<BatchQuality> c, Response<BatchQuality> r) {
                android.view.View card = findViewById(R.id.card_quality);
                if (!r.isSuccessful() || r.body() == null) {
                    if (card != null) card.setVisibility(android.view.View.GONE);
                    return;
                }
                BatchQuality q = r.body();
                if (card != null) card.setVisibility(android.view.View.VISIBLE);
                TextView tv = findViewById(R.id.tv_quality);
                if (tv != null) {
                    tv.setText("Inspected items: " + q.distinctItemsInspected
                            + "\nReviewed items: " + q.distinctItemsReviewed
                            + "\nConfirmed defective items: " + q.distinctItemsWithConfirmedDefects
                            + "\nPending reviews: " + q.pendingReviews
                            + "\nPending AI processing: " + q.pendingProcessing
                            + "\nOpen corrective actions: " + q.openCorrectiveActions
                            + "\nCurrent decision: " + (q.currentDecision == null ? "—" : q.currentDecision));
                }
            }
            @Override public void onFailure(Call<BatchQuality> c, Throwable t) {
                android.view.View card = findViewById(R.id.card_quality);
                if (card != null) card.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void editBatch() {
        if (current == null) return;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);
        android.widget.EditText code = field("Code", current.code);
        android.widget.EditText date = field("Production date (YYYY-MM-DD)", current.productionDate);
        android.widget.EditText qty = field("Quantity", String.valueOf(current.quantityProduced));
        android.widget.EditText line = field("Production line", current.productionLine);
        android.widget.EditText supplier = field("Supplier", current.supplier);
        wrap.addView(code); wrap.addView(date); wrap.addView(qty);
        wrap.addView(line); wrap.addView(supplier);
        new AlertDialog.Builder(this)
                .setTitle("Edit batch")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    BatchUpdateRequest req = new BatchUpdateRequest();
                    req.code = text(code);
                    req.productionDate = text(date);
                    try { req.quantityProduced = Integer.parseInt(text(qty)); } catch (NumberFormatException ignored) {}
                    req.productionLine = text(line);
                    req.supplier = text(supplier);
                    ApiClient.get().updateBatch(id, req).enqueue(new Callback<Batch>() {
                        @Override public void onResponse(Call<Batch> c, Response<Batch> r) {
                            Toast.makeText(BatchDetailActivity.this,
                                    r.isSuccessful() ? "Batch updated" : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                            load();
                        }
                        @Override public void onFailure(Call<Batch> c, Throwable t) {
                            Toast.makeText(BatchDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmArchive() {
        new AlertDialog.Builder(this)
                .setTitle("Archive batch?")
                .setMessage("Archived batches keep all history but cannot receive new inspections or decisions.")
                .setPositiveButton("Archive", (d, w) ->
                        ApiClient.get().archiveBatch(id).enqueue(new Callback<Batch>() {
                            @Override public void onResponse(Call<Batch> c, Response<Batch> r) {
                                Toast.makeText(BatchDetailActivity.this,
                                        r.isSuccessful() ? "Batch archived" : DefectDetailActivity.err(r),
                                        Toast.LENGTH_LONG).show();
                                load();
                            }
                            @Override public void onFailure(Call<Batch> c, Throwable t) {
                                Toast.makeText(BatchDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void decide() {
        final String[] options = {"HOLD", "RELEASE", "CONDITIONAL_RELEASE"};
        final int[] picked = {0};
        final android.widget.EditText reason = new android.widget.EditText(this);
        reason.setHint("Reason (required, min 5 chars)");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);
        wrap.addView(reason);
        new AlertDialog.Builder(this)
                .setTitle("Record batch decision")
                .setView(wrap)
                .setSingleChoiceItems(options, 0, (d, w) -> picked[0] = w)
                .setPositiveButton("Submit", (d, w) -> {
                    DecisionRequest req = new DecisionRequest();
                    req.decision = options[picked[0]];
                    req.reason = reason.getText().toString().trim();
                    ApiClient.get().decide(id, req).enqueue(new Callback<Decision>() {
                        @Override public void onResponse(Call<Decision> c, Response<Decision> r) {
                            Toast.makeText(BatchDetailActivity.this,
                                    r.isSuccessful() ? "Decision recorded: " + req.decision : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                            load();
                            loadQuality();
                        }
                        @Override public void onFailure(Call<Decision> c, Throwable t) {
                            Toast.makeText(BatchDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private android.widget.EditText field(String hint, String value) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint);
        if (value != null) et.setText(value);
        return et;
    }

    private static String text(android.widget.EditText et) {
        String s = et.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String nvl(String s) { return DefectDetailActivity.nvl(s); }
}
