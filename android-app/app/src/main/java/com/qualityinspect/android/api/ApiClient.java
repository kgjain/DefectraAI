package com.qualityinspect.android.api;

import android.content.Context;
import com.qualityinspect.android.data.SessionManager;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class ApiClient {

    private static volatile ApiService api;
    private static SessionManager session;

    private ApiClient() {}

    public static void init(Context context, SessionManager sessionManager) {
        session = sessionManager;
    }

    public static ApiService get() {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) {
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .addInterceptor(authInterceptor())
                            .addInterceptor(logging)
                            .build();

                    api = new Retrofit.Builder()
                            .baseUrl(session.getBaseUrl() + "/")
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(ApiService.class);
                }
            }
        }
        return api;
    }

    public static void reset() {
        api = null;
    }

    private static Interceptor authInterceptor() {
        return chain -> {
            Request original = chain.request();
            String token = session.getAccessToken();
            if (token != null && original.header("Authorization") == null) {
                Request.Builder b = original.newBuilder()
                        .header("Authorization", "Bearer " + token);
                return chain.proceed(b.build());
            }
            return chain.proceed(original);
        };
    }
}
