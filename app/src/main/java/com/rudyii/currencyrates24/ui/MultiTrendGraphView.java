package com.rudyii.currencyrates24.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rudyii.currencyrates24.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MultiTrendGraphView extends View {

    private final List<TrendPoint> points = new ArrayList<>();
    private final Paint usdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint generalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint usdDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eurDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint generalDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public MultiTrendGraphView(Context context) {
        super(context);
        init(context);
    }

    public MultiTrendGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MultiTrendGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;

        int usdColor = ContextCompat.getColor(context, R.color.line_usd);
        int eurColor = ContextCompat.getColor(context, R.color.line_eur);
        int generalColor = ContextCompat.getColor(context, R.color.line_general);

        usdPaint.setColor(usdColor);
        usdPaint.setStyle(Paint.Style.STROKE);
        usdPaint.setStrokeWidth(2.4f * density);
        usdPaint.setStrokeCap(Paint.Cap.ROUND);
        usdPaint.setStrokeJoin(Paint.Join.ROUND);

        eurPaint.setColor(eurColor);
        eurPaint.setStyle(Paint.Style.STROKE);
        eurPaint.setStrokeWidth(2.4f * density);
        eurPaint.setStrokeCap(Paint.Cap.ROUND);
        eurPaint.setStrokeJoin(Paint.Join.ROUND);

        generalPaint.setColor(generalColor);
        generalPaint.setStyle(Paint.Style.STROKE);
        generalPaint.setStrokeWidth(2.8f * density);
        generalPaint.setStrokeCap(Paint.Cap.ROUND);
        generalPaint.setStrokeJoin(Paint.Join.ROUND);
        generalPaint.setPathEffect(new DashPathEffect(new float[]{8f * density, 4f * density}, 0f));

        usdDotPaint.setColor(usdColor);
        usdDotPaint.setStyle(Paint.Style.FILL);

        eurDotPaint.setColor(eurColor);
        eurDotPaint.setStyle(Paint.Style.FILL);

        generalDotPaint.setColor(generalColor);
        generalDotPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(ContextCompat.getColor(context, R.color.graph_grid));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);

        textPaint.setColor(ContextCompat.getColor(context, R.color.graph_axis_text));
        textPaint.setTextSize(10f * density);
    }

    public void setPoints(List<TrendPoint> newPoints) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float plotLeft = 42f * density;
        float plotRight = w - 10f * density;
        float plotTop = 8f * density;
        float plotBottom = h - 20f * density;

        if (points.isEmpty()) {
            canvas.drawText(getContext().getString(R.string.waiting_for_data), plotLeft, h / 2f, textPaint);
            return;
        }

        double rawMin = Double.MAX_VALUE;
        double rawMax = -Double.MAX_VALUE;
        for (TrendPoint p : points) {
            if (p.usdAvg != null) {
                rawMin = Math.min(rawMin, p.usdAvg);
                rawMax = Math.max(rawMax, p.usdAvg);
            }
            if (p.eurAvg != null) {
                rawMin = Math.min(rawMin, p.eurAvg);
                rawMax = Math.max(rawMax, p.eurAvg);
            }
            if (p.generalUahAvg != null) {
                rawMin = Math.min(rawMin, p.generalUahAvg);
                rawMax = Math.max(rawMax, p.generalUahAvg);
            }
        }

        if (rawMin == Double.MAX_VALUE) {
            canvas.drawText(getContext().getString(R.string.waiting_for_data), plotLeft, h / 2f, textPaint);
            return;
        }

        double pad = Math.max(0.10, (rawMax - rawMin) * 0.12);
        double minVal = rawMin - pad;
        double maxVal = rawMax + pad;
        double range = maxVal - minVal;

        int gridSteps = 3;
        for (int i = 0; i <= gridSteps; i++) {
            float frac = (float) i / gridSteps;
            float y = plotBottom - frac * (plotBottom - plotTop);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);

            double labelVal = minVal + frac * range;
            canvas.drawText(String.format(Locale.US, "%.2f", labelVal),
                    4f * density, y + 3.5f * density, textPaint);
        }

        drawSeries(canvas, plotLeft, plotRight, plotTop, plotBottom, minVal, range, density, SeriesType.USD);
        drawSeries(canvas, plotLeft, plotRight, plotTop, plotBottom, minVal, range, density, SeriesType.EUR);
        drawSeries(canvas, plotLeft, plotRight, plotTop, plotBottom, minVal, range, density, SeriesType.GENERAL);

        String startLabel = points.get(0).shortLabel;
        String endLabel = points.get(points.size() - 1).shortLabel;

        canvas.drawText(startLabel, plotLeft, h - 5f * density, textPaint);
        float endWidth = textPaint.measureText(endLabel);
        canvas.drawText(endLabel, Math.max(plotLeft + 50f * density, plotRight - endWidth), h - 5f * density, textPaint);
    }

    private void drawSeries(Canvas canvas, float plotLeft, float plotRight, float plotTop, float plotBottom,
                            double minVal, double range, float density, SeriesType type) {
        Path path = new Path();
        Paint linePaint;
        Paint dotPaint;
        switch (type) {
            case USD:
                linePaint = usdPaint;
                dotPaint = usdDotPaint;
                break;
            case EUR:
                linePaint = eurPaint;
                dotPaint = eurDotPaint;
                break;
            default:
                linePaint = generalPaint;
                dotPaint = generalDotPaint;
                break;
        }

        int n = points.size();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            TrendPoint p = points.get(i);
            Double val = (type == SeriesType.USD) ? p.usdAvg
                    : (type == SeriesType.EUR) ? p.eurAvg
                    : p.generalUahAvg;
            if (val == null) continue;

            float x = (n == 1)
                    ? (plotLeft + plotRight) / 2f
                    : plotLeft + ((float) i / (n - 1)) * (plotRight - plotLeft);
            float y = (range <= 0)
                    ? (plotTop + plotBottom) / 2f
                    : plotBottom - (float) (((val - minVal) / range) * (plotBottom - plotTop));

            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }

            if (n <= 45 || i == n - 1) {
                canvas.drawCircle(x, y, 2.3f * density, dotPaint);
            }
        }

        if (started) {
            canvas.drawPath(path, linePaint);
        }
    }

    private enum SeriesType {
        USD, EUR, GENERAL
    }

    public static class TrendPoint {
        public final String dateKey;
        public final String shortLabel;
        public final Double usdAvg;
        public final Double eurAvg;
        public final Double generalUahAvg;

        public TrendPoint(String dateKey, String shortLabel,
                          @Nullable Double usdAvg,
                          @Nullable Double eurAvg,
                          @Nullable Double generalUahAvg) {
            this.dateKey = dateKey;
            this.shortLabel = shortLabel;
            this.usdAvg = usdAvg;
            this.eurAvg = eurAvg;
            this.generalUahAvg = generalUahAvg;
        }
    }
}