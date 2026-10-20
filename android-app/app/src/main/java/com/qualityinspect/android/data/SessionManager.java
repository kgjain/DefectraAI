package com.qualityinspect.android.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores JWT access/refresh tokens and current user info. */
public class SessionManager {

    private static final String PREF = "qi_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLES = "roles";
    private static final String KEY_BASE_URL = "base_url";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void saveTokens(String access, String refresh) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply();
    }

    public void saveUser(String username, String roles) {
        prefs.edit().putString(KEY_USERNAME, username).putString(KEY_ROLES, roles).apply();
    }

    public String getAccessToken() { return prefs.getString(KEY_ACCESS, null); }
    public String getRefreshToken() { return prefs.getString(KEY_REFRESH, null); }
    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }
    public String getRoles() { return prefs.getString(KEY_ROLES, ""); }

    public boolean isLoggedIn() { return getAccessToken() != null; }

    public boolean isQualityManager() {
        return getRoles().contains("QUALITY_MANAGER");
    }

    public String getBaseUrl() {
        // Physical phones on Wi-Fi cannot reach "localhost" (that is the phone).
        // Any stored localhost/127.0.0.1 value is transparently redirected to the
        // dev machine's LAN address; explicitly saved non-localhost URLs win.
        String stored = prefs.getString(KEY_BASE_URL, null);
        if (stored == null || stored.contains("localhost") || stored.contains("127.0.0.1")) {
            return "http://10.12.183.188:8080";
        }
        return stored;
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
