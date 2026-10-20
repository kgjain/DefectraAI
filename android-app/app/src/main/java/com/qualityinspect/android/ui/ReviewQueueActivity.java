package com.qualityinspect.android.ui;

import android.content.Intent;
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

public class ReviewQueueActivity extends AppCompatActivity {

    private final List<ReviewQueueItem> items = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("Review Queue");
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        ListUi.attach(this, null);
        ApiClient.get().reviewQueue().enqueue(new Callback<List<ReviewQueueItem>>() {
            @Override public void onResponse(Call<List<ReviewQueueItem>> c, Response<List<ReviewQueueItem>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    items.clear(); items.addAll(r.body()); adapter.notifyDataSetChanged();
                }
            }
            @Override public void onFailure(Call<List<ReviewQueueItem>> c, Throwable t) {}
        });
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            ReviewQueueItem q = items.get(pos);
            h.title.setText(q.itemReference + " — " + q.productName);
            StringBuilder reasonText = new StringBuilder();
            if (q.reasons != null) {
                for (ReviewQueueItem.Reason r : q.reasons) {
                    if (reasonText.length() > 0) reasonText.append(" · ");
                    reasonText.append(r.detail != null ? r.detail : r.factor);
                }
            }
            h.sub.setText("Score " + q.priorityScore
                    + (reasonText.length() > 0 ? " · " + reasonText : ""));
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ReviewQueueActivity.this, InspectionDetailActivity.class);
                i.putExtra("id", q.inspectionId); startActivity(i);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView title, sub;
            VH(@NonNull View v) { super(v); title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); }
        }
    }
}
