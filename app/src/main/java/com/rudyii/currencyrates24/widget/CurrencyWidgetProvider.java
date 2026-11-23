package com.rudyii.currencyrates24.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.db.CurrencyDbHelper;
import com.rudyii.currencyrates24.model.RateRecord;
import com.rudyii.currencyrates24.ui.MainActivity;
import com.rudyii.currencyrates24.ui.RateGraphView;
import com.rudyii.currencyrates24.worker.RateSyncWorker;

import java.util.List;
import java.util.Locale;

public class CurrencyWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_MANUAL_REFRESH = "com.rudyii.currencyrates24.ACTION_WIDGET_REFRESH";
    private static final String ACTION_CYCLE_CURRENCY = "com.rudyii.currencyrates24.ACTION_WIDGET_CYCLE_CCY";

    private static final int COLOR_UP = Color.parseColor("#4ADE80");
    private static final int COLOR_DOWN = Color.parseColor("#EF4444");

    public static void updateSingleWidget(Context context, AppWidgetManager manager, int widgetId) {
        CurrencyDbHelper db = CurrencyDbHelper.getInstance(context);
        String ccyChoice = db.getSetting("widget_ccy_" + widgetId, "USD");
        String viewMode = db.getSetting("widget_mode_" + widgetId, "NUMBERS");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_currency);

        // Tap anywhere on widget body to open app
        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context, widgetId, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, openPi);

        // Tap bottom-right sync icon to trigger immediate background fetch
        Intent refreshIntent = new Intent(context, CurrencyWidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, widgetId + 10000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPi);

        // Tap currency title header to cycle tracked currency (USD <-> EUR)
        Intent cycleIntent = new Intent(context, CurrencyWidgetProvider.class);
        cycleIntent.setAction(ACTION_CYCLE_CURRENCY);
        cycleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent cyclePi = PendingIntent.getBroadcast(context, widgetId + 20000, cycleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.tvPrimaryTitle, cyclePi);
        views.setOnClickPendingIntent(R.id.tvSecondaryTitle, cyclePi);
        views.setOnClickPendingIntent(R.id.tvGraphCurrencySwitch, cyclePi);

        if ("NUMBERS".equals(viewMode)) {
            views.setViewVisibility(R.id.llWidgetNumbersContainer, View.VISIBLE);
            views.setViewVisibility(R.id.ivWidgetGraph, View.GONE);
            views.setViewVisibility(R.id.tvGraphCurrencySwitch, View.GONE);
            views.setViewVisibility(R.id.ivWidgetHorizontalDivider, View.GONE);
            views.setViewVisibility(R.id.llSecondaryCurrency, View.GONE);

            bindCurrencyBlock(context, views, ccyChoice, db.getRatesForCurrency(ccyChoice),
                    R.id.tvPrimaryTitle, R.id.tvPrimaryBuyVal, R.id.tvPrimaryBuyDiff,
                    R.id.tvPrimarySellVal, R.id.tvPrimarySellDiff);
        } else {
            views.setViewVisibility(R.id.llWidgetNumbersContainer, View.GONE);
            views.setViewVisibility(R.id.ivWidgetGraph, View.VISIBLE);
            views.setViewVisibility(R.id.tvGraphCurrencySwitch, View.VISIBLE);
            views.setTextViewText(R.id.tvGraphCurrencySwitch, ccyChoice + " ▾");

            List<RateRecord> records = db.getRatesForCurrency(ccyChoice);
            Bitmap bmp = renderSingleGraphBitmap(context, ccyChoice + "/UAH", records, 640, 260);
            views.setImageViewBitmap(R.id.ivWidgetGraph, bmp);
        }

        manager.updateAppWidget(widgetId, views);
    }

    private static void bindCurrencyBlock(Context context, RemoteViews views, String ccy,
                                          List<RateRecord> history,
                                          int titleId, int buyValId, int buyDiffId,
                                          int sellValId, int sellDiffId) {
        views.setTextViewText(titleId, ccy + "/UAH ▾");
        int neutralColor = ContextCompat.getColor(context, R.color.text_secondary);

        if (history == null || history.isEmpty()) {
            views.setTextViewText(buyValId, "--.--");
            views.setTextViewText(sellValId, "--.--");
            views.setTextViewText(buyDiffId, "• 0.00 (0.00%)");
            views.setTextViewText(sellDiffId, "• 0.00 (0.00%)");
            views.setTextColor(buyDiffId, neutralColor);
            views.setTextColor(sellDiffId, neutralColor);
            return;
        }

        RateRecord latest = history.get(history.size() - 1);
        views.setTextViewText(buyValId, String.format(Locale.US, "%.2f", latest.buy));
        views.setTextViewText(sellValId, String.format(Locale.US, "%.2f", latest.sale));

        if (history.size() == 1) {
            views.setTextViewText(buyDiffId, "• 0.00 (0.00%)");
            views.setTextViewText(sellDiffId, "• 0.00 (0.00%)");
            views.setTextColor(buyDiffId, neutralColor);
            views.setTextColor(sellDiffId, neutralColor);
            return;
        }

        RateRecord prev = history.get(history.size() - 2);
        applyDeltaFormatting(views, buyDiffId, latest.buy - prev.buy, prev.buy, neutralColor);
        applyDeltaFormatting(views, sellDiffId, latest.sale - prev.sale, prev.sale, neutralColor);
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

    private static Bitmap renderSingleGraphBitmap(Context context, String title, List<RateRecord> records, int width, int height) {
        RateGraphView graphView = new RateGraphView(context);
        graphView.setChartTitle(title);
        graphView.setRecords(records);

        graphView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        graphView.layout(0, 0, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        graphView.draw(canvas);
        return bitmap;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_MANUAL_REFRESH.equals(action)) {
            Toast.makeText(context, R.string.toast_syncing_rates, Toast.LENGTH_SHORT).show();
            RateSyncWorker.runImmediateSync(context, true);
        } else if (ACTION_CYCLE_CURRENCY.equals(action)) {
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
    public void onDeleted(Context context, int[] appWidgetIds) {
        CurrencyDbHelper db = CurrencyDbHelper.getInstance(context);
        for (int id : appWidgetIds) {
            db.getWritableDatabase().delete(CurrencyDbHelper.TABLE_SETTINGS,
                    "setting_key IN (?, ?)",
                    new String[]{"widget_ccy_" + id, "widget_mode_" + id});
        }
    }
}