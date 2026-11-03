# Minimal PDF Viewer

A lightweight PDF viewer for Android. It has no launcher icon and requests no permissions. It opens when you tap a PDF in any app.

## Stack

- Java 17 on the Android framework APIs (`PdfRenderer`), with no third-party libraries
- Android SDK 36, minimum API 31
- `build.sh` with `aapt2`, `javac`, `d8` and `apksigner`, with no Gradle

## Features

- Continuous vertical scrolling, with pages fitted to the screen width
- Pinch and double tap zoom up to 6x, re-rendered sharply at the current zoom
- Page counter while scrolling
- Uses the Android `PdfRenderer` API, with no third-party libraries
- No internet access, ads or tracking

## Requirements

- Android 12 (API 31) or later
- JDK 17+ and the Android SDK with `platforms;android-36` and `build-tools;36.0.0`

## Build

```bash
./build.sh
```

To build and install on a device connected via `adb`:

```bash
./build.sh install
```

The script uses `JAVA_HOME` and `ANDROID_HOME` when they are set. On the first run it creates a signing key (`release.jks`) and a password file (`release.password`), and both are ignored by git. Keep them safe, because updates must be signed with the same key.

## Usage

Open any PDF and choose **Reader** (shown as **Leitor** on devices set to Portuguese). Select **Always** to make it the default viewer.

## Project structure

```
src/io/github/gasmorais/pdfviewer/
├── ViewerActivity.java     Receives the file and manages the lifecycle
├── file/                   Copies the incoming file to the cache
├── document/               Opens and renders the PDF on a dedicated thread
└── view/                   Layout, gestures, bitmap caching and drawing
```
