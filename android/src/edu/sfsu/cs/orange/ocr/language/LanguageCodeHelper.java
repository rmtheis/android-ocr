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

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import edu.sfsu.cs.orange.ocr.PreferencesActivity;
import edu.sfsu.cs.orange.ocr.R;

// TODO See about re-doing this using enum as in:
//   https://github.com/rmtheis/multi-translator/blob/master/src/com/robtheis/translator/TranslatorGoogle.java

/**
 * Class for handling functions relating to converting between standard language
 * codes, and converting language codes to language names.
 * 
 * @author Jingjing
 */
public class LanguageCodeHelper {
	public static final String TAG = "LanguageCodeHelper";

	/**
	 * Private constructor to enforce noninstantiability
	 */
	private LanguageCodeHelper() {
		throw new AssertionError();
	}

	/**
	 * Map an ISO 639-3 language code to an ISO 639-1 language code.
	 * 
	 * There is one entry here for each language recognized by the OCR engine.
	 * 
	 * @param languageCode
	 *            ISO 639-3 language code
	 * @return ISO 639-1 language code
	 */
	public static String mapLanguageCode(String languageCode) {	  
	  if (languageCode.equals("bul")) { // Bulgarian
	    return "bg";
	  } else if (languageCode.equals("cat")) { // Catalan
	    return "ca";
    } else if (languageCode.equals("chi_sim")) { // Chinese (Simplified)
      return "zh-CN";
    } else if (languageCode.equals("chi_tra")) { // Chinese (Traditional)
      return "zh-TW";
    } else if (languageCode.equals("ces")) { // Czech
      return "cs";
    } else if (languageCode.equals("dan")) { // Danish
      return "da";
    } else if (languageCode.equals("nld")) { // Dutch
      return "nl";
    } else if (languageCode.equals("eng")) { // English
      return "en";
    } else if (languageCode.equals("fin")) { // Finnish
      return "fi";
    } else if (languageCode.equals("fra")) { // French
      return "fr";
    } else if (languageCode.equals("deu")) { // German
      return "de";
    } else if (languageCode.equals("ell")) { // Greek
      return "el";
    } else if (languageCode.equals("hun")) { // Hungarian
      return "hu";
    } else if (languageCode.equals("ind")) { // Indonesian
      return "id";
    } else if (languageCode.equals("ita")) { // Italian
      return "it";
    } else if (languageCode.equals("jpn")) { // Japanese
      return "ja";
    } else if (languageCode.equals("kor")) { // Korean
      return "ko";
    } else if (languageCode.equals("lav")) { // Latvian
      return "lv";
    } else if (languageCode.equals("lit")) { // Lithuanian
      return "lt";
    } else if (languageCode.equals("nor")) { // Norwegian
      return "no";
    } else if (languageCode.equals("pol")) { // Polish
      return "pl";
    } else if (languageCode.equals("por")) { // Portuguese
      return "pt";
    } else if (languageCode.equals("ron")) { // Romanian
      return "ro";
    } else if (languageCode.equals("rus")) { // Russian
      return "ru";
    } else if (languageCode.equals("srp")) { // Serbian (Latin) // TODO is google expecting Cyrillic?
      return "sr";
    } else if (languageCode.equals("slk")) { // Slovak
      return "sk";
    } else if (languageCode.equals("slv")) { // Slovenian
      return "sl";
    } else if (languageCode.equals("spa")) { // Spanish
      return "es";
    } else if (languageCode.equals("swe")) { // Swedish
      return "sv";
    } else if (languageCode.equals("tgl")) { // Tagalog
      return "tl";
    } else if (languageCode.equals("tur")) { // Turkish
      return "tr";
    } else if (languageCode.equals("ukr")) { // Ukranian
      return "uk";
    } else if (languageCode.equals("vie")) { // Vietnamese
      return "vi";
	  } else {
	    return "";
	  }
	}

	/**
	 * Map the given ISO 639-3 language code to a name of a language, for example,
	 * "Spanish"
	 * 
	 * @param context
	 *            interface to calling application environment. Needed to access
	 *            values from strings.xml.
	 * @param languageCode
	 *            ISO 639-3 language code
	 * @return language name
	 */
	public static String getLanguageName(Context context, String languageCode) {
		Resources res = context.getResources();
		String[] language6393 = res.getStringArray(R.array.iso6393);
		String[] languageNames = res.getStringArray(R.array.languagenames);
		int len;

		// Finds the given language code in the iso6393 array, and takes the name with the same index
		// from the languagenames array.
		for (len = 0; len < language6393.length; len++) {
			if (language6393[len].equals(languageCode)) {
				Log.d(TAG, "languageCode: " + languageCode + "->"
						+ languageNames[len]);
				return languageNames[len];
			}
		}
		
		Log.d(TAG, "languageCode: Could not find language name for ISO 693-3: "
				+ languageCode);
		return languageCode;
	}
	
	/**
   * Map the given ISO 639-1 language code to a name of a language, for example,
   * "Spanish"
	 * 
	 * @param languageCode
	 *             ISO 639-1 language code
	 * @return name of the language. For example, "English"
	 */
	public static String getTranslationLanguageName(Context context, String languageCode) {
    Resources res = context.getResources();
    String[] language6391 = res.getStringArray(R.array.translationtargetiso6391_google);
    String[] languageNames = res.getStringArray(R.array.translationtargetlanguagenames_google);
    int len;

    // Finds the given language code in the translationtargetiso6391 array, and takes the name
    // with the same index from the translationtargetlanguagenames array.
    for (len = 0; len < language6391.length; len++) {
      if (language6391[len].equals(languageCode)) {
        Log.d(TAG, "languageCode: " + languageCode + "->" + languageNames[len]);
        return languageNames[len];
      }
    }
    
    // Now look in the Microsoft Translate API list. Currently this will only be needed for 
    // Haitian Creole.
    language6391 = res.getStringArray(R.array.translationtargetiso6391_microsoft);
    languageNames = res.getStringArray(R.array.translationtargetlanguagenames_microsoft);
    for (len = 0; len < language6391.length; len++) {
      if (language6391[len].equals(languageCode)) {
        Log.d(TAG, "languageCode: " + languageCode + "->" + languageNames[len]);
        return languageNames[len];
      }
    }    
    
    Log.d(TAG, "languageCode: Could not find language name for ISO 693-1: " + languageCode);
    return "";
	}
	
	/**
	 * Checks if the given language code is supported by the given translation provider, and returns
	 * true if it is supported.
	 */
	public static boolean isSupported(Context context, String translator, String languageCode) {
	  Resources res = context.getResources();
	  String[] supportedCodes;
	  int len;
	  
	  // Note: Need to separately check for Chinese because Google and Bing use different codes for
	  // representing Chinese/Simplified Chinese/Traditional Chinese.
	  
	  if (translator.equals(PreferencesActivity.TRANSLATOR_BING)) {
	    supportedCodes = res.getStringArray(R.array.translationtargetiso6391_microsoft);
	    for (len = 0; len < supportedCodes.length; len++) {
	      if (supportedCodes[len].equals(languageCode)) {
	        return true;
	      }
	    }
	  } else if (translator.equals(PreferencesActivity.TRANSLATOR_GOOGLE)) {
      supportedCodes = res.getStringArray(R.array.translationtargetiso6391_google);
      for (len = 0; len < supportedCodes.length; len++) {
        if (supportedCodes[len].equals(languageCode)) {
          return true;
        }
      }	    
	  }
	  
	  return false;
	}
}
