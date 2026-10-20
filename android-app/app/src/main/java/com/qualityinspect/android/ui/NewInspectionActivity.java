package com.qualityinspect.android.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.*;
import java.util.UUID;

public class NewInspectionActivity extends AppCompatActivity {

    private EditText etItemRef, etObservations;
    private Spinner spBatch, spScope;
    private TextView tvImageStatus;
    private Button btnCapture, btnSubmit, btnScanBatch, btnScanItem;
    private final java.util.List<Batch> batchList = new java.util.ArrayList<>();
    private Long createdInspectionId;
    private File capturedFile;
    private Uri capturedUri;
    private String scanTarget = "ITEM";

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) applyScan(result.getContents());
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && capturedFile != null) {
                    tvImageStatus.setText("Evidence captured: " + capturedFile.getName());
                    btnSubmit.setEnabled(true);
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    capturePhoto();
                } else {
                    Toast.makeText(this, "Camera permission is required to capture evidence",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_inspection);
        setTitle("New Inspection");
        etItemRef = findViewById(R.id.et_item_ref);
        etObservations = findViewById(R.id.et_observations);
        spBatch = findViewById(R.id.sp_batch);
        spScope = findViewById(R.id.sp_scope);
        tvImageStatus = findViewById(R.id.tv_image_status);
        btnCapture = findViewById(R.id.btn_capture);
        btnSubmit = findViewById(R.id.btn_submit);
        btnScanBatch = findViewById(R.id.btn_scan_batch);
        btnScanItem = findViewById(R.id.btn_scan_item);

        ArrayAdapter<CharSequence> scopeAdapter = ArrayAdapter.createFromResource(
                this, R.array.evidence_scopes, android.R.layout.simple_spinner_item);
        scopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spScope.setAdapter(scopeAdapter);

        loadBatches();

        btnCapture.setOnClickListener(v -> capturePhoto());
        btnSubmit.setOnClickListener(v -> submit());
        btnSubmit.setEnabled(false);
        btnScanBatch.setOnClickListener(v -> scan("BATCH"));
        btnScanItem.setOnClickListener(v -> scan("ITEM"));
    }

    private void scan(String target) {
        scanTarget = target;
        ScanOptions options = new ScanOptions()
                .setPrompt("Point the camera at the barcode or QR code")
                .setBeepEnabled(true)
                .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);
        barcodeLauncher.launch(options);
    }

    private void applyScan(String value) {
        if ("BATCH".equals(scanTarget)) {
            int idx = -1;
            for (int i = 0; i < batchList.size(); i++) {
                Batch b = batchList.get(i);
                if (b.code != null && b.code.equalsIgnoreCase(value)) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                spBatch.setSelection(idx);
                Toast.makeText(this, "Batch " + value + " selected", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "No active batch matches code: " + value, Toast.LENGTH_LONG).show();
            }
        } else {
            etItemRef.setText(value);
            etItemRef.setSelection(value.length());
            Toast.makeText(this, "Item reference filled from barcode",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void capturePhoto() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            return;
        }
        try {
            File dir = new File(getCacheDir(), "captures");
            dir.mkdirs();
            capturedFile = File.createTempFile("evidence_", ".jpg", dir);
            capturedUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", capturedFile);
            cameraLauncher.launch(capturedUri);
        } catch (IOException e) {
            Toast.makeText(this, "Cannot create file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadBatches() {
        ApiClient.get().batches(null, null, "ACTIVE", 0, 100)
                .enqueue(new Callback<PageResponse<Batch>>() {
                    @Override
                    public void onResponse(Call<PageResponse<Batch>> c, Response<PageResponse<Batch>> r) {
                        if (r.isSuccessful() && r.body() != null && r.body().content != null
                                && !r.body().content.isEmpty()) {
                            batchList.clear();
                            batchList.addAll(r.body().content);
                            java.util.List<String> labels = new java.util.ArrayList<>();
                            for (Batch b : batchList) {
                                labels.add(b.code + " (ID " + b.id + ")");
                            }
                            spBatch.setAdapter(new ArrayAdapter<>(NewInspectionActivity.this,
                                    android.R.layout.simple_spinner_item, labels));
                        } else {
                            Toast.makeText(NewInspectionActivity.this,
                                    "Could not load batches: " + errMessage(r), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PageResponse<Batch>> c, Throwable t) {
                        Toast.makeText(NewInspectionActivity.this,
                                "Could not load batches: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String errMessage(Response<?> r) {
        try {
            String body = r.errorBody() != null ? r.errorBody().string() : "";
            if (!body.isEmpty()) {
                String m = new org.json.JSONObject(body).optString("message", "");
                if (!m.isEmpty()) return m;
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + r.code();
    }

    private void submit() {
        if (batchList.isEmpty() || spBatch.getSelectedItemPosition() < 0) {
            Toast.makeText(this, "No active batch selected — pull batches first",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        long batchId = batchList.get(spBatch.getSelectedItemPosition()).id;
        String itemRef = etItemRef.getText().toString().trim();
        String scope = spScope.getSelectedItem().toString();
        String obs = etObservations.getText().toString().trim();
        String idemKey = UUID.randomUUID().toString();

        InspectionCreateRequest req = new InspectionCreateRequest();
        req.batchId = batchId; req.itemReference = itemRef;
        req.evidenceScope = scope; req.observations = obs;
        req.idempotencyKey = idemKey;

        btnSubmit.setEnabled(false);
        ApiClient.get().createInspection(req).enqueue(new Callback<Inspection>() {
            @Override public void onResponse(Call<Inspection> c, Response<Inspection> r) {
                if (r.isSuccessful() && r.body() != null) {
                    createdInspectionId = r.body().id;
                    uploadImage();
                } else {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(NewInspectionActivity.this,
                            "Create failed: " + errMessage(r), Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<Inspection> c, Throwable t) {
                btnSubmit.setEnabled(true);
                Toast.makeText(NewInspectionActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadImage() {
        RequestBody rb = RequestBody.create(capturedFile, MediaType.parse("image/jpeg"));
        MultipartBody.Part part = MultipartBody.Part.createFormData(
                "file", capturedFile.getName(), rb);
        ApiClient.get().uploadImage(createdInspectionId, part).enqueue(new Callback<Image>() {
            @Override public void onResponse(Call<Image> c, Response<Image> r) {
                if (r.isSuccessful()) {
                    submitInspection();
                } else {
                    Toast.makeText(NewInspectionActivity.this,
                            "Upload failed: " + errMessage(r), Toast.LENGTH_LONG).show();
                    btnSubmit.setEnabled(true);
                }
            }
            @Override public void onFailure(Call<Image> c, Throwable t) {
                Toast.makeText(NewInspectionActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                btnSubmit.setEnabled(true);
            }
        });
    }

    private void submitInspection() {
        ApiClient.get().submitInspection(createdInspectionId).enqueue(new Callback<Inspection>() {
            @Override public void onResponse(Call<Inspection> c, Response<Inspection> r) {
                if (r.isSuccessful()) {
                    Toast.makeText(NewInspectionActivity.this,
                            "Inspection submitted", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(NewInspectionActivity.this, InspectionDetailActivity.class);
                    i.putExtra("id", createdInspectionId);
                    startActivity(i);
                    finish();
                } else {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(NewInspectionActivity.this,
                            "Submit failed: " + errMessage(r), Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<Inspection> c, Throwable t) {
                btnSubmit.setEnabled(true);
                Toast.makeText(NewInspectionActivity.this,
                        "Submit failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
