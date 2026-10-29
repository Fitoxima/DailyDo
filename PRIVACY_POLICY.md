# Privacy Policy for DailyDo

**Effective Date:** October 29, 2026

DailyDo ("we", "our", or "us") respects your privacy. This Privacy Policy describes how your information is handled when you use the DailyDo mobile application (the "App").

## 1. Information Collection and Use

DailyDo is designed as a privacy-first, local-only to-do application.

- **No Personal Data Collection:** We do not collect, store, transmit, or share any personally identifiable information (PII) or user data with external servers, third parties, or analytics services.
- **Local Data Storage:** All your tasks, categories, due dates, reminder settings, and preferences created within the App are stored locally on your device using Android's Room Database and DataStore Preferences.
- **No External Servers:** The App does not operate or communicate with any remote servers or cloud backend services.

## 2. Permissions Required

The App requests the following device permissions solely to provide core functionality:

- **Notifications (`android.permission.POST_NOTIFICATIONS`):** Used to display scheduled reminder notifications for your tasks on Android 13+.
- **Exact Alarms & Boot Completion (`RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`):** Used by `AlarmManager` and `BootReceiver` to ensure task reminders fire accurately at scheduled times and survive device reboots.
- **Battery Optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`):** Optionally requested to prevent domestic device OS battery managers from killing background alarms.

None of these permissions are used to collect or transmit data from your device.

## 3. Data Security

Because all your data remains strictly on your local device, data security relies on your device's built-in Android security features. Uninstalling the App will permanently delete all locally stored task data and preferences.

## 4. Third-Party Services

DailyDo does not integrate any third-party SDKs, ad networks, tracking scripts, or analytics tools.

## 5. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be reflected in the App repository and updated effective date.

## 6. Contact Us

If you have any questions or feedback regarding this Privacy Policy, please open an issue in the project repository on GitHub.
