package com.rudyii.currencyrates24.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.rudyii.currencyrates24.db.CurrencyDbHelper;

public class WidgetConfigureActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        CurrencyDbHelper db = CurrencyDbHelper.getInstance(this);
        String savedCcy = db.getSetting("widget_ccy_" + appWidgetId, "USD");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        TextView tvCcy = new TextView(this);
        tvCcy.setText("Tracked Currency:");
        layout.addView(tvCcy);

        String[] ccyKeys = {"USD", "EUR"};
        String[] ccyLabels = {"USD / UAH", "EUR / UAH"};
        Spinner spCcy = new Spinner(this);
        spCcy.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ccyLabels));
        if ("EUR".equals(savedCcy)) {
            spCcy.setSelection(1);
        }
        layout.addView(spCcy);

        Button btnConfirm = new Button(this);
        btnConfirm.setText("Save Widget");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 32;
        btnConfirm.setLayoutParams(params);

        btnConfirm.setOnClickListener(v -> {
            String chosenCcy = ccyKeys[spCcy.getSelectedItemPosition()];
            db.saveSetting("widget_ccy_" + appWidgetId, chosenCcy);

            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(appWidgetId);
            if (info != null && GraphWidgetProvider.class.getName().equals(info.provider.getClassName())) {
                GraphWidgetProvider.updateSingleWidget(this, manager, appWidgetId);
            } else {
                NumbersWidgetProvider.updateSingleWidget(this, manager, appWidgetId);
            }

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
        layout.addView(btnConfirm);

        setContentView(layout);
    }
}