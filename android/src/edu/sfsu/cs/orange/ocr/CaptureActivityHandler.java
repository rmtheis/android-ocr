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

import edu.sfsu.cs.orange.ocr.CaptureActivity;
import edu.sfsu.cs.orange.ocr.R;
import edu.sfsu.cs.orange.ocr.camera.CameraManager;
import edu.sfsu.cs.orange.ocr.OcrResult;

import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

/**
 * This class handles all the messaging which comprises the state machine for capture.
 *
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing/
 */
final class CaptureActivityHandler extends Handler {

  private static final String TAG = CaptureActivityHandler.class.getSimpleName();
  
  private final CaptureActivity activity;
  private final DecodeThread decodeThread;
  private static State state;
  private final CameraManager cameraManager;
  private static boolean isAutofocusLoopStarted = false;
  private long delay;

  private enum State {
    PREVIEW,
    PREVIEW_FOCUSING,
    PREVIEW_PAUSED,
    CONTINUOUS,
    CONTINUOUS_FOCUSING,
    CONTINUOUS_PAUSED,
    CONTINUOUS_WAITING_FOR_AUTOFOCUS_TO_FINISH,
    SUCCESS,
    DONE
  }

  CaptureActivityHandler(CaptureActivity activity, CameraManager cameraManager, boolean isContinuousModeActive) {
    this.activity = activity;
    this.cameraManager = cameraManager;

    // Start ourselves capturing previews (and decoding if using continuous recognition mode).
    cameraManager.startPreview();
    
    decodeThread = new DecodeThread(activity);
    decodeThread.start();
    
    if (isContinuousModeActive) {
      state = State.CONTINUOUS;

      // Show the shutter and torch buttons
      activity.setButtonVisibility(true);
      
      // Display a "be patient" message while first recognition request is running
      activity.setStatusViewForContinuous();
      
      cameraManager.requestAutoFocus(this, R.id.auto_focus);
      restartOcrPreviewAndDecode();
    } else {
      state = State.SUCCESS;
      
      // Show the shutter and torch buttons
      activity.setButtonVisibility(true);
      
      restartOcrPreview();
    }
  }

  @Override
  public void handleMessage(Message message) {
    
    switch (message.what) {
      case R.id.auto_focus:
        // If the last autofocus was successful, use a longer delay.
        if (message.getData().getBoolean("success")) {
          delay = CaptureActivity.AUTOFOCUS_SUCCESS_INTERVAL_MS;
        } else {
          delay = CaptureActivity.AUTOFOCUS_FAILURE_INTERVAL_MS;
        }
        
        // Submit another delayed autofocus request.
        if (state == State.PREVIEW_FOCUSING || state == State.PREVIEW) {
          state = State.PREVIEW;
          requestDelayedAutofocus(delay, R.id.auto_focus);
        } else if (state == State.CONTINUOUS_FOCUSING || state == State.CONTINUOUS) {
          state = State.CONTINUOUS;
          requestDelayedAutofocus(delay, R.id.auto_focus);
        } else if (state == State.PREVIEW_FOCUSING) {
          requestDelayedAutofocus(delay, R.id.auto_focus);
        } else if (state == State.CONTINUOUS_FOCUSING) {
          requestDelayedAutofocus(delay, R.id.auto_focus);
        } else if (state == State.CONTINUOUS_WAITING_FOR_AUTOFOCUS_TO_FINISH) {
          state = State.CONTINUOUS;
          requestDelayedAutofocus(delay, R.id.auto_focus);
          restartOcrPreviewAndDecode();
        } else {
          isAutofocusLoopStarted = false;
        }
        break;
      case R.id.user_requested_auto_focus:
        // Reset the state, but don't request more autofocusing.
        if (state == State.PREVIEW_FOCUSING) {
          state = State.PREVIEW;
        } else if (state == State.CONTINUOUS_FOCUSING) {
          state = State.CONTINUOUS;
        } else if (state == State.CONTINUOUS_WAITING_FOR_AUTOFOCUS_TO_FINISH) {
          state = State.CONTINUOUS;
          restartOcrPreviewAndDecode();
        }
        break;
      case R.id.restart_preview:
        restartOcrPreview();
        break;
      case R.id.ocr_continuous_decode_failed:
        DecodeHandler.resetDecodeState();        
        try {
          activity.handleOcrContinuousDecode((OcrResultFailure) message.obj);
        } catch (NullPointerException e) {
          Log.w(TAG, "got bad OcrResultFailure", e);
        }
        if (state == State.CONTINUOUS) {
          restartOcrPreviewAndDecode();
        } else if (state == State.CONTINUOUS_FOCUSING) {
          state = State.CONTINUOUS_WAITING_FOR_AUTOFOCUS_TO_FINISH;
        }
        break;
      case R.id.ocr_continuous_decode_succeeded:
        DecodeHandler.resetDecodeState();
        try {
          activity.handleOcrContinuousDecode((OcrResult) message.obj);
        } catch (NullPointerException e) {
          // Continue
        }
        if (state == State.CONTINUOUS) {
          restartOcrPreviewAndDecode();
        } else if (state == State.CONTINUOUS_FOCUSING) {
          state = State.CONTINUOUS_WAITING_FOR_AUTOFOCUS_TO_FINISH;
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
    if (cameraManager != null) {
      cameraManager.stopPreview();
    }
    //Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
    try {
      //quit.sendToTarget(); // This always gives "sending message to a Handler on a dead thread"
      
      // Wait at most half a second; should be enough time, and onPause() will timeout quickly
      decodeThread.join(500L);
    } catch (InterruptedException e) {
      Log.w(TAG, "Caught InterruptedException in quitSyncronously()", e);
      // continue
    } catch (RuntimeException e) {
      Log.w(TAG, "Caught RuntimeException in quitSyncronously()", e);
      // continue
    } catch (Exception e) {
      Log.w(TAG, "Caught unknown Exception in quitSynchronously()", e);
    }

    // Be absolutely sure we don't send any queued up messages
    removeMessages(R.id.auto_focus);
    removeMessages(R.id.ocr_continuous_decode);
    removeMessages(R.id.ocr_decode);

  }

  /**
   *  Start the preview, but don't try to OCR anything until the user presses the shutter button.
   */
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
  
  /**
   *  Send a decode request for realtime OCR mode
   */
  private void restartOcrPreviewAndDecode() {
    // Continue capturing camera frames
    cameraManager.startPreview();
    
    // Continue requesting decode of images
    cameraManager.requestOcrDecode(decodeThread.getHandler(), R.id.ocr_continuous_decode);
    activity.drawViewfinder();    
  }

  /**
   * Request OCR on the current preview frame. 
   */
  private void ocrDecode() {
    state = State.PREVIEW_PAUSED;

    
    cameraManager.requestOcrDecode(decodeThread.getHandler(), R.id.ocr_decode);
  }
  
  /**
   * Request OCR when the hardware shutter button is clicked.
   */
  void hardwareShutterButtonClick() {
    // Ensure that we're not in continuous recognition mode
    if (state == State.PREVIEW || state == State.PREVIEW_FOCUSING) {
      ocrDecode();
    }
  }
  
  /**
   * Request OCR when the on-screen shutter button is clicked.
   */
  void shutterButtonClick() {
    // Disable further clicks on this button until OCR request is finished
    activity.setShutterButtonClickable(false);
    ocrDecode();
  }
  
  /**
   * Request autofocus from the CameraManager if we're in an appropriate state.
   * 
   * @param message The message to deliver
   */
  private void requestAutofocus(int message) {
    if (state == State.PREVIEW || state == State.CONTINUOUS){
      if (state == State.PREVIEW) {
        state = State.PREVIEW_FOCUSING;
      } else if (state == State.CONTINUOUS){
        state = State.CONTINUOUS_FOCUSING;
      }
      cameraManager.requestAutoFocus(this, message);
    } else {
      // If we're bumping up against a user-requested focus, enqueue another focus request,
      // otherwise stop autofocusing until the next restartOcrPreview()
      if (state == State.PREVIEW_FOCUSING && message == R.id.auto_focus) { 
        //Log.d(TAG, "focusing now, so Requesting a new delayed autofocus");
        requestDelayedAutofocus(CaptureActivity.AUTOFOCUS_FAILURE_INTERVAL_MS, message);
      } else if (state == State.CONTINUOUS_FOCUSING && message == R.id.auto_focus) {
        requestDelayedAutofocus(CaptureActivity.AUTOFOCUS_FAILURE_INTERVAL_MS, message);
      } else if (message == R.id.auto_focus) {
        isAutofocusLoopStarted = false;
      }
    }
  }
  
  /**
   * Request autofocus after the given delay.
   * 
   * @param delay The delay, in milliseconds, until autofocus will be requested
   * @param message The message to deliver
   */
  void requestDelayedAutofocus(final long delay, final int message) {
    Handler autofocusHandler = new Handler(); 
    autofocusHandler.postDelayed(new Runnable() {
      public void run() {
        requestAutofocus(message);
      }
    }, delay);
  } 
}
