# Privacy Policy for Currency Rates 24

**Effective Date:** November 18, 2026  
**Developer:** Ievgen "Eugene" Rudyi  
**Contact:** ievgen.rudyi.consulting@gmail.com

## 1. Overview

**Currency Rates 24** ("the App") is provided **"AS IS"**, without warranties of any kind. Your
privacy is fully respected: the App does **not** collect, transmit, store, or share any Personally
Identifiable Information (PII) or user tracking data.

## 2. Information Collection and Use

* **No Personal Data Collected:** The App does not require registration, login, accounts, names,
  email addresses, phone numbers, location data, device identifiers, or contacts.
* **No Analytics or Advertising SDKs:** The App contains no third-party advertising networks,
  behavioral trackers, or telemetry/analytics libraries.
* **Local-Only Storage:** Exchange rate history, configured price alerts, and application
  preferences (such as refresh interval, rate type, history retention period, and theme mode) are
  stored exclusively on your device inside a local SQLite database. This data never leaves your
  device and is permanently removed when you clear the App's storage or uninstall the App.

## 3. Network Requests & Third-Party Services

* **PrivatBank Public API:** To display current USD/UAH and EUR/UAH exchange rates, the App performs
  direct `GET` requests over HTTPS to PrivatBank's public exchange rate API
  (`https://api.privatbank.ua/p24api/pubinfo`). No user-identifying parameters are attached to these
  requests.
* **Third-Party Infrastructure:** Standard network metadata (such as your IP address) may be
  processed at the transport layer by your internet service provider or PrivatBank's web servers
  strictly to deliver the requested exchange rate response.

## 4. Permissions

* **Internet / Network State:** Used solely to check network availability and download public
  exchange rates.
* **Notifications (`POST_NOTIFICATIONS`):** Optional permission used exclusively to display local
  alerts on your device when a currency rate crosses a threshold you configured.

## 5. Disclaimer of Warranty & Financial Notice

The App is an independent, unofficial utility and is not affiliated with, endorsed by, or sponsored
by PrivatBank. The App and all exchange rate data are provided **"AS IS"** and **"AS AVAILABLE"**
for informational purposes only, without warranty of accuracy, completeness, or uninterrupted
availability, and do not constitute financial or investment advice.

## 6. Changes to This Policy

Any updates to this Privacy Policy will be posted in this document with an updated effective date.