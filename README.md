# CAPJI Signal Pro — كابجي سيجنال برو

تطبيق أندرويد (`com.capji.signalpro`) بلغة Kotlin و Jetpack Compose و Material 3 و MVVM، بالعربية (افتراضياً) والإنجليزية.

[![Build APK](https://github.com/Colored1/CAPJI-SignalPro/actions/workflows/build.yml/badge.svg)](https://github.com/Colored1/CAPJI-SignalPro/actions/workflows/build.yml)

## التحميل
حمّل `app-debug.apk` من [آخر إصدار](https://github.com/Colored1/CAPJI-SignalPro/releases/latest). يتطلب أندرويد 8.0 أو أحدث.

## المزايا
| التبويب | ما يفعله |
|---|---|
| التسريع | نفق `VpnService` محلي لطلبات DNS فقط، اختبار سرعة الخوادم، قبل/بعد، قفل Wi-Fi |
| الرادار | RSSI وسرعة الربط والتردد والقناة والعناوين، سونار صوتي واهتزاز، مرسام حي، مقارنة الغرف |
| المحلل | رسم تداخل القنوات، توصية بأنظف قناة، اختبار التأخير والتذبذب والفقدان للراوتر والإنترنت |
| الأدوات | بطاقة QR للواي فاي (حفظ/مشاركة)، ماسح بالكاميرا، مولّد كلمات مرور، تواصل مع المطوّر |

## حدود يفرضها أندرويد (مهم)
- **التسريع** يقلّل زمن ترجمة الأسماء (DNS). لا يغيّر مسار الألعاب ولا يضمن خفض بينج اللعبة. إذا كان خادم مزودك أسرع فالتطبيق يعرض ذلك كما هو.
- **قفل Wi-Fi منخفض التأخير** يعمل فقط والشاشة مضاءة والتطبيق في الواجهة. على أندرويد 13 وأقدم يُستخدم أيضاً قفل الأداء العالي الذي يغطي الخلفية؛ وأُلغي هذا السلوك في أندرويد 14.
- **DNS الخاص** بوضع المضيف المحدد يتجاوز DNS التطبيق، ويظهر تنبيه بذلك.
- **فحص الشبكات** يحتاج الموقع الدقيق وتشغيل خدمة الموقع، ويحدّه أندرويد بأربع مرات كل دقيقتين.
- DNS المستخدم هو العادي (UDP 53) غير المشفّر.

## Firebase (اختياري)
يُبنى التطبيق ويعمل دون Firebase. لتفعيله:
1. أنشئ مشروع Firebase وأضف تطبيق أندرويد بالمعرّف `com.capji.signalpro`.
2. ضع الملف في `app/google-services.json` (مستثنى من Git؛ لإدخاله في بناء GitHub استخدم `git add -f app/google-services.json`).
3. يشترك التطبيق تلقائياً في الموضوع `all_users`.

| مفتاح Remote Config | النوع | الاستخدام |
|---|---|---|
| `latest_version_code` | رقم | يظهر التحديث إذا كان أكبر من رقم النسخة (رقم تشغيل GitHub Actions) |
| `force_update` | منطقي | تحديث إجباري |
| `update_message` | نص | رسالة التحديث |
| `update_url` | نص | يُقبل رابط Google Play فقط (سياسة المتجر) |
| `announcement_banner_enabled` | منطقي | إظهار الإعلان |
| `announcement_text` | نص | نص الإعلان |

## البناء
```bash
./gradlew testDebugUnitTest assembleDebug
```
يتطلب JDK 17 و Android SDK 36. الناتج: `app/build/outputs/apk/debug/app-debug.apk`.

## النشر في Google Play
راجع [docs/PLAY_STORE.md](docs/PLAY_STORE.md) و [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

جميع الحقوق محفوظة لـ CAPJI © 2026
