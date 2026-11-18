package com.rudyii.currencyrates24.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
        int minWidth = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200) : 200;
        int minHeight = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100) : 100;
        boolean isSmallerThan2x1 = (minWidth < 180) || (minHeight < 90);

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
        views.setOnClickPendingIntent(R.id.tvPrimaryTitle, togglePi);
        views.setOnClickPendingIntent(R.id.tvCompactTitle, togglePi);

        // Manual refresh icon
        Intent refreshIntent = new Intent(context, NumbersWidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, widgetId + 10000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPi);

        List<RateRecord> history = db.getRatesForCurrency(ccy);
        int neutralColor = ContextCompat.getColor(context, R.color.text_secondary);

        if (isSmallerThan2x1) {
            views.setViewVisibility(R.id.llStandardNumbers, View.GONE);
            views.setViewVisibility(R.id.llCompactNumbers, View.VISIBLE);
            views.setTextViewText(R.id.tvCompactTitle, ccy + "/UAH ▾");

            if (history == null || history.isEmpty()) {
                views.setTextViewText(R.id.tvCompactBuyVal, "0.00");
                views.setTextViewText(R.id.tvCompactSellVal, "0.00");
            } else {
                RateRecord latest = history.get(history.size() - 1);
                views.setTextViewText(R.id.tvCompactBuyVal, String.format(Locale.US, "%.2f", latest.buy));
                views.setTextViewText(R.id.tvCompactSellVal, String.format(Locale.US, "%.2f", latest.sale));
            }
        } else {
            views.setViewVisibility(R.id.llCompactNumbers, View.GONE);
            views.setViewVisibility(R.id.llStandardNumbers, View.VISIBLE);
            views.setTextViewText(R.id.tvPrimaryTitle, ccy + "/UAH ▾");

            if (history == null || history.isEmpty()) {
                views.setTextViewText(R.id.tvPrimaryBuyVal, "--.--");
                views.setTextViewText(R.id.tvPrimarySellVal, "--.--");
                views.setTextViewText(R.id.tvPrimaryBuyDiff, "• 0.00 (0.00%)");
                views.setTextViewText(R.id.tvPrimarySellDiff, "• 0.00 (0.00%)");
                views.setTextColor(R.id.tvPrimaryBuyDiff, neutralColor);
                views.setTextColor(R.id.tvPrimarySellDiff, neutralColor);
            } else {
                RateRecord latest = history.get(history.size() - 1);
                views.setTextViewText(R.id.tvPrimaryBuyVal, String.format(Locale.US, "%.2f", latest.buy));
                views.setTextViewText(R.id.tvPrimarySellVal, String.format(Locale.US, "%.2f", latest.sale));

                if (history.size() == 1) {
                    views.setTextViewText(R.id.tvPrimaryBuyDiff, "• 0.00 (0.00%)");
                    views.setTextViewText(R.id.tvPrimarySellDiff, "• 0.00 (0.00%)");
                    views.setTextColor(R.id.tvPrimaryBuyDiff, neutralColor);
                    views.setTextColor(R.id.tvPrimarySellDiff, neutralColor);
                } else {
                    RateRecord prev = history.get(history.size() - 2);
                    applyDeltaFormatting(views, R.id.tvPrimaryBuyDiff, latest.buy - prev.buy, prev.buy, neutralColor);
                    applyDeltaFormatting(views, R.id.tvPrimarySellDiff, latest.sale - prev.sale, prev.sale, neutralColor);
                }
            }
        }

        manager.updateAppWidget(widgetId, views);
    }

    private static void applyDeltaFormatting(RemoteViews views, int viewId, double diff, double prevVal, int neutralColor) {
        double pct = prevVal != 0 ? (diff / prevVal) * 100.0 : 0.0;
        if (diff > 0.0001) {
            views.setTextViewText(viewId, String.format(Locale.US, "▲ %+.2f (%+.2f%%)", diff, pct));
            views.setTextColor(viewId, COLOR_UP);
        } else if (diff < -0.0001) {
            views.setTextViewText(viewId, String.format(Locale.US, "▼ %+.2f (%+.2f%%)", diff, pct));
            views.setTextColor(viewId, COLOR_DOWN);
        } else {
            views.setTextViewText(viewId, "• 0.00 (0.00%)");
            views.setTextColor(viewId, neutralColor);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_MANUAL_REFRESH.equals(action)) {
            Toast.makeText(context, R.string.toast_syncing_rates, Toast.LENGTH_SHORT).show();
            RateSyncWorker.runImmediateSync(context);
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