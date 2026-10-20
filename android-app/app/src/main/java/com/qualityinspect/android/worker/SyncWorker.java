package com.qualityinspect.android.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.DashboardSummary;
import retrofit2.Response;

/** Periodically refreshes the dashboard summary so the UI is warm on open. */
public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Response<DashboardSummary> resp = ApiClient.get().dashboardSummary().execute();
            return resp.isSuccessful() ? Result.success() : Result.retry();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
