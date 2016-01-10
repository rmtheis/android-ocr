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

import edu.sfsu.cs.orange.ocr.CaptureActivity;

public class TranslatorGoogle {
  private static final String TAG = TranslatorGoogle.class.getSimpleName();
  private static final String API_KEY = " [PUT YOUR API KEY HERE] ";

  private TranslatorGoogle() {  
    // Private constructor to enforce noninstantiability
  }

  // Translate using Google Translate API
  static String translate(String sourceLanguageCode, String targetLanguageCode, String sourceText) {   
    Log.d(TAG, sourceLanguageCode + " -> " + targetLanguageCode);
    
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

  /**
   * Convert the given name of a natural language into a language code from the enum of Languages 
   * supported by this translation service.
   * 
   * @param languageName The name of the language, for example, "English"
   * @return code representing this language, for example, "en", for this translation API
   * @throws IllegalArgumentException
   */
  public static String toLanguage(String languageName) throws IllegalArgumentException {   
    // Convert string to all caps
    String standardizedName = languageName.toUpperCase();
    
    // Replace spaces with underscores
    standardizedName = standardizedName.replace(' ', '_');
    
    // Remove parentheses
    standardizedName = standardizedName.replace("(", "");   
    standardizedName = standardizedName.replace(")", "");
    
    // Hack to fix misspelling in google-api-translate-java
    if (standardizedName.equals("UKRAINIAN")) {
      standardizedName = "UKRANIAN";
    }
    
    // Map Norwegian-Bokmal to Norwegian
    if (standardizedName.equals("NORWEGIAN_BOKMAL")) {
      standardizedName = "NORWEGIAN";
    }
    
    try {
      return Language.valueOf(standardizedName).toString();
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "Not found--returning default language code");
      return CaptureActivity.DEFAULT_TARGET_LANGUAGE_CODE;
    }
  }
}
