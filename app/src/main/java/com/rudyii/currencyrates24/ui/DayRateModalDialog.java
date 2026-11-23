package com.rudyii.currencyrates24.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.model.RateRecord;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DayRateModalDialog {

    private static final double DELTA_EPSILON = 0.0001;

    @SuppressLint("ClickableViewAccessibility")
    public static void show(@NonNull Context context,
                            @NonNull String dateKey,
                            @NonNull List<String> trackedCurrencies,
                            @NonNull Map<String, RateRecord> ratesForDate,
                            @NonNull Map<String, RateRecord> prevRatesForDate,
                            @NonNull OnWatchClickListener watchClickListener) {
        if (trackedCurrencies.isEmpty()) return;

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View rootView = LayoutInflater.from(context).inflate(R.layout.dialog_day_rate_modal, null);
        dialog.setContentView(rootView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int horizontalMargin = (int) (32 * context.getResources().getDisplayMetrics().density);
            int maxWidth = (int) (460 * context.getResources().getDisplayMetrics().density);
            int dialogWidth = Math.min(screenWidth - horizontalMargin, maxWidth);
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        View swipeContainer = rootView.findViewById(R.id.modalSwipeContainer);
        TextView tvDate = rootView.findViewById(R.id.tvModalDate);
        TextView btnClose = rootView.findViewById(R.id.btnModalClose);
        TextView btnPrevCcy = rootView.findViewById(R.id.btnModalPrevCcy);
        TextView btnNextCcy = rootView.findViewById(R.id.btnModalNextCcy);
        TextView tvCurrencyTitle = rootView.findViewById(R.id.tvModalCurrencyTitle);
        TextView tvSwipeHint = rootView.findViewById(R.id.tvModalSwipeHint);
        TextView tvBuyRate = rootView.findViewById(R.id.tvModalBuyRate);
        TextView tvBuyDelta = rootView.findViewById(R.id.tvModalBuyDelta);
        TextView tvSaleRate = rootView.findViewById(R.id.tvModalSaleRate);
        TextView tvSaleDelta = rootView.findViewById(R.id.tvModalSaleDelta);
        MaterialButton btnWatch = rootView.findViewById(R.id.btnModalWatch);

        tvDate.setText(formatDisplayDate(dateKey));

        int colorWeak = ContextCompat.getColor(context, R.color.cal_uah_weak);
        int colorStrong = ContextCompat.getColor(context, R.color.cal_uah_strong);
        int colorNeutral = ContextCompat.getColor(context, R.color.text_secondary);

        final int[] currentIndex = {0};

        Runnable renderCurrentCurrency = () -> {
            String ccy = trackedCurrencies.get(currentIndex[0]);
            tvCurrencyTitle.setText(ccy + " / UAH");

            if (trackedCurrencies.size() > 1) {
                tvSwipeHint.setVisibility(View.VISIBLE);
                tvSwipeHint.setText(context.getString(R.string.modal_swipe_hint,
                        currentIndex[0] + 1, trackedCurrencies.size()));
                btnPrevCcy.setVisibility(View.VISIBLE);
                btnNextCcy.setVisibility(View.VISIBLE);
            } else {
                tvSwipeHint.setVisibility(View.GONE);
                btnPrevCcy.setVisibility(View.INVISIBLE);
                btnNextCcy.setVisibility(View.INVISIBLE);
            }

            RateRecord current = ratesForDate.get(ccy);
            RateRecord previous = prevRatesForDate.get(ccy);

            if (current == null) {
                tvBuyRate.setText("--.--");
                tvSaleRate.setText("--.--");
                tvBuyDelta.setText("• 0.00 (0.00%)");
                tvSaleDelta.setText("• 0.00 (0.00%)");
                tvBuyDelta.setTextColor(colorNeutral);
                tvSaleDelta.setTextColor(colorNeutral);
                btnWatch.setEnabled(false);
            } else {
                tvBuyRate.setText(String.format(Locale.US, "%.2f ₴", current.buy));
                tvSaleRate.setText(String.format(Locale.US, "%.2f ₴", current.sale));
                btnWatch.setEnabled(true);

                if (previous != null) {
                    bindDelta(tvBuyDelta, current.buy - previous.buy, previous.buy, colorWeak, colorStrong, colorNeutral);
                    bindDelta(tvSaleDelta, current.sale - previous.sale, previous.sale, colorWeak, colorStrong, colorNeutral);
                } else {
                    tvBuyDelta.setText("• 0.00 (0.00%)");
                    tvSaleDelta.setText("• 0.00 (0.00%)");
                    tvBuyDelta.setTextColor(colorNeutral);
                    tvSaleDelta.setTextColor(colorNeutral);
                }
            }
        };

        Runnable switchNext = () -> {
            if (trackedCurrencies.size() <= 1) return;
            currentIndex[0] = (currentIndex[0] + 1) % trackedCurrencies.size();
            animateCardTransition(swipeContainer, 1, renderCurrentCurrency);
        };

        Runnable switchPrev = () -> {
            if (trackedCurrencies.size() <= 1) return;
            currentIndex[0] = (currentIndex[0] - 1 + trackedCurrencies.size()) % trackedCurrencies.size();
            animateCardTransition(swipeContainer, -1, renderCurrentCurrency);
        };

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnNextCcy.setOnClickListener(v -> switchNext.run());
        btnPrevCcy.setOnClickListener(v -> switchPrev.run());

        btnWatch.setOnClickListener(v -> {
            String ccy = trackedCurrencies.get(currentIndex[0]);
            RateRecord current = ratesForDate.get(ccy);
            double buyVal = current != null ? current.buy : 0.0;
            double saleVal = current != null ? current.sale : 0.0;
            dialog.dismiss();
            watchClickListener.onWatchClick(ccy, buyVal, saleVal);
        });

        GestureDetector swipeDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 60f) {
                    if (dx < 0) {
                        switchNext.run();
                    } else {
                        switchPrev.run();
                    }
                    return true;
                }
                return false;
            }
        });

        swipeContainer.setOnTouchListener((v, event) -> swipeDetector.onTouchEvent(event));

        renderCurrentCurrency.run();
        dialog.show();
    }

    private static void animateCardTransition(View container, int direction, Runnable updateContent) {
        float shift = 36f * container.getResources().getDisplayMetrics().density * direction;
        container.animate()
                .translationX(-shift)
                .alpha(0.35f)
                .setDuration(90L)
                .withEndAction(() -> {
                    updateContent.run();
                    container.setTranslationX(shift);
                    container.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(110L)
                            .start();
                })
                .start();
    }

    private static void bindDelta(TextView tv, double diff, double prevVal,
                                  int colorWeak, int colorStrong, int colorNeutral) {
        double pct = prevVal != 0.0 ? (diff / prevVal) * 100.0 : 0.0;
        if (diff > DELTA_EPSILON) {
            tv.setText(String.format(Locale.US, "▲ %+.2f (%+.2f%%)", diff, pct));
            tv.setTextColor(colorWeak);
        } else if (diff < -DELTA_EPSILON) {
            tv.setText(String.format(Locale.US, "▼ %+.2f (%+.2f%%)", diff, pct));
            tv.setTextColor(colorStrong);
        } else {
            tv.setText("• 0.00 (0.00%)");
            tv.setTextColor(colorNeutral);
        }
    }

    private static String formatDisplayDate(String dateKey) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date parsed = parser.parse(dateKey);
            if (parsed != null) {
                SimpleDateFormat formatter = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());
                return formatter.format(parsed);
            }
        } catch (ParseException ignored) {
        }
        return dateKey;
    }

    public interface OnWatchClickListener {
        void onWatchClick(String currency, double buyRate, double saleRate);
    }
}