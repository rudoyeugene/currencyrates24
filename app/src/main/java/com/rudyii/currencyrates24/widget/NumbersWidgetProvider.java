package com.rudyii.currencyrates24.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.db.CurrencyDbHelper;
import com.rudyii.currencyrates24.model.RateRecord;
import com.rudyii.currencyrates24.ui.MainActivity;
import com.rudyii.currencyrates24.worker.RateSyncWorker;

import java.util.List;
import java.util.Locale;

public class NumbersWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_MANUAL_REFRESH = "com.rudyii.currencyrates24.ACTION_NUM_WIDGET_REFRESH";
    private static final String ACTION_TOGGLE_CURRENCY = "com.rudyii.currencyrates24.ACTION_NUM_WIDGET_TOGGLE_CCY";

    private static final int COLOR_UP = 0xFF4ADE80;
    private static final int COLOR_DOWN = 0xFFEF4444;

    // Launcher width thresholds (dp):
    // 2x1: ~110–179dp | 3x1: ~180–249dp | 4x1+: >= 250dp
    private static final int WIDTH_3X1_DP = 180;
    private static final int WIDTH_4X1_DP = 250;

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NumbersWidgetProvider.class));
        for (int id : ids) {
            updateSingleWidget(context, manager, id);
        }
    }

    public static void updateSingleWidget(Context context, AppWidgetManager manager, int widgetId) {
        CurrencyDbHelper db = CurrencyDbHelper.getInstance(context);
        String ccy = db.getSetting("widget_ccy_" + widgetId, "USD");
        if (!"USD".equals(ccy) && !"EUR".equals(ccy)) {
            ccy = "USD";
        }

        Bundle options = manager.getAppWidgetOptions(widgetId);
        int minWidth = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) : 180;
        boolean is4x1OrWider = minWidth >= WIDTH_4X1_DP;
        boolean is3x1 = !is4x1OrWider && minWidth >= WIDTH_3X1_DP;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_numbers);

        // Open MainActivity on root tap
        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context, widgetId, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, openPi);

        // Toggle USD <-> EUR on title tap
        Intent toggleIntent = new Intent(context, NumbersWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE_CURRENCY);
        toggleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent togglePi = PendingIntent.getBroadcast(context, widgetId + 20000, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tvWideTitle, togglePi);
        views.setOnClickPendingIntent(R.id.tvStackedTitle, togglePi);

        // Manual refresh icon
        Intent refreshIntent = new Intent(context, NumbersWidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, widgetId + 10000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPi);

        List<RateRecord> history = db.getRatesForCurrency(ccy);
        int neutralColor = ContextCompat.getColor(context, R.color.text_secondary);

        if (is4x1OrWider) {
            views.setViewVisibility(R.id.llStackedNumbers, View.GONE);
            views.setViewVisibility(R.id.llWideNumbers, View.VISIBLE);
            views.setTextViewText(R.id.tvWideTitle, ccy + "/UAH ▾");

            bindRates(views, history, neutralColor, true,
                    R.id.tvWideBuyVal, R.id.tvWideSellVal,
                    R.id.tvWideBuyDiff, R.id.tvWideSellDiff);
        } else {
            views.setViewVisibility(R.id.llWideNumbers, View.GONE);
            views.setViewVisibility(R.id.llStackedNumbers, View.VISIBLE);
            views.setTextViewText(R.id.tvStackedTitle, ccy + "/UAH ▾");

            // Scale text slightly larger on 3x1 compared to 2x1
            float titleSp = is3x1 ? 14f : 12.5f;
            float labelSp = is3x1 ? 14f : 12.5f;
            float valSp = is3x1 ? 21f : 18f;
            float diffSp = is3x1 ? 12f : 11f;

            views.setTextViewTextSize(R.id.tvStackedTitle, TypedValue.COMPLEX_UNIT_SP, titleSp);
            views.setTextViewTextSize(R.id.tvStackedBuyLabel, TypedValue.COMPLEX_UNIT_SP, labelSp);
            views.setTextViewTextSize(R.id.tvStackedSellLabel, TypedValue.COMPLEX_UNIT_SP, labelSp);
            views.setTextViewTextSize(R.id.tvStackedBuyVal, TypedValue.COMPLEX_UNIT_SP, valSp);
            views.setTextViewTextSize(R.id.tvStackedSellVal, TypedValue.COMPLEX_UNIT_SP, valSp);
            views.setTextViewTextSize(R.id.tvStackedBuyDiff, TypedValue.COMPLEX_UNIT_SP, diffSp);
            views.setTextViewTextSize(R.id.tvStackedSellDiff, TypedValue.COMPLEX_UNIT_SP, diffSp);

            bindRates(views, history, neutralColor, false,
                    R.id.tvStackedBuyVal, R.id.tvStackedSellVal,
                    R.id.tvStackedBuyDiff, R.id.tvStackedSellDiff);
        }

        manager.updateAppWidget(widgetId, views);
    }

    private static void bindRates(RemoteViews views, List<RateRecord> history, int neutralColor,
                                  boolean includeAmountDiff,
                                  int buyValId, int sellValId, int buyDiffId, int sellDiffId) {
        String zeroDiff = includeAmountDiff ? "• 0.00 (0.00%)" : "• 0.00%";

        if (history == null || history.isEmpty()) {
            views.setTextViewText(buyValId, "--.--");
            views.setTextViewText(sellValId, "--.--");
            views.setTextViewText(buyDiffId, zeroDiff);
            views.setTextViewText(sellDiffId, zeroDiff);
            views.setTextColor(buyDiffId, neutralColor);
            views.setTextColor(sellDiffId, neutralColor);
            return;
        }

        RateRecord latest = history.get(history.size() - 1);
        views.setTextViewText(buyValId, String.format(Locale.US, "%.2f", latest.buy));
        views.setTextViewText(sellValId, String.format(Locale.US, "%.2f", latest.sale));

        if (history.size() == 1) {
            views.setTextViewText(buyDiffId, zeroDiff);
            views.setTextViewText(sellDiffId, zeroDiff);
            views.setTextColor(buyDiffId, neutralColor);
            views.setTextColor(sellDiffId, neutralColor);
            return;
        }

        RateRecord prev = history.get(history.size() - 2);
        applyDeltaFormatting(views, buyDiffId, latest.buy - prev.buy, prev.buy, neutralColor, includeAmountDiff);
        applyDeltaFormatting(views, sellDiffId, latest.sale - prev.sale, prev.sale, neutralColor, includeAmountDiff);
    }

    private static void applyDeltaFormatting(RemoteViews views, int viewId, double diff, double prevVal,
                                             int neutralColor, boolean includeAmountDiff) {
        double pct = prevVal != 0 ? (diff / prevVal) * 100.0 : 0.0;
        if (diff > 0.0001) {
            String text = includeAmountDiff
                    ? String.format(Locale.US, "▲ %+.2f (%+.2f%%)", diff, pct)
                    : String.format(Locale.US, "▲ %+.2f%%", pct);
            views.setTextViewText(viewId, text);
            views.setTextColor(viewId, COLOR_UP);
        } else if (diff < -0.0001) {
            String text = includeAmountDiff
                    ? String.format(Locale.US, "▼ %+.2f (%+.2f%%)", diff, pct)
                    : String.format(Locale.US, "▼ %+.2f%%", pct);
            views.setTextViewText(viewId, text);
            views.setTextColor(viewId, COLOR_DOWN);
        } else {
            views.setTextViewText(viewId, includeAmountDiff ? "• 0.00 (0.00%)" : "• 0.00%");
            views.setTextColor(viewId, neutralColor);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_MANUAL_REFRESH.equals(action)) {
            Toast.makeText(context, R.string.toast_syncing_rates, Toast.LENGTH_SHORT).show();
            RateSyncWorker.runImmediateSync(context, true);
        } else if (ACTION_TOGGLE_CURRENCY.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                CurrencyDbHelper db = CurrencyDbHelper.getInstance(context);
                String current = db.getSetting("widget_ccy_" + widgetId, "USD");
                String next = "USD".equals(current) ? "EUR" : "USD";
                db.saveSetting("widget_ccy_" + widgetId, next);
                updateSingleWidget(context, AppWidgetManager.getInstance(context), widgetId);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateSingleWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        CurrencyDbHelper db = CurrencyDbHelper.getInstance(context);
        for (int id : appWidgetIds) {
            db.getWritableDatabase().delete(CurrencyDbHelper.TABLE_SETTINGS,
                    "setting_key = ?",
                    new String[]{"widget_ccy_" + id});
        }
    }
}