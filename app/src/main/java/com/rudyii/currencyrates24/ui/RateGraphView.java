package com.rudyii.currencyrates24.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.model.RateRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RateGraphView extends View {

    private final List<RateRecord> records = new ArrayList<>();
    private final Paint buyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint salePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotBuyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotSalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String chartTitle = null;
    private OnPointTapListener pointTapListener;
    private GestureDetector gestureDetector;
    private float plotLeft, plotRight, plotTop, plotBottom;
    private double minVal, maxVal;
    public RateGraphView(Context context) {
        super(context);
        init(context);
    }

    public RateGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RateGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;

        int buyColor = ContextCompat.getColor(context, R.color.line_buy);
        int saleColor = ContextCompat.getColor(context, R.color.line_sale);

        buyPaint.setColor(buyColor);
        buyPaint.setStyle(Paint.Style.STROKE);
        buyPaint.setStrokeWidth(2.5f * density);
        buyPaint.setStrokeCap(Paint.Cap.ROUND);
        buyPaint.setStrokeJoin(Paint.Join.ROUND);

        salePaint.setColor(saleColor);
        salePaint.setStyle(Paint.Style.STROKE);
        salePaint.setStrokeWidth(2.5f * density);
        salePaint.setStrokeCap(Paint.Cap.ROUND);
        salePaint.setStrokeJoin(Paint.Join.ROUND);

        dotBuyPaint.setColor(buyColor);
        dotBuyPaint.setStyle(Paint.Style.FILL);

        dotSalePaint.setColor(saleColor);
        dotSalePaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(ContextCompat.getColor(context, R.color.graph_grid));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);

        textPaint.setColor(ContextCompat.getColor(context, R.color.graph_axis_text));
        textPaint.setTextSize(10f * density);

        titlePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        titlePaint.setTextSize(12f * density);
        titlePaint.setFakeBoldText(true);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (pointTapListener != null && !records.isEmpty() && plotRight > plotLeft && plotBottom > plotTop) {
                    int idx = findNearestIndex(e.getX());
                    if (idx >= 0 && idx < records.size()) {
                        RateRecord tapped = records.get(idx);
                        float buyY = valueToY(tapped.buy);
                        float saleY = valueToY(tapped.sale);
                        if (Math.abs(e.getY() - saleY) < Math.abs(e.getY() - buyY)) {
                            pointTapListener.onPointTap(tapped, "SALE", tapped.sale);
                        } else {
                            pointTapListener.onPointTap(tapped, "BUY", tapped.buy);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void setChartTitle(String title) {
        this.chartTitle = title;
        invalidate();
    }

    public void setRecords(List<RateRecord> newRecords) {
        records.clear();
        if (newRecords != null) {
            records.addAll(newRecords);
        }
        invalidate();
    }

    public void setOnPointTapListener(OnPointTapListener listener) {
        this.pointTapListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pointTapListener != null) {
            gestureDetector.onTouchEvent(event);
            return true;
        }
        return super.onTouchEvent(event);
    }

    private int findNearestIndex(float touchX) {
        if (records.size() == 1) return 0;
        float clampedX = Math.max(plotLeft, Math.min(plotRight, touchX));
        float fraction = (clampedX - plotLeft) / (plotRight - plotLeft);
        int idx = Math.round(fraction * (records.size() - 1));
        return Math.max(0, Math.min(records.size() - 1, idx));
    }

    private float valueToY(double val) {
        double range = maxVal - minVal;
        if (range <= 0) return (plotTop + plotBottom) / 2f;
        return plotBottom - (float) (((val - minVal) / range) * (plotBottom - plotTop));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float topOffset = (chartTitle != null) ? 18f * density : 6f * density;

        plotLeft = 42f * density;
        plotRight = w - 10f * density;
        plotTop = topOffset;
        plotBottom = h - 20f * density;

        if (chartTitle != null) {
            canvas.drawText(chartTitle, 8f * density, 13f * density, titlePaint);
        }

        if (records.isEmpty()) {
            canvas.drawText("No rate data yet", plotLeft, h / 2f, textPaint);
            return;
        }

        double rawMin = Double.MAX_VALUE;
        double rawMax = -Double.MAX_VALUE;
        for (RateRecord r : records) {
            rawMin = Math.min(rawMin, Math.min(r.buy, r.sale));
            rawMax = Math.max(rawMax, Math.max(r.buy, r.sale));
        }

        double pad = Math.max(0.05, (rawMax - rawMin) * 0.15);
        minVal = rawMin - pad;
        maxVal = rawMax + pad;

        int gridSteps = 3;
        for (int i = 0; i <= gridSteps; i++) {
            float frac = (float) i / gridSteps;
            float y = plotBottom - frac * (plotBottom - plotTop);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);

            double labelVal = minVal + frac * (maxVal - minVal);
            canvas.drawText(String.format(Locale.US, "%.2f", labelVal),
                    4f * density, y + 3.5f * density, textPaint);
        }

        Path buyPath = new Path();
        Path salePath = new Path();
        int n = records.size();

        for (int i = 0; i < n; i++) {
            RateRecord r = records.get(i);
            float x = (n == 1)
                    ? (plotLeft + plotRight) / 2f
                    : plotLeft + ((float) i / (n - 1)) * (plotRight - plotLeft);
            float yBuy = valueToY(r.buy);
            float ySale = valueToY(r.sale);

            if (i == 0) {
                buyPath.moveTo(x, yBuy);
                salePath.moveTo(x, ySale);
            } else {
                buyPath.lineTo(x, yBuy);
                salePath.lineTo(x, ySale);
            }

            if (n <= 40 || i == n - 1) {
                canvas.drawCircle(x, yBuy, 2.5f * density, dotBuyPaint);
                canvas.drawCircle(x, ySale, 2.5f * density, dotSalePaint);
            }
        }

        canvas.drawPath(buyPath, buyPaint);
        canvas.drawPath(salePath, salePaint);

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
        String startLabel = sdf.format(new Date(records.get(0).timestamp));
        String endLabel = sdf.format(new Date(records.get(n - 1).timestamp));

        canvas.drawText(startLabel, plotLeft, h - 5f * density, textPaint);
        float endWidth = textPaint.measureText(endLabel);
        canvas.drawText(endLabel, Math.max(plotLeft + 60f * density, plotRight - endWidth), h - 5f * density, textPaint);
    }

    public interface OnPointTapListener {
        void onPointTap(RateRecord record, String rateType, double rateValue);
    }
}