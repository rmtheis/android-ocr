/*
 * Copyright (C) 2008 ZXing authors
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

import com.googlecode.tesseract.android.TessBaseAPI;

import edu.sfsu.cs.orange.ocr.CaptureActivity;
import edu.sfsu.cs.orange.ocr.R;
import edu.sfsu.cs.orange.ocr.camera.CameraManager;
import edu.sfsu.cs.orange.ocr.OcrResult;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

/**
 * This class handles all the messaging which comprises the state machine for capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class CaptureActivityHandler extends Handler {

  private static final String TAG = CaptureActivityHandler.class.getSimpleName();
  
  private final CaptureActivity activity;
  private final DecodeThread decodeThread;
  private static State state;
  private static boolean isAutofocusLoopStarted = false;

  private enum State {
    PREVIEW,
    PREVIEW_FOCUSING,
    PREVIEW_PAUSED,
    CONTINUOUS,
    CONTINUOUS_FOCUSING,
    CONTINUOUS_PAUSED,
    SUCCESS,
    DONE
  }

  CaptureActivityHandler(CaptureActivity activity, TessBaseAPI baseApi, 
      boolean isContinuousModeActive) {
    this.activity = activity;

    // Start ourselves capturing previews (and decoding if using continuous recognition mode).
    CameraManager.get().startPreview();
    
    decodeThread = new DecodeThread(activity, 
        //new ViewfinderResultPointCallback(activity.getViewfinderView()), 
        baseApi);
    decodeThread.start();
    
    if (isContinuousModeActive) {
      state = State.CONTINUOUS;

      // Show the shutter and torch buttons
      activity.setButtonVisibility(true);
      
      // Display a "be patient" message while first recognition request is running
      activity.setStatusViewForContinuous();

      CameraManager.get().requestAutoFocus(this, R.id.auto_focus);
      restartOcrPreviewAndDecode();
    } else {
      state = State.SUCCESS;
      
      // Show the shutter and torch buttons
      activity.setButtonVisibility(true);
      
      restartOcrPreview();
    }
  }

  @Override
  public void handleMessage(Message message) { // Messages from the decode handler returning decode results
    
    switch (message.what) {
      case R.id.auto_focus:
        // When one auto focus pass finishes, start another. This is the closest thing to
        // continuous AF. It does seem to hunt a bit, but I'm not sure what else to do.
        
        // Submit another delayed autofocus request.
        if (state == State.PREVIEW_FOCUSING || state == State.PREVIEW) {
          state = State.PREVIEW;
          requestDelayedAutofocus(CaptureActivity.PREVIEW_AUTOFOCUS_INTERVAL_MS, R.id.auto_focus);
        } else if (state == State.CONTINUOUS_FOCUSING || state == State.CONTINUOUS) {
          state = State.CONTINUOUS;
          requestDelayedAutofocus(CaptureActivity.CONTINUOUS_AUTOFOCUS_INTERVAL_MS, R.id.auto_focus);
        } else if (state == State.PREVIEW_FOCUSING) {
          requestDelayedAutofocus(CaptureActivity.PREVIEW_AUTOFOCUS_INTERVAL_MS, R.id.auto_focus);
        } else if (state == State.CONTINUOUS_FOCUSING) {
          requestDelayedAutofocus(CaptureActivity.CONTINUOUS_AUTOFOCUS_INTERVAL_MS, R.id.auto_focus);
        } else {
          isAutofocusLoopStarted = false;
        }

        break;
      case R.id.user_requested_auto_focus_done:
        // Reset the state, but don't request more autofocusing.
        if (state == State.PREVIEW_FOCUSING) {
          state = State.PREVIEW;
        } else if (state == State.CONTINUOUS_FOCUSING) {
          state = State.CONTINUOUS;
        }
        break;
      case R.id.restart_preview:
        restartOcrPreview();
        break;
      case R.id.ocr_continuous_decode_failed:
        DecodeHandler.resetDecodeState();        
        if (state == State.CONTINUOUS || state == State.CONTINUOUS_FOCUSING) {
          try {
            activity.handleOcrContinuousDecode((OcrResultFailure) message.obj);
          } catch (NullPointerException e) {
            Log.w(TAG, "got bad OcrResultFailure", e);
          }
          restartOcrPreviewAndDecode();
        }
        break;
      case R.id.ocr_continuous_decode_succeeded:
        DecodeHandler.resetDecodeState();
        if (state == State.CONTINUOUS || state == State.CONTINUOUS_FOCUSING) {
          restartOcrPreviewAndDecode();
          activity.handleOcrContinuousDecode((OcrResult) message.obj);
        }
        break;
      case R.id.ocr_decode_succeeded:
        state = State.SUCCESS;
        activity.setShutterButtonClickable(true);
        activity.handleOcrDecode((OcrResult) message.obj);
        break;
      case R.id.ocr_decode_failed:
        state = State.PREVIEW;
        activity.setShutterButtonClickable(true);
        Toast toast = Toast.makeText(activity.getBaseContext(), "OCR failed. Please try again.", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 0);
        toast.show();
        break;
    }
  }
  
  void stop() {
    // TODO See if this should be done by sending a quit message to decodeHandler as is done
    // below in quitSynchronously().
    
    Log.d(TAG, "Setting state to CONTINUOUS_PAUSED.");
    state = State.CONTINUOUS_PAUSED;
    removeMessages(R.id.auto_focus);
    removeMessages(R.id.ocr_continuous_decode);
    removeMessages(R.id.ocr_decode);
    removeMessages(R.id.ocr_continuous_decode_failed);
    removeMessages(R.id.ocr_continuous_decode_succeeded); // TODO are these removeMessages() calls doing anything?
    
    // Freeze the view displayed to the user.
//    CameraManager.get().stopPreview();
  }
  
  void resetState() {
    //Log.d(TAG, "in restart()");
    if (state == State.CONTINUOUS_PAUSED) {
      Log.d(TAG, "Setting state to CONTINUOUS");
      state = State.CONTINUOUS;
      restartOcrPreviewAndDecode();
    }
  }
  
  void quitSynchronously() {    
    state = State.DONE;
    CameraManager cameraManager = CameraManager.get();
    if (cameraManager != null) {
      CameraManager.get().stopPreview();
    }

    try {
      Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
      quit.sendToTarget();
      decodeThread.join();
    } catch (InterruptedException e) {
      Log.w(TAG, "Caught InterruptedException in quitSyncronously()", e);
      // continue
    } catch (Exception e) {
      Log.w(TAG, "Caught unknown Exception in quitSynchronously()", e);
    }

    // Be absolutely sure we don't send any queued up messages
    removeMessages(R.id.auto_focus);
    removeMessages(R.id.ocr_continuous_decode);
    removeMessages(R.id.ocr_decode);

  }

  // Start the preview, but don't try to OCR anything until the user presses the shutter button.
  private void restartOcrPreview() {    
    // Display the shutter and torch buttons
    activity.setButtonVisibility(true);

    if (state == State.SUCCESS) {
      state = State.PREVIEW;
      
      // Draw the viewfinder.
      activity.drawViewfinder();
      
      // Start cycling the autofocus
      if (!isAutofocusLoopStarted) {
        isAutofocusLoopStarted = true;
        requestAutofocus(R.id.auto_focus);
      }
    }
  }
  
  // Send a decode request for continuous OCR mode
  private void restartOcrPreviewAndDecode() {
    // Continue capturing camera frames
    CameraManager.get().startPreview();
    
    // Continue requesting decode of images
    CameraManager.get().requestOcrDecode(decodeThread.getHandler(), R.id.ocr_continuous_decode);
    activity.drawViewfinder();    
  }

  private void ocrDecode() {
    state = State.PREVIEW_PAUSED;
    CameraManager.get().requestOcrDecode(decodeThread.getHandler(), R.id.ocr_decode);
  }

  void hardwareShutterButtonClick() {
    // Ensure that we're not in continuous recognition mode
    if (state == State.PREVIEW || state == State.PREVIEW_FOCUSING) {
      ocrDecode();
    }
  }
  
  void shutterButtonClick() {
    // Disable further clicks on this button until OCR request is finished
    activity.setShutterButtonClickable(false);
    ocrDecode();
  }
  
  private void requestAutofocus(int message) {
    if (state == State.PREVIEW || state == State.CONTINUOUS){
      if (state == State.PREVIEW) {
        state = State.PREVIEW_FOCUSING;
      } else if (state == State.CONTINUOUS){
        state = State.CONTINUOUS_FOCUSING;
      }
      CameraManager.get().requestAutoFocus(this, message);
    } else {
      //Log.d(TAG, "requestAutofocus(): didn't get state PREVIEW or CONTINUOUS. state=" + state);
      
      // If we're bumping up against a user-requested focus, enqueue another focus request,
      // otherwise stop autofocusing until the next restartOcrPreview()
      if (state == State.PREVIEW_FOCUSING && message == R.id.auto_focus) { 
        //Log.d(TAG, "focusing now, so Requesting a new delayed autofocus");
        requestDelayedAutofocus(CaptureActivity.PREVIEW_AUTOFOCUS_INTERVAL_MS, message);
      } else if (state == State.CONTINUOUS_FOCUSING && message == R.id.auto_focus) {
        requestDelayedAutofocus(CaptureActivity.CONTINUOUS_AUTOFOCUS_INTERVAL_MS, message);
      } else if (message == R.id.auto_focus) {
        isAutofocusLoopStarted = false;
      }
    }
  }
  
  void requestDelayedAutofocus(final long delay, final int message) {
    Handler autofocusHandler = new Handler(); 
    autofocusHandler.postDelayed(new Runnable() {
      public void run() {
        requestAutofocus(message);
      }
    }, delay);
  } 
}
