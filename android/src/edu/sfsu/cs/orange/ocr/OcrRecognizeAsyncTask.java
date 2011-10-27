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

import edu.sfsu.cs.orange.ocr.camera.CameraManager;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
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

    // Get bounding boxes for characters and words
    List<Rect> words = baseApi.getWords().getBoxRects();
    List<Rect> characters = baseApi.getCharacters().getBoxRects();
    
//    long getRegionsStart = System.currentTimeMillis();
//    List<Rect> regions = baseApi.getRegions().getBoxRects();
//    long getRegionsEnd = System.currentTimeMillis();
//    Log.d(TAG, "getRegions took " + (getRegionsEnd - getRegionsStart) + " ms");
//
//    if (words.size() > 0) {
//      for (int i = 0; i < words.size(); i++) {
//        Rect r = words.get(i);
//        Log.d(TAG, "Words Rect " + i + "\n" +
//            "  left = " + r.left + "\n" +
//            "  right = " + r.right + "\n" +
//            "  top = " + r.top + "\n" +
//            "  bottom = " + r.bottom);
//      }
//    } else {
//      Log.d(TAG, "No words found.");
//    }
//    
//    if (characters.size() > 0) {
//      for (int i = 0; i < characters.size(); i++) {
//        Rect r = characters.get(i);
//        Log.d(TAG, "Characters Rect " + i + "\n" +
//            "  left = " + r.left + "\n" +
//            "  right = " + r.right + "\n" +
//            "  top = " + r.top + "\n" +
//            "  bottom = " + r.bottom);
//      }
//    } else {
//      Log.d(TAG, "No characters found.");
//    }
//
//    if (regions.size() > 0) {
//      for (int i = 0; i < regions.size(); i++) {
//        Rect r = regions.get(i);
//        Log.e(TAG, "Regions Rect " + i + "\n" +
//            "  left = " + r.left + "\n" +
//            "  right = " + r.right + "\n" +
//            "  top = " + r.top + "\n" +
//            "  bottom = " + r.bottom);
//      }
//    } else {
//      Log.e(TAG, "No regions found.");
//    }
    
    if (textResult == null || textResult.equals("")) {
      ocrResultFailure = new OcrResultFailure(end - start);
      return false;
    } else {  
      // TODO See about re-using the same OcrResult object
      ocrResult = new OcrResult(bitmap, textResult, wordConfidences, overallConf, characters, words, (end - start));
    }
    
    if (wordConfidences != null) {
      for (int i = 0; i < wordConfidences.length; i++) {
        //Log.d(TAG, "conf " + i + ": " + wordConfidences[i]);
      }
    }

    if (overallConf < CaptureActivity.MINIMUM_MEAN_CONFIDENCE) {
      //Log.d(TAG, "meanConfidence is " + overallConf + ", which is below the minimum score of " + CaptureActivity.MINIMUM_MEAN_CONFIDENCE);
      return false;
    }

    return true;
  }

  @Override
  protected synchronized void onPostExecute(Boolean result) {
    super.onPostExecute(result);

    if (!isContinuous) {
      // Send results for single-shot mode recognition.
      if (result) {
        //Log.i(TAG, "SUCCESS");
        // Send the result to CaptureActivityHandler
        Message message = Message.obtain(activity.getHandler(), R.id.ocr_decode_succeeded, ocrResult);
//        Bundle bundle = new Bundle();
//        bundle.putParcelable(DecodeThread.OCR_BITMAP, bitmap);
//        message.setData(bundle);
        message.sendToTarget();
      } else {
        //Log.i(TAG, "FAILURE");
        Message message = Message.obtain(activity.getHandler(), R.id.ocr_decode_failed, ocrResult);
        message.sendToTarget();
      }
      indeterminateDialog.dismiss();
    } else {
      // Send results for continuous mode recognition.
      if (result) {
        //Log.i(TAG, "SUCCESS");

        try {
          // Send the result to CaptureActivityHandler
          Message message = Message.obtain(activity.getHandler(), R.id.ocr_continuous_decode_succeeded, ocrResult);
//          Bundle bundle = new Bundle();
//          bundle.putParcelable(DecodeThread.OCR_BITMAP, bitmap);
//          message.setData(bundle);
          message.sendToTarget();
        } catch (NullPointerException e) {
          //Log.d(TAG, "Caught NullPointerException sending continuous OCR result message [ocr succeed]. calling stopHandler()...");

          activity.stopHandler();
        }
      } else {
        //Log.i(TAG, "FAILURE");
        
        try {
          Message message = Message.obtain(activity.getHandler(), R.id.ocr_continuous_decode_failed, ocrResultFailure);
          message.sendToTarget();
        } catch (NullPointerException e) {
          //Log.d(TAG, "Caught NullPointerException sending continuous OCR result message [ocr fail]. calling stopHandler()...");
          
          activity.stopHandler();
        }
      }
      if (baseApi != null) {
        //Log.d(TAG, "Clearing baseApi...");
        baseApi.clear();
      }
    }
  }

}
