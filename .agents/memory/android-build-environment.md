---
name: Android build environment
description: Environment requirements and compatibility notes for compiling the native Android module.
---

Native Android compilation requires both an Android SDK and a JDK compatible with the Android Gradle Plugin. This project’s dependency graph requires at least Java 21; Java 21 is configured in the workspace, but an Android SDK is still not present.

**Why:** The source build reached project configuration after Java 21 was configured, then stopped before compilation because Gradle could not locate an Android SDK.

**How to apply:** Before validating Android changes, set `JAVA_HOME` to a Java 21+ installation and provide a valid `ANDROID_HOME`/`sdk.dir` with the required platform and build tools. Do not commit temporary runtime configuration solely to make a local check pass.