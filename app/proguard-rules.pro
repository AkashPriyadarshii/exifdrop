# Keep ExifInterface and FileProvider out of R8 obfuscation — reflective segment reads.
-keep class androidx.exifinterface.** { *; }