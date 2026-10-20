package com.qualityinspect.android;

import android.app.Application;
import androidx.work.Configuration;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.data.SessionManager;

public class QualityInspectApp extends Application implements Configuration.Provider {

    private SessionManager session;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new SessionManager(this);
        ApiClient.init(this, session);
    }

    public SessionManager getSession() {
        return session;
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
    }
}
