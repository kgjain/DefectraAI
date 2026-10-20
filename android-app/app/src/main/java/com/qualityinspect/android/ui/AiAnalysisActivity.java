package com.qualityinspect.android.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.*;
import com.qualityinspect.android.report.PdfReportBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Detailed AI analysis view. Two entry modes:
 * - inspectionId: evidence image, model verdict, processing attempts, GenAI batch assistant.
 * - productId: product/batch context, GenAI batch assistant, recent inspections with AI states.
 */
public class AiAnalysisActivity extends AppCompatActivity {

    private long inspectionId = -1;
    private long productId = -1;
    private Long batchId;

    private TextView tvContext, tvImageMeta, tvVerdictEmpty, tvVerdictClass,
            tvVerdictScore, tvVerdictMeta, tvGenaiBatch, tvGenai, tvGenaiMeta;
    private ImageView ivEvidence;
    private ProgressBar pbImage, pbGenai;
    private LinearLayout llAttempts, llInspections;
    private View cardEvidence, cardVerdict, cardAttempts, llActions, cardInspections;
    private Button btnRerun, btnOpenInspection, btnSummary, btnAsk, btnDownloadPdf;
    private EditText etAsk;

    private InspectionDetail detail;
    private AiReport lastReport;
    private long loadingImageId;
    private int pollTicks;
    private boolean pollScheduled;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTick = () -> {
        pollScheduled = false;
        if (!isFinishing() && !isDestroyed()) loadInspection();
    };
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_analysis);
        setTitle("AI Analysis");

        inspectionId = getIntent().getLongExtra("inspectionId", -1);
        productId = getIntent().getLongExtra("productId", -1);

        tvContext = findViewById(R.id.tv_ai_context);
        cardEvidence = findViewById(R.id.card_evidence);
        ivEvidence = findViewById(R.id.iv_evidence);
        pbImage = findViewById(R.id.pb_image);
        tvImageMeta = findViewById(R.id.tv_image_meta);
        cardVerdict = findViewById(R.id.card_verdict);
        tvVerdictEmpty = findViewById(R.id.tv_verdict_empty);
        tvVerdictClass = findViewById(R.id.tv_verdict_class);
        tvVerdictScore = findViewById(R.id.tv_verdict_score);
        tvVerdictMeta = findViewById(R.id.tv_verdict_meta);
        cardAttempts = findViewById(R.id.card_attempts);
        llAttempts = findViewById(R.id.ll_attempts);
        llActions = findViewById(R.id.ll_actions);
        btnRerun = findViewById(R.id.btn_rerun);
        btnOpenInspection = findViewById(R.id.btn_open_inspection);
        tvGenaiBatch = findViewById(R.id.tv_genai_batch);
        etAsk = findViewById(R.id.et_ask);
        btnSummary = findViewById(R.id.btn_summary);
        btnAsk = findViewById(R.id.btn_ask);
        pbGenai = findViewById(R.id.pb_genai);
        tvGenai = findViewById(R.id.tv_genai);
        tvGenaiMeta = findViewById(R.id.tv_genai_meta);
        cardInspections = findViewById(R.id.card_inspections);
        llInspections = findViewById(R.id.ll_inspections);
        btnDownloadPdf = findViewById(R.id.btn_download_pdf);

        btnSummary.setOnClickListener(v -> generateSummary());
        btnAsk.setOnClickListener(v -> ask());
        btnRerun.setOnClickListener(v -> rerun());
        btnDownloadPdf.setOnClickListener(v -> downloadPdf());
        btnOpenInspection.setOnClickListener(v -> {
            Intent i = new Intent(this, InspectionDetailActivity.class);
            i.putExtra("id", inspectionId);
            startActivity(i);
        });

        if (inspectionId > 0) {
            loadInspection();
        } else if (productId > 0) {
            applyProductMode();
            loadProduct();
        } else {
            tvContext.setText("Nothing to analyse — open this screen from an inspection or product.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacksAndMessages(null);
        io.shutdownNow();
    }

    // ---------------------------------------------------------------- inspection mode

    private void loadInspection() {
        ApiClient.get().inspection(inspectionId).enqueue(new Callback<InspectionDetail>() {
            @Override
            public void onResponse(Call<InspectionDetail> c, Response<InspectionDetail> r) {
                if (r.isSuccessful() && r.body() != null) {
                    detail = r.body();
                    render();
                } else {
                    tvContext.setText("Could not load inspection: " + errMessage(r));
                }
            }

            @Override
            public void onFailure(Call<InspectionDetail> c, Throwable t) {
                tvContext.setText("Error: " + t.getMessage());
            }
        });
    }

    private void render() {
        Inspection ins = detail.inspection;
        batchId = ins.batchId;
        tvContext.setText("Item: " + ins.itemReference
                + "\nProduct: " + ins.productName
                + "\nBatch: " + ins.batchCode
                + "\nStatus: " + ins.status + " · AI: " + ins.aiState + " · Review: " + ins.reviewState);
        tvGenaiBatch.setText("Batch context: " + ins.batchCode + " — " + ins.productName);

        renderEvidence();
        renderVerdict();
        renderAttempts();

        boolean busy = "PENDING".equals(ins.aiState) || "PROCESSING".equals(ins.aiState);
        btnRerun.setEnabled(!busy && "SUBMITTED".equals(ins.status));
        if (busy) schedulePoll();
    }

    private void renderEvidence() {
        Image img = null;
        for (Image i : detail.images == null ? List.<Image>of() : detail.images) {
            if (i.current) { img = i; break; }
        }
        if (img == null && detail.images != null && !detail.images.isEmpty()) img = detail.images.get(0);
        if (img == null) {
            pbImage.setVisibility(View.GONE);
            tvImageMeta.setText("No evidence image on this inspection.");
            ivEvidence.setImageDrawable(null);
            return;
        }
        if (loadingImageId != img.id) loadImage(img);
    }

    private void loadImage(Image img) {
        loadingImageId = img.id;
        pbImage.setVisibility(View.VISIBLE);
        tvImageMeta.setText("Loading image…");
        ApiClient.get().imageContent(img.id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    pbImage.setVisibility(View.GONE);
                    tvImageMeta.setText("Image unavailable (HTTP " + r.code() + ")");
                    return;
                }
                ResponseBody body = r.body();
                io.execute(() -> {
                    try {
                        byte[] bytes = body.bytes();
                        Bitmap decoded = decodeSampled(bytes, 1600, 1600);
                        if (decoded == null) throw new IOException("decode failed");
                        final Bitmap bmp = applyExif(bytes, decoded);
                        String meta = img.originalFilename + " · "
                                + (bytes.length / 1024) + " KB · " + img.contentType;
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            ivEvidence.setImageBitmap(bmp);
                            pbImage.setVisibility(View.GONE);
                            tvImageMeta.setText(meta);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            pbImage.setVisibility(View.GONE);
                            tvImageMeta.setText("Could not load image: " + e.getMessage());
                        });
                    }
                });
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                pbImage.setVisibility(View.GONE);
                tvImageMeta.setText("Image load failed: " + t.getMessage());
            }
        });
    }

    private static Bitmap decodeSampled(byte[] bytes, int maxW, int maxH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxW || bounds.outHeight / (sample * 2) >= maxH) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    }

    private static Bitmap applyExif(byte[] bytes, Bitmap bmp) {
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(bytes));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            Matrix m = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: m.postRotate(90); m.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: m.postRotate(270); m.postScale(-1, 1); break;
                default: return bmp;
            }
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        } catch (Exception e) {
            return bmp;
        }
    }

    private void renderVerdict() {
        ModelResult result = null;
        if (detail.modelResults != null && !detail.modelResults.isEmpty()) {
            for (ModelResult mr : detail.modelResults) {
                if (mr.current) result = mr;
            }
            if (result == null) result = detail.modelResults.get(detail.modelResults.size() - 1);
        }
        if (result == null) {
            tvVerdictEmpty.setVisibility(View.VISIBLE);
            tvVerdictEmpty.setText("No model result yet — AI state: " + detail.inspection.aiState);
            tvVerdictClass.setVisibility(View.GONE);
            tvVerdictScore.setVisibility(View.GONE);
            tvVerdictMeta.setVisibility(View.GONE);
            return;
        }
        tvVerdictEmpty.setVisibility(View.GONE);
        tvVerdictClass.setVisibility(View.VISIBLE);
        tvVerdictScore.setVisibility(View.VISIBLE);
        tvVerdictMeta.setVisibility(View.VISIBLE);

        tvVerdictClass.setText(result.predictedClass);
        boolean pass = "OK".equalsIgnoreCase(result.predictedClass);
        tvVerdictClass.setTextColor(getColor(pass ? R.color.positive : R.color.danger));

        String score = "probability".equalsIgnoreCase(result.scoreType)
                ? String.format(Locale.US, "%.2f%% confidence (%s)", result.score * 100, result.scoreType)
                : String.format(Locale.US, "%s = %s", result.scoreType, result.score);
        tvVerdictScore.setText(score + (result.current ? "" : " · superseded"));

        tvVerdictMeta.setText("Model: " + result.modelName + " v" + result.modelVersion
                + "\nDuration: " + result.durationMs + " ms"
                + "\nProcessed: " + isoTime(result.processedAt)
                + "\nImage id: " + result.imageId + " · result id: " + result.id);
    }

    private void renderAttempts() {
        llAttempts.removeAllViews();
        List<ProcessingAttempt> attempts = detail.attempts;
        if (attempts == null || attempts.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No processing attempts yet.");
            t.setTextSize(13);
            t.setTextColor(getColor(R.color.on_surface_variant));
            t.setPadding(4, 14, 4, 14);
            llAttempts.addView(t);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ProcessingAttempt pa : attempts) {
            View row = inflater.inflate(R.layout.item_attempt, llAttempts, false);
            TextView title = row.findViewById(R.id.tv_attempt_title);
            TextView badge = row.findViewById(R.id.tv_attempt_badge);
            TextView meta = row.findViewById(R.id.tv_attempt_meta);
            TextView error = row.findViewById(R.id.tv_attempt_error);

            title.setText("Attempt " + pa.attemptNo);
            badge.setText(pa.status);
            int color;
            if ("COMPLETED".equals(pa.status)) color = getColor(R.color.positive);
            else if ("FAILED".equals(pa.status)) color = getColor(R.color.danger);
            else color = getColor(R.color.warning);
            GradientDrawable bg = (GradientDrawable) badge.getBackground().mutate();
            bg.setColor(color);
            badge.setTextColor(getColor(R.color.on_primary));

            StringBuilder m = new StringBuilder();
            if (pa.modelName != null) m.append(pa.modelName).append(" v").append(pa.modelVersion).append(" · ");
            if (pa.durationMs != null) m.append(pa.durationMs).append(" ms · ");
            m.append(isoTime(pa.finishedAt != null ? pa.finishedAt : pa.startedAt));
            meta.setText(m.toString());

            if (pa.errorDetails != null && !pa.errorDetails.isEmpty()) {
                error.setVisibility(View.VISIBLE);
                error.setText("Error: " + pa.errorDetails);
            }
            llAttempts.addView(row);
        }
    }

    private void rerun() {
        Image img = null;
        if (detail != null && detail.images != null && !detail.images.isEmpty()) {
            img = detail.images.get(0);
            for (Image i : detail.images) if (i.current) img = i;
        }
        if (img == null) {
            Toast.makeText(this, "No image to process", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.get().processImage(img.id).enqueue(new Callback<ProcessingAttempt>() {
            @Override
            public void onResponse(Call<ProcessingAttempt> c, Response<ProcessingAttempt> r) {
                if (r.isSuccessful() && r.body() != null) {
                    Toast.makeText(AiAnalysisActivity.this,
                            "Analysis started (attempt " + r.body().attemptNo + ")",
                            Toast.LENGTH_SHORT).show();
                    pollTicks = 0;
                    loadInspection();
                } else {
                    Toast.makeText(AiAnalysisActivity.this,
                            "Could not start analysis: " + errMessage(r), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ProcessingAttempt> c, Throwable t) {
                Toast.makeText(AiAnalysisActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void schedulePoll() {
        if (pollScheduled || pollTicks++ >= 30) return;
        pollScheduled = true;
        pollHandler.postDelayed(pollTick, 2500);
    }

    // ---------------------------------------------------------------- product mode

    private void applyProductMode() {
        cardEvidence.setVisibility(View.GONE);
        cardVerdict.setVisibility(View.GONE);
        cardAttempts.setVisibility(View.GONE);
        llActions.setVisibility(View.GONE);
        cardInspections.setVisibility(View.VISIBLE);
    }

    private void loadProduct() {
        ApiClient.get().product(productId).enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> c, Response<Product> r) {
                if (r.isSuccessful() && r.body() != null) {
                    Product p = r.body();
                    tvContext.setText("Product: " + p.name + " (" + p.code + ")"
                            + "\nCategory: " + p.category + " · Status: " + p.status);
                } else {
                    tvContext.setText("Could not load product: " + errMessage(r));
                }
            }

            @Override
            public void onFailure(Call<Product> c, Throwable t) {
                tvContext.setText("Error: " + t.getMessage());
            }
        });

        ApiClient.get().batches(null, productId, null, 0, 50).enqueue(new Callback<PageResponse<Batch>>() {
            @Override
            public void onResponse(Call<PageResponse<Batch>> c, Response<PageResponse<Batch>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().content != null
                        && !r.body().content.isEmpty()) {
                    Batch b = r.body().content.get(0);
                    batchId = b.id;
                    tvGenaiBatch.setText("Batch context: " + b.code + " ("
                            + b.status + ") — first of " + r.body().content.size());
                } else {
                    tvGenaiBatch.setText("No batches found for this product — GenAI unavailable.");
                }
            }

            @Override
            public void onFailure(Call<PageResponse<Batch>> c, Throwable t) {
                tvGenaiBatch.setText("Batch lookup failed: " + t.getMessage());
            }
        });

        ApiClient.get().inspections(null, productId, null, null, null, null, 0, 20)
                .enqueue(new Callback<PageResponse<Inspection>>() {
                    @Override
                    public void onResponse(Call<PageResponse<Inspection>> c,
                                           Response<PageResponse<Inspection>> r) {
                        llInspections.removeAllViews();
                        if (!r.isSuccessful() || r.body() == null || r.body().content == null
                                || r.body().content.isEmpty()) {
                            TextView t = new TextView(AiAnalysisActivity.this);
                            t.setText("No inspections for this product yet.");
                            t.setTextSize(13);
                            t.setTextColor(getColor(R.color.on_surface_variant));
                            t.setPadding(12, 14, 12, 8);
                            llInspections.addView(t);
                            return;
                        }
                        LayoutInflater inflater = LayoutInflater.from(AiAnalysisActivity.this);
                        for (Inspection ins : r.body().content) {
                            View row = inflater.inflate(R.layout.item_row, llInspections, false);
                            TextView title = row.findViewById(R.id.tv_title);
                            TextView sub = row.findViewById(R.id.tv_sub);
                            title.setText(ins.itemReference);
                            sub.setText(ins.batchCode + " · AI " + ins.aiState + " · " + ins.status);
                            final long openId = ins.id;
                            row.setOnClickListener(v -> {
                                Intent i = new Intent(AiAnalysisActivity.this,
                                        AiAnalysisActivity.class);
                                i.putExtra("inspectionId", openId);
                                startActivity(i);
                            });
                            llInspections.addView(row);
                        }
                    }

                    @Override
                    public void onFailure(Call<PageResponse<Inspection>> c, Throwable t) {
                        tvContext.setText("Error: " + t.getMessage());
                    }
                });
    }

    // ---------------------------------------------------------------- GenAI assistant

    private void generateSummary() {
        if (batchId == null) {
            Toast.makeText(this, "No batch available for this view", Toast.LENGTH_SHORT).show();
            return;
        }
        AiReportRequest req = new AiReportRequest();
        req.batchId = batchId;
        setGenaiBusy(true);
        ApiClient.get().aiBatchSummary(req).enqueue(genaiCallback("Batch summary"));
    }

    private void ask() {
        String q = etAsk.getText().toString().trim();
        if (q.isEmpty()) {
            etAsk.setError("Type a question first");
            return;
        }
        setGenaiBusy(true);
        ApiClient.get().aiQuestion(new AiQuestionRequest(q, batchId))
                .enqueue(genaiCallback("Question"));
    }

    private Callback<AiReport> genaiCallback(String label) {
        return new Callback<AiReport>() {
            @Override
            public void onResponse(Call<AiReport> c, Response<AiReport> r) {
                setGenaiBusy(false);
                if (r.isSuccessful() && r.body() != null) {
                    renderReport(r.body());
                } else {
                    tvGenaiMeta.setText(label + " failed: " + errMessage(r));
                }
            }

            @Override
            public void onFailure(Call<AiReport> c, Throwable t) {
                setGenaiBusy(false);
                tvGenaiMeta.setText(label + " failed: " + t.getMessage());
            }
        };
    }

    private void renderReport(AiReport report) {
        lastReport = report;
        StringBuilder sb = new StringBuilder();
        sb.append(report.responseText == null || report.responseText.isEmpty()
                ? "(no response text)" : report.responseText);
        if (report.facts != null && !report.facts.isEmpty() && !"dev-no-llm".equals(report.mode)) {
            sb.append("\n\nFACTS (server-computed):");
            for (String f : report.facts) sb.append("\n  - ").append(f);
        }
        if (report.suggestions != null && !report.suggestions.isEmpty()) {
            sb.append("\n\nSUGGESTIONS (advisory only):");
            for (String s : report.suggestions) sb.append("\n  - ").append(s);
        }
        if (report.missingEvidence != null && !report.missingEvidence.isEmpty()) {
            sb.append("\n\nMISSING EVIDENCE:");
            for (String s : report.missingEvidence) sb.append("\n  - ").append(s);
        }
        tvGenai.setText(sb.toString());
        tvGenaiMeta.setText("mode: " + report.mode + " · generated=" + report.generated
                + " · " + report.reportType + " · " + isoTime(report.createdAt));
    }

    private void setGenaiBusy(boolean busy) {
        pbGenai.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnSummary.setEnabled(!busy);
        btnAsk.setEnabled(!busy);
        if (busy) tvGenaiMeta.setText("Generating report…");
    }

    // ---------------------------------------------------------------- PDF export

    private void downloadPdf() {
        if (inspectionId > 0 && detail == null) {
            Toast.makeText(this, "Load the analysis first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (inspectionId <= 0 && productId <= 0) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show();
            return;
        }
        final String contextLine = tvContext.getText().toString();
        final String batchLine = tvGenaiBatch.getText().toString();
        final InspectionDetail snapshot = detail;
        final AiReport report = lastReport;
        final String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        btnDownloadPdf.setEnabled(false);
        btnDownloadPdf.setText("Generating PDF…");
        io.execute(() -> {
            try {
                PdfReportBuilder builder = new PdfReportBuilder();
                File out;
                if (inspectionId > 0) {
                    out = new File(new File(getFilesDir(), "reports"),
                            "DEFECTRA_AI_INSP-" + inspectionId + "_" + stamp + ".pdf");
                    builder.buildInspectionReport(snapshot, report);
                } else {
                    out = new File(new File(getFilesDir(), "reports"),
                            "DEFECTRA_AI_PROD-" + productId + "_" + stamp + ".pdf");
                    builder.buildProductReport(contextLine, batchLine, report);
                }
                builder.writeTo(out);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnDownloadPdf.setEnabled(true);
                    btnDownloadPdf.setText("Download PDF report");
                    sharePdf(out);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnDownloadPdf.setEnabled(true);
                    btnDownloadPdf.setText("Download PDF report");
                    Toast.makeText(AiAnalysisActivity.this,
                            "PDF failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void sharePdf(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, "DEFECTRA AI report");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Save or share AI report"));
        Toast.makeText(this, "PDF saved: " + file.getName(), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- helpers

    private static String isoTime(String iso) {
        if (iso == null) return "—";
        return iso.length() >= 19 ? iso.substring(0, 19).replace('T', ' ') + " UTC" : iso;
    }

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
