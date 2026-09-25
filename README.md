# DocuFlow Office Suite

Android office suite built with Jetpack Compose and an embedded LibreOfficeKit architecture.

Current repository state is a buildable Android shell with the preserved architecture:

`Compose UI -> ViewModel -> OfficeEngine -> LibreOfficeSession -> JNI/C++ -> LibreOfficeKit`

The native LibreOffice runtime is intentionally not fabricated as a binary. It is the next packaging step after the Android shell build is verified.

GitHub Actions builds a debug APK on every push to `main`.
