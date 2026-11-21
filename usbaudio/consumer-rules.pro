# Keep classes and constructors looked up from jni_bridge.cpp.
-keep class app.music.backend.sonari.usbaudio.model.DacFormat { <init>(...); }
-keep class app.music.backend.sonari.usbaudio.model.DacCapabilities { <init>(...); }

# Keep names used by the exported JNI method symbols.
-keepclasseswithmembernames class app.music.backend.sonari.usbaudio.UsbAudioDriver { native <methods>; }
