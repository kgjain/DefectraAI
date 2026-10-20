package com.qualityinspect.android.ui;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.qualityinspect.android.R;

/**
 * One-line attach for list screens: pull-to-refresh + automatic empty state.
 * The empty view (R.id.tv_empty) is hidden until the adapter reports a change
 * with zero items, so it never flashes while the first page loads.
 */
public final class ListUi {

    private ListUi() {}

    public static void attach(AppCompatActivity activity, Runnable refresh) {
        SwipeRefreshLayout swipe = activity.findViewById(R.id.swipe);
        if (swipe != null) {
            if (refresh != null) {
                swipe.setOnRefreshListener(() -> {
                    refresh.run();
                    swipe.postDelayed(() -> swipe.setRefreshing(false), 1600);
                });
            } else {
                swipe.setEnabled(false);
            }
        }
        RecyclerView rv = activity.findViewById(R.id.rv);
        if (rv == null) rv = activity.findViewById(R.id.rv_results);
        View empty = activity.findViewById(R.id.tv_empty);
        if (rv == null || empty == null) return;
        RecyclerView.Adapter<?> adapter = rv.getAdapter();
        if (adapter == null) return;
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            private void sync() {
                empty.setVisibility(adapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
            }
            @Override public void onChanged() { sync(); }
            @Override public void onItemRangeInserted(int positionStart, int itemCount) { sync(); }
            @Override public void onItemRangeRemoved(int positionStart, int itemCount) { sync(); }
            @Override public void onItemRangeChanged(int positionStart, int itemCount) { sync(); }
        });
    }
}
