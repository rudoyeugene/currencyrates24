package com.rudyii.currencyrates24.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.rudyii.currencyrates24.model.PriceAlert;
import com.rudyii.currencyrates24.model.RateRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CurrencyDbHelper extends SQLiteOpenHelper {

    public static final String TABLE_RATES = "rates";
    public static final String TABLE_ALERTS = "alerts";
    public static final String TABLE_SETTINGS = "settings";
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

    public void clearRates() {
        getWritableDatabase().delete(TABLE_RATES, null, null);
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
                null, null, "timestamp ASC")) {
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