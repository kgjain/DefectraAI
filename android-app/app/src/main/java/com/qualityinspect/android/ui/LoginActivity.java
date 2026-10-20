package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.qualityinspect.android.R;
import com.qualityinspect.android.QualityInspectApp;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.qualityinspect.android.data.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText username, password;
    private Button loginBtn;
    private TextView loginError;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = ((QualityInspectApp) getApplication()).getSession();
        if (session.isLoggedIn()) {
            goDashboard();
            return;
        }
        setContentView(R.layout.activity_login);
        username = findViewById(R.id.et_username);
        password = findViewById(R.id.et_password);
        loginBtn = findViewById(R.id.btn_login);
        loginError = findViewById(R.id.tv_login_error);
        loginBtn.setOnClickListener(v -> doLogin());
        findViewById(R.id.tv_go_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void showError(String msg) {
        loginError.setText(msg);
        loginError.setVisibility(android.view.View.VISIBLE);
    }

    private void doLogin() {
        loginBtn.setEnabled(false);
        loginError.setVisibility(android.view.View.GONE);
        String u = username.getText().toString().trim();
        String p = password.getText().toString();
        try {
            ApiClient.get().login(new LoginRequest(u, p)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> resp) {
                loginBtn.setEnabled(true);
                if (resp.isSuccessful() && resp.body() != null) {
                    AuthResponse ar = resp.body();
                    session.saveTokens(ar.accessToken, ar.refreshToken);
                    if (ar.user != null) {
                        session.saveUser(ar.user.username,
                                ar.user.roles == null ? "" : String.join(",", ar.user.roles));
                    }
                    goDashboard();
                } else {
                    showError(resp.code() == 401 || resp.code() == 403
                            ? "Invalid username or password"
                            : "Login failed (HTTP " + resp.code() + ")");
                }
            }
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                loginBtn.setEnabled(true);
                showError("Network error: " + t.getMessage());
            }
        });
        } catch (RuntimeException e) {
            showError("Login error");
            loginBtn.setEnabled(true);
        }
    }

    private void goDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
