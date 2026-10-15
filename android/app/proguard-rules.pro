# kotlinx.serialization: сгенерированные сериализаторы и их companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.texasprogram.app.** {
    static <fields>;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.texasprogram.app.**$$serializer { *; }
-keepclasseswithmembers class com.texasprogram.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Приёмники из манифеста находятся по имени.
-keep class com.texasprogram.app.service.** extends android.content.BroadcastReceiver { *; }
