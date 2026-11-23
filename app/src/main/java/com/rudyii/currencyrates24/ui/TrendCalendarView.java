package com.rudyii.currencyrates24.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rudyii.currencyrates24.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TrendCalendarView extends View {

    private final Map<String, DayTrend> dayTrends = new HashMap<>();
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weekdayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cellRect = new RectF();
    private Calendar displayedMonth;
    private String todayKey;
    private float dragOffsetX = 0f;
    private OverScroller scroller;
    private GestureDetector gestureDetector;
    private OnDayClickListener dayClickListener;
    private int colorUnchanged;
    private int colorWeak;
    private int colorStrong;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private float headerBottomY;
    private float gridTopY;
    private float cellWidth;
    private float cellHeight;

    public TrendCalendarView(Context context) {
        super(context);
        init(context);
    }

    public TrendCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TrendCalendarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private static int getMondayFirstOffset(Calendar monthStart) {
        int dow = monthStart.get(Calendar.DAY_OF_WEEK); // Sun=1, Mon=2..Sat=7
        return (dow == Calendar.SUNDAY) ? 6 : (dow - Calendar.MONDAY);
    }

    private static String formatDateKey(Calendar cal) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;

        colorUnchanged = ContextCompat.getColor(context, R.color.cal_uah_unchanged);
        colorWeak = ContextCompat.getColor(context, R.color.cal_uah_weak);
        colorStrong = ContextCompat.getColor(context, R.color.cal_uah_strong);
        colorTextPrimary = ContextCompat.getColor(context, R.color.text_primary);
        colorTextSecondary = ContextCompat.getColor(context, R.color.text_secondary);

        headerPaint.setColor(colorTextPrimary);
        headerPaint.setTextSize(15f * density);
        headerPaint.setFakeBoldText(true);
        headerPaint.setTextAlign(Paint.Align.CENTER);

        weekdayPaint.setColor(colorTextSecondary);
        weekdayPaint.setTextSize(11f * density);
        weekdayPaint.setFakeBoldText(true);
        weekdayPaint.setTextAlign(Paint.Align.CENTER);

        dayTextPaint.setColor(colorTextPrimary);
        dayTextPaint.setTextSize(13f * density);
        dayTextPaint.setTextAlign(Paint.Align.CENTER);

        badgePaint.setStyle(Paint.Style.FILL);

        todayStrokePaint.setStyle(Paint.Style.STROKE);
        todayStrokePaint.setStrokeWidth(1.5f * density);
        todayStrokePaint.setColor(colorTextPrimary);

        displayedMonth = Calendar.getInstance();
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
        todayKey = formatDateKey(Calendar.getInstance());

        scroller = new OverScroller(context);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                dragOffsetX -= distanceX;
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (velocityX < -300f) {
                        shiftMonth(1);
                        return true;
                    } else if (velocityX > 300f) {
                        shiftMonth(-1);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setDayTrends(Map<String, DayTrend> trends) {
        dayTrends.clear();
        if (trends != null) {
            dayTrends.putAll(trends);
        }
        todayKey = formatDateKey(Calendar.getInstance());
        invalidate();
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.dayClickListener = listener;
    }

    public void shiftMonth(int deltaMonths) {
        displayedMonth.add(Calendar.MONTH, deltaMonths);
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
        animateResetOffset(deltaMonths > 0 ? getWidth() * 0.35f : -getWidth() * 0.35f);
    }

    private void animateResetOffset(float startOffset) {
        dragOffsetX = startOffset;
        scroller.startScroll((int) dragOffsetX, 0, (int) -dragOffsetX, 0, 220);
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            dragOffsetX = scroller.getCurrX();
            postInvalidateOnAnimation();
        } else if (dragOffsetX != 0f) {
            dragOffsetX = 0f;
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            float threshold = getWidth() * 0.22f;
            if (dragOffsetX < -threshold) {
                shiftMonth(1);
            } else if (dragOffsetX > threshold) {
                shiftMonth(-1);
            } else if (dragOffsetX != 0f) {
                animateResetOffset(dragOffsetX);
            }
        }
        return handled || super.onTouchEvent(event);
    }

    private void handleTap(float x, float y) {
        float w = getWidth();
        if (w <= 0) return;

        // Header left/right month arrows
        if (y <= headerBottomY) {
            if (x < w * 0.25f) {
                shiftMonth(-1);
            } else if (x > w * 0.75f) {
                shiftMonth(1);
            }
            return;
        }

        if (y < gridTopY || cellWidth <= 0 || cellHeight <= 0) return;

        int col = (int) (x / cellWidth);
        int row = (int) ((y - gridTopY) / cellHeight);
        if (col < 0 || col > 6 || row < 0 || row > 5) return;

        int firstWeekdayOffset = getMondayFirstOffset(displayedMonth);
        int daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dayNum = row * 7 + col - firstWeekdayOffset + 1;

        if (dayNum >= 1 && dayNum <= daysInMonth) {
            Calendar tappedCal = (Calendar) displayedMonth.clone();
            tappedCal.set(Calendar.DAY_OF_MONTH, dayNum);
            String key = formatDateKey(tappedCal);
            if (dayClickListener != null && dayTrends.containsKey(key)) {
                dayClickListener.onDayClick(key);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        headerBottomY = 28f * density;
        float weekdayRowY = headerBottomY + 16f * density;
        gridTopY = weekdayRowY + 8f * density;

        // Month Header (< Month YYYY >)
        SimpleDateFormat monthFormat = new SimpleDateFormat("LLLL yyyy", Locale.getDefault());
        String rawTitle = monthFormat.format(displayedMonth.getTime());
        String monthTitle = rawTitle.substring(0, 1).toUpperCase(Locale.getDefault()) + rawTitle.substring(1);

        float headerBaseline = 20f * density;
        canvas.drawText("◀", w * 0.12f, headerBaseline, headerPaint);
        canvas.drawText(monthTitle, w * 0.5f + dragOffsetX * 0.35f, headerBaseline, headerPaint);
        canvas.drawText("▶", w * 0.88f, headerBaseline, headerPaint);

        // Weekday Strip (Mon .. Sun)
        String[] weekdays = getResources().getStringArray(R.array.weekday_short_labels);
        cellWidth = w / 7f;
        for (int col = 0; col < 7; col++) {
            float cx = col * cellWidth + cellWidth / 2f;
            String label = col < weekdays.length ? weekdays[col] : "";
            canvas.drawText(label, cx, weekdayRowY, weekdayPaint);
        }

        // 6-row Month Grid
        cellHeight = (h - gridTopY) / 6f;
        int firstWeekdayOffset = getMondayFirstOffset(displayedMonth);
        int daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        float badgeRadius = Math.min(cellWidth, cellHeight) * 0.38f;
        float textCenterOffset = (dayTextPaint.descent() + dayTextPaint.ascent()) / 2f;

        Calendar iterCal = (Calendar) displayedMonth.clone();
        for (int day = 1; day <= daysInMonth; day++) {
            int slot = firstWeekdayOffset + (day - 1);
            int row = slot / 7;
            int col = slot % 7;

            float cx = col * cellWidth + cellWidth / 2f + dragOffsetX;
            float cy = gridTopY + row * cellHeight + cellHeight / 2f;

            iterCal.set(Calendar.DAY_OF_MONTH, day);
            String key = formatDateKey(iterCal);
            DayTrend trend = dayTrends.getOrDefault(key, DayTrend.NONE);

            if (trend != DayTrend.NONE) {
                int fillColor = (trend == DayTrend.UNCHANGED) ? colorUnchanged
                        : (trend == DayTrend.UAH_WEAK) ? colorWeak
                        : colorStrong;
                badgePaint.setColor(fillColor);
                cellRect.set(cx - badgeRadius, cy - badgeRadius, cx + badgeRadius, cy + badgeRadius);
                canvas.drawRoundRect(cellRect, badgeRadius * 0.55f, badgeRadius * 0.55f, badgePaint);
                dayTextPaint.setColor(Color.WHITE);
                dayTextPaint.setFakeBoldText(true);
            } else {
                dayTextPaint.setColor(colorTextPrimary);
                dayTextPaint.setFakeBoldText(false);
            }

            if (key.equals(todayKey)) {
                float ringR = badgeRadius + 2f * density;
                cellRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
                canvas.drawRoundRect(cellRect, ringR * 0.55f, ringR * 0.55f, todayStrokePaint);
            }

            canvas.drawText(String.valueOf(day), cx, cy - textCenterOffset, dayTextPaint);
        }
    }

    public enum DayTrend {
        NONE,
        UNCHANGED,   // Green: UAH unchanged
        UAH_WEAK,    // Red: UAH weakened (foreign rates rose)
        UAH_STRONG   // Blue: UAH strengthened (foreign rates fell)
    }

    public interface OnDayClickListener {
        void onDayClick(String dateKey);
    }
}