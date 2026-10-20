package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
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

import java.util.ArrayList;
import java.util.List;

public class ActionsActivity extends AppCompatActivity {

    private final List<Action> items = new ArrayList<>();
    private final List<OwnerOption> owners = new ArrayList<>();
    private Adapter adapter;

    private String fStatus;
    private Long fOwnerId;
    private boolean fOverdueOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setTitle("Corrective Actions");
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        ListUi.attach(this, this::load);
        loadOwners();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ApiClient.get().actions(null, null, fOwnerId, fStatus, fOverdueOnly, 0, 100)
                .enqueue(new Callback<PageResponse<Action>>() {
            @Override public void onResponse(Call<PageResponse<Action>> c, Response<PageResponse<Action>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().content != null) {
                    items.clear(); items.addAll(r.body().content); adapter.notifyDataSetChanged();
                }
            }
            @Override public void onFailure(Call<PageResponse<Action>> c, Throwable t) {}
        });
    }

    private void loadOwners() {
        ApiClient.get().actionOwners(null).enqueue(new Callback<List<OwnerOption>>() {
            @Override public void onResponse(Call<List<OwnerOption>> c, Response<List<OwnerOption>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    owners.clear(); owners.addAll(r.body());
                }
            }
            @Override public void onFailure(Call<List<OwnerOption>> c, Throwable t) {}
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

        wrap.addView(label("Status"));
        String[] statuses = {"All", "OPEN", "IN_PROGRESS", "VERIFICATION_PENDING", "CLOSED", "CANCELLED"};
        int statusChecked = fStatus == null ? 0 : indexOf(statuses, fStatus);
        RadioGroup statusGroup = singleChoice(wrap, statuses, statusChecked);

        wrap.addView(label("Owner"));
        // owner spinner: index 0 = All owners, then one per owner
        List<String> ownerLabels = new ArrayList<>();
        ownerLabels.add("All owners");
        for (OwnerOption o : owners) {
            String roleText = (o.roles == null || o.roles.isEmpty()) ? "" : " (" + String.join(", ", o.roles) + ")";
            ownerLabels.add(o.username + roleText);
        }
        Spinner ownerSpinner = new Spinner(this);
        ownerSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, ownerLabels));
        if (fOwnerId != null) {
            for (int i = 0; i < owners.size(); i++) {
                if (owners.get(i).id == fOwnerId) { ownerSpinner.setSelection(i + 1); break; }
            }
        }
        wrap.addView(ownerSpinner);

        wrap.addView(label("Due date"));
        RadioGroup overdueGroup = singleChoice(wrap,
                new String[]{"All", "Overdue only"}, fOverdueOnly ? 1 : 0);

        new AlertDialog.Builder(this)
                .setTitle("Filter actions")
                .setView(wrap)
                .setPositiveButton("Apply", (d, w) -> {
                    int sIdx = statusGroup.getCheckedRadioButtonId();
                    fStatus = sIdx == 0 ? null : statuses[sIdx];
                    int oIdx = ownerSpinner.getSelectedItemPosition();
                    fOwnerId = (oIdx <= 0 || oIdx > owners.size()) ? null : owners.get(oIdx - 1).id;
                    fOverdueOnly = overdueGroup.getCheckedRadioButtonId() == 1;
                    setTitle("Corrective Actions" + activeFilterSuffix());
                    load();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String activeFilterSuffix() {
        List<String> parts = new ArrayList<>();
        if (fStatus != null) parts.add(fStatus);
        if (fOwnerId != null) parts.add("owner assigned");
        if (fOverdueOnly) parts.add("overdue");
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
            Action a = items.get(pos);
            h.title.setText(a.code + " — " + a.title);
            h.sub.setText(a.status + " · due " + a.targetDate + (a.overdue ? " · OVERDUE" : ""));
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ActionsActivity.this, ActionDetailActivity.class);
                i.putExtra("id", a.id); startActivity(i);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView title, sub;
            VH(@NonNull View v) { super(v); title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); }
        }
    }
}
