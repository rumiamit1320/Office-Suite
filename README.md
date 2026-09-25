# DocuFlow Office Suite

Android office suite built with Jetpack Compose and LibreOfficeKit.

The source bundle is currently stored as `DocuFlow-LOKit-Phase6.zip`. GitHub Actions extracts it and attempts an Android debug build.

Architecture: Compose UI -> ViewModel -> OfficeEngine -> LibreOfficeSession -> JNI/C++ -> LibreOfficeKit.
