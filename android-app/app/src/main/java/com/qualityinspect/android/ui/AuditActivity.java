package com.qualityinspect.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

public class AuditActivity extends AppCompatActivity {

    private final List<AuditEvent> items = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("Audit Trail (QM only)");
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        ListUi.attach(this, null);
        ApiClient.get().auditEvents(null, 0, 200).enqueue(new Callback<PageResponse<AuditEvent>>() {
            @Override public void onResponse(Call<PageResponse<AuditEvent>> c, Response<PageResponse<AuditEvent>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().content != null) {
                    items.clear(); items.addAll(r.body().content); adapter.notifyDataSetChanged();
                } else {
                    TextView err = findViewById(R.id.tv_error);
                    err.setVisibility(View.VISIBLE);
                    err.setText("Access denied (" + r.code() + ") — QM role required");
                }
            }
            @Override public void onFailure(Call<PageResponse<AuditEvent>> c, Throwable t) {}
        });
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            AuditEvent e = items.get(pos);
            h.title.setText(e.action + " " + e.entityType + "#" + e.entityId);
            h.sub.setText(e.actor + " · " + e.occurredAt);
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView title, sub;
            VH(@NonNull View v) { super(v); title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); }
        }
    }
}
