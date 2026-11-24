package com.rudyii.currencyrates24.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.db.CurrencyDbHelper;
import com.rudyii.currencyrates24.model.PriceAlert;
import com.rudyii.currencyrates24.model.RateRecord;
import com.rudyii.currencyrates24.widget.GraphWidgetProvider;
import com.rudyii.currencyrates24.widget.NumbersWidgetProvider;
import com.rudyii.currencyrates24.worker.RateSyncWorker;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final double UNCHANGED_PCT_THRESHOLD = 0.05;

    private final Handler refreshTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Map<String, RateRecord>> cachedDailyRecords = new HashMap<>();
    private final List<String> cachedSortedDates = new ArrayList<>();

    private CurrencyDbHelper db;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTrendSummary;
    private MultiTrendGraphView multiTrendGraph;
    private TrendCalendarView trendCalendar;
    private TextView tvCalendarLastUpdated;
    private Runnable refreshTimeoutRunnable;

    private UUID activeObservedWorkId = null;
    private boolean activeWorkWasRunning = false;
    private String lastPaintedDay = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        syncStatusBarAppearance();

        View root = findViewById(R.id.mainRoot);
        View content = findViewById(R.id.mainContent);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbar.setPadding(insets.left, insets.top, insets.right, 0);
            content.setPadding(insets.left + 16, 12, insets.right + 16, insets.bottom + 12);
            return WindowInsetsCompat.CONSUMED;
        });

        db = CurrencyDbHelper.getInstance(this);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvTrendSummary = findViewById(R.id.tvTrendSummary);
        multiTrendGraph = findViewById(R.id.multiTrendGraph);
        trendCalendar = findViewById(R.id.trendCalendar);
        tvCalendarLastUpdated = findViewById(R.id.tvCalendarLastUpdated);

        colorizeThreeDots(findViewById(R.id.tvGraphLegend),
                getString(R.string.legend_multi_trend),
                ContextCompat.getColor(this, R.color.line_usd),
                ContextCompat.getColor(this, R.color.line_eur),
                ContextCompat.getColor(this, R.color.line_general));

        colorizeThreeDots(findViewById(R.id.tvCalendarLegend),
                getString(R.string.calendar_legend),
                ContextCompat.getColor(this, R.color.cal_uah_unchanged),
                ContextCompat.getColor(this, R.color.cal_uah_weak),
                ContextCompat.getColor(this, R.color.cal_uah_strong));

        trendCalendar.setOnDayClickListener(this::openDayDetailModal);

        swipeRefresh.setOnRefreshListener(() -> triggerManualSync(true));

        findViewById(R.id.btnManageAlerts).setOnClickListener(v -> showManageAlertsDialog());

        observeImmediateSyncWork();

        requestNotificationPermissionIfNeeded();
        if (db.getRatesForCurrency("USD").isEmpty()) {
            triggerManualSync(false);
        }
    }

    private void observeImmediateSyncWork() {
        WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData(RateSyncWorker.IMMEDIATE_WORK_NAME)
                .observe(this, workInfos -> {
                    if (workInfos == null || workInfos.isEmpty()) return;
                    WorkInfo workInfo = workInfos.get(0);
                    UUID id = workInfo.getId();
                    WorkInfo.State state = workInfo.getState();

                    if (!id.equals(activeObservedWorkId)) {
                        if (state.isFinished()) {
                            return;
                        }
                        activeObservedWorkId = id;
                        activeWorkWasRunning = false;
                        lastPaintedDay = "";
                    }

                    if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING) {
                        db.saveSetting(SettingsActivity.KEY_PENDING_SETTINGS_RESYNC, "false");
                        startRefreshSpinner();

                        if (state == WorkInfo.State.RUNNING) {
                            activeWorkWasRunning = true;
                            String progressDay = workInfo.getProgress().getString(RateSyncWorker.PROGRESS_DAY_STAMP);
                            if (progressDay != null && !progressDay.equals(lastPaintedDay)) {
                                lastPaintedDay = progressDay;
                                refreshUi();
                            }
                        }
                    } else if (state.isFinished() || activeWorkWasRunning) {
                        activeObservedWorkId = null;
                        activeWorkWasRunning = false;
                        stopRefreshSpinner();
                        refreshUi();
                        NumbersWidgetProvider.updateAllWidgets(MainActivity.this);
                        GraphWidgetProvider.updateAllWidgets(MainActivity.this);
                    }
                });
    }

    private void startRefreshSpinner() {
        if (swipeRefresh != null && !swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(true);
        }
        if (refreshTimeoutRunnable != null) {
            refreshTimeoutHandler.removeCallbacks(refreshTimeoutRunnable);
        }
        refreshTimeoutRunnable = this::stopRefreshSpinner;
        refreshTimeoutHandler.postDelayed(refreshTimeoutRunnable, 45000L);
    }

    private void stopRefreshSpinner() {
        if (refreshTimeoutRunnable != null) {
            refreshTimeoutHandler.removeCallbacks(refreshTimeoutRunnable);
        }
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void openDayDetailModal(String dateKey) {
        List<String> tracked = db.getTrackedCurrencies();
        if (tracked.isEmpty()) return;

        int dateIdx = cachedSortedDates.indexOf(dateKey);
        String prevDateKey = (dateIdx > 0) ? cachedSortedDates.get(dateIdx - 1) : null;

        Map<String, RateRecord> ratesForDate = new HashMap<>();
        Map<String, RateRecord> prevRatesForDate = new HashMap<>();

        for (String ccy : tracked) {
            Map<String, RateRecord> byDay = cachedDailyRecords.get(ccy);
            if (byDay == null) continue;
            if (byDay.containsKey(dateKey)) {
                ratesForDate.put(ccy, byDay.get(dateKey));
            }
            if (prevDateKey != null && byDay.containsKey(prevDateKey)) {
                prevRatesForDate.put(ccy, byDay.get(prevDateKey));
            }
        }

        DayRateModalDialog.show(
                this,
                dateKey,
                tracked,
                ratesForDate,
                prevRatesForDate,
                this::showWatchAlertDialog
        );
    }

    private void showWatchAlertDialog(String currency, double buyRate, double saleRate) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 12);

        TextView tvHeader = new TextView(this);
        tvHeader.setText(currency + " / UAH");
        tvHeader.setTextSize(17f);
        tvHeader.setTypeface(null, Typeface.BOLD);
        tvHeader.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvHeader.setPadding(8, 4, 8, 16);
        layout.addView(tvHeader);

        String[] typeKeys = {"SALE", "BUY"};
        String[] typeLabels = {getString(R.string.type_sale), getString(R.string.type_buy)};
        Spinner spType = new Spinner(this);
        spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, typeLabels));
        layout.addView(spType);

        String[] condKeys = {"ABOVE", "BELOW"};
        String[] condLabels = {getString(R.string.cond_above), getString(R.string.cond_below)};
        Spinner spCond = new Spinner(this);
        spCond.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, condLabels));
        layout.addView(spCond);

        EditText etRate = new EditText(this);
        etRate.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double initialRate = saleRate > 0 ? saleRate : buyRate;
        if (initialRate > 0) {
            etRate.setText(String.format(Locale.US, "%.2f", initialRate));
            etRate.setSelection(etRate.getText().length());
        } else {
            etRate.setHint(R.string.hint_target_rate);
        }
        layout.addView(etRate);

        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                double chosenPrefill = (position == 0) ? saleRate : buyRate;
                if (chosenPrefill > 0) {
                    etRate.setText(String.format(Locale.US, "%.2f", chosenPrefill));
                    etRate.setSelection(etRate.getText().length());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_set_alert_title)
                .setView(layout)
                .setPositiveButton(R.string.btn_save, (d, w) -> {
                    String rateStr = etRate.getText().toString().trim().replace(',', '.');
                    if (!rateStr.isEmpty()) {
                        try {
                            double target = Double.parseDouble(rateStr);
                            String chosenType = typeKeys[spType.getSelectedItemPosition()];
                            String chosenCond = condKeys[spCond.getSelectedItemPosition()];
                            db.addAlert(currency, chosenType, chosenCond, target);
                            Toast.makeText(this, R.string.toast_alert_saved, Toast.LENGTH_SHORT).show();
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void colorizeThreeDots(TextView view, String text, int c1, int c2, int c3) {
        if (view == null) return;
        SpannableString spannable = new SpannableString(text);
        int dot1 = text.indexOf('●');
        if (dot1 >= 0) {
            spannable.setSpan(new ForegroundColorSpan(c1), dot1, dot1 + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int dot2 = text.indexOf('●', dot1 + 1);
            if (dot2 >= 0) {
                spannable.setSpan(new ForegroundColorSpan(c2), dot2, dot2 + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int dot3 = text.indexOf('●', dot2 + 1);
                if (dot3 >= 0) {
                    spannable.setSpan(new ForegroundColorSpan(c3), dot3, dot3 + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        view.setText(spannable);
    }

    private void triggerManualSync(boolean isManual) {
        if (isManual) {
            Toast.makeText(this, R.string.toast_syncing_rates, Toast.LENGTH_SHORT).show();
        }
        startRefreshSpinner();
        RateSyncWorker.runImmediateSync(this, isManual);
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncStatusBarAppearance();
        if (Boolean.parseBoolean(db.getSetting(SettingsActivity.KEY_PENDING_SETTINGS_RESYNC, "false"))) {
            db.saveSetting(SettingsActivity.KEY_PENDING_SETTINGS_RESYNC, "false");
            startRefreshSpinner();
        }
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshTimeoutRunnable != null) {
            refreshTimeoutHandler.removeCallbacks(refreshTimeoutRunnable);
        }
    }

    private void syncStatusBarAppearance() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(isNight);
    }

    private void refreshUi() {
        List<String> tracked = db.getTrackedCurrencies();
        Set<String> allNeeded = new LinkedHashSet<>();
        allNeeded.add("USD");
        allNeeded.add("EUR");
        allNeeded.addAll(tracked);

        SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Map<String, Map<String, Double>> ccyDailyAvgs = new HashMap<>();
        TreeSet<String> allDates = new TreeSet<>();
        cachedDailyRecords.clear();
        long latestRecordTimestamp = 0L;

        for (String ccy : allNeeded) {
            Map<String, Double> perDayAvg = new LinkedHashMap<>();
            Map<String, RateRecord> perDayRec = new LinkedHashMap<>();
            for (RateRecord rec : db.getRatesForCurrency(ccy)) {
                String dayKey = dayKeyFormat.format(new Date(rec.timestamp));
                perDayAvg.put(dayKey, (rec.buy + rec.sale) / 2.0);
                perDayRec.put(dayKey, rec);
                allDates.add(dayKey);
                if (rec.timestamp > latestRecordTimestamp) {
                    latestRecordTimestamp = rec.timestamp;
                }
            }
            ccyDailyAvgs.put(ccy, perDayAvg);
            cachedDailyRecords.put(ccy, perDayRec);
        }

        cachedSortedDates.clear();
        cachedSortedDates.addAll(allDates);

        if (tvCalendarLastUpdated != null) {
            if (latestRecordTimestamp > 0L) {
                DateFormat dtFormat = DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());
                String formattedTime = dtFormat.format(new Date(latestRecordTimestamp));
                tvCalendarLastUpdated.setText(
                        getString(R.string.calendar_last_updated_format, formattedTime));
                tvCalendarLastUpdated.setVisibility(View.VISIBLE);
            } else {
                tvCalendarLastUpdated.setText("");
                tvCalendarLastUpdated.setVisibility(View.GONE);
            }
        }

        if (cachedSortedDates.isEmpty()) {
            multiTrendGraph.setPoints(Collections.emptyList());
            trendCalendar.setDayTrends(Collections.emptyMap());
            tvTrendSummary.setText(R.string.waiting_for_data);
            return;
        }

        Map<String, Double> usdMap = ccyDailyAvgs.getOrDefault("USD", Collections.emptyMap());
        Map<String, Double> eurMap = ccyDailyAvgs.getOrDefault("EUR", Collections.emptyMap());

        double baselineAnchor = 0.0;
        for (String d : cachedSortedDates) {
            Double u = usdMap.get(d);
            Double e = eurMap.get(d);
            if (u != null && e != null) {
                baselineAnchor = (u + e) / 2.0;
                break;
            } else if (u != null) {
                baselineAnchor = u;
                break;
            } else if (e != null) {
                baselineAnchor = e;
                break;
            }
        }
        if (baselineAnchor <= 0.0) baselineAnchor = 40.0;

        Map<String, Double> baseTrackedAvg = new HashMap<>();
        for (String ccy : tracked) {
            Map<String, Double> m = ccyDailyAvgs.get(ccy);
            if (m != null) {
                for (String d : cachedSortedDates) {
                    if (m.containsKey(d)) {
                        baseTrackedAvg.put(ccy, m.get(d));
                        break;
                    }
                }
            }
        }

        List<MultiTrendGraphView.TrendPoint> graphPoints = new ArrayList<>();
        Map<String, TrendCalendarView.DayTrend> calendarTrends = new HashMap<>();

        double latestUsd = 0.0;
        double latestEur = 0.0;
        double latestDailyBasketPct = 0.0;

        for (int i = 0; i < cachedSortedDates.size(); i++) {
            String dateKey = cachedSortedDates.get(i);
            String shortLabel = dateKey.substring(8, 10) + "." + dateKey.substring(5, 7);

            Double usdVal = usdMap.get(dateKey);
            Double eurVal = eurMap.get(dateKey);
            if (usdVal != null) latestUsd = usdVal;
            if (eurVal != null) latestEur = eurVal;

            double ratioSum = 0.0;
            int ratioCount = 0;
            for (String ccy : tracked) {
                Map<String, Double> m = ccyDailyAvgs.get(ccy);
                Double cur = (m != null) ? m.get(dateKey) : null;
                Double base = baseTrackedAvg.get(ccy);
                if (cur != null && base != null && base > 0) {
                    ratioSum += (cur / base);
                    ratioCount++;
                }
            }
            Double generalIndexVal = (ratioCount > 0) ? baselineAnchor * (ratioSum / ratioCount) : null;

            graphPoints.add(new MultiTrendGraphView.TrendPoint(
                    dateKey, shortLabel, usdVal, eurVal, generalIndexVal));

            if (i == 0) {
                calendarTrends.put(dateKey, TrendCalendarView.DayTrend.UNCHANGED);
                latestDailyBasketPct = 0.0;
            } else {
                String prevDateKey = cachedSortedDates.get(i - 1);
                double pctChangeSum = 0.0;
                int pairCount = 0;
                for (String ccy : tracked) {
                    Map<String, Double> m = ccyDailyAvgs.get(ccy);
                    if (m == null) continue;
                    Double cur = m.get(dateKey);
                    Double prev = m.get(prevDateKey);
                    if (cur != null && prev != null && prev > 0) {
                        pctChangeSum += ((cur - prev) / prev) * 100.0;
                        pairCount++;
                    }
                }
                double avgPctChange = (pairCount > 0) ? (pctChangeSum / pairCount) : 0.0;
                latestDailyBasketPct = avgPctChange;

                if (Math.abs(avgPctChange) < UNCHANGED_PCT_THRESHOLD) {
                    calendarTrends.put(dateKey, TrendCalendarView.DayTrend.UNCHANGED);
                } else if (avgPctChange > 0) {
                    calendarTrends.put(dateKey, TrendCalendarView.DayTrend.UAH_WEAK);
                } else {
                    calendarTrends.put(dateKey, TrendCalendarView.DayTrend.UAH_STRONG);
                }
            }
        }

        multiTrendGraph.setPoints(graphPoints);
        trendCalendar.setDayTrends(calendarTrends);

        String arrow = latestDailyBasketPct > UNCHANGED_PCT_THRESHOLD ? "▲"
                : (latestDailyBasketPct < -UNCHANGED_PCT_THRESHOLD ? "▼" : "•");
        tvTrendSummary.setText(getString(R.string.trend_summary_format,
                latestUsd, latestEur, arrow, latestDailyBasketPct));
    }

    private void showAlertDialog(String defaultCcy, String defaultType, double prefillRate) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 12);

        List<String> trackedList = db.getTrackedCurrencies();
        String[] ccyKeys = trackedList.toArray(new String[0]);
        String[] typeKeys = {"BUY", "SALE"};
        String[] typeLabels = {getString(R.string.type_buy), getString(R.string.type_sale)};
        String[] condKeys = {"ABOVE", "BELOW"};
        String[] condLabels = {getString(R.string.cond_above), getString(R.string.cond_below)};

        Spinner spCcy = new Spinner(this);
        spCcy.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ccyKeys));
        int ccyIdx = trackedList.indexOf(defaultCcy);
        if (ccyIdx >= 0) spCcy.setSelection(ccyIdx);
        layout.addView(spCcy);

        Spinner spType = new Spinner(this);
        spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, typeLabels));
        if ("SALE".equals(defaultType)) spType.setSelection(1);
        layout.addView(spType);

        Spinner spCond = new Spinner(this);
        spCond.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, condLabels));
        layout.addView(spCond);

        EditText etRate = new EditText(this);
        etRate.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (prefillRate > 0) {
            etRate.setText(String.format(Locale.US, "%.2f", prefillRate));
            etRate.setSelection(etRate.getText().length());
        } else {
            etRate.setHint(R.string.hint_target_rate);
        }
        layout.addView(etRate);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_set_alert_title)
                .setView(layout)
                .setPositiveButton(R.string.btn_save, (d, w) -> {
                    String rateStr = etRate.getText().toString().trim().replace(',', '.');
                    if (!rateStr.isEmpty()) {
                        try {
                            double target = Double.parseDouble(rateStr);
                            String chosenCcy = ccyKeys[spCcy.getSelectedItemPosition()];
                            String chosenType = typeKeys[spType.getSelectedItemPosition()];
                            String chosenCond = condKeys[spCond.getSelectedItemPosition()];

                            db.addAlert(chosenCcy, chosenType, chosenCond, target);
                            Toast.makeText(this, R.string.toast_alert_saved, Toast.LENGTH_SHORT).show();
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showManageAlertsDialog() {
        List<PriceAlert> alerts = db.getActiveAlerts();
        if (alerts.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_active_alerts, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[alerts.size()];
        for (int i = 0; i < alerts.size(); i++) {
            PriceAlert a = alerts.get(i);
            String typeLabel = "BUY".equals(a.type) ? getString(R.string.type_buy) : getString(R.string.type_sale);
            String condLabel = "ABOVE".equals(a.condition) ? getString(R.string.cond_above) : getString(R.string.cond_below);
            labels[i] = getString(R.string.alert_item_format, a.ccy, typeLabel, condLabel, a.targetRate);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_active_alerts_title)
                .setItems(labels, (dialog, which) -> {
                    db.deleteAlert(alerts.get(which).id);
                    Toast.makeText(this, R.string.toast_alert_removed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_add_alert) {
            List<String> tracked = db.getTrackedCurrencies();
            showAlertDialog(tracked.isEmpty() ? "USD" : tracked.get(0), "BUY", 0);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }
}