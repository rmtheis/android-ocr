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
package edu.sfsu.cs.orange.ocr;

import java.util.List;

import com.googlecode.tesseract.android.TessBaseAPI;

import edu.sfsu.cs.orange.ocr.language.PseudoTranslator;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

final class OcrRecognizeAsyncTask extends AsyncTask<String, String, Boolean> {

  private CaptureActivity activity;
  private TessBaseAPI baseApi;
  private Bitmap bitmap;
  private OcrResult ocrResult;
  private OcrResultFailure ocrResultFailure;
  private boolean isContinuous;
  private ProgressDialog indeterminateDialog;
  private long start;
  private long end;
  
  // Constructor for single-shot mode
  OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, 
      ProgressDialog indeterminateDialog, Bitmap bitmap) {
    this.activity = activity;
    this.baseApi = baseApi;
    this.indeterminateDialog = indeterminateDialog;
    this.bitmap = bitmap;
    isContinuous = false;
  }

  // Constructor for continuous recognition mode
  OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, Bitmap bitmap) {
    this.activity = activity;
    this.baseApi = baseApi;
    this.bitmap = bitmap;
    isContinuous = true;
  }
  
  @Override
  protected Boolean doInBackground(String... arg0) {
    String textResult = null;   
    int[] wordConfidences = null;
    int overallConf = -1;
    start = System.currentTimeMillis();
    end = start;
    
    try {     
      baseApi.setImage(bitmap);
      textResult = baseApi.getUTF8Text();
      wordConfidences = baseApi.wordConfidences();
      overallConf = baseApi.meanConfidence();
      end = System.currentTimeMillis();
    } catch (RuntimeException e) {
      //Log.w(TAG, "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
      try {
        baseApi.clear();
        activity.stopHandler();
      } catch (NullPointerException e1) {
        // Continue
      }
      return false;
    }

    // Check for failure to recognize text
    if (textResult == null || textResult.equals("")) {
      ocrResultFailure = new OcrResultFailure(end - start);
      return false;
    }  
    
    // Get bounding boxes for characters and words
    List<Rect> wordBoxes = baseApi.getWords().getBoxRects();
    List<Rect> characterBoxes = baseApi.getCharacters().getBoxRects();
    List<Rect> textlineBoxes = baseApi.getTextlines().getBoxRects();
    List<Rect> regionBoxes = baseApi.getRegions().getBoxRects();

    //textResult = PseudoTranslator.translate(textResult);

    ocrResult = new OcrResult(bitmap, textResult, wordConfidences, overallConf, characterBoxes, 
        textlineBoxes, wordBoxes, regionBoxes, (end - start));
    return true;
  }
  
  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);

    Handler handler = activity.getHandler();
    if (!isContinuous && handler != null) {
      // Send results for single-shot mode recognition.
      if (result) {
        Message message = Message.obtain(handler, R.id.ocr_decode_succeeded, ocrResult);
        message.sendToTarget();
      } else {
        bitmap.recycle();
        Message message = Message.obtain(handler, R.id.ocr_decode_failed, ocrResult);
        message.sendToTarget();
      }
      indeterminateDialog.dismiss();
    } else  if (handler != null) {
      // Send results for continuous mode recognition.
      if (result) {
        try {
          // Send the result to CaptureActivityHandler
          Message message = Message.obtain(handler, R.id.ocr_continuous_decode_succeeded, ocrResult);
          message.sendToTarget();
        } catch (NullPointerException e) {
          activity.stopHandler();
        }
      } else {
        bitmap.recycle();
        try {
          Message message = Message.obtain(handler, R.id.ocr_continuous_decode_failed, ocrResultFailure);
          message.sendToTarget();
        } catch (NullPointerException e) {
          activity.stopHandler();
        }
      }
    }
    if (baseApi != null) {
      baseApi.clear();
    }
  }
}
