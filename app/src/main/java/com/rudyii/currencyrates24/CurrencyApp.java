package com.rudyii.currencyrates24;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.rudyii.currencyrates24.db.CurrencyDbHelper;
import com.rudyii.currencyrates24.worker.RateSyncWorker;

public class CurrencyApp extends Application {
    public static void applyThemeMode(String mode) {
        switch (mode) {
            case "LIGHT":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "DARK":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CurrencyDbHelper db = CurrencyDbHelper.getInstance(this);
        applyThemeMode(db.getThemeMode());
        RateSyncWorker.scheduleWork(this, db.getCheckPeriodMinutes());
    }
}