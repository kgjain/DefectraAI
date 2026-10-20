package com.qualityinspect.android.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.qualityinspect.android.R;
import com.qualityinspect.android.QualityInspectApp;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.qualityinspect.android.data.SessionManager;
import com.qualityinspect.android.widget.TrendChartView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvStats;
    private ProgressBar pbStats;
    private SessionManager session;
    private TrendChartView trendChart;
    private LinearLayout llRecent;
    private TextView tvRecentEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = ((QualityInspectApp) getApplication()).getSession();
        setContentView(R.layout.activity_dashboard);
        setTitle("Dashboard");
        tvStats = findViewById(R.id.tv_stats);
        pbStats = findViewById(R.id.pb_stats);
        trendChart = findViewById(R.id.trend_chart);
        llRecent = findViewById(R.id.ll_recent);
        tvRecentEmpty = findViewById(R.id.tv_recent_empty);
        TextView greeting = findViewById(R.id.tv_greeting);
        TextView subtitle = findViewById(R.id.tv_subtitle);
        greeting.setText(timeGreeting() + ",");
        subtitle.setText(session.getUsername() + " · Quality Console");
        findViewById(R.id.btn_new_inspection).setOnClickListener(v ->
                startActivity(new Intent(this, NewInspectionActivity.class)));
        findViewById(R.id.btn_review_queue).setOnClickListener(v ->
                startActivity(new Intent(this, ReviewQueueActivity.class)));
        findViewById(R.id.btn_products).setOnClickListener(v ->
                startActivity(new Intent(this, ProductsActivity.class)));
        findViewById(R.id.btn_batches).setOnClickListener(v ->
                startActivity(new Intent(this, BatchesActivity.class)));
        findViewById(R.id.btn_defects).setOnClickListener(v ->
                startActivity(new Intent(this, DefectsActivity.class)));
        findViewById(R.id.btn_actions).setOnClickListener(v ->
                startActivity(new Intent(this, ActionsActivity.class)));
        findViewById(R.id.btn_audit).setOnClickListener(v ->
                startActivity(new Intent(this, AuditActivity.class)));
        findViewById(R.id.btn_search).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));

        // drill-through: summary totals open the corresponding source lists
        findViewById(R.id.tile_products).setOnClickListener(v ->
                startActivity(new Intent(this, ProductsActivity.class)));
        findViewById(R.id.tile_batches).setOnClickListener(v ->
                startActivity(new Intent(this, BatchesActivity.class)));
        findViewById(R.id.tile_inspected).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.tile_pending).setOnClickListener(v ->
                startActivity(new Intent(this, ReviewQueueActivity.class)));
        findViewById(R.id.chip_defects).setOnClickListener(v ->
                startActivity(new Intent(this, DefectsActivity.class)));
        findViewById(R.id.chip_actions).setOnClickListener(v ->
                startActivity(new Intent(this, ActionsActivity.class)));

        android.view.View btnUsers = findViewById(R.id.btn_users);
        if (session.isQualityManager()) {
            btnUsers.setVisibility(android.view.View.VISIBLE);
            btnUsers.setOnClickListener(v ->
                    startActivity(new Intent(this, UsersActivity.class)));
        }
    }

    private String timeGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSummary();
        loadTrends();
        loadRecent();
    }

    private void loadSummary() {
        pbStats.setVisibility(android.view.View.VISIBLE);
        ApiClient.get().dashboardSummary().enqueue(new Callback<DashboardSummary>() {
            @Override
            public void onResponse(Call<DashboardSummary> call, Response<DashboardSummary> resp) {
                pbStats.setVisibility(android.view.View.GONE);
                if (resp.isSuccessful() && resp.body() != null) {
                    DashboardSummary s = resp.body();
                    countUp(findViewById(R.id.tv_stat_products), s.activeProducts);
                    countUp(findViewById(R.id.tv_stat_batches), s.activeBatches);
                    countUp(findViewById(R.id.tv_stat_inspected), s.distinctItemsInspected);
                    countUp(findViewById(R.id.tv_stat_pending), s.pendingReviews);
                    String detail = "Defect rate " + s.rateNumerator + "/" + s.rateDenominator
                            + " · Confirmed defects: " + s.distinctItemsWithConfirmedDefects
                            + " · Pending AI: " + s.pendingProcessing
                            + " · Open CAPA: " + s.openCorrectiveActions;
                    if (s.overdueCorrectiveActions > 0) {
                        detail += " · Overdue: " + s.overdueCorrectiveActions;
                    }
                    tvStats.setText(detail);
                } else if (resp.code() == 401 || resp.code() == 403) {
                    session.clear();
                    ApiClient.reset();
                    startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
                    finish();
                } else {
                    tvStats.setText("Could not load summary" +
                            (resp.code() > 0 ? " (HTTP " + resp.code() + ")" : ""));
                }
            }

            @Override
            public void onFailure(Call<DashboardSummary> call, Throwable t) {
                pbStats.setVisibility(android.view.View.GONE);
                tvStats.setText("Failed to load: " + t.getMessage());
            }
        });
    }

    private void loadTrends() {
        ApiClient.get().dashboardTrends(30).enqueue(new Callback<TrendResponse>() {
            @Override
            public void onResponse(Call<TrendResponse> c, Response<TrendResponse> r) {
                if (r.isSuccessful() && r.body() != null) {
                    trendChart.setPoints(r.body().points);
                }
            }
            @Override
            public void onFailure(Call<TrendResponse> c, Throwable t) {
                trendChart.setPoints(null);
            }
        });
    }

    private void loadRecent() {
        ApiClient.get().dashboardRecent().enqueue(new Callback<RecentActivity>() {
            @Override
            public void onResponse(Call<RecentActivity> c, Response<RecentActivity> r) {
                llRecent.removeAllViews();
                if (!r.isSuccessful() || r.body() == null) {
                    tvRecentEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                RecentActivity recent = r.body();
                int rows = 0;
                if (recent.recentInspections != null) {
                    for (Inspection ins : recent.recentInspections) {
                        if (rows++ >= 6) break;
                        addRecentRow("Inspection #" + ins.id, ins.batchCode + " · " + ins.itemReference
                                + " · " + ins.status, v -> {
                            Intent i = new Intent(DashboardActivity.this, InspectionDetailActivity.class);
                            i.putExtra("id", ins.id);
                            startActivity(i);
                        });
                    }
                }
                if (recent.recentlyConfirmedDefects != null) {
                    for (Defect d : recent.recentlyConfirmedDefects) {
                        if (rows++ >= 6) break;
                        addRecentRow("Defect " + d.code, d.severity + " · " + d.status
                                + " · " + d.description, v -> {
                            Intent i = new Intent(DashboardActivity.this, DefectDetailActivity.class);
                            i.putExtra("id", d.id);
                            startActivity(i);
                        });
                    }
                }
                if (recent.actionsNeedingAttention != null) {
                    for (Action a : recent.actionsNeedingAttention) {
                        if (rows++ >= 6) break;
                        addRecentRow("Action " + a.code, a.status + " · " + a.title
                                + (a.overdue ? " · OVERDUE" : ""), v -> {
                            Intent i = new Intent(DashboardActivity.this, ActionDetailActivity.class);
                            i.putExtra("id", a.id);
                            startActivity(i);
                        });
                    }
                }
                tvRecentEmpty.setVisibility(rows == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<RecentActivity> c, Throwable t) {
                tvRecentEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void addRecentRow(String title, String sub, View.OnClickListener onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_row, llRecent, false);
        ((TextView) row.findViewById(R.id.tv_title)).setText(title);
        ((TextView) row.findViewById(R.id.tv_sub)).setText(sub);
        row.setOnClickListener(onClick);
        llRecent.addView(row);
    }

    private void countUp(TextView tv, int target) {
        ValueAnimator anim = ValueAnimator.ofInt(0, target);
        anim.setDuration(650);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> tv.setText(String.valueOf((int) a.getAnimatedValue())));
        anim.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            session.clear();
            ApiClient.reset();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
