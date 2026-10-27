# Javadsolvpn 🛡️🌐

Javadsolvpn is a complete, native mobile VPN application for Android featuring autonomous public configuration discovery, private encrypted local storage, intelligent connection selection, and broad protocol support (VLESS Reality, VMess, Trojan, Shadowsocks, and OpenVPN).

---

## 🚀 راهنمای بیلد خودکار روی گیت‌هاب (GitHub Actions CI/CD)

این مخزن به فایل ورک‌فلو خودکار `.github/workflows/build-and-release.yml` مجهز شده است. با آپلود این پروژه در گیت‌هاب، فایل‌های خروجی زیر به صورت کاملاً اتوماتیک ساخته و تحویل داده می‌شوند:

1. **فایل نصبی اندروید (`.apk`):** قابل نصب مستقیم روی تمام گوشی‌ها و تبلت‌های اندروید.
2. **بسته نرم‌افزاری اندروید (`.aab`):** جهت انتشار در گوگل پلی (Google Play Store).

---

### مراحل گام‌به‌گام ارسال به گیت‌هاب و دریافت فایل نصبی:

#### روش اول: از طریق دکمه‌های استودیو (ساده‌ترین روش)
1. در منوی بالای همین صفحه (گوشه بالا سمت راست)، بر روی دکمه **Settings** یا آیکون اشتراک‌گذاری/سه نقطه کلیک کنید.
2. گزینه **Push to GitHub** یا **Export to GitHub** را انتخاب کنید.
3. حساب گیت‌هاب خود را متصل کرده و یک مخزن (Repository) جدید مثلاً به نام `javadsolvpn` بسازید.
4. به محض ارسال کدها، تب **Actions** در گیت‌هاب فعال شده و بیلد شروع می‌شود!
5. پس از ۲ الی ۳ دقیقه، در بخش **Artifacts** می‌توانید فایل `javadsolvpn-android-apk` را دانلود کنید.

#### روش دوم: از طریق ترمینال Git
```bash
git remote add origin https://github.com/YOUR_USERNAME/javadsolvpn.git
git branch -M main
git push -u origin main
```

---

### 📱 در مورد نسخه iOS (آیفون و آیپد)
سیستم‌عامل iOS برای اپلیکیشن‌های VPN شرایط اختصاصی زیر را الزامی می‌داند:
1. **مجوز رسمی شبکه از اپل (Network Extension Entitlement):**
   - برای ایجاد تونل واقعی وی‌پی‌ان روی آیفون، حساب توسعه‌دهنده پولی اپل (Apple Developer Program - سالانه ۹۹ دلار) با فعال‌سازی پرمیشن `com.apple.developer.networking.networkextension` لازم است.
2. **رانر سیستم‌عامل مک (macOS Runner):**
   - برای کامپایل پکیج `.ipa`، گیت‌هاب اکشنز نیاز به رانر `macos-14` و نرم‌افزار Xcode دارد.
3. برای iOS یک گردش‌کار جداگانه در `.github/workflows/ios-client.yml` اضافه شده است. این گردش‌کار کد متن‌باز sing-box-for-apple و موتور Libbox را روی macOS/Xcode می‌سازد و یک IPA بدون امضا تولید می‌کند. این فایل تا زمانی که با حساب توسعه‌دهنده اپل، مجوز Network Extension و پروفایل مناسب امضا نشود، نصب مستقیم نمی‌شود. نسخه iOS در این مرحله از کلاینت متن‌باز sing-box با نام‌گذاری Javadsolvpn و دکمهٔ دریافت/کپی کانفیگ‌های مخزن استفاده می‌کند؛ این نسخه هنوز در App Store منتشر نشده است.

### مخزن کانفیگ‌های عمومی و رتبه‌بندی‌شده

- پوشهٔ `config-repository/` شامل `latest.json`، `all.txt`، `top20.txt` و جدول `top20.md` است.
- گردش‌کار `.github/workflows/hourly-config-repository.yml` هر ساعت منابع عمومی را می‌خواند، کانفیگ‌ها را با sing-box اجرا می‌کند و فقط در صورت موفقیت درخواست HTTPS از داخل تونل آن‌ها را منتشر می‌کند.
- `top20.txt` برای کپی سریع ۲۰ کانفیگ برتر است. رتبه‌بندی بر اساس زمان پاسخ موفق HTTPS از داخل تونل است.
- اپ اندروید سهمیهٔ دریافت حداکثر ۱۱۰ لینک در روز دارد؛ فهرست فعال محلی همچنان سقف ۱۲ سرور دارد.
- کانفیگ عمومی ممکن است بعد از آزمون از کار بیفتد؛ رتبه‌بندی نشان‌دهندهٔ زمان آخرین آزمون است و تضمین دائمی اتصال نیست.
- کانفیگ خصوصی یا اطلاعات ورود شخصی را به مخزن عمومی ارسال نکنید.
