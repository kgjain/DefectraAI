package com.qualityinspect.android.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.qualityinspect.android.data.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** QUALITY_MANAGER-only user administration: list, create, enable/disable, roles, password reset. */
public class UsersActivity extends AppCompatActivity {

    private static final String[] ALL_ROLES = {"INSPECTOR", "SUPERVISOR", "QUALITY_MANAGER"};

    private LinearLayout llUsers;
    private ProgressBar pbUsers;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        setTitle("Users");

        session = new SessionManager(this);
        if (!session.isQualityManager()) {
            Toast.makeText(this, "Quality manager role required", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        llUsers = findViewById(R.id.ll_users);
        pbUsers = findViewById(R.id.pb_users);
        findViewById(R.id.btn_add_user).setOnClickListener(v -> showAddDialog());

        loadUsers();
    }

    private void loadUsers() {
        pbUsers.setVisibility(View.VISIBLE);
        ApiClient.get().adminUsers().enqueue(new Callback<List<AdminUser>>() {
            @Override
            public void onResponse(Call<List<AdminUser>> c, Response<List<AdminUser>> r) {
                pbUsers.setVisibility(View.GONE);
                if (r.isSuccessful() && r.body() != null) {
                    renderUsers(r.body());
                } else {
                    Toast.makeText(UsersActivity.this, "Load failed: " + errMessage(r),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<AdminUser>> c, Throwable t) {
                pbUsers.setVisibility(View.GONE);
                Toast.makeText(UsersActivity.this, "Error: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderUsers(List<AdminUser> users) {
        llUsers.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        String self = session.getUsername();
        for (AdminUser u : users) {
            View row = inflater.inflate(R.layout.item_user_admin, llUsers, false);
            TextView name = row.findViewById(R.id.tv_user_name);
            TextView username = row.findViewById(R.id.tv_user_username);
            TextView roles = row.findViewById(R.id.tv_user_roles);
            TextView status = row.findViewById(R.id.tv_user_status);
            Button toggle = row.findViewById(R.id.btn_user_toggle);
            Button rolesBtn = row.findViewById(R.id.btn_user_roles);
            Button passwordBtn = row.findViewById(R.id.btn_user_password);

            name.setText(u.displayName);
            username.setText("@" + u.username
                    + (u.username.equals(self) ? "  (you)" : ""));
            roles.setText(u.roles == null ? "" : String.join(" · ", u.roles));

            status.setText(u.enabled ? "ENABLED" : "DISABLED");
            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(8f);
            badge.setColor(u.enabled ? getColor(R.color.positive) : getColor(R.color.danger));
            status.setBackground(badge);
            status.setTextColor(Color.WHITE);

            toggle.setText(u.enabled ? "Disable" : "Enable");
            boolean isSelf = u.username.equals(self);
            toggle.setEnabled(!isSelf);
            if (isSelf) toggle.setAlpha(0.4f);

            toggle.setOnClickListener(v -> setEnabled(u, !u.enabled));
            rolesBtn.setOnClickListener(v -> showRolesDialog(u));
            passwordBtn.setOnClickListener(v -> showPasswordDialog(u));

            llUsers.addView(row);
        }
        if (users.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No users found.");
            empty.setTextSize(14);
            empty.setPadding(24, 32, 24, 32);
            empty.setTextColor(getColor(R.color.on_surface_variant));
            llUsers.addView(empty);
        }
    }

    // ---------------------------------------------------------------- create

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, 0);

        EditText etUsername = new EditText(this);
        etUsername.setHint("Username");
        etUsername.setInputType(InputType.TYPE_CLASS_TEXT);
        form.addView(etUsername);

        EditText etDisplay = new EditText(this);
        etDisplay.setHint("Display name (optional)");
        form.addView(etDisplay);

        EditText etPassword = new EditText(this);
        etPassword.setHint("Password (min 8 chars)");
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(etPassword);

        List<CheckBox> roleBoxes = new ArrayList<>();
        for (String role : ALL_ROLES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(role);
            if ("INSPECTOR".equals(role)) cb.setChecked(true);
            form.addView(cb);
            roleBoxes.add(cb);
        }

        new AlertDialog.Builder(this)
                .setTitle("Add user")
                .setView(form)
                .setPositiveButton("Create", (d, w) -> {
                    List<String> roles = new ArrayList<>();
                    for (int i = 0; i < roleBoxes.size(); i++) {
                        if (roleBoxes.get(i).isChecked()) roles.add(ALL_ROLES[i]);
                    }
                    if (roles.isEmpty()) {
                        Toast.makeText(this, "Select at least one role", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createUser(etUsername.getText().toString().trim(),
                            etPassword.getText().toString(),
                            etDisplay.getText().toString().trim(), roles);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createUser(String username, String password, String display, List<String> roles) {
        if (username.length() < 3) {
            Toast.makeText(this, "Username too short (min 3)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 8) {
            Toast.makeText(this, "Password too short (min 8)", Toast.LENGTH_SHORT).show();
            return;
        }
        pbUsers.setVisibility(View.VISIBLE);
        ApiClient.get().createUser(new CreateUserRequest(username, password,
                        display.isEmpty() ? null : display, roles))
                .enqueue(new Callback<AdminUser>() {
                    @Override
                    public void onResponse(Call<AdminUser> c, Response<AdminUser> r) {
                        if (r.isSuccessful()) {
                            Toast.makeText(UsersActivity.this, "User created: " + username,
                                    Toast.LENGTH_SHORT).show();
                            loadUsers();
                        } else {
                            pbUsers.setVisibility(View.GONE);
                            Toast.makeText(UsersActivity.this, "Create failed: " + errMessage(r),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AdminUser> c, Throwable t) {
                        pbUsers.setVisibility(View.GONE);
                        Toast.makeText(UsersActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------------------------------------------------------- enable/disable

    private void setEnabled(AdminUser user, boolean enabled) {
        ApiClient.get().setUserEnabled(user.id, new EnabledRequest(enabled))
                .enqueue(new Callback<AdminUser>() {
                    @Override
                    public void onResponse(Call<AdminUser> c, Response<AdminUser> r) {
                        if (r.isSuccessful()) {
                            Toast.makeText(UsersActivity.this,
                                    user.username + (enabled ? " enabled" : " disabled"),
                                    Toast.LENGTH_SHORT).show();
                            loadUsers();
                        } else {
                            Toast.makeText(UsersActivity.this, errMessage(r),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AdminUser> c, Throwable t) {
                        Toast.makeText(UsersActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------------------------------------------------------- roles

    private void showRolesDialog(AdminUser user) {
        boolean[] checked = new boolean[ALL_ROLES.length];
        List<String> current = user.roles == null ? List.of() : user.roles;
        for (int i = 0; i < ALL_ROLES.length; i++) {
            checked[i] = current.contains(ALL_ROLES[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Roles for " + user.username)
                .setMultiChoiceItems(ALL_ROLES, checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (d, w) -> {
                    List<String> roles = new ArrayList<>();
                    for (int i = 0; i < ALL_ROLES.length; i++) {
                        if (checked[i]) roles.add(ALL_ROLES[i]);
                    }
                    if (roles.isEmpty()) {
                        Toast.makeText(this, "At least one role required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveRoles(user, roles);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveRoles(AdminUser user, List<String> roles) {
        ApiClient.get().setUserRoles(user.id, new RolesRequest(roles))
                .enqueue(new Callback<AdminUser>() {
                    @Override
                    public void onResponse(Call<AdminUser> c, Response<AdminUser> r) {
                        if (r.isSuccessful()) {
                            Toast.makeText(UsersActivity.this, "Roles updated", Toast.LENGTH_SHORT).show();
                            loadUsers();
                        } else {
                            Toast.makeText(UsersActivity.this, errMessage(r),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AdminUser> c, Throwable t) {
                        Toast.makeText(UsersActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------------------------------------------------------- password reset

    private void showPasswordDialog(AdminUser user) {
        EditText input = new EditText(this);
        input.setHint("New password (min 8 chars)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout wrap = new LinearLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, 0, pad, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Reset password — " + user.username)
                .setView(wrap)
                .setPositiveButton("Reset", (d, w) -> resetPassword(user,
                        input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetPassword(AdminUser user, String password) {
        if (password.length() < 8) {
            Toast.makeText(this, "Password too short (min 8)", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.get().resetUserPassword(user.id, new PasswordRequest(password))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> c, Response<Void> r) {
                        Toast.makeText(UsersActivity.this,
                                r.isSuccessful() ? "Password reset for " + user.username
                                        : "Reset failed: " + errMessage(r),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(Call<Void> c, Throwable t) {
                        Toast.makeText(UsersActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------------------------------------------------------- helpers

    private static String errMessage(Response<?> r) {
        String body = null;
        try (ResponseBody e = r.errorBody()) {
            if (e != null) body = e.string();
        } catch (IOException ignored) {
        }
        if (body != null) {
            try {
                String m = new JSONObject(body).optString("message", null);
                if (m != null && !m.isEmpty()) return m;
            } catch (JSONException ignored) {
            }
            return body.length() > 160 ? body.substring(0, 160) + "…" : body;
        }
        return "HTTP " + r.code();
    }
}
