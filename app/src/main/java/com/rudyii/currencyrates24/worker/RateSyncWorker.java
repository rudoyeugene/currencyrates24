package com.rudyii.currencyrates24.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

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
import com.rudyii.currencyrates24.widget.GraphWidgetProvider;
import com.rudyii.currencyrates24.widget.NumbersWidgetProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RateSyncWorker extends Worker {

    public static final String CHANNEL_ID = "currency_alerts_channel";
    private static final String API_URL_PUBLIC = "https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=5";
    private static final String API_URL_COMMERCIAL = "https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=11";
    private static final String SCHEDULED_WORK_NAME = "PrivatCurrencyAlignedSync";
    private static final String IMMEDIATE_WORK_NAME = "PrivatCurrencyImmediateSync";
    private static final String KEY_IS_SCHEDULED = "is_scheduled";

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
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_SCHEDULED, false)
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

    public static long computeNextAlignedRunMillis(long nowMillis, int periodMinutes) {
        int period = normalizePeriodMinutes(periodMinutes);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (period < 60) {
            int currentMin = cal.get(Calendar.MINUTE);
            int slot = ((currentMin - 1) / period) * period + 1;
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

    @NonNull
    @Override
    public Result doWork() {
        boolean isScheduled = getInputData().getBoolean(KEY_IS_SCHEDULED, false);
        try {
            CurrencyDbHelper db = CurrencyDbHelper.getInstance(getApplicationContext());
            String endpoint = db.isPublicRates() ? API_URL_PUBLIC : API_URL_COMMERCIAL;

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) {
                return isScheduled ? Result.retry() : Result.failure();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            JSONArray array = new JSONArray(sb.toString());
            long now = System.currentTimeMillis();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String ccy = obj.getString("ccy");
                String baseCcy = obj.getString("base_ccy");
                double buy = Double.parseDouble(obj.getString("buy"));
                double sale = Double.parseDouble(obj.getString("sale"));

                if ("USD".equals(ccy) || "EUR".equals(ccy)) {
                    db.insertRate(ccy, baseCcy, buy, sale, now);
                    evaluateAlerts(db, ccy, buy, sale);
                }
            }

            db.pruneOldRates(db.getHistoryDays());
            NumbersWidgetProvider.updateAllWidgets(getApplicationContext());
            GraphWidgetProvider.updateAllWidgets(getApplicationContext());

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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        nm.notify((int) alert.id, builder.build());
    }
}