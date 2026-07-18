# OkHttp ships its own consumer rules; we only need to silence the lint
# warnings for optional dependencies it imports reflectively.
-dontwarn okhttp3.**
-dontwarn okio.**

# Play in-app review (review-ktx) pulls in a play-services Task listener that
# references a compile-only annotation not on the runtime classpath. It's
# unused at runtime, so silence R8's missing-class error rather than shipping it.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
