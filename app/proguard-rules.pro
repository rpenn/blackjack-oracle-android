# OkHttp ships its own consumer rules; we only need to silence the lint
# warnings for optional dependencies it imports reflectively.
-dontwarn okhttp3.**
-dontwarn okio.**
