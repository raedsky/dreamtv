# My TV — مشغّل IPTV

تطبيق أندرويد يعمل على الجوال، شاشات Android TV، وأجهزة السمارت بوكس (يدعم الريموت بالكامل).

## الميزات
- الدخول بـ **Xtream Codes** (سيرفر + اسم مستخدم + كلمة مرور) أو **رابط M3U**
- أقسام: البث المباشر، الأفلام، المسلسلات (مع المواسم والحلقات)
- **إضافة قروب في الأقسام الثلاثة** (مباشر / أفلام / مسلسلات):
  - "＋ إضافة قروب" في قائمة التصنيفات
  - ضغط مطوّل على أي عنصر (أو زر **Menu** في الريموت) ← إضافة إلى قروب / المفضلة
  - ضغط مطوّل على قروب ← إعادة تسمية / حذف
  - ضغط مطوّل على تصنيف من السيرفر ← إضافة كل عناصره لقروب دفعة واحدة
- المفضلة لكل قسم
- بحث داخل القسم
- مشغّل ExoPlayer يدعم TS و HLS و MP4 و MKV، وتقليب القنوات بالأسهم ↑↓ في المباشر
- واجهة عربية وإنجليزية (حسب لغة الجهاز)

## طريقة البناء

### الطريقة 1: Android Studio (الأسهل)
1. ثبّت [Android Studio](https://developer.android.com/studio) (مجاني).
2. File → Open ← اختر مجلد `MyTV`.
3. انتظر حتى ينتهي Gradle من التحميل (أول مرة تأخذ عدة دقائق).
4. Build → Build App Bundle(s) / APK(s) → **Build APK(s)**.
5. الملف يكون في `app/build/outputs/apk/debug/app-debug.apk`.

### الطريقة 2: GitHub (بدون تثبيت أي شيء)
1. أنشئ مستودع جديد على GitHub وارفع محتويات المجلد.
2. اذهب إلى تبويب **Actions** — البناء يبدأ تلقائياً.
3. بعد انتهائه (~5 دقائق) افتح البناء ونزّل **MyTV-apk** من قسم Artifacts.

## التخصيص السريع
| ماذا تغيّر | أين |
|---|---|
| اسم التطبيق | `app/src/main/res/values/strings.xml` و `values-ar/strings.xml` ← `app_name` |
| اسم الحزمة | `app/build.gradle.kts` ← `applicationId` |
| الألوان | `app/src/main/res/values/colors.xml` |
| الأيقونة | `app/src/main/res/drawable/ic_launcher.xml` و `banner.xml` (بانر الشاشات) |

> ملاحظة: نسخة release موقّعة بمفتاح debug لتسهيل التثبيت. قبل النشر على متجر أنشئ مفتاح توقيع خاص.

## بنية الكود
```
data/
  XtreamClient.kt   الاتصال بسيرفر Xtream
  M3uParser.kt      قراءة قوائم M3U
  Repository.kt     تحميل وتخزين البيانات مؤقتاً
  GroupStore.kt     حفظ القروبات الخاصة والمفضلة
ui/
  LoginActivity     شاشة الدخول
  HomeActivity      الشاشة الرئيسية
  BrowseActivity    شاشة القسم (التصنيفات + القروبات + العناصر)
  SeriesDetailActivity  حلقات المسلسل
  PlayerActivity    المشغّل
```
