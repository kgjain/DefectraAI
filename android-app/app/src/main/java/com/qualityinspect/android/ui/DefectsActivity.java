package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DefectsActivity extends AppCompatActivity {

    private final List<Defect> items = new ArrayList<>();
    private Adapter adapter;

    // active filters (null = all)
    private String fSeverity;
    private String fStatus;
    private String fCategory;
    private String fFrom;
    private String fTo;
    private String fDateLabel = "All time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("Defects");
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        ListUi.attach(this, this::load);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ApiClient.get().defects(null, null, null, fStatus, fSeverity, fCategory, fFrom, fTo, 0, 100)
                .enqueue(new Callback<PageResponse<Defect>>() {
            @Override public void onResponse(Call<PageResponse<Defect>> c, Response<PageResponse<Defect>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().content != null) {
                    items.clear(); items.addAll(r.body().content); adapter.notifyDataSetChanged();
                }
            }
            @Override public void onFailure(Call<PageResponse<Defect>> c, Throwable t) {}
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list_filter, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFilterDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);

        wrap.addView(label("Severity"));
        RadioGroup severityGroup = singleChoice(wrap,
                new String[]{"All", "LOW", "MEDIUM", "HIGH", "CRITICAL"},
                fSeverity == null ? 0 : indexOf(new String[]{"All", "LOW", "MEDIUM", "HIGH", "CRITICAL"}, fSeverity));

        wrap.addView(label("Status"));
        RadioGroup statusGroup = singleChoice(wrap,
                new String[]{"All", "OPEN", "REOPENED", "CLOSED"},
                fStatus == null ? 0 : indexOf(new String[]{"All", "OPEN", "REOPENED", "CLOSED"}, fStatus));

        wrap.addView(label("Date range"));
        RadioGroup dateGroup = singleChoice(wrap,
                new String[]{"All time", "Last 7 days", "Last 30 days"},
                fFrom == null ? 0 : (fDateLabel.startsWith("Last 7") ? 1 : 2));

        wrap.addView(label("Category (optional, free text)"));
        EditText categoryInput = new EditText(this);
        categoryInput.setHint("e.g. SCRATCH");
        if (fCategory != null) categoryInput.setText(fCategory);
        wrap.addView(categoryInput);

        new AlertDialog.Builder(this)
                .setTitle("Filter defects")
                .setView(wrap)
                .setPositiveButton("Apply", (d, w) -> {
                    int sevIdx = severityGroup.getCheckedRadioButtonId();
                    fSeverity = sevIdx == 0 ? null : severityLabel(sevIdx);
                    int stIdx = statusGroup.getCheckedRadioButtonId();
                    String[] statuses = {"All", "OPEN", "REOPENED", "CLOSED"};
                    fStatus = stIdx == 0 ? null : statuses[stIdx];
                    int dIdx = dateGroup.getCheckedRadioButtonId();
                    if (dIdx == 0) {
                        fFrom = null; fTo = null; fDateLabel = "All time";
                    } else if (dIdx == 1) {
                        fFrom = LocalDate.now().minusDays(7).toString();
                        fTo = LocalDate.now().toString();
                        fDateLabel = "Last 7 days";
                    } else {
                        fFrom = LocalDate.now().minusDays(30).toString();
                        fTo = LocalDate.now().toString();
                        fDateLabel = "Last 30 days";
                    }
                    String cat = categoryInput.getText().toString().trim();
                    fCategory = cat.isEmpty() ? null : cat;
                    setTitle("Defects" + activeFilterSuffix());
                    load();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String severityLabel(int idx) {
        return new String[]{"All", "LOW", "MEDIUM", "HIGH", "CRITICAL"}[idx];
    }

    private String activeFilterSuffix() {
        List<String> parts = new ArrayList<>();
        if (fSeverity != null) parts.add(fSeverity);
        if (fStatus != null) parts.add(fStatus);
        if (fCategory != null) parts.add(fCategory);
        if (fFrom != null) parts.add(fDateLabel);
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(0, 16, 0, 4);
        return tv;
    }

    private RadioGroup singleChoice(LinearLayout parent, String[] options, int checked) {
        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(options[i]);
            rb.setId(i);
            group.addView(rb);
        }
        group.check(checked);
        parent.addView(group);
        return group;
    }

    private static int indexOf(String[] arr, String value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(value)) return i;
        }
        return 0;
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Defect d = items.get(pos);
            h.title.setText(d.code + " — " + d.severity);
            h.sub.setText(d.status + " · " + d.description);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(DefectsActivity.this, DefectDetailActivity.class);
                i.putExtra("id", d.id); startActivity(i);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView title, sub;
            VH(@NonNull View v) { super(v); title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); }
        }
    }
}
