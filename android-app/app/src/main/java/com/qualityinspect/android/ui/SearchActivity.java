package com.qualityinspect.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.ApiService;
import com.qualityinspect.android.api.Models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private EditText etQuery;
    private TextView tvResult;
    private final List<String> lines = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        setTitle("Search");
        etQuery = findViewById(R.id.et_query);
        tvResult = findViewById(R.id.tv_result);
        Button btn = findViewById(R.id.btn_search);
        RecyclerView rv = findViewById(R.id.rv_results);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        ListUi.attach(this, null);
        btn.setOnClickListener(v -> doSearch());
    }

    private void doSearch() {
        tvResult.setVisibility(View.GONE);
        String q = etQuery.getText().toString().trim();
        if (q.isEmpty()) return;
        ApiClient.get().search(q, null, 0, 20).enqueue(new Callback<ApiService.SearchResponse>() {
            @Override public void onResponse(Call<ApiService.SearchResponse> c, Response<ApiService.SearchResponse> r) {
                lines.clear();
                if (r.isSuccessful() && r.body() != null) {
                    ApiService.SearchResponse s = r.body();
                    if (s.products != null && s.products.content != null)
                        for (Product p : s.products.content) lines.add("PRODUCT " + p.code + " — " + p.name);
                    if (s.batches != null && s.batches.content != null)
                        for (Batch b : s.batches.content) lines.add("BATCH " + b.code + " — " + b.productName);
                    if (s.inspections != null && s.inspections.content != null)
                        for (Inspection i : s.inspections.content) lines.add("INSPECTION " + i.itemReference + " — " + i.status);
                    if (s.defects != null && s.defects.content != null)
                        for (Defect d : s.defects.content) lines.add("DEFECT " + d.code + " — " + d.severity);
                    if (s.actions != null && s.actions.content != null)
                        for (Action a : s.actions.content) lines.add("ACTION " + a.code + " — " + a.status);
                }
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText("Results: " + lines.size());
                adapter.notifyDataSetChanged();
            }
            @Override public void onFailure(Call<ApiService.SearchResponse> c, Throwable t) {
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText("Error: " + t.getMessage());
            }
        });
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            String line = lines.get(pos);
            int idx = line.indexOf(' ');
            h.title.setText(line.substring(0, idx));
            h.sub.setText(line.substring(idx + 1));
        }
        @Override public int getItemCount() { return lines.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView title, sub;
            VH(@NonNull View v) { super(v); title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); }
        }
    }
}
