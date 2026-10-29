# MangaVerse — Android starter project

واجهة عربية RTL لتطبيق مانجا، مبنية بـ Kotlin + Jetpack Compose، مع هيكل Firebase للمصادقة والمزامنة ورفع المحتوى.

## المتطلبات
- Android Studio حديث (Koala أو أحدث)
- JDK 17
- Android SDK Platform 35
- مشروع Firebase خاص بك
- ملف `google-services.json` من Firebase Console (لا تضعه في Git العام)

## التشغيل
1. فك ضغط المشروع وافتح مجلد `MangaVerse-Android` في Android Studio.
2. أنشئ مشروع Firebase، ثم أضف تطبيق Android بالمعرّف `com.example.mangaverse`.
3. فعّل Authentication > Email/Password وفعّل Cloud Firestore وFirebase Storage.
4. نزّل `google-services.json` وضعه داخل `app/`.
5. نفّذ Sync Gradle ثم Run.

يمكن تشغيل الواجهة التجريبية دون Firebase، لكن تسجيل الدخول والمزامنة والرفع الحقيقي لن تعمل قبل إعداد Firebase.

## بناء APK
من Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s).

أو من الطرفية:
```bash
./gradlew assembleDebug
```
الناتج المعتاد: `app/build/outputs/apk/debug/app-debug.apk`.

## نقاط أمنية مهمة
- لا تضع مفاتيح Admin أو Service Account داخل تطبيق Android.
- أمان Firestore وStorage يجب أن يكون عبر Security Rules؛ قواعد البداية موجودة في `firebase/` ويجب مراجعتها قبل النشر.
- لوحة الإدارة داخل التطبيق مخفية عن المستخدم العادي، لكن هذا وحده ليس حماية: تحقق من دور admin في قواعد الخادم.
- لا ترفع إلا مانجا لديك حق نشرها أو لديك ترخيص صريح لها.
- هذا مشروع أساس قابل للتطوير وليس خدمة إنتاجية مكتملة؛ يلزم إضافة اختبار، سياسة خصوصية، معالجة أخطاء، مراقبة، وإعدادات نشر.

## الميزات الموجودة
- واجهة عربية فاتحة واتجاه RTL.
- الرئيسية، البحث، تفاصيل العمل، المكتبة، الحساب.
- نموذج قارئ بفصول تجريبية.
- تسجيل/خروج Firebase Email/Password.
- مزامنة قائمة المفضلة وسجل القراءة إلى Firestore.
- رفع صور فصول إلى Firebase Storage عبر واجهة إدارة أولية.
- بيانات مانجا تجريبية محلية لتشغيل الواجهة فوراً.
