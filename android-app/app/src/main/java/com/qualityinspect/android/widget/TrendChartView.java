package com.qualityinspect.android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import com.qualityinspect.android.api.Models.TrendPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dependency-free dual-line trend chart (inspections submitted vs confirmed
 * defects). Draws axes, gridlines, two polylines with end-point dots and a
 * legend; shows an empty-state label until data arrives.
 */
public class TrendChartView extends View {

    private static final int COLOR_SUBMITTED = Color.parseColor("#1B4F9C");
    private static final int COLOR_DEFECTS = Color.parseColor("#C62828");
    private static final int COLOR_GRID = Color.parseColor("#22000000");
    private static final int COLOR_TEXT = Color.parseColor("#666666");
    private static final int COLOR_EMPTY = Color.parseColor("#999999");

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path submittedPath = new Path();
    private final Path defectsPath = new Path();

    private List<TrendPoint> points = new ArrayList<>();

    public TrendChartView(Context context) {
        this(context, null);
    }

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.2f));
        dotPaint.setStyle(Paint.Style.FILL);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        textPaint.setTextSize(sp(10));
        textPaint.setColor(COLOR_TEXT);
        legendPaint.setTextSize(sp(11));
    }

    public void setPoints(List<TrendPoint> data) {
        this.points = data == null ? new ArrayList<>() : new ArrayList<>(data);
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = (int) dp(190);
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(36);
        float right = getWidth() - dp(12);
        float top = dp(26);
        float bottom = getHeight() - dp(24);
        float width = right - left;
        float height = bottom - top;

        // legend
        legendPaint.setColor(COLOR_SUBMITTED);
        canvas.drawText("— Inspections submitted", left, dp(14), legendPaint);
        float legendX = left + legendPaint.measureText("— Inspections submitted ") + dp(14);
        legendPaint.setColor(COLOR_DEFECTS);
        canvas.drawText("— Confirmed defects", legendX, dp(14), legendPaint);

        if (points.isEmpty()) {
            textPaint.setColor(COLOR_EMPTY);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No trend data yet", getWidth() / 2f, (top + bottom) / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(COLOR_TEXT);
            return;
        }

        long max = 1;
        for (TrendPoint p : points) {
            max = Math.max(max, Math.max(p.inspectionsSubmitted, p.confirmedDefects));
        }

        // gridlines + y labels (0, mid, max)
        for (int i = 0; i <= 2; i++) {
            float y = bottom - height * i / 2f;
            canvas.drawLine(left, y, right, y, gridPaint);
            String label;
            if (i == 0) {
                label = "0";
            } else if (i == 1) {
                label = (max % 2 == 0) ? String.valueOf(max / 2)
                        : String.valueOf(max / 2.0);
            } else {
                label = String.valueOf(max);
            }
            canvas.drawText(label, dp(4), y + dp(4), textPaint);
        }

        // x labels: first, middle, last dates
        textPaint.setTextAlign(Paint.Align.LEFT);
        if (!points.isEmpty()) {
            String first = shortDate(points.get(0).date);
            String mid = shortDate(points.get(points.size() / 2).date);
            String last = shortDate(points.get(points.size() - 1).date);
            canvas.drawText(first, left, bottom + dp(14), textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(mid, (left + right) / 2f, bottom + dp(14), textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(last, right, bottom + dp(14), textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }

        int n = points.size();
        float stepX = n > 1 ? width / (n - 1) : 0;

        submittedPath.reset();
        defectsPath.reset();
        for (int i = 0; i < n; i++) {
            float x = n > 1 ? left + i * stepX : (left + right) / 2f;
            float ySub = bottom - height * points.get(i).inspectionsSubmitted / (float) max;
            float yDef = bottom - height * points.get(i).confirmedDefects / (float) max;
            if (i == 0) {
                submittedPath.moveTo(x, ySub);
                defectsPath.moveTo(x, yDef);
            } else {
                submittedPath.lineTo(x, ySub);
                defectsPath.lineTo(x, yDef);
            }
        }
        linePaint.setColor(COLOR_SUBMITTED);
        canvas.drawPath(submittedPath, linePaint);
        linePaint.setColor(COLOR_DEFECTS);
        canvas.drawPath(defectsPath, linePaint);

        // end-point dots
        if (n > 0) {
            float lastX = n > 1 ? left + (n - 1) * stepX : (left + right) / 2f;
            float lastSubY = bottom - height * points.get(n - 1).inspectionsSubmitted / (float) max;
            float lastDefY = bottom - height * points.get(n - 1).confirmedDefects / (float) max;
            dotPaint.setColor(COLOR_SUBMITTED);
            canvas.drawCircle(lastX, lastSubY, dp(3.5f), dotPaint);
            dotPaint.setColor(COLOR_DEFECTS);
            canvas.drawCircle(lastX, lastDefY, dp(3.5f), dotPaint);
        }
    }

    private String shortDate(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        return iso.substring(5); // MM-DD
    }
}
