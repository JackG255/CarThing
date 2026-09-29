# R8 rules for release builds. Libraries (Room, WorkManager, ML Kit, kotlinx.serialization,
# Navigation) ship their own consumer rules; add app-specific ones here only when needed.

# Keep file and line numbers so release crash traces are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
