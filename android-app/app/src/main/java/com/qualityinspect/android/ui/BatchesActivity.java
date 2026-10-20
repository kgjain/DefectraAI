package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qualityinspect.android.R;
import com.qualityinspect.android.QualityInspectApp;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.qualityinspect.android.data.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BatchesActivity extends AppCompatActivity {

    private final List<Batch> items = new ArrayList<>();
    private Adapter adapter;
    private SessionManager session;
    private final List<Product> productCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("Batches");
        session = ((QualityInspectApp) getApplication()).getSession();
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        ListUi.attach(this, this::load);

        FloatingActionButton fab = findViewById(R.id.fab_add);
        if (session.isQualityManager()) {
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(v -> showCreateDialog());
        }
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ApiClient.get().batches(null, null, null, 0, 100).enqueue(new Callback<PageResponse<Batch>>() {
            @Override public void onResponse(Call<PageResponse<Batch>> c, Response<PageResponse<Batch>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().content != null) {
                    items.clear(); items.addAll(r.body().content); adapter.notifyDataSetChanged();
                }
            }
            @Override public void onFailure(Call<PageResponse<Batch>> c, Throwable t) {}
        });
    }

    private void showCreateDialog() {
        // product picker needs the current catalog
        ApiClient.get().products(null, null, "ACTIVE", 0, 100).enqueue(new Callback<PageResponse<Product>>() {
            @Override public void onResponse(Call<PageResponse<Product>> c, Response<PageResponse<Product>> r) {
                productCache.clear();
                if (r.isSuccessful() && r.body() != null && r.body().content != null) {
                    productCache.addAll(r.body().content);
                }
                if (productCache.isEmpty()) {
                    Toast.makeText(BatchesActivity.this,
                            "Create a product first — batches must reference one", Toast.LENGTH_LONG).show();
                    return;
                }
                openCreateDialog();
            }
            @Override public void onFailure(Call<PageResponse<Product>> c, Throwable t) {
                Toast.makeText(BatchesActivity.this, "Could not load products: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openCreateDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);

        String[] labels = new String[productCache.size()];
        for (int i = 0; i < productCache.size(); i++) {
            labels[i] = productCache.get(i).code + " — " + productCache.get(i).name;
        }
        Spinner productSpinner = new Spinner(this);
        productSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        wrap.addView(productSpinner);

        android.widget.EditText code = field("Batch code (e.g. B-2026-001)");
        android.widget.EditText qty = field("Quantity produced");
        android.widget.EditText line = field("Production line (optional)");
        wrap.addView(code); wrap.addView(qty); wrap.addView(line);

        new AlertDialog.Builder(this)
                .setTitle("New batch")
                .setView(wrap)
                .setPositiveButton("Create", (d, w) -> {
                    Product picked = productCache.get(productSpinner.getSelectedItemPosition());
                    BatchRequest req = new BatchRequest();
                    req.code = text(code);
                    req.productId = picked.id;
                    req.productionDate = LocalDate.now().toString();
                    try { req.quantityProduced = Integer.parseInt(text(qty)); } catch (Exception ignored) {}
                    req.productionLine = text(line);
                    ApiClient.get().createBatch(req).enqueue(new Callback<Batch>() {
                        @Override public void onResponse(Call<Batch> c, Response<Batch> r) {
                            Toast.makeText(BatchesActivity.this,
                                    r.isSuccessful() ? "Batch created" : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                            if (r.isSuccessful()) load();
                        }
                        @Override public void onFailure(Call<Batch> c, Throwable t) {
                            Toast.makeText(BatchesActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private android.widget.EditText field(String hint) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint);
        return et;
    }

    private static String text(android.widget.EditText et) {
        String s = et.getText().toString().trim();
        return s.isEmpty() ? "" : s;
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Batch b = items.get(pos);
            h.title.setText(b.code + " — " + b.productName);
            h.sub.setText("Qty " + b.quantityProduced + " · " + b.status + " · " + b.currentDecision);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(BatchesActivity.this, BatchDetailActivity.class);
                i.putExtra("id", b.id); startActivity(i);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView title, sub;
            VH(@NonNull View v) { super(v); title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); }
        }
    }
}
