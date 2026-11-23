package com.rudyii.currencyrates24.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int COLOR_BUY_DOT = Color.parseColor("#2E7D32");
    private static final int COLOR_SALE_DOT = Color.parseColor("#1565C0");

    private final Handler refreshTimeoutHandler = new Handler(Looper.getMainLooper());
    private CurrencyDbHelper db;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvUsdDelta, tvEurDelta;
    private RateGraphView graphUsd, graphEur;
    private Runnable refreshTimeoutRunnable;

    public static String formatDeltaSummary(Context context, List<RateRecord> history) {
        if (history == null || history.isEmpty()) {
            return context.getString(R.string.waiting_for_data);
        }
        RateRecord latest = history.get(history.size() - 1);
        if (history.size() == 1) {
            return context.getString(R.string.delta_single_format, latest.buy, latest.sale);
        }
        RateRecord prev = history.get(history.size() - 2);

        double buyDiff = latest.buy - prev.buy;
        double buyPct = (buyDiff / prev.buy) * 100.0;
        String buyArrow = buyDiff > 0 ? "▲" : (buyDiff < 0 ? "▼" : "•");

        double saleDiff = latest.sale - prev.sale;
        double salePct = (saleDiff / prev.sale) * 100.0;
        String saleArrow = saleDiff > 0 ? "▲" : (saleDiff < 0 ? "▼" : "•");

        return context.getString(R.string.delta_full_format,
                latest.buy, buyArrow, buyDiff, buyPct,
                latest.sale, saleArrow, saleDiff, salePct);
    }

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
        tvUsdDelta = findViewById(R.id.tvUsdDelta);
        tvEurDelta = findViewById(R.id.tvEurDelta);
        graphUsd = findViewById(R.id.graphUsd);
        graphEur = findViewById(R.id.graphEur);

        applyLegendDotColors(findViewById(R.id.tvUsdLegend));
        applyLegendDotColors(findViewById(R.id.tvEurLegend));

        swipeRefresh.setOnRefreshListener(() -> triggerManualSync(true));

        graphUsd.setOnPointTapListener((record, rateType, rateValue) ->
                showAlertDialog(record.ccy, rateType, rateValue, true));
        graphEur.setOnPointTapListener((record, rateType, rateValue) ->
                showAlertDialog(record.ccy, rateType, rateValue, true));

        findViewById(R.id.btnManageAlerts).setOnClickListener(v -> showManageAlertsDialog());

        requestNotificationPermissionIfNeeded();
        if (db.getRatesForCurrency("USD").isEmpty()) {
            triggerManualSync(false);
        }
    }

    private void applyLegendDotColors(TextView legendView) {
        if (legendView == null) return;
        String text = getString(R.string.legend_buy_sale);
        SpannableString spannable = new SpannableString(text);

        int firstDot = text.indexOf('●');
        if (firstDot >= 0) {
            spannable.setSpan(new ForegroundColorSpan(COLOR_BUY_DOT),
                    firstDot, firstDot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int secondDot = text.indexOf('●', firstDot + 1);
            if (secondDot >= 0) {
                spannable.setSpan(new ForegroundColorSpan(COLOR_SALE_DOT),
                        secondDot, secondDot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        legendView.setText(spannable);
    }

    private void triggerManualSync(boolean isManual) {
        if (isManual) {
            Toast.makeText(this, R.string.toast_syncing_rates, Toast.LENGTH_SHORT).show();
        }
        swipeRefresh.setRefreshing(true);

        if (refreshTimeoutRunnable != null) {
            refreshTimeoutHandler.removeCallbacks(refreshTimeoutRunnable);
        }
        refreshTimeoutRunnable = () -> {
            if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
                swipeRefresh.setRefreshing(false);
            }
        };
        refreshTimeoutHandler.postDelayed(refreshTimeoutRunnable, 15000L);

        UUID workId = RateSyncWorker.runImmediateSync(this, isManual);
        LiveData<WorkInfo> liveData = WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId);

        liveData.observe(this, new Observer<WorkInfo>() {
            private boolean wasRunning = false;

            @Override
            public void onChanged(WorkInfo workInfo) {
                if (workInfo == null) return;
                WorkInfo.State state = workInfo.getState();

                if (state == WorkInfo.State.RUNNING) {
                    wasRunning = true;
                } else if (state.isFinished() || (wasRunning && state == WorkInfo.State.ENQUEUED)) {
                    liveData.removeObserver(this);
                    if (refreshTimeoutRunnable != null) {
                        refreshTimeoutHandler.removeCallbacks(refreshTimeoutRunnable);
                    }
                    swipeRefresh.setRefreshing(false);
                    refreshUi();
                    NumbersWidgetProvider.updateAllWidgets(MainActivity.this);
                    GraphWidgetProvider.updateAllWidgets(MainActivity.this);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncStatusBarAppearance();
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
        List<RateRecord> usdList = db.getRatesForCurrency("USD");
        List<RateRecord> eurList = db.getRatesForCurrency("EUR");

        graphUsd.setRecords(usdList);
        graphEur.setRecords(eurList);

        tvUsdDelta.setText(formatDeltaSummary(this, usdList));
        tvEurDelta.setText(formatDeltaSummary(this, eurList));
    }

    private void showAlertDialog(String defaultCcy, String defaultType, double prefillRate, boolean lockTargetFields) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 12);

        String[] ccyKeys = {"USD", "EUR"};
        String[] typeKeys = {"BUY", "SALE"};
        String[] typeLabels = {getString(R.string.type_buy), getString(R.string.type_sale)};
        String[] condKeys = {"ABOVE", "BELOW"};
        String[] condLabels = {getString(R.string.cond_above), getString(R.string.cond_below)};

        Spinner spCcy = new Spinner(this);
        Spinner spType = new Spinner(this);

        if (lockTargetFields) {
            String fixedTypeLabel = "SALE".equals(defaultType)
                    ? getString(R.string.type_sale)
                    : getString(R.string.type_buy);

            TextView tvLockedInfo = new TextView(this);
            tvLockedInfo.setText(getString(R.string.alert_fixed_target_format, defaultCcy, fixedTypeLabel));
            tvLockedInfo.setTextSize(16f);
            tvLockedInfo.setTypeface(null, Typeface.BOLD);
            tvLockedInfo.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvLockedInfo.setPadding(8, 8, 8, 20);
            layout.addView(tvLockedInfo);
        } else {
            spCcy.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ccyKeys));
            if ("EUR".equals(defaultCcy)) spCcy.setSelection(1);

            spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, typeLabels));
            if ("SALE".equals(defaultType)) spType.setSelection(1);

            layout.addView(spCcy);
            layout.addView(spType);
        }

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
                            String chosenCcy = lockTargetFields ? defaultCcy : ccyKeys[spCcy.getSelectedItemPosition()];
                            String chosenType = lockTargetFields ? defaultType : typeKeys[spType.getSelectedItemPosition()];
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
            showAlertDialog("USD", "BUY", 0, false);
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