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

import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;

class TranslatorBing {
  private static final String TAG = TranslatorBing.class.getSimpleName();
  private static final String API_KEY = " [PUT YOUR API KEY HERE] ";
  
  private TranslatorBing() {  
    // Private constructor to enforce noninstantiability
  }

  // Translate using Microsoft Translate API
  static String translate(String sourceLanguageCode, String targetLanguageCode, String sourceText) {      
    Translate.setKey(API_KEY);
    try {
      return Translate.execute(sourceText, Language.fromString(sourceLanguageCode), 
          Language.fromString(targetLanguageCode));
    } catch (Exception e) {
      Log.e(TAG, "Caught exeption in translation request.");
      return Translator.BAD_TRANSLATION_MSG;
    }
  }
}