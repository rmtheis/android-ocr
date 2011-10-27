#android-ocr
* * *

An experimental app for Android that performs optical character recognition (OCR) on images captured using the device camera.

Runs the Tesseract 3.00 OCR engine using Tesseract Tools for Android.

Most of the code making up the core structure of this project has been adapted from the ZXing Barcode Scanner. Some ZXing files have been reused as-is, but most have been modified.

## Requires

* Installation of [tess-two](https://github.com/rmtheis/tess-two) as a library project, to act as the OCR engine.
* A [Bing API key](http://www.bing.com/developers/appids.aspx).
* A [Google Translate API key](https://code.google.com/apis/console/?api=translate).

Installing the APK
==================

The APK is available for download to an Android device from Android Market [here](https://market.android.com/details?id=edu.sfsu.cs.orange.ocr).

License
=======

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
