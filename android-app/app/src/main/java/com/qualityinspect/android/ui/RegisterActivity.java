package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.qualityinspect.android.R;
import com.qualityinspect.android.QualityInspectApp;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.qualityinspect.android.data.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private SessionManager session;
    private LinearLayout stepDetails, stepOtp;
    private TextInputEditText etUsername, etDisplayName, etPassword, etContact, etOtp;
    private TextView tvError, tvOtpError, tvOtpSentTo;
    private Button btnSendOtp, btnVerify, btnResend;
    private String contact = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = ((QualityInspectApp) getApplication()).getSession();
        setContentView(R.layout.activity_register);

        stepDetails = findViewById(R.id.ll_step_details);
        stepOtp = findViewById(R.id.ll_step_otp);
        etUsername = findViewById(R.id.et_reg_username);
        etDisplayName = findViewById(R.id.et_reg_display_name);
        etPassword = findViewById(R.id.et_reg_password);
        etContact = findViewById(R.id.et_reg_contact);
        etOtp = findViewById(R.id.et_otp);
        tvError = findViewById(R.id.tv_register_error);
        tvOtpError = findViewById(R.id.tv_otp_error);
        tvOtpSentTo = findViewById(R.id.tv_otp_sent_to);
        btnSendOtp = findViewById(R.id.btn_send_otp);
        btnVerify = findViewById(R.id.btn_verify_otp);
        btnResend = findViewById(R.id.btn_resend_otp);

        btnSendOtp.setOnClickListener(v -> sendOtp());
        btnVerify.setOnClickListener(v -> verifyOtp());
        btnResend.setOnClickListener(v -> resendOtp());
        findViewById(R.id.btn_change_contact).setOnClickListener(v -> showStep1());
        findViewById(R.id.tv_back_to_login).setOnClickListener(v -> finish());
    }

    private void showError(TextView target, String msg) {
        target.setText(msg);
        target.setVisibility(View.VISIBLE);
    }

    private void hideErrors() {
        tvError.setVisibility(View.GONE);
        tvOtpError.setVisibility(View.GONE);
    }

    private void showStep1() {
        hideErrors();
        stepOtp.setVisibility(View.GONE);
        stepDetails.setVisibility(View.VISIBLE);
    }

    private void showStep2(String masked) {
        hideErrors();
        stepDetails.setVisibility(View.GONE);
        stepOtp.setVisibility(View.VISIBLE);
        tvOtpSentTo.setText("We sent a 6-digit code to " + masked + ". Enter it below.");
    }

    private void sendOtp() {
        hideErrors();
        String u = etUsername.getText().toString().trim();
        String d = etDisplayName.getText().toString().trim();
        String p = etPassword.getText().toString();
        contact = etContact.getText().toString().trim();
        if (u.length() < 3) { showError(tvError, "Username must be at least 3 characters"); return; }
        if (p.length() < 8) { showError(tvError, "Password must be at least 8 characters"); return; }
        if (contact.isEmpty()) { showError(tvError, "Enter your email or phone number"); return; }
        btnSendOtp.setEnabled(false);
        ApiClient.get().register(new RegisterRequest(u, p, d, contact)).enqueue(new Callback<OtpResponse>() {
            @Override
            public void onResponse(Call<OtpResponse> call, Response<OtpResponse> resp) {
                btnSendOtp.setEnabled(true);
                if (resp.isSuccessful() && resp.body() != null) {
                    OtpResponse body = resp.body();
                    showStep2(body.contactMasked != null ? body.contactMasked : contact);
                    if (body.devOtp != null && !body.devOtp.isEmpty()) {
                        etOtp.setText(body.devOtp);
                        Toast.makeText(RegisterActivity.this,
                                "Demo mode: OTP " + body.devOtp, Toast.LENGTH_LONG).show();
                    }
                } else {
                    showError(tvError, parseError(resp, "Could not send code"));
                }
            }
            @Override
            public void onFailure(Call<OtpResponse> call, Throwable t) {
                btnSendOtp.setEnabled(true);
                showError(tvError, "Network error: " + t.getMessage());
            }
        });
    }

    private void resendOtp() {
        hideErrors();
        btnResend.setEnabled(false);
        ApiClient.get().resendOtp(new ResendOtpRequest(contact)).enqueue(new Callback<OtpResponse>() {
            @Override
            public void onResponse(Call<OtpResponse> call, Response<OtpResponse> resp) {
                btnResend.setEnabled(true);
                if (resp.isSuccessful() && resp.body() != null) {
                    OtpResponse body = resp.body();
                    if (body.devOtp != null && !body.devOtp.isEmpty()) {
                        etOtp.setText(body.devOtp);
                        Toast.makeText(RegisterActivity.this,
                                "Demo mode: OTP " + body.devOtp, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(RegisterActivity.this, "Code resent", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    showError(tvOtpError, parseError(resp, "Could not resend code"));
                }
            }
            @Override
            public void onFailure(Call<OtpResponse> call, Throwable t) {
                btnResend.setEnabled(true);
                showError(tvOtpError, "Network error: " + t.getMessage());
            }
        });
    }

    private void verifyOtp() {
        hideErrors();
        String otp = etOtp.getText().toString().trim();
        String p = etPassword.getText().toString();
        if (otp.length() != 6) { showError(tvOtpError, "Enter the 6-digit code"); return; }
        btnVerify.setEnabled(false);
        ApiClient.get().verifyRegistration(new OtpVerifyRequest(contact, otp, p))
                .enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> resp) {
                btnVerify.setEnabled(true);
                if (resp.isSuccessful() && resp.body() != null) {
                    AuthResponse ar = resp.body();
                    session.saveTokens(ar.accessToken, ar.refreshToken);
                    if (ar.user != null) {
                        session.saveUser(ar.user.username,
                                ar.user.roles == null ? "" : String.join(",", ar.user.roles));
                    }
                    Toast.makeText(RegisterActivity.this, "Welcome to DEFECTRA AI!",
                            Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, DashboardActivity.class));
                    finish();
                } else {
                    showError(tvOtpError, parseError(resp, "Verification failed"));
                }
            }
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                btnVerify.setEnabled(true);
                showError(tvOtpError, "Network error: " + t.getMessage());
            }
        });
    }

    private String parseError(Response<?> resp, String fallback) {
        try {
            if (resp.errorBody() != null) {
                org.json.JSONObject jo = new org.json.JSONObject(resp.errorBody().string());
                String msg = jo.optString("message", "");
                if (!msg.isEmpty()) return msg;
            }
        } catch (Exception ignored) { }
        if (resp.code() == 409) return "That username is already taken";
        if (resp.code() == 400) return fallback + " — check your details and try again";
        return fallback + " (HTTP " + resp.code() + ")";
    }
}
