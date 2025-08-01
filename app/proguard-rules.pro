-keepattributes SourceFile,LineNumberTable

-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <fields>;
}
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <fields>;
}

-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
    @dagger.Provides *;
    @javax.inject.Inject <init>(...);
}

-keep class com.nexttechtitan.aptustutor.data.Payloads** { *; }
-keep class com.nexttechtitan.aptustutor.data.AptusTutorEntities** { *; }
-keepattributes Signature
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField