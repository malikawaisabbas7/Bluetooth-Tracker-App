# Preserve source file and line numbers for obfuscated crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Navigation SafeArgs classes instantiated via reflection
-keepnames class * implements androidx.navigation.NavArgs
-keepclassmembers class * implements androidx.navigation.NavArgs {
    <init>(...);
}
