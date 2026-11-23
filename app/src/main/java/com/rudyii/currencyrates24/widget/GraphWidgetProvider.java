package com.rudyii.currencyrates24.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.db.CurrencyDbHelper;
import com.rudyii.currencyrates24.model.RateRecord;
import com.rudyii.currencyrates24.ui.MainActivity;
import com.rudyii.currencyrates24.ui.RateGraphView;
import com.rudyii.currencyrates24.worker.RateSyncWorker;

import java.util.List;

public class GraphWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_MANUAL_REFRESH = "com.rudyii.currencyrates24.ACTION_GRAPH_WIDGET_REFRESH";
    private static final String ACTION_TOGGLE_CURRENCY = "com.rudyii.currencyrates24.ACTION_GRAPH_WIDGET_TOGGLE_CCY";

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, GraphWidgetProvider.class));
        for (int id : ids) {
            updateSingleWidget(context, manager, id);
        }
    }

    public static void updateSingleWidget(Context context, AppWidgetManager manager, int widgetId) {
        CurrencyDbHelper db = CurrencyDbHelper.getInstance(context);
        List<String> tracked = db.getTrackedCurrencies();
        String ccy = db.getSetting("widget_ccy_" + widgetId, tracked.get(0));
        if (!CurrencyDbHelper.SUPPORTED_CURRENCIES.contains(ccy)) {
            ccy = tracked.get(0);
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_graph);

        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context, widgetId + 30000, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, openPi);

        Intent toggleIntent = new Intent(context, GraphWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE_CURRENCY);
        toggleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent togglePi = PendingIntent.getBroadcast(context, widgetId + 40000, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tvGraphCurrencySwitch, togglePi);
        views.setTextViewText(R.id.tvGraphCurrencySwitch, ccy + " ▾");

        Intent refreshIntent = new Intent(context, GraphWidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, widgetId + 50000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPi);

        List<RateRecord> records = db.getRatesForCurrency(ccy);
        Bitmap bmp = renderGraphBitmap(context, ccy + "/UAH", records, 640, 280);
        views.setImageViewBitmap(R.id.ivWidgetGraph, bmp);

        manager.updateAppWidget(widgetId, views);
    }

    private static Bitmap renderGraphBitmap(Context context, String title, List<RateRecord> records, int width, int height) {
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
        } else if (ACTION_TOGGLE_CURRENCY.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                CurrencyDbHelper db = CurrencyDbHelper.getInstance(context);
                String current = db.getSetting("widget_ccy_" + widgetId, "USD");
                String next = db.getNextTrackedCurrency(current);
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
                    "setting_key = ?",
                    new String[]{"widget_ccy_" + id});
        }
    }
}