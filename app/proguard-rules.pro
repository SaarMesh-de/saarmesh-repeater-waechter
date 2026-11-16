# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class de.saarmesh.waechter.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.saarmesh.waechter.**$$serializer { *; }
# Retrofit-Interfaces
-keep,allowobfuscation interface de.saarmesh.waechter.data.remote.AusfallApi
-keepattributes Signature, Exceptions
# osmdroid
-dontwarn org.osmdroid.**
