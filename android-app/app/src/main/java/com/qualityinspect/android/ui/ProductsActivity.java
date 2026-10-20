package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

import java.util.ArrayList;
import java.util.List;

public class ProductsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private final List<Product> items = new ArrayList<>();
    private ProductAdapter adapter;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("Products");
        session = ((QualityInspectApp) getApplication()).getSession();
        rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter();
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
        ApiClient.get().products(null, null, null, 0, 100).enqueue(new Callback<PageResponse<Product>>() {
            @Override public void onResponse(Call<PageResponse<Product>> c, Response<PageResponse<Product>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().content != null) {
                    items.clear(); items.addAll(r.body().content); adapter.notifyDataSetChanged();
                }
            }
            @Override public void onFailure(Call<PageResponse<Product>> c, Throwable t) {}
        });
    }

    private void showCreateDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);
        android.widget.EditText code = field("Code (e.g. SKU-100)");
        android.widget.EditText name = field("Name");
        android.widget.EditText category = field("Category (e.g. PARTS)");
        android.widget.EditText description = field("Description (optional)");
        wrap.addView(code); wrap.addView(name); wrap.addView(category); wrap.addView(description);
        new AlertDialog.Builder(this)
                .setTitle("New product")
                .setView(wrap)
                .setPositiveButton("Create", (d, w) -> {
                    ProductRequest req = new ProductRequest();
                    req.code = text(code);
                    req.name = text(name);
                    req.category = text(category);
                    req.description = text(description);
                    ApiClient.get().createProduct(req).enqueue(new Callback<Product>() {
                        @Override public void onResponse(Call<Product> c, Response<Product> r) {
                            Toast.makeText(ProductsActivity.this,
                                    r.isSuccessful() ? "Product created" : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                            if (r.isSuccessful()) load();
                        }
                        @Override public void onFailure(Call<Product> c, Throwable t) {
                            Toast.makeText(ProductsActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
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
        return s.isEmpty() ? null : s;
    }

    class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Product p = items.get(pos);
            h.title.setText(p.code + " — " + p.name);
            h.sub.setText(p.category + " · " + p.status);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ProductsActivity.this, ProductDetailActivity.class);
                i.putExtra("id", p.id); startActivity(i);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView title, sub;
            VH(@NonNull View v) { super(v); title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); }
        }
    }
}
