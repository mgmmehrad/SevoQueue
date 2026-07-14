# غیرفعال کردن پیام‌های اخطار مربوط به پکیج‌های خارجی برای جلوگیری از توقف بیلد
-dontwarn
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# خاموش کردن بهینه‌سازی و حذف کدهای اضافه برای پایداری پلاگین
-dontshrink
-dontoptimize

# تغییر نام پکیج‌ها و کلاس‌های مربوط به پروژه شما
-repackageclasses 'o.m.s'
-allowaccessmodification

# -----------------------------------------------------------------
# !!! بسیار مهم !!! - دست‌نخورده باقی ماندن کتابخانه Configurate و وابستگی‌های آن
-keep class org.spongepowered.configurate.** { *; }
-keep class io.leangen.geantyref.** { *; }

# نگهداری مقادیر و متدهای پیش‌فرض Enumها در کتابخانه تا جاوا در زمان Reflection ارور ندهد
-keepclassmembers enum org.spongepowered.configurate.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------
# حفظ کلاس اصلی شما و متدهای آن تا Velocity بتواند پلاگین را لود کند
-keep public class org.mgmmehrad.sevoqueue.SevoQueue {
    public *;
}

# حفظ کلاس‌های اصلی APIهای سرور
-keep public class * extends net.md_5.bungee.api.plugin.Plugin
-keep public class * extends com.velocitypowered.api.plugin.Plugin
-keep public class * extends org.bukkit.plugin.java.JavaPlugin