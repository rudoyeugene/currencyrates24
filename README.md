# Currency Rates 24

A lightweight, native Android application and home screen widget suite for tracking real-time
**USD/UAH** and **EUR/UAH** exchange rates from PrivatBank, complete with interactive historical
trend charts and customizable price alerts.

## Features

* **Live PrivatBank Exchange Rates**: Fetches current Buy and Sale rates for USD and EUR with
  support for both **Public cash rates** (`coursid=5`) and **Commercial non-cash rates**
  (`coursid=11`).
* **Interactive Trend Graphs**: View rate history across configurable retention windows, inspect
  price deltas, and **tap any data point** on the chart to pre-fill a price alert.
* **Custom Price Alerts**: Get high-priority push notifications the moment Buy or Sale rates cross
  above or below your target threshold.
* **Home Screen Widgets**:
    * **Numbers Widget**: Compact view displaying live Buy/Sell rates and quick currency toggling.
    * **7-Day Graph Widget**: At-a-glance visual trend chart directly on your home screen.
* **Clock-Aligned Background Sync**: Powered by `WorkManager` with configurable intervals (15 min to
  12 hours), battery-aware constraints, and pull-to-refresh support.
* **Multi-Language & Theme Support**: Localized in English, Ukrainian, and Russian with Light, Dark,
  and System theme modes.

## Tech Stack

* **Language**: Java 21
* **UI**: Material Design 3, Custom `Canvas` Graph View, `SwipeRefreshLayout`, App Widgets
* **Storage**: Local SQLite database (`SQLiteOpenHelper`)
* **Background Processing**: AndroidX `WorkManager`
* **API**: PrivatBank Public Exchange API

## Disclaimer

Currency Rates 24 is an independent, unofficial utility application and is not affiliated with,
endorsed by, or sponsored by PrivatBank. Exchange rates are retrieved from PrivatBank's public API
for informational purposes only and do not constitute financial or investment advice.