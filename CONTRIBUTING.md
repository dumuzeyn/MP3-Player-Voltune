# Contributing to Voltune

Thank you for improving the project. Keep changes focused and preserve the existing offline-first behavior.

## Before opening a pull request

1. Create a branch from the current `main` branch.
2. Keep UI, playback, storage, and theme changes in their responsible controllers or packages.
3. Do not log music URIs or local file paths.
4. Do not add APK files, signing keys, or passwords to Git.
5. Run:

```bash
./gradlew qualityCheck
```

Production `.java` and `.kt` files are limited to 500 lines. Split responsibilities instead
of compressing formatting when a file approaches the limit.

Official release builds require private signing credentials and are produced only by the release workflow.

## Pull requests

Describe the problem, the chosen solution, and the Android versions used for verification. Add focused JVM tests for pure logic and instrumentation tests for Android lifecycle, storage, or service behavior.

By contributing, you agree that your contribution is distributed under the repository's source-available non-commercial license.
