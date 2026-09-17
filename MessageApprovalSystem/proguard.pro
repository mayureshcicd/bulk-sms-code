# Preserve runtime behavior while obfuscating application class names.
-dontshrink
-dontoptimize
-dontcompress
-keepdirectories
-dontwarn
-ignorewarnings
-dontnote

-keep class com.message.approval.MessageApprovalSystemApplication {
    public static void main(java.lang.String[]);
}
-keep class org.springframework.boot.loader.** { *; }
-keep class org.springframework.** { *; }
-keep class jakarta.** { *; }
-keep class org.hibernate.** { *; }
-keep class org.thymeleaf.** { *; }

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes MethodParameters
-keepattributes Record
-keepattributes SourceFile,LineNumberTable

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep package names so Spring component/repository/entity scanning remains valid.
-keeppackagenames com.message.approval.**

# Preserve reflected members and derived Spring Data repository method names.
-keep,allowobfuscation class com.message.approval.controller.** { *; }
-keep,allowobfuscation class com.message.approval.service.** { *; }
-keep,allowobfuscation class com.message.approval.config.** { *; }
-keep,allowobfuscation class com.message.approval.domain.** { *; }
# Spring Data parses repository method names (for example findByUsername) at
# runtime. Neither repository interface names nor their method names may be
# obfuscated.
-keep interface com.message.approval.repository.** { *; }

# Jackson serializes these nested API records by their accessor names. Preserve
# id(), title(), content(), createdBy(), createdAt(), and files().
-keep class com.message.approval.controller.ApprovedMessageApiController$* { *; }

# These records are read by Jackson or Thymeleaf by their declared accessor
# names. Obfuscating messageId(), recipients(), mobileNumber(), etc. makes the
# packaged application silently receive empty report data or fail template
# property lookup.
-keep class com.message.approval.service.BulkMessageReportService$RecordBulkSendRequest { *; }
-keep class com.message.approval.service.OpenWAAdministrationService$OpenWASession { *; }

-keep @org.springframework.stereotype.* class * { *; }
-keep @org.springframework.context.annotation.* class * { *; }
-keep @org.springframework.web.bind.annotation.* class * { *; }
-keep @jakarta.persistence.Entity class * { *; }
-keep @jakarta.persistence.Embeddable class * { *; }
-keep @jakarta.persistence.MappedSuperclass class * { *; }

-allowaccessmodification
-adaptclassstrings
-useuniqueclassmembernames
