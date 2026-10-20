package com.qualityinspect.android.report;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.qualityinspect.android.api.Models.AiReport;
import com.qualityinspect.android.api.Models.Image;
import com.qualityinspect.android.api.Models.Inspection;
import com.qualityinspect.android.api.Models.InspectionDetail;
import com.qualityinspect.android.api.Models.ModelResult;
import com.qualityinspect.android.api.Models.ProcessingAttempt;
import com.qualityinspect.android.api.Models.Review;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Renders AI-analysis data into a multi-page A4-style PDF using the platform
 * PdfDocument API (no third-party dependencies). Pages break automatically.
 */
public final class PdfReportBuilder {

    private static final int PAGE_W = 1240;
    private static final int PAGE_H = 1754;
    private static final int MARGIN_X = 100;
    private static final int MARGIN_TOP = 120;
    private static final int MARGIN_BOTTOM = 120;

    private static final int C_TEXT = Color.rgb(30, 41, 59);
    private static final int C_MUTED = Color.rgb(100, 116, 139);
    private static final int C_SECTION = Color.rgb(79, 70, 229);
    private static final int C_PASS = Color.rgb(22, 163, 74);
    private static final int C_FAIL = Color.rgb(220, 38, 38);
    private static final int C_WARN = Color.rgb(217, 119, 6);
    private static final int C_RULE = Color.rgb(203, 213, 225);
    private static final int C_HEADER_BG = Color.rgb(30, 41, 59);
    private static final int C_HEADER_FG = Color.rgb(248, 250, 252);

    private final PdfDocument doc = new PdfDocument();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private PdfDocument.Page page;
    private float y;

    public PdfReportBuilder() {
        paint.setColor(C_TEXT);
    }

    // ------------------------------------------------------------------ content

    public void buildInspectionReport(InspectionDetail detail, AiReport report) {
        Inspection ins = detail.inspection;
        header("INSPECTION AI REPORT", "Inspection #" + ins.id + " · " + ins.itemReference);

        section("INSPECTION CONTEXT");
        kv("Item reference", ins.itemReference);
        kv("Product", ins.productName);
        kv("Batch", ins.batchCode);
        kv("Inspection type", ins.inspectionType);
        kv("Evidence scope", ins.evidenceScope);
        kv("Status", ins.status);
        kv("AI state", ins.aiState);
        kv("Review state", ins.reviewState);
        kv("Inspector", ins.inspector);
        kv("Submitted", isoTime(ins.submittedAt));
        para("Observations: " + orDash(ins.observations), 24, C_TEXT, 0);

        section("MODEL VERDICT");
        ModelResult result = currentResult(detail);
        if (result == null) {
            para("No model result recorded (AI state: " + ins.aiState + ").", 24, C_MUTED, 0);
        } else {
            boolean pass = "OK".equalsIgnoreCase(result.predictedClass);
            para(result.predictedClass, 40, pass ? C_PASS : C_FAIL, 0);
            String score = "probability".equalsIgnoreCase(result.scoreType)
                    ? String.format(Locale.US, "%.2f%% confidence (%s)",
                    result.score * 100, result.scoreType)
                    : String.format(Locale.US, "%s = %s", result.scoreType, result.score);
            para(score, 26, C_TEXT, 0);
            kv("Model", result.modelName + " v" + result.modelVersion);
            kv("Duration", result.durationMs + " ms");
            kv("Processed", isoTime(result.processedAt));
            kv("Result id", String.valueOf(result.id));
        }

        section("PROCESSING ATTEMPTS");
        if (detail.attempts == null || detail.attempts.isEmpty()) {
            para("No processing attempts.", 24, C_MUTED, 0);
        } else {
            for (ProcessingAttempt pa : detail.attempts) {
                ensureSpace(120);
                String line = "Attempt " + pa.attemptNo + " · " + pa.status;
                int color = "COMPLETED".equals(pa.status) ? C_PASS
                        : "FAILED".equals(pa.status) ? C_FAIL : C_WARN;
                para(line, 26, color, 0);
                StringBuilder meta = new StringBuilder();
                if (pa.modelName != null) meta.append(pa.modelName).append(" v").append(pa.modelVersion).append(" · ");
                if (pa.durationMs != null) meta.append(pa.durationMs).append(" ms · ");
                meta.append(isoTime(pa.finishedAt != null ? pa.finishedAt : pa.startedAt));
                para(meta.toString().replaceAll(" · $", ""), 22, C_MUTED, 24);
                if (pa.errorDetails != null && !pa.errorDetails.isEmpty()) {
                    para("Error: " + pa.errorDetails, 22, C_FAIL, 24);
                }
            }
        }

        section("EVIDENCE");
        if (detail.images == null || detail.images.isEmpty()) {
            para("No evidence images.", 24, C_MUTED, 0);
        } else {
            for (Image img : detail.images) {
                String flags = (img.current ? "current" : "superseded") + " · v" + img.version;
                para("Image #" + img.id + " · " + img.originalFilename + " · "
                        + (img.sizeBytes / 1024) + " KB · " + flags, 24, C_TEXT, 0);
                para("sha256: " + img.sha256, 18, C_MUTED, 24);
            }
        }

        section("HUMAN REVIEWS");
        if (detail.reviews == null || detail.reviews.isEmpty()) {
            para("No reviews yet.", 24, C_MUTED, 0);
        } else {
            for (Review rv : detail.reviews) {
                ensureSpace(100);
                para(rv.decision + (rv.confirmedOutcome != null ? " → " + rv.confirmedOutcome : ""),
                        26, C_TEXT, 0);
                kv("Reviewer", rv.reviewer + " (" + rv.reviewerRole + ")");
                kv("When", isoTime(rv.createdAt));
                if (rv.reason != null && !rv.reason.isEmpty()) para("Reason: " + rv.reason, 22, C_MUTED, 24);
                if (rv.evidenceRequestedNote != null && !rv.evidenceRequestedNote.isEmpty())
                    para("Evidence requested: " + rv.evidenceRequestedNote, 22, C_MUTED, 24);
            }
        }

        genaiSection(report);
        finish("Evidence-grounded report generated by DEFECTRA AI. "
                + "Model outputs and GenAI text are decision support only — not a certification.");
    }

    public void buildProductReport(String contextLine, String batchLine, AiReport report) {
        header("PRODUCT AI REPORT", contextLine.split("\n")[0]);
        section("CONTEXT");
        for (String l : contextLine.split("\n")) para(l, 24, C_TEXT, 0);
        para("", 10, C_TEXT, 0);
        for (String l : batchLine.split("\n")) para(l, 24, C_MUTED, 0);
        genaiSection(report);
        finish("Evidence-grounded report generated by DEFECTRA AI. "
                + "GenAI text is decision support only — not a certification.");
    }

    private void genaiSection(AiReport report) {
        section("GENAI ASSISTANT");
        if (report == null) {
            para("No GenAI report generated in this session.", 24, C_MUTED, 0);
            return;
        }
        kv("Type", report.reportType);
        kv("Mode", report.mode + (report.generated ? " (generated)" : " (fallback)"));
        if (report.modelName != null) kv("Model", report.modelProvider + " / " + report.modelName);
        kv("Created", isoTime(report.createdAt));
        if (report.requestText != null && !report.requestText.isEmpty())
            para("Request: " + report.requestText, 22, C_MUTED, 0);
        para(orDash(report.responseText), 24, C_TEXT, 0);
        bullets("Facts (server-computed)", report.facts);
        bullets("Suggestions (advisory only)", report.suggestions);
        bullets("Missing evidence", report.missingEvidence);
    }

    private void bullets(String label, java.util.List<String> items) {
        if (items == null || items.isEmpty()) return;
        para(label + ":", 24, C_SECTION, 0);
        for (String s : items) {
            ensureSpace(40);
            para("• " + s, 23, C_TEXT, 24);
        }
    }

    // ------------------------------------------------------------------ layout engine

    private void header(String title, String subtitle) {
        newPage();
        Canvas c = page.getCanvas();
        c.drawRect(0, 0, PAGE_W, 190, filled(C_HEADER_BG));
        paint.setColor(C_HEADER_FG);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(46);
        c.drawText("DEFECTRA AI", MARGIN_X, 90, paint);
        paint.setTextSize(30);
        c.drawText(title, MARGIN_X, 140, paint);
        paint.setTypeface(Typeface.DEFAULT);
        y = 240;
        para(subtitle, 28, C_MUTED, 0);
        para("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()),
                22, C_MUTED, 0);
    }

    private void section(String title) {
        ensureSpace(90);
        y += 18;
        rule();
        paint.setColor(C_SECTION);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(26);
        page.getCanvas().drawText(title, MARGIN_X, y + 26, paint);
        paint.setTypeface(Typeface.DEFAULT);
        y += 44;
    }

    private void kv(String key, String value) {
        ensureSpace(36);
        paint.setTextSize(24);
        paint.setColor(C_MUTED);
        String k = key + ": ";
        float kw = paint.measureText(k);
        page.getCanvas().drawText(k, MARGIN_X, y + 24, paint);
        paraInLine(value == null || value.isEmpty() ? "—" : value, MARGIN_X + kw, 24, C_TEXT);
    }

    private void para(String s, float size, int color, float indent) {
        paint.setTextSize(size);
        paint.setColor(color);
        float width = PAGE_W - 2 * MARGIN_X - indent;
        for (String logical : s.split("\n", -1)) {
            if (logical.isEmpty()) {
                y += size * 0.6f;
                continue;
            }
            String rest = logical;
            while (!rest.isEmpty()) {
                ensureSpace(size * 1.45f);
                int n = paint.breakText(rest, true, width, null);
                if (n <= 0) n = 1;
                page.getCanvas().drawText(rest.substring(0, n), MARGIN_X + indent, y + size, paint);
                y += size * 1.35f;
                rest = rest.substring(n);
            }
        }
    }

    private void paraInLine(String s, float startX, float size, int color) {
        paint.setTextSize(size);
        paint.setColor(color);
        float width = PAGE_W - MARGIN_X - startX;
        String rest = s;
        float x = startX;
        while (!rest.isEmpty()) {
            int n = paint.breakText(rest, true, width, null);
            if (n <= 0) n = 1;
            page.getCanvas().drawText(rest.substring(0, n), x, y + size, paint);
            rest = rest.substring(n);
            y += size * 1.35f;
            x = MARGIN_X;
            width = PAGE_W - 2 * MARGIN_X;
        }
    }

    private void rule() {
        page.getCanvas().drawLine(MARGIN_X, y, PAGE_W - MARGIN_X, y, rulePaint());
    }

    private Paint rulePaint() {
        Paint p = new Paint();
        p.setStrokeWidth(2f);
        p.setColor(C_RULE);
        return p;
    }

    private Paint filled(int color) {
        Paint p = new Paint();
        p.setColor(color);
        return p;
    }

    private void ensureSpace(float needed) {
        if (y + needed > PAGE_H - MARGIN_BOTTOM) newPage();
    }

    private void newPage() {
        if (page != null) doc.finishPage(page);
        PdfDocument.PageInfo info = new PdfDocument.PageInfo
                .Builder(PAGE_W, PAGE_H, doc.getPages().size() + 1).create();
        page = doc.startPage(info);
        y = MARGIN_TOP;
    }

    private void finish(String disclaimer) {
        section("DISCLAIMER");
        para(disclaimer, 20, C_MUTED, 0);
        y += 10;
        rule();
        para("DEFECTRA AI · Quality inspection platform · End of report", 18, C_MUTED, 0);
    }

    public void writeTo(File file) throws IOException {
        if (page == null) newPage();
        doc.finishPage(page);
        page = null;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Cannot create directory " + parent);
        try (FileOutputStream out = new FileOutputStream(file)) {
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static ModelResult currentResult(InspectionDetail detail) {
        if (detail.modelResults == null || detail.modelResults.isEmpty()) return null;
        ModelResult pick = null;
        for (ModelResult mr : detail.modelResults) if (mr.current) pick = mr;
        return pick != null ? pick : detail.modelResults.get(detail.modelResults.size() - 1);
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private static String isoTime(String iso) {
        if (iso == null) return "—";
        return iso.length() >= 19 ? iso.substring(0, 19).replace('T', ' ') + " UTC" : iso;
    }
}
