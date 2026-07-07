-dontwarn
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-dontshrink
-dontoptimize

-repackageclasses 'o.m.s'
-allowaccessmodification

# -----------------------------------------------------------------
-keep class org.spongepowered.configurate.** { *; }
-keep class io.leangen.geantyref.** { *; }

-keepclassmembers enum org.spongepowered.configurate.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep public class org.mgmmehrad.sevoqueue.Main {
    public *;
}

-keep public class * extends net.md_5.bungee.api.plugin.Plugin
-keep public class * extends com.velocitypowered.api.plugin.Plugin
-keep public class * extends org.bukkit.plugin.java.JavaPlugin