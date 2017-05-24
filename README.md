# android-ocr

An experimental app for Android that performs optical character recognition (OCR) on images captured using the device camera.

Runs the Tesseract OCR engine using [tess-two](https://github.com/rmtheis/tess-two), a fork of Tesseract Tools for Android.

Most of the code making up the core structure of this project has been adapted from the ZXing Barcode Scanner. Along with Tesseract-OCR and Tesseract Tools for Android (tesseract-android-tools), several open source projects have been used in this project, including leptonica, google-api-translate-java, microsoft-translator-java-api, and jtar.

## Video

[![Video](http://img.youtube.com/vi/FOSgiPjGwx4/0.jpg)](http://www.youtube.com/watch?v=FOSgiPjGwx4)

A slightly modified version:

[![Video](http://img.youtube.com/vi/7vNepTmBTG8/0.jpg)](http://www.youtube.com/watch?v=7vNepTmBTG8)

## Requires

* A Windows Azure Marketplace Client ID and Client Secret (for translation) - [Documentation](http://msdn.microsoft.com/en-us/library/hh454950.aspx)
* A Google Translate API key (for translation) - [Documentation](https://code.google.com/apis/console/?api=translate)

## Training data for OCR

A data file is required for every language you want to recognize. For English, this data file is included in the application assets and is automatically installed when the app is first run.

For other languages (Spanish, French, Chinese, etc.), the app will try to download the training data from an old Google Code repository that is no longer available, and [the download fails](https://github.com/rmtheis/android-ocr/issues/55). So if you want to use training data for other languages, you'll need to package the appropriate [training data files](https://github.com/tesseract-ocr/tessdata) in the app or change the code to point to your own download location.

## Installation

To build and run the app, clone this project, open it as an existing project in Android Studio, and click Run.

## License

This project is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

    /*
     * Copyright 2011 Robert Theis
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */

One of the jar files in the android/libs directory (google-api-translate-java-0.98-mod2.jar) is licensed under the [GNU Lesser GPL](http://www.gnu.org/licenses/lgpl.html).
