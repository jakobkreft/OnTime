# kotlinx.serialization generates a serializer for every @Serializable class and looks it up
# through the class's synthetic Companion. R8 has rules for this shipped with the library, so the
# only thing left to state is that the generated serializers must survive.
-keepclassmembers class si.jakobkreft.ontime.data.** {
    *** Companion;
}
-keepclasseswithmembers class si.jakobkreft.ontime.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
