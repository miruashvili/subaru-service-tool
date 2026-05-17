# Subaru Service Tool — ProGuard rules

# Keep all app classes
-keep class com.subaru.servicetool.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel { *; }

# Kotlin coroutines / flows
-dontwarn kotlinx.**
-keep class kotlinx.coroutines.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Navigation Compose
-keep class androidx.navigation.** { *; }

# Compose
-keep class androidx.compose.** { *; }

# Keep Bluetooth / OBD classes
-keep class com.subaru.servicetool.data.bluetooth.** { *; }
-keep class com.subaru.servicetool.data.obd.** { *; }
