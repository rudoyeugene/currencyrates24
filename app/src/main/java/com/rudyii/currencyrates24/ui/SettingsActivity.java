package com.rudyii.currencyrates24.ui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.rudyii.currencyrates24.CurrencyApp;
import com.rudyii.currencyrates24.R;
import com.rudyii.currencyrates24.db.CurrencyDbHelper;
import com.rudyii.currencyrates24.widget.GraphWidgetProvider;
import com.rudyii.currencyrates24.widget.NumbersWidgetProvider;
import com.rudyii.currencyrates24.worker.RateSyncWorker;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final int[] INTERVAL_MINUTES = {15, 30, 60, 180, 360, 480, 720};
    private static final String[] THEME_KEYS = {"SYSTEM", "LIGHT", "DARK"};

    private CurrencyDbHelper db;
    private Spinner spRefreshInterval;
    private MaterialCheckBox cbPublicRates;
    private EditText etHistoryDays;
    private Spinner spThemeMode;
    private TextView tvChannelStatus;

    private int savedPeriodMinutes;
    private boolean savedPublicRates;
    private int savedHistoryDays;
    private String savedThemeMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        syncStatusBarAppearance();

        View root = findViewById(R.id.settingsRoot);
        View scroll = findViewById(R.id.settingsScroll);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbar.setPadding(insets.left, insets.top, insets.right, 0);
            scroll.setPadding(insets.left, 0, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        db = CurrencyDbHelper.getInstance(this);

        TextView tvPeriodLabel = findViewById(R.id.tvPeriodLabel);
        TextView tvPeriodScheduleHint = findViewById(R.id.tvPeriodScheduleHint);
        spRefreshInterval = findViewById(R.id.spRefreshInterval);
        cbPublicRates = findViewById(R.id.cbPublicRates);
        etHistoryDays = findViewById(R.id.etHistoryDays);
        spThemeMode = findViewById(R.id.spThemeMode);
        tvChannelStatus = findViewById(R.id.tvChannelStatus);
        TextView txtCopyrights = findViewById(R.id.txtCopyrights);

        try {
            txtCopyrights.setText(getString(R.string.txt_copyrights,
                    LocalDate.now().getYear(),
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (PackageManager.NameNotFoundException e) {
            txtCopyrights.setText(getString(R.string.txt_copyrights,
                    LocalDate.now().getYear(),
                    ""));
        }

        String[] intervalLabels = getResources().getStringArray(R.array.refresh_interval_labels);
        spRefreshInterval.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, intervalLabels));

        savedPeriodMinutes = db.getCheckPeriodMinutes();
        int selectedIdx = 2; // Default: 60 minutes (1 hour)
        for (int i = 0; i < INTERVAL_MINUTES.length; i++) {
            if (INTERVAL_MINUTES[i] == savedPeriodMinutes) {
                selectedIdx = i;
                break;
            }
        }
        spRefreshInterval.setSelection(selectedIdx);
        updateSchedulePreview(tvPeriodLabel, tvPeriodScheduleHint, INTERVAL_MINUTES[selectedIdx]);

        spRefreshInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSchedulePreview(tvPeriodLabel, tvPeriodScheduleHint, INTERVAL_MINUTES[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        savedPublicRates = db.isPublicRates();
        cbPublicRates.setChecked(savedPublicRates);

        savedHistoryDays = db.getHistoryDays();
        etHistoryDays.setText(String.valueOf(savedHistoryDays));

        findViewById(R.id.btnSystemNotificationSettings).setOnClickListener(v -> {
            RateSyncWorker.ensureNotificationChannel(this);
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, RateSyncWorker.CHANNEL_ID);
            startActivity(intent);
        });

        String[] themeLabels = getResources().getStringArray(R.array.theme_labels);
        spThemeMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, themeLabels));
        savedThemeMode = db.getThemeMode();
        for (int i = 0; i < THEME_KEYS.length; i++) {
            if (THEME_KEYS[i].equals(savedThemeMode)) {
                spThemeMode.setSelection(i);
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistSettingsIfChanged();
    }

    private void persistSettingsIfChanged() {
        if (db == null || spRefreshInterval == null) return;

        int chosenMinutes = INTERVAL_MINUTES[spRefreshInterval.getSelectedItemPosition()];
        boolean chosenPublicRates = cbPublicRates.isChecked();
        int chosenHistoryDays = savedHistoryDays;
        try {
            String rawDays = etHistoryDays.getText().toString().trim();
            if (!rawDays.isEmpty()) {
                chosenHistoryDays = Math.max(1, Integer.parseInt(rawDays));
            }
        } catch (NumberFormatException ignored) {
        }
        String chosenTheme = THEME_KEYS[spThemeMode.getSelectedItemPosition()];

        boolean periodChanged = chosenMinutes != savedPeriodMinutes;
        boolean publicRatesChanged = chosenPublicRates != savedPublicRates;
        boolean historyChanged = chosenHistoryDays != savedHistoryDays;
        boolean themeChanged = !chosenTheme.equals(savedThemeMode);

        if (!periodChanged && !publicRatesChanged && !historyChanged && !themeChanged) {
            return;
        }

        db.saveSetting("check_period_minutes", String.valueOf(chosenMinutes));
        db.saveSetting("public_rates", String.valueOf(chosenPublicRates));
        db.saveSetting("history_days", String.valueOf(chosenHistoryDays));
        db.saveSetting("theme_mode", chosenTheme);

        savedPeriodMinutes = chosenMinutes;
        savedPublicRates = chosenPublicRates;
        savedHistoryDays = chosenHistoryDays;
        savedThemeMode = chosenTheme;

        if (publicRatesChanged) {
            db.clearRates();
            RateSyncWorker.runImmediateSync(this);
        } else if (historyChanged) {
            db.pruneOldRates(chosenHistoryDays);
        }

        if (periodChanged) {
            RateSyncWorker.scheduleWork(this, chosenMinutes);
        }

        if (themeChanged) {
            CurrencyApp.applyThemeMode(chosenTheme);
        }

        NumbersWidgetProvider.updateAllWidgets(this);
        GraphWidgetProvider.updateAllWidgets(this);

        Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void syncStatusBarAppearance() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(isNight);
    }

    private void updateSchedulePreview(TextView tvLabel, TextView tvHint, int periodMinutes) {
        String shortName;
        if (periodMinutes < 60) {
            shortName = getString(R.string.interval_short_min, periodMinutes);
        } else if (periodMinutes / 60 == 1) {
            shortName = getString(R.string.interval_short_hour_one);
        } else {
            shortName = getString(R.string.interval_short_hours, periodMinutes / 60);
        }
        tvLabel.setText(getString(R.string.refresh_interval_format, shortName));

        long nextRunMillis = RateSyncWorker.computeNextAlignedRunMillis(System.currentTimeMillis(), periodMinutes);
        String formattedTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(nextRunMillis));
        tvHint.setText(getString(R.string.next_sync_hint_format, formattedTime));
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncStatusBarAppearance();
        updateNotificationChannelSummary();
    }

    private void updateNotificationChannelSummary() {
        RateSyncWorker.ensureNotificationChannel(this);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel channel = nm.getNotificationChannel(RateSyncWorker.CHANNEL_ID);
        if (channel == null) return;

        String vibroState = channel.shouldVibrate()
                ? getString(R.string.state_on)
                : getString(R.string.state_off);
        Uri soundUri = channel.getSound();
        String soundName;
        if (soundUri == null) {
            soundName = getString(R.string.sound_silent);
        } else {
            Ringtone ringtone = RingtoneManager.getRingtone(this, soundUri);
            soundName = ringtone != null ? ringtone.getTitle(this) : getString(R.string.sound_default);
        }

        tvChannelStatus.setText(getString(R.string.channel_status_format, soundName, vibroState));
    }
}