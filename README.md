# MangaVerse — Android starter project

واجهة عربية RTL لتطبيق مانجا، مبنية بـ Kotlin + Jetpack Compose، مع هيكل Firebase للمصادقة والمزامنة ورفع المحتوى.

## المتطلبات
- Android Studio حديث (Koala أو أحدث)
- JDK 17
- Android SDK Platform 35
- مشروع Firebase خاص بك
- ملف `google-services.json` من Firebase Console (لا تضعه في Git العام؛ ضعه في `app/` قبل اختبار المصادقة)

## التشغيل
1. فك ضغط المشروع وافتح مجلد `MangaVerse-Android` في Android Studio.
2. أنشئ مشروع Firebase، ثم أضف تطبيق Android بالمعرّف `com.example.mangaverse`.
3. فعّل Authentication > Email/Password وفعّل Cloud Firestore وFirebase Storage.
4. نزّل `google-services.json` وضعه داخل `app/`.
5. نفّذ Sync Gradle ثم Run. أزلنا الاعتماد الإجباري على Google Services Gradle plugin حتى يفتح المشروع قبل إضافة إعداد Firebase؛ ميزات Firebase تحتاج ملف الإعداد قبل استخدامها.

يمكن تشغيل الواجهة التجريبية دون Firebase، لكن تسجيل الدخول والمزامنة والرفع الحقيقي لن تعمل قبل إعداد Firebase.

## بناء APK
من Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s).

أو من الطرفية إذا كان Gradle مثبتاً:
```bash
gradle assembleDebug
```
ملف إعداد توزيع Gradle 8.9 موجود في `gradle/wrapper/gradle-wrapper.properties`، لكن ملفات تشغيل الـ Wrapper الثنائية/السكربتات قد تحتاج توليدها من Android Studio أو عبر `gradle wrapper`.

### بناء APK سحابياً (بدون كمبيوتر محلي)
يوجد GitHub Actions workflow في `.github/workflows/android-build.yml`. ارفع المشروع إلى مستودع GitHub، ثم افتح تبويب **Actions** وشغّل **Android build**. إذا نجح البناء، نزّل artifact باسم `MangaVerse-debug-apk` وثبّت ملف APK على هاتفك. هذا البناء يحتاج اتصالاً بالإنترنت في GitHub، ولم يتم التحقق من نجاحه بعد من هذه البيئة.

الناتج المحلي المعتاد: `app/build/outputs/apk/debug/app-debug.apk`.

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


## آخر تحسينات
- تم حفظ قائمة المفضلة محلياً على الجهاز، لذلك تبقى بعد إغلاق التطبيق وإعادة فتحه.
- تمت إضافة إعداد Android SDK صريح إلى GitHub Actions قبل بناء APK.
- نموذج اقتراح المانجا أصبح يرسل إلى `mangaSubmissions` بدل مجموعة المحتوى المنشور، وقواعد Firestore تسمح للمستخدم بإرسال اقتراحه فقط مع إبقاء النشر للمشرف الموثّق.
- مزامنة المفضلة عبر Firebase تعمل فقط عند إعداد Firebase وتسجيل الدخول؛ الحفظ المحلي لا يحتاج إعداداً سحابياً.

## بناء APK من GitHub باستخدام الجوال
1. أنشئ مستودعاً جديداً على GitHub وارفع محتويات مجلد `MangaVerse-Android` إليه.
2. افتح تبويب **Actions** وشغّل **Android build** من زر **Run workflow**.
3. بعد نجاح التشغيل، افتح نتيجة التشغيل ونزّل artifact باسم `MangaVerse-debug-apk` ثم فك ضغطه للحصول على `app-debug.apk`.
4. هذا APK تجريبي للتثبيت والاختبار؛ لا تنشره قبل ربط Firebase ومراجعة قواعد الأمان ومصادر المحتوى المرخّصة.

## تحديث تطوير: متابعة القراءة والبحث في الرئيسية
- تظهر بطاقة **كمّل قراءتك** في الرئيسية إذا كان آخر عنوان مقروء موجوداً ضمن الكتالوج الحالي.
- الضغط على البطاقة يفتح القارئ مباشرة عند آخر فصل محفوظ محلياً.
- بحث الرئيسية يفلتر العناوين حسب الاسم والتصنيف والوصف.
- لم يتم تأكيد بناء APK في بيئة التحرير الحالية لعدم توفر Gradle محلياً. يمكن تشغيل سير عمل GitHub Actions من تبويب Actions بعد رفع المشروع إلى مستودع.


## إصلاح قبل أول تجربة
- تم توصيل زر تسجيل الخروج في شاشة الحساب إلى FirebaseAuth، مع تحديث واجهة الحساب محلياً.
- سير عمل GitHub Actions يثبت الآن Android SDK Platform 35 وBuild Tools 35.0.0 صراحةً قبل البناء.
- لا يوجد APK جاهز داخل هذا الأرشيف؛ يجب تشغيل GitHub Actions بعد رفع الملفات إلى مستودع GitHub لتنفيذ البناء وتنزيل الناتج إذا نجح.
