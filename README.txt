This is the SMS-Prototype-PIN-Contacts (v3) project.

Modules:
- :deviceapp (Android phone)
- :wearapp   (Wear OS watch)

Build (no Android Studio) via GitHub Actions:
1) Push this folder to a new GitHub repo.
2) Add .github/workflows/build.yml below.
3) Run the workflow, download APKs from the artifact.

.build.yml (optional):

name: Build APKs
on: { workflow_dispatch: {} }
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: gradle }
      - uses: android-actions/setup-android@v3
      - run: yes | sdkmanager --licenses
      - run: ./gradlew :deviceapp:assembleDebug :wearapp:assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: APKs
          path: |
            deviceapp/build/outputs/apk/debug/*-debug.apk
            wearapp/build/outputs/apk/debug/*-debug.apk
