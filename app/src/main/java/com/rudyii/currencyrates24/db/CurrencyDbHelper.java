package com.rudyii.currencyrates24.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.rudyii.currencyrates24.model.PriceAlert;
import com.rudyii.currencyrates24.model.RateRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CurrencyDbHelper extends SQLiteOpenHelper {

    public static final String TABLE_RATES = "rates";
    public static final String TABLE_ALERTS = "alerts";
    public static final String TABLE_SETTINGS = "settings";

    public static final List<String> SUPPORTED_CURRENCIES = Collections.unmodifiableList(
            Arrays.asList("USD", "EUR", "GBP", "CHF", "PLN", "CAD", "SEK", "XAU")
    );

    private static final String DEFAULT_TRACKED_CURRENCIES = "USD,EUR";
    private static final String DB_NAME = "currency_rates.db";
    private static final int DB_VERSION = 1;
    private static CurrencyDbHelper instance;

    private CurrencyDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public static synchronized CurrencyDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new CurrencyDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_RATES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ccy TEXT NOT NULL, " +
                "base_ccy TEXT NOT NULL, " +
                "buy REAL NOT NULL, " +
                "sale REAL NOT NULL, " +
                "timestamp INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_rates_ccy_time ON " + TABLE_RATES + "(ccy, timestamp)");

        db.execSQL("CREATE TABLE " + TABLE_ALERTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ccy TEXT NOT NULL, " +
                "type TEXT NOT NULL, " +
                "condition TEXT NOT NULL, " +
                "target_rate REAL NOT NULL, " +
                "active INTEGER NOT NULL DEFAULT 1)");

        db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " (" +
                "setting_key TEXT PRIMARY KEY, " +
                "setting_value TEXT NOT NULL)");

        setSettingInternal(db, "check_period_minutes", "60");
        setSettingInternal(db, "history_days", "28");
        setSettingInternal(db, "theme_mode", "SYSTEM"); // SYSTEM, LIGHT, DARK
        setSettingInternal(db, "public_rates", "true");
        setSettingInternal(db, "tracked_currencies", DEFAULT_TRACKED_CURRENCIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RATES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALERTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
        onCreate(db);
    }

    private void setSettingInternal(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("setting_key", key);
        cv.put("setting_value", value);
        db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void saveSetting(String key, String value) {
        setSettingInternal(getWritableDatabase(), key, value);
    }

    public String getSetting(String key, String defaultValue) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_SETTINGS, new String[]{"setting_value"},
                "setting_key = ?", new String[]{key}, null, null, null)) {
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        }
        return defaultValue;
    }

    public int getCheckPeriodMinutes() {
        return Integer.parseInt(getSetting("check_period_minutes", "60"));
    }

    public int getHistoryDays() {
        return Integer.parseInt(getSetting("history_days", "28"));
    }

    public String getThemeMode() {
        return getSetting("theme_mode", "SYSTEM");
    }

    public boolean isPublicRates() {
        return Boolean.parseBoolean(getSetting("public_rates", "true"));
    }

    public List<String> getTrackedCurrencies() {
        String raw = getSetting("tracked_currencies", DEFAULT_TRACKED_CURRENCIES);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String token : raw.split(",")) {
                String code = token.trim().toUpperCase(Locale.US);
                if (SUPPORTED_CURRENCIES.contains(code)) {
                    result.add(code);
                }
            }
        }
        if (result.isEmpty()) {
            result.add("USD");
            result.add("EUR");
        }
        return new ArrayList<>(result);
    }

    public void setTrackedCurrencies(List<String> currencies) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        if (currencies != null) {
            for (String code : currencies) {
                if (code != null) {
                    String upper = code.trim().toUpperCase(Locale.US);
                    if (SUPPORTED_CURRENCIES.contains(upper)) {
                        cleaned.add(upper);
                    }
                }
            }
        }
        if (cleaned.isEmpty()) {
            cleaned.add("USD");
            cleaned.add("EUR");
        }
        StringBuilder sb = new StringBuilder();
        for (String c : cleaned) {
            if (sb.length() > 0) sb.append(',');
            sb.append(c);
        }
        saveSetting("tracked_currencies", sb.toString());
    }

    public String getNextTrackedCurrency(String current) {
        List<String> tracked = getTrackedCurrencies();
        if (tracked.isEmpty()) return "USD";
        int idx = tracked.indexOf(current);
        if (idx < 0 || idx + 1 >= tracked.size()) {
            return tracked.get(0);
        }
        return tracked.get(idx + 1);
    }

    public void clearRates() {
        getWritableDatabase().delete(TABLE_RATES, null, null);
    }

    public Set<String> getCompleteStoredDates(Collection<String> requiredCurrencies) {
        Set<String> completeDates = new HashSet<>();
        if (requiredCurrencies == null || requiredCurrencies.isEmpty()) {
            return completeDates;
        }
        Set<String> requiredSet = new HashSet<>(requiredCurrencies);
        Map<String, Set<String>> dateToCurrencies = new HashMap<>();
        SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_RATES, new String[]{"ccy", "timestamp"},
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                String ccy = c.getString(0);
                if (!requiredSet.contains(ccy)) continue;
                long ts = c.getLong(1);
                String dayKey = dayKeyFormat.format(new Date(ts));
                dateToCurrencies.computeIfAbsent(dayKey, k -> new HashSet<>()).add(ccy);
            }
        }

        for (Map.Entry<String, Set<String>> entry : dateToCurrencies.entrySet()) {
            if (entry.getValue().containsAll(requiredSet)) {
                completeDates.add(entry.getKey());
            }
        }
        return completeDates;
    }

    public RateRecord getLatestRate(String ccy) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_RATES, null, "ccy = ?", new String[]{ccy},
                null, null, "timestamp DESC, id DESC", "1")) {
            if (c.moveToFirst()) {
                return new RateRecord(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("ccy")),
                        c.getString(c.getColumnIndexOrThrow("base_ccy")),
                        c.getDouble(c.getColumnIndexOrThrow("buy")),
                        c.getDouble(c.getColumnIndexOrThrow("sale")),
                        c.getLong(c.getColumnIndexOrThrow("timestamp"))
                );
            }
        }
        return null;
    }

    public void insertRate(String ccy, String baseCcy, double buy, double sale, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("ccy", ccy);
        cv.put("base_ccy", baseCcy);
        cv.put("buy", buy);
        cv.put("sale", sale);
        cv.put("timestamp", timestamp);
        db.insert(TABLE_RATES, null, cv);
    }

    public void pruneOldRates(int historyDays) {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(historyDays);
        getWritableDatabase().delete(TABLE_RATES, "timestamp < ?", new String[]{String.valueOf(cutoff)});
    }

    public List<RateRecord> getRatesForCurrency(String ccy) {
        List<RateRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_RATES, null, "ccy = ?", new String[]{ccy},
                null, null, "timestamp ASC, id ASC")) {
            while (c.moveToNext()) {
                list.add(new RateRecord(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("ccy")),
                        c.getString(c.getColumnIndexOrThrow("base_ccy")),
                        c.getDouble(c.getColumnIndexOrThrow("buy")),
                        c.getDouble(c.getColumnIndexOrThrow("sale")),
                        c.getLong(c.getColumnIndexOrThrow("timestamp"))
                ));
            }
        }
        return list;
    }

    public long addAlert(String ccy, String type, String condition, double targetRate) {
        ContentValues cv = new ContentValues();
        cv.put("ccy", ccy);
        cv.put("type", type);
        cv.put("condition", condition);
        cv.put("target_rate", targetRate);
        cv.put("active", 1);
        return getWritableDatabase().insert(TABLE_ALERTS, null, cv);
    }

    public List<PriceAlert> getActiveAlerts() {
        List<PriceAlert> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_ALERTS, null, "active = 1", null, null, null, "id DESC")) {
            while (c.moveToNext()) {
                list.add(new PriceAlert(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("ccy")),
                        c.getString(c.getColumnIndexOrThrow("type")),
                        c.getString(c.getColumnIndexOrThrow("condition")),
                        c.getDouble(c.getColumnIndexOrThrow("target_rate")),
                        c.getInt(c.getColumnIndexOrThrow("active")) == 1
                ));
            }
        }
        return list;
    }

    public void deleteAlert(long id) {
        getWritableDatabase().delete(TABLE_ALERTS, "id = ?", new String[]{String.valueOf(id)});
    }
}