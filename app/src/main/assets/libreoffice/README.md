# Embedded LibreOfficeKit runtime

DocuFlow expects the Android LibreOffice runtime to be packaged at build/package time.

The runtime is intentionally not checked into this repository because the LibreOffice Android native core is a large binary distribution.

The packaged runtime must provide liblo-native-code.so and the LibreOffice installation data required by the Android build. The application uses files/libreoffice as the runtime root and a private lo-profile directory for the user profile.

The native bridge resolves the Android LibreOfficeKit hook libreofficekit_hook_2 from liblo-native-code.so.

Build the runtime from the official LibreOffice Android source/build system. LibreOffice currently documents Android builds around NDK 27 and Android SDK 30.0.3.

Official source:
https://github.com/LibreOffice/core/tree/master/android
