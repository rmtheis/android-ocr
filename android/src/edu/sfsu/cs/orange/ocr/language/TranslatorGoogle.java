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
package edu.sfsu.cs.orange.ocr.language;

import android.util.Log;

import com.google.api.GoogleAPI;
import com.google.api.translate.Language;
import com.google.api.translate.Translate;

class TranslatorGoogle {
  private static final String TAG = TranslatorGoogle.class.getSimpleName();
  private static final String API_KEY = " [PUT YOUR API KEY HERE] ";

  private TranslatorGoogle() {  
    // Private constructor to enforce noninstantiability
  }

  // Translate using Google Translate API
  static String translate(String sourceLanguageCode, String targetLanguageCode, String sourceText) {   

    // Truncate excessively long strings. Limit for Google Translate is 5000 characters
    if (sourceText.length() > 4500) {
      sourceText = sourceText.substring(0, 4500);
    }
    
    GoogleAPI.setKey(API_KEY);
    GoogleAPI.setHttpReferrer("https://github.com/rmtheis/android-ocr");
    try {
      return Translate.DEFAULT.execute(sourceText, Language.fromString(sourceLanguageCode), 
          Language.fromString(targetLanguageCode));
    } catch (Exception e) {
      Log.e(TAG, "Caught exeption in translation request.");
      return Translator.BAD_TRANSLATION_MSG;
    }
  }
}
