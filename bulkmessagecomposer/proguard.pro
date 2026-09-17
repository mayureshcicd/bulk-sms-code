# =============================================
# Basic Settings
# =============================================
-dontshrink
-dontoptimize
-dontcompress
-keepdirectories
-dontwarn
-ignorewarnings
-dontnote
-dontpreverify

# =============================================
# Keep Entry Point & Spring Boot Loader
# =============================================
-keep class com.sms.**Application {
    public static void main(java.lang.String[]);
}

# Spring Boot Launcher (Critical for fat JAR)
-keep class org.springframework.boot.loader.** { *; }

# =============================================
# Keep Important Frameworks
# =============================================
-keep class org.springframework.** { *; }
-keep class org.springframework.boot.** { *; }
-keep class jakarta.** { *; }
-keep class jakarta.validation.** { *; }

# Swagger / SpringDoc
-keep class io.swagger.** { *; }
-keep class org.springdoc.** { *; }

# =============================================
# Annotations & Reflection Support
# =============================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes MethodParameters
-keepattributes Record
-keepattributes SourceFile,LineNumberTable

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# =============================================
# Your Application Packages (Obfuscate these)
# =============================================
-keeppackagenames com.sms.**
-keep,allowobfuscation class com.sms.controller.** { *; }
-keep,allowobfuscation class com.sms.service.** { *; }
-keep,allowobfuscation class com.sms.util.** { *; }
-keep,allowobfuscation class com.sms.config.** { *; }
-keep,allowobfuscation class com.sms.domain.** { *; }
-keep interface com.sms.repository.** { *; }

# Jackson deserializes the MessageApprovalSystem response into these nested
# records. Their constructor/accessor property names must remain unchanged.
-keep class com.sms.service.ApprovedMessageClient$* { *; }

# Keep Spring annotations on your classes
-keep @org.springframework.stereotype.* class * { *; }
-keep @org.springframework.web.bind.annotation.* class * { *; }
-keep @org.springframework.context.annotation.* class * { *; }
-keep @jakarta.validation.* class * { *; }
-keep @jakarta.persistence.Entity class * { *; }

# =============================================
# Additional Useful Options
# =============================================
-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-adaptclassstrings
-useuniqueclassmembernames

# Optional: You can enable these later when everything is stable
# -adaptresourcefilenames
# -adaptresourcefilecontents

# Preserve JSON property names for the private-contact request/response records.
-keep class com.sms.controller.ContactController$* { *; }
