# kotlinx.serialization: keep generated serializers (also covers type-safe navigation routes)
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers class com.penz7.proofdrop.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.penz7.proofdrop.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.penz7.proofdrop.** { *; }

# Retrofit: keep service interfaces and generic signatures used by suspend functions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.penz7.proofdrop.core.network.ProofDropApi { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
