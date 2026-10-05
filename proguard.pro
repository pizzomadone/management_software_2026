# WareStat production protection rules.
# ProGuard is used only for release artifacts. Development builds remain untouched.

-dontusemixedcaseclassnames
-optimizationpasses 5
-allowaccessmodification

# Keep enough line information to retrace production crashes without exposing
# source file names or local-variable names.
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod,PermittedSubclasses,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Java runtime modules used by WareStat and its bundled libraries.
-libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.desktop.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.datatransfer.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.prefs.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.sql.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.logging.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.naming.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.management.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.net.http.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.xml.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/jdk.crypto.ec.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/jdk.unsupported.jmod(!**.jar;!module-info.class)

# SwingWebView remains a separate runtime library and must not be renamed.
-libraryjars lib/webview-1.0.10.jar

# Application entry point. Keep only the externally required signature.
-keep public class MainWindow {
    public static void main(java.lang.String[]);
}

# macOS integration refers to this class by its stable name.
-keepnames class AboutDialog

# SQLite registers/loads its JDBC entry point reflectively.
-keep class org.sqlite.JDBC { *; }
-keep class * implements java.sql.Driver { public <init>(); }

# NativeDB.c resolves these callbacks and fields during JNI_OnLoad.
# Name-only rules do not prevent shrinking; keep the native bridge explicitly.
-keep class org.sqlite.core.NativeDB { *; }
-keep class org.sqlite.core.DB { *; }
-keep class org.sqlite.Function { *; }
-keep class org.sqlite.Function$Aggregate { *; }
-keep class org.sqlite.Function$Window { *; }
-keep class org.sqlite.Collation { *; }
-keep interface org.sqlite.core.DB$ProgressObserver { *; }
-keep class org.sqlite.ProgressHandler { *; }
-keep class org.sqlite.BusyHandler { *; }

# Preserve names that native/JNI and reflection-heavy third-party code may use,
# while still allowing ProGuard to remove unreachable code.
-keepnames class org.sqlite.**
-keepclassmembernames class org.sqlite.** { *; }
-keepnames class org.apache.pdfbox.**
-keepclassmembernames class org.apache.pdfbox.** { *; }

# Optional integrations bundled inside PDFBox/Commons Logging are intentionally
# absent from WareStat. They are not runtime requirements and are removed by shrinking.
-dontwarn javax.servlet.**
-dontwarn org.apache.avalon.framework.**
-dontwarn org.apache.log.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.logging.impl.**

# PDFBox uses MethodHandle.invokeExact with signature-polymorphic calls; this is a
# known static-analysis false positive rather than a missing JDK method.
-dontwarn java.lang.invoke.MethodHandle
-dontwarn org.apache.pdfbox.io.IOUtils

# Generic Java/JNI safety rules recommended for optimized desktop applications.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep service-provider descriptors consistent with renamed classes.
-adaptclassstrings
-adaptresourcefilecontents META-INF/services/**

# Do not leak build machine paths/names through optional debugging attributes.
-keeppackagenames org.sqlite,org.apache.pdfbox

# Commons Logging discovers the JDK backend by name and invokes its String constructor.
-keep class org.apache.commons.logging.impl.Jdk14Logger { *; }
-keep class org.apache.commons.logging.impl.LogFactoryImpl { public <init>(); }

# FontBox loads resources relative to its class packages.
-keepnames class org.apache.fontbox.**

# ImageIO providers are instantiated through META-INF/services, not Java calls.
-keep class * extends javax.imageio.spi.IIOServiceProvider { public <init>(); }
