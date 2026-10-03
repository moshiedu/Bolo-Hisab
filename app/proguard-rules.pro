# sherpa-onnx config classes are read from JNI by field name.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Type-safe navigation routes are serialised by name.
-keep @kotlinx.serialization.Serializable class com.bolohisab.ui.navigation.** { *; }
