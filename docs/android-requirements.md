# پیش‌نیازها و مسیر تکمیل نسخه Android

## ابزار توسعه

- Android Studio جدید با Android SDK Platform 35
- JDK 17
- Gradle از طریق Android Studio sync
- دستگاه یا emulator با Android 8.0 به بالا؛ برای تست قابلیت‌های جدید ترجیحاً Android 14 یا 15
- فعال بودن USB debugging برای تست روی دستگاه واقعی

## معماری فعلی

```text
Compose UI
    ↓
RecboxApp / navigation state
    ↓
SessionRepository + RecordingForegroundService
    ↓
لایه‌های بعدی: Auth API، RecordingEngine، MediaStore/Room
```

## اتصال‌های لازم برای نسخه production

### احراز هویت

`SessionRepository` فعلاً in-memory است. برای production باید:

1. API ارسال OTP و تأیید OTP اضافه شود.
2. توکن session در Encrypted DataStore نگهداری شود.
3. rate limit، انقضای OTP و مدیریت خطای شبکه پیاده‌سازی شود.

### ضبط صدا

یک `RecordingEngine` مستقل به `RecordingForegroundService` متصل شود و فایل‌ها در `MediaStore` ذخیره شوند. وضعیت‌های `Idle`, `Recording`, `Paused`, `Saved` و `Error` باید با `StateFlow` به Compose برسند.

### ویدیو و عکس

برای دوربین از CameraX استفاده شود. خاموش شدن صفحه یا قفل شدن دستگاه نباید به معنی دور زدن indicator و رضایت کاربر باشد؛ اجرای کار در پس‌زمینه باید با foreground service، اعلان دائمی و سیاست‌های Android سازگار باشد.

### ضبط تماس

این قابلیت به نسخه اندروید، سازنده دستگاه، نوع تماس و قوانین محل استفاده وابسته است و روی بسیاری از دستگاه‌ها با API عمومی قابل اتکا نیست. پیش از پیاده‌سازی باید امکان فنی دستگاه هدف و نیاز به اطلاع‌رسانی/رضایت طرفین مشخص شود.

## تست‌های ضروری

- رد کردن هر مجوز و نمایش مسیر راهنمای مجوزها
- قطع شدن سرویس، restart شدن اپ و بازگشت state
- قفل صفحه، رفتن به background و برگشت به foreground
- کمبود فضای ذخیره‌سازی و خطای I/O
- توقف اجباری سرویس و پاک‌سازی فایل ناقص
- تست روی Android 13، 14 و 15
