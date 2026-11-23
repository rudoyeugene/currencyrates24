package com.rudyii.currencyrates24.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.db.CurrencyDbHelper;
import com.rudyii.currencyrates24.model.PriceAlert;
import com.rudyii.currencyrates24.model.RateRecord;
import com.rudyii.currencyrates24.ui.MainActivity;
import com.rudyii.currencyrates24.widget.GraphWidgetProvider;
import com.rudyii.currencyrates24.widget.NumbersWidgetProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RateSyncWorker extends Worker {

    public static final String CHANNEL_ID = "currency_alerts_channel";

    public static final String PROGRESS_DAY_STAMP = "progress_day_stamp";
    public static final String IMMEDIATE_WORK_NAME = "PrivatCurrencyImmediateSync";
    private static final String SCHEDULED_WORK_NAME = "PrivatCurrencyAlignedSync";
    private static final String ARCHIVE_API_BASE = "https://api.privatbank.ua/p24api/exchange_rates?date=";
    private static final String FALLBACK_TODAY_API = "https://api.privatbank.ua/p24api/pubinfo?exchange&json&coursid=11";
    private static final String KEY_IS_SCHEDULED = "is_scheduled";
    private static final String KEY_IS_MANUAL = "is_manual";
    private static final double RATE_EPSILON = 0.0001;

    public static long computeNextAlignedRunMillis(long nowMillis, int periodMinutes) {
        int period = normalizePeriodMinutes(periodMinutes);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (period < 60) {
            int currentMin = cal.get(Calendar.MINUTE);
            int slot = ((currentMin - 1) / period) * period + 5;
            if (currentMin < 1) {
                slot = 1 - period;
            }
            cal.set(Calendar.MINUTE, 0);
            cal.add(Calendar.MINUTE, slot);
            while (cal.getTimeInMillis() <= nowMillis + 2000L) {
                cal.add(Calendar.MINUTE, period);
            }
            return cal.getTimeInMillis();
        } else {
            int stepHours = period / 60;
            int currentHour = cal.get(Calendar.HOUR_OF_DAY);
            int baseHour = (currentHour / stepHours) * stepHours;

            cal.set(Calendar.HOUR_OF_DAY, baseHour);
            cal.set(Calendar.MINUTE, 1);

            while (cal.getTimeInMillis() <= nowMillis + 2000L) {
                cal.add(Calendar.HOUR_OF_DAY, stepHours);
            }
            return cal.getTimeInMillis();
        }
    }

    public RateSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void ensureNotificationChannel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
            if (existing == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(context.getString(R.string.notification_channel_desc));
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            } else {
                existing.setName(context.getString(R.string.notification_channel_name));
                existing.setDescription(context.getString(R.string.notification_channel_desc));
                nm.createNotificationChannel(existing);
            }
        }
    }

    public static void scheduleWork(Context context) {
        int periodMinutes = CurrencyDbHelper.getInstance(context).getCheckPeriodMinutes();
        scheduleWork(context, periodMinutes);
    }

    public static void scheduleWork(Context context, int periodMinutes) {
        int normalizedPeriod = normalizePeriodMinutes(periodMinutes);
        long now = System.currentTimeMillis();
        long nextRun = computeNextAlignedRunMillis(now, normalizedPeriod);
        long initialDelayMs = Math.max(1000L, nextRun - now);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_SCHEDULED, true)
                .putBoolean(KEY_IS_MANUAL, false)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RateSyncWorker.class)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                SCHEDULED_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    public static UUID runImmediateSync(Context context) {
        return runImmediateSync(context, false);
    }

    public static UUID runImmediateSync(Context context, boolean isManual) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_SCHEDULED, false)
                .putBoolean(KEY_IS_MANUAL, isManual)
                .build();

        OneTimeWorkRequest oneTime = new OneTimeWorkRequest.Builder(RateSyncWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTime
        );
        return oneTime.getId();
    }

    public static int normalizePeriodMinutes(int minutes) {
        switch (minutes) {
            case 15:
            case 30:
            case 60:
            case 180:
            case 360:
            case 480:
            case 720:
                return minutes;
            default:
                return 60;
        }
    }

    private static String normalizeCurrencyCode(String rawCode) {
        if ("PLZ".equals(rawCode)) {
            return "PLN";
        }
        return rawCode;
    }

    private static double extractRate(JSONObject obj, String primaryKey, String fallbackKey) {
        double val = obj.optDouble(primaryKey, 0.0);
        if (Double.isNaN(val) || val <= 0.0) {
            val = obj.optDouble(fallbackKey, 0.0);
        }
        return Double.isNaN(val) ? 0.0 : val;
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean isScheduled = getInputData().getBoolean(KEY_IS_SCHEDULED, false);
        boolean isManual = getInputData().getBoolean(KEY_IS_MANUAL, false);
        try {
            CurrencyDbHelper db = CurrencyDbHelper.getInstance(getApplicationContext());
            int historyDays = Math.max(1, db.getHistoryDays());

            // Always ensure USD and EUR are fetched alongside any additional user-selected currencies
            Set<String> currenciesToFetch = new LinkedHashSet<>();
            currenciesToFetch.add("USD");
            currenciesToFetch.add("EUR");
            currenciesToFetch.addAll(db.getTrackedCurrencies());

            Set<String> completeStoredDates = db.getCompleteStoredDates(currenciesToFetch);
            SimpleDateFormat apiDateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
            SimpleDateFormat dbDateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            boolean anyTodayChanged = false;
            boolean anyBackfilled = false;
            boolean usedTodayFallback = false;
            int successfulCalls = 0;

            // Iterate chronologically from oldest day in retention window to today
            for (int daysAgo = historyDays - 1; daysAgo >= 0; daysAgo--) {
                if (isStopped()) break;

                boolean isToday = (daysAgo == 0);
                Calendar targetCal = Calendar.getInstance();
                targetCal.add(Calendar.DAY_OF_YEAR, -daysAgo);

                String dbDayKey = dbDateKeyFormat.format(targetCal.getTime());
                if (!isToday && completeStoredDates.contains(dbDayKey)) {
                    continue;
                }

                long recordTimestamp;
                if (isToday) {
                    recordTimestamp = System.currentTimeMillis();
                } else {
                    targetCal.set(Calendar.HOUR_OF_DAY, 12);
                    targetCal.set(Calendar.MINUTE, 0);
                    targetCal.set(Calendar.SECOND, 0);
                    targetCal.set(Calendar.MILLISECOND, 0);
                    recordTimestamp = targetCal.getTimeInMillis();
                }

                String apiDateStr = apiDateFormat.format(targetCal.getTime());
                List<ParsedRateItem> parsedItems = parseArchiveRates(
                        fetchUrl(ARCHIVE_API_BASE + apiDateStr),
                        apiDateStr,
                        currenciesToFetch
                );

                // For today: if archive API has no data yet or failed, fallback to pubinfo?exchange&json&coursid=11
                if (isToday && parsedItems.isEmpty()) {
                    parsedItems = parsePubinfoFallbackRates(fetchUrl(FALLBACK_TODAY_API), currenciesToFetch);
                    if (!parsedItems.isEmpty()) {
                        usedTodayFallback = true;
                    }
                }

                if (parsedItems.isEmpty()) {
                    if (isToday && successfulCalls == 0) {
                        return isScheduled ? Result.retry() : Result.failure();
                    }
                    continue;
                }
                successfulCalls++;

                boolean dayInserted = false;
                for (ParsedRateItem item : parsedItems) {
                    Set<String> ccyStoredDates = db.getCompleteStoredDates(Collections.singleton(item.ccy));
                    boolean hasRecordForDay = ccyStoredDates.contains(dbDayKey);

                    if (isToday) {
                        RateRecord last = db.getLatestRate(item.ccy);
                        boolean isRateUnchanged = last != null
                                && Math.abs(last.buy - item.buy) < RATE_EPSILON
                                && Math.abs(last.sale - item.sale) < RATE_EPSILON;

                        // Ensure today always gets a row if missing for today, or if today's rate changed
                        if (!hasRecordForDay || !isRateUnchanged) {
                            db.insertRate(item.ccy, item.baseCcy, item.buy, item.sale, recordTimestamp);
                            anyTodayChanged = true;
                            dayInserted = true;
                        }
                        evaluateAlerts(db, item.ccy, item.buy, item.sale);
                    } else if (!hasRecordForDay) {
                        db.insertRate(item.ccy, item.baseCcy, item.buy, item.sale, recordTimestamp);
                        anyBackfilled = true;
                        dayInserted = true;
                    }
                }

                if (dayInserted) {
                    setProgressAsync(new Data.Builder()
                            .putString(PROGRESS_DAY_STAMP, dbDayKey)
                            .build());
                }
            }

            if (anyTodayChanged || anyBackfilled) {
                db.pruneOldRates(historyDays);
                NumbersWidgetProvider.updateAllWidgets(getApplicationContext());
                GraphWidgetProvider.updateAllWidgets(getApplicationContext());

                if (isManual && usedTodayFallback) {
                    Context appCtx = getApplicationContext();
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(appCtx, R.string.toast_using_live_fallback, Toast.LENGTH_SHORT).show()
                    );
                }
            } else if (isManual) {
                Context appCtx = getApplicationContext();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(appCtx, R.string.toast_rates_unchanged, Toast.LENGTH_SHORT).show()
                );
            }

            if (isScheduled) {
                scheduleWork(getApplicationContext(), db.getCheckPeriodMinutes());
            }

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            if (isScheduled) {
                scheduleWork(getApplicationContext());
                return Result.retry();
            }
            return Result.failure();
        }
    }

    private List<ParsedRateItem> parseArchiveRates(String jsonStr, String expectedDateStr, Set<String> allowedCurrencies) {
        List<ParsedRateItem> items = new ArrayList<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return items;
        }
        try {
            JSONObject root = new JSONObject(jsonStr);
            String returnedDate = root.optString("date", "").trim();
            if (!returnedDate.isEmpty() && !returnedDate.equals(expectedDateStr)) {
                return items;
            }
            JSONArray ratesArray = root.optJSONArray("exchangeRate");
            if (ratesArray == null || ratesArray.length() == 0) {
                return items;
            }
            for (int i = 0; i < ratesArray.length(); i++) {
                JSONObject obj = ratesArray.getJSONObject(i);
                String rawCcy = obj.optString("currency", "").toUpperCase(Locale.US);
                String ccy = normalizeCurrencyCode(rawCcy);
                if (!allowedCurrencies.contains(ccy)) {
                    continue;
                }
                String baseCcy = obj.optString("baseCurrency", "UAH").toUpperCase(Locale.US);
                double buy = extractRate(obj, "purchaseRateNB", "purchaseRate");
                double sale = extractRate(obj, "saleRate", "saleRateNB");
                if (buy > 0.0 && sale > 0.0) {
                    items.add(new ParsedRateItem(ccy, baseCcy, buy, sale));
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private List<ParsedRateItem> parsePubinfoFallbackRates(String jsonStr, Set<String> allowedCurrencies) {
        List<ParsedRateItem> items = new ArrayList<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String rawCcy = obj.optString("ccy", "").toUpperCase(Locale.US);
                String ccy = normalizeCurrencyCode(rawCcy);
                if (!allowedCurrencies.contains(ccy)) {
                    continue;
                }
                String baseCcy = obj.optString("base_ccy", "UAH").toUpperCase(Locale.US);
                double buy = Double.parseDouble(obj.optString("buy", "0"));
                double sale = Double.parseDouble(obj.optString("sale", "0"));
                if (buy > 0.0 && sale > 0.0) {
                    items.add(new ParsedRateItem(ccy, baseCcy, buy, sale));
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private String fetchUrl(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static class ParsedRateItem {
        final String ccy;
        final String baseCcy;
        final double buy;
        final double sale;

        ParsedRateItem(String ccy, String baseCcy, double buy, double sale) {
            this.ccy = ccy;
            this.baseCcy = baseCcy;
            this.buy = buy;
            this.sale = sale;
        }
    }

    private void evaluateAlerts(CurrencyDbHelper db, String ccy, double buy, double sale) {
        List<PriceAlert> alerts = db.getActiveAlerts();
        for (PriceAlert alert : alerts) {
            if (!alert.ccy.equals(ccy)) continue;

            double currentRate = "BUY".equals(alert.type) ? buy : sale;
            boolean triggered = ("ABOVE".equals(alert.condition) && currentRate >= alert.targetRate)
                    || ("BELOW".equals(alert.condition) && currentRate <= alert.targetRate);

            if (triggered) {
                sendAlertNotification(alert, currentRate);
                db.deleteAlert(alert.id);
            }
        }
    }

    private void sendAlertNotification(PriceAlert alert, double currentRate) {
        Context ctx = getApplicationContext();
        ensureNotificationChannel(ctx);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String typeLabel = "BUY".equals(alert.type)
                ? ctx.getString(R.string.type_buy)
                : ctx.getString(R.string.type_sale);
        String condLabel = "ABOVE".equals(alert.condition)
                ? ctx.getString(R.string.cond_above).toLowerCase(Locale.getDefault())
                : ctx.getString(R.string.cond_below).toLowerCase(Locale.getDefault());

        String title = ctx.getString(R.string.notification_alert_title, alert.ccy, typeLabel);
        String body = ctx.getString(R.string.notification_alert_body,
                alert.ccy, typeLabel, currentRate, condLabel, alert.targetRate);

        Intent openAppIntent = new Intent(ctx, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                ctx,
                (int) alert.id,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        nm.notify((int) alert.id, builder.build());
    }
}