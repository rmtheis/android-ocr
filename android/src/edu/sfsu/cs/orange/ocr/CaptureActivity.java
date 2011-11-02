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

//import com.google.zxing.ResultMetadataType;
//import com.google.zxing.ResultPoint;
import edu.sfsu.cs.orange.ocr.BeepManager;

import com.googlecode.tesseract.android.TessBaseAPI;

import edu.sfsu.cs.orange.ocr.camera.CameraManager;
import edu.sfsu.cs.orange.ocr.camera.ShutterButton;
import edu.sfsu.cs.orange.ocr.HelpActivity;
import edu.sfsu.cs.orange.ocr.OcrResult;
import edu.sfsu.cs.orange.ocr.PreferencesActivity;
import edu.sfsu.cs.orange.ocr.language.LanguageCodeHelper;
import edu.sfsu.cs.orange.ocr.language.TranslateAsyncTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * This activity opens the camera and does the actual scanning on a background thread.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback, 
  ShutterButton.OnShutterButtonListener {

  private static final String TAG = CaptureActivity.class.getSimpleName();
  
  // Note these values might be overridden by the ones in preferences.xml
  public static final String DEFAULT_SOURCE_LANGUAGE_CODE = "eng";
  public static final String DEFAULT_TARGET_LANGUAGE_CODE = "es";
  public static final boolean DEFAULT_TOGGLE_CONTINUOUS = false;
  public static final String DEFAULT_ACCURACY_VS_SPEED_MODE = PreferencesActivity.AVS_MODE_MOST_ACCURATE;
  public static final String DEFAULT_CHARACTER_BLACKLIST = "";
  public static final String DEFAULT_CHARACTER_WHITELIST = "";
  public static final String DEFAULT_PAGE_SEGMENTATION_MODE = "Auto";
  public static final String DEFAULT_TRANSLATOR = PreferencesActivity.TRANSLATOR_GOOGLE;

  // Minimum mean confidence score necessary to not reject single-shot OCR result
  public static final int MINIMUM_MEAN_CONFIDENCE = 0; // 0 means don't reject any scored results
  
  // Length of time between subsequent autofocus requests. Used in CaptureActivityHandler.
  static final long CONTINUOUS_AUTOFOCUS_INTERVAL_MS = 4000L; // originally 1500L
  static final long PREVIEW_AUTOFOCUS_INTERVAL_MS = 6000L; // originally 1500L
  
  // Context menu
  private static final int SETTINGS_ID = Menu.FIRST;
  private static final int ABOUT_ID = Menu.FIRST + 1;
  
  // Options menu, for copy to clipboard
  private static final int OPTIONS_COPY_RECOGNIZED_TEXT_ID = Menu.FIRST;
  private static final int OPTIONS_COPY_TRANSLATED_TEXT_ID = Menu.FIRST + 1;

  private CaptureActivityHandler handler;
  private ViewfinderView viewfinderView;
  private SurfaceView surfaceView;
  private SurfaceHolder surfaceHolder;
  private TextView statusViewBottom;
  private TextView statusViewTop;
  private TextView ocrResultView;
  private TextView translationView;
  private View cameraButtonView;
  private View resultView;
  private View progressView;
  private OcrResult lastResult;
  private Bitmap lastBitmap;
  private boolean hasSurface;
  private BeepManager beepManager;
  private TessBaseAPI baseApi; // Java interface for the Tesseract OCR engine
  private String sourceLanguageCodeOcr; // ISO 639-3 language code
  private String sourceLanguageReadable; // Language name, for example, "English"
  private String sourceLanguageCodeTranslation; // ISO 639-1 language code
  private String targetLanguageCodeTranslation; // ISO 639-1 language code
  private String targetLanguageReadable; // Language name, for example, "English"
  private int pageSegmentationMode = TessBaseAPI.PSM_AUTO;
  private Integer accuracyVsSpeedMode = TessBaseAPI.AVS_MOST_ACCURATE;
  private String characterBlacklist;
  private String characterWhitelist;
  private ShutterButton shutterButton;
//  private ToggleButton torchButton;
  private boolean isTranslationActive; // Whether we want to show translations
  private boolean isContinuousModeActive; // Whether we are doing OCR in continuous mode
  private SharedPreferences prefs;
  private OnSharedPreferenceChangeListener listener;
  private ProgressDialog dialog; // for initOcr - language download & unzip
  private ProgressDialog indeterminateDialog; // also for initOcr - init OCR engine
  private boolean isEngineReady;
  private boolean isPaused;

  Handler getHandler() {
    return handler;
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    showHelpOnFirstLaunch();
    
    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    setContentView(R.layout.capture); // TODO update layout-ldpi/capture.xml to add shutter button, etc. 

    CameraManager.init(this);
    viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
    cameraButtonView = findViewById(R.id.camera_button_view);
    resultView = findViewById(R.id.result_view);
    
    statusViewBottom = (TextView) findViewById(R.id.status_view_bottom);
    registerForContextMenu(statusViewBottom);
    statusViewTop = (TextView) findViewById(R.id.status_view_top);
    registerForContextMenu(statusViewTop);
    
    handler = null;
    lastResult = null;
    hasSurface = false;
//    inactivityTimer = new InactivityTimer(this);
    beepManager = new BeepManager(this);
    
    // Camera shutter button
    shutterButton = (ShutterButton) findViewById(R.id.shutter_button);
    shutterButton.setOnShutterButtonListener(this);
    
//    // Camera light toggle button
//    torchButton = (ToggleButton) findViewById(R.id.torch_button);
//    torchButton.setOnClickListener(new OnClickListener() {
//      public void onClick(View v) {
//        if (!isPaused) {
//          CameraManager.get().toggleLight();
//        }
//      }
//    });
    
    ocrResultView = (TextView) findViewById(R.id.ocr_result_text_view);
    registerForContextMenu(ocrResultView);
    translationView = (TextView) findViewById(R.id.translation_text_view);
    registerForContextMenu(translationView);
    
    progressView = (View) findViewById(R.id.indeterminate_progress_indicator_view);

    isEngineReady = false;
    
    // Set listener to change the size of the viewfinder rectangle.
    viewfinderView.setOnTouchListener(new View.OnTouchListener() {
      int lastX = -1;
      int lastY = -1;

      @Override
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          lastX = -1;
          lastY = -1;
          return true;
        case MotionEvent.ACTION_MOVE:
          int currentX = (int) event.getX();
          int currentY = (int) event.getY();

          try {
            Rect rect = CameraManager.get().getFramingRect();

            final int BUFFER = 50;
            final int BIG_BUFFER = 60;
            if (lastX >= 0) {
              // Adjust the size of the viewfinder rectangle. Check if the touch event occurs in the corner areas first, because the regions overlap.
              if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                  && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                // Top left corner: adjust both top and left sides
                CameraManager.get().adjustFramingRect( 2 * (lastX - currentX), 2 * (lastY - currentY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER)) 
                  && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                // Top right corner: adjust both top and right sides
                CameraManager.get().adjustFramingRect( 2 * (currentX - lastX), 2 * (lastY - currentY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                  && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                // Bottom left corner: adjust both bottom and left sides
                CameraManager.get().adjustFramingRect(2 * (lastX - currentX), 2 * (currentY - lastY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER)) 
                  && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                // Bottom right corner: adjust both bottom and right sides
                CameraManager.get().adjustFramingRect(2 * (currentX - lastX), 2 * (currentY - lastY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.left - BUFFER && currentX <= rect.left + BUFFER) || (lastX >= rect.left - BUFFER && lastX <= rect.left + BUFFER))
                  && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                // Adjusting left side: event falls within BUFFER pixels of left side, and between top and bottom side limits
                CameraManager.get().adjustFramingRect(2 * (lastX - currentX), 0);
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.right - BUFFER && currentX <= rect.right + BUFFER) || (lastX >= rect.right - BUFFER && lastX <= rect.right + BUFFER))
                  && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                // Adjusting right side: event falls within BUFFER pixels of right side, and between top and bottom side limits
                CameraManager.get().adjustFramingRect(2 * (currentX - lastX), 0);
                viewfinderView.removeResultText();
              } else if (((currentY <= rect.top + BUFFER && currentY >= rect.top - BUFFER) || (lastY <= rect.top + BUFFER && lastY >= rect.top - BUFFER))
                  && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                // Adjusting top side: event falls within BUFFER pixels of top side, and between left and right side limits
                CameraManager.get().adjustFramingRect(0, 2 * (lastY - currentY));
                viewfinderView.removeResultText();
              } else if (((currentY <= rect.bottom + BUFFER && currentY >= rect.bottom - BUFFER) || (lastY <= rect.bottom + BUFFER && lastY >= rect.bottom - BUFFER))
                  && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                // Adjusting bottom side: event falls within BUFFER pixels of bottom side, and between left and right side limits
                CameraManager.get().adjustFramingRect(0, 2 * (currentY - lastY));
                viewfinderView.removeResultText();
              }     
            }
          } catch (NullPointerException e) {
            Log.e(TAG, "Framing rect not available", e);
          }
          v.invalidate();
          lastX = currentX;
          lastY = currentY;
          return true;
        case MotionEvent.ACTION_UP:
          lastX = -1;
          lastY = -1;
          return true;
        }
        return false;
      }
    });
  }

//  public boolean getTorchButtonState() {
//    return torchButton.isChecked();
//  }
//  
//  public void setTorchButtonState(boolean enabled) {
//    torchButton.setChecked(enabled);
//  }
  
  /**
   * Called when the shutter button is pressed in continuous mode.
   */
  void onShutterButtonPressContinuous() {
    isPaused = true;
    handler.stop();  
    beepManager.playBeepSoundAndVibrate();
    statusViewBottom.setText("");
    statusViewBottom.setTextSize(14);
    statusViewBottom.setTextColor(getResources().getColor(R.color.status_text));
//    torchButton.setVisibility(View.GONE);
    shutterButton.setVisibility(View.GONE);
    boolean nullText = false;
    if (isTranslationActive) {
      try {
        lastResult.getText();
      } catch (NullPointerException e) {
        Toast toast = Toast.makeText(this, "OCR failed. Please try again.", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 0);
        toast.show();
        nullText = true;
        resumeContinuousDecoding();
      }
    }
    if (!nullText) {
      handleOcrDecode(lastResult);
    }
  }

  /**
   * Called to resume recognition after translation in continuous mode.
   */
  void resumeContinuousDecoding() {
    isPaused = false;
    resetStatusView();
    setStatusViewForContinuous();
    handler.resetState();
//    torchButton.setVisibility(View.VISIBLE);
    shutterButton.setVisibility(View.VISIBLE);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    
    resetStatusView();
    
    String previousSourceLanguageCodeOcr = sourceLanguageCodeOcr;
    retrievePreferences();
    beepManager.updatePrefs();
    
    // Set up the camera preview surface.
    surfaceView = (SurfaceView) findViewById(R.id.preview_view);
    surfaceHolder = surfaceView.getHolder();
    if (!hasSurface) {
      //Log.d(TAG, "onResume(): adding the callback...");
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    //Log.v(TAG,"Maximum memory available: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
    
    // Comment out the following block to test non-OCR functions without an SD card
    
    // Do OCR engine initialization, if necessary
    boolean doNewInit = (baseApi == null) || !sourceLanguageCodeOcr.equals(previousSourceLanguageCodeOcr);
    if (doNewInit) {      
      // Initialize the OCR engine
      File storageDirectory = getStorageDirectory();
      if (storageDirectory != null) {
        initOcrEngine(storageDirectory, sourceLanguageCodeOcr, sourceLanguageReadable);
      }
    } else {
      // We already have the engine initialized, so just start the camera.
      resumeOCR();
    }
  }
  
  void resumeOCR() {
    Log.d(TAG, "resumeOCR()");
    
    // This method is called when Tesseract has already been successfully initiliazed, so set 
    // isEngineReady = true here.
    isEngineReady = true;
    
    isPaused = false;

    if (handler != null) {
      handler.resetState();
    }
    if (baseApi != null) {
      baseApi.setPageSegMode(pageSegmentationMode);
      baseApi.setVariable(TessBaseAPI.VAR_ACCURACYVSPEED, accuracyVsSpeedMode.toString());
      baseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, characterBlacklist);
      baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, characterWhitelist);
    }

    if (hasSurface) {
      // The activity was paused but not stopped, so the surface still exists. Therefore
      // surfaceCreated() won't be called, so init the camera here.
      initCamera(surfaceHolder);
    }
  }

  public void surfaceCreated(SurfaceHolder holder) {
    Log.d(TAG, "surfaceCreated()");
    
    if (holder == null) {
      Log.e(TAG, "surfaceCreated gave us a null surface");
    }
    
    // Only initialize the camera if the OCR engine is ready to go.
    if (!hasSurface && isEngineReady) {
      Log.d(TAG, "surfaceCreated(): calling initCamera()...");
      initCamera(holder);
    }
    hasSurface = true;
  }
  
  private void initCamera(SurfaceHolder surfaceHolder) {
    Log.d(TAG, "initCamera()");
    try {

      // Open and initialize the camera
      CameraManager.get().openDriver(surfaceHolder);
      
      // Creating the handler starts the preview, which can also throw a RuntimeException.
      handler = new CaptureActivityHandler(this, baseApi, isContinuousModeActive);
      
    } catch (IOException ioe) {
      showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
    } catch (RuntimeException e) {
      // Barcode Scanner has seen crashes in the wild of this variety:
      // java.?lang.?RuntimeException: Fail to connect to camera service
      showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
    }   
  }
  
  @Override
  protected void onPause() {
    super.onPause();
    if (handler != null) {
      handler.quitSynchronously();
    }
    //    inactivityTimer.onPause();
    
    // Stop using the camera, to avoid conflicting with other camera-based apps
    CameraManager.get().closeDriver();
  }

  void stopHandler() {
    if (handler != null) {
      handler.stop();
    }
  }

  @Override
  protected void onDestroy() {
    //    inactivityTimer.shutdown();
    if (baseApi != null) {
      baseApi.end();
    }
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {

      // First check if we're paused in continuous mode, and if so, just unpause.
      if (isPaused) {
        Log.d(TAG, "only resuming continuous recognition, not quitting...");
        resumeContinuousDecoding();
        return true;
      }

      // Exit the app if we're not viewing an OCR result.
      if (lastResult == null) {
        setResult(RESULT_CANCELED);
        finish();
        return true;
      } else {
        // Go back to previewing in regular OCR mode.
        resetStatusView();
        if (handler != null) {
          handler.sendEmptyMessage(R.id.restart_preview);
        }
        return true;
      }
    } else if (keyCode == KeyEvent.KEYCODE_CAMERA) {
      if (isContinuousModeActive) {
        onShutterButtonPressContinuous();
      } else {
        handler.hardwareShutterButtonClick();
      }
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_FOCUS) {      
      // Only perform autofocus if user is not holding down the button.
      if (event.getRepeatCount() == 0) {
        handler.requestDelayedAutofocus(500L, R.id.user_requested_auto_focus_done);
      }
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    //    MenuInflater inflater = getMenuInflater();
    //    inflater.inflate(R.menu.options_menu, menu);
    super.onCreateOptionsMenu(menu);
    menu.add(0, SETTINGS_ID, 0, "Settings").setIcon(android.R.drawable.ic_menu_preferences);
    menu.add(0, ABOUT_ID, 0, "About").setIcon(android.R.drawable.ic_menu_info_details);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
    case SETTINGS_ID: {
      intent = new Intent().setClass(this, PreferencesActivity.class);
      startActivity(intent);
      break;
    }
    case ABOUT_ID: {
      intent = new Intent(this, HelpActivity.class);
      intent.putExtra(HelpActivity.REQUESTED_PAGE_KEY, HelpActivity.ABOUT_PAGE);
      startActivity(intent);
      break;
    }
    }
    return super.onOptionsItemSelected(item);
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    hasSurface = false;
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
  }
  
  private void retrievePreferences() {
      prefs = PreferenceManager.getDefaultSharedPreferences(this);
      
      // Retrieve from preferences, and set in this Activity, the language preferences
      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      setSourceLanguage(prefs.getString(PreferencesActivity.KEY_SOURCE_LANGUAGE_PREFERENCE, CaptureActivity.DEFAULT_SOURCE_LANGUAGE_CODE));
      setTargetLanguage(prefs.getString(PreferencesActivity.KEY_TARGET_LANGUAGE_PREFERENCE, CaptureActivity.DEFAULT_TARGET_LANGUAGE_CODE));
      isTranslationActive = prefs.getBoolean(PreferencesActivity.KEY_TOGGLE_TRANSLATION, false);
      
      // Retrieve from preferences, and set in this Activity, the capture mode preference
      if (prefs.getBoolean(PreferencesActivity.KEY_CONTINUOUS_PREVIEW, CaptureActivity.DEFAULT_TOGGLE_CONTINUOUS)) {
        isContinuousModeActive = true;
      } else {
        isContinuousModeActive = false;
      }

      // Retrieve from preferences, and set in this Activity, the page segmentation mode preference
      String[] pageSegmentationModes = getResources().getStringArray(R.array.pagesegmentationmodes);
      String pageSegmentationModeName = prefs.getString(PreferencesActivity.KEY_PAGE_SEGMENTATION_MODE, pageSegmentationModes[0]);
      if (pageSegmentationModeName.equals(pageSegmentationModes[0])) {
        pageSegmentationMode = TessBaseAPI.PSM_AUTO;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[1])) {
        pageSegmentationMode = TessBaseAPI.PSM_SINGLE_BLOCK;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[2])) {
        pageSegmentationMode = TessBaseAPI.PSM_SINGLE_CHAR;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[3])) {
        pageSegmentationMode = TessBaseAPI.PSM_SINGLE_COLUMN;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[4])) {
        pageSegmentationMode = TessBaseAPI.PSM_SINGLE_LINE;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[5])) {
        pageSegmentationMode = TessBaseAPI.PSM_SINGLE_WORD;
      }

      // Retrieve from preferences, and set in this Activity, the accuracy vs. speed preference
      String accuracyVsSpeedModeName = prefs.getString(PreferencesActivity.KEY_ACCURACY_VS_SPEED_MODE, PreferencesActivity.AVS_MODE_MOST_ACCURATE);
      if (accuracyVsSpeedModeName.equals(PreferencesActivity.AVS_MODE_FASTEST)) {
        accuracyVsSpeedMode = TessBaseAPI.AVS_FASTEST;
      } else {
        accuracyVsSpeedMode = TessBaseAPI.AVS_MOST_ACCURATE;
      }
      
      // Retrieve from preferences, and set in this Activity, the character blacklist and whitelist
      characterBlacklist = prefs.getString(PreferencesActivity.KEY_CHARACTER_BLACKLIST, CaptureActivity.DEFAULT_CHARACTER_BLACKLIST);
      characterWhitelist = prefs.getString(PreferencesActivity.KEY_CHARACTER_WHITELIST, CaptureActivity.DEFAULT_CHARACTER_WHITELIST);
      
      prefs.registerOnSharedPreferenceChangeListener(listener);    
  }

  private boolean setSourceLanguage(String languageCode) {
    sourceLanguageCodeOcr = languageCode;
    sourceLanguageCodeTranslation = LanguageCodeHelper.mapLanguageCode(languageCode);
    sourceLanguageReadable = LanguageCodeHelper.getLanguageName(this, languageCode);
    return true;
  }

  private boolean setTargetLanguage(String languageCode) {
    targetLanguageCodeTranslation = languageCode;
    targetLanguageReadable = LanguageCodeHelper.getTranslationLanguageName(this, languageCode);
    return true;
  }

  private File getStorageDirectory() {
    //Log.d(TAG, "getStorageDirectory(): API level is " + Integer.valueOf(android.os.Build.VERSION.SDK_INT));
    
    String state = null;
    try {
      state = Environment.getExternalStorageState();
    } catch (RuntimeException e) {
      Log.e(TAG, "Is the SD card visible?", e);
      showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable.");
    }
    
    if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
        // We can read and write the media
    	if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) > 7) {
    	    // For Android 2.2 and above
          return getExternalFilesDir(Environment.MEDIA_MOUNTED);
        } else {
          // For Android 2.1 and below, explicitly give the path as, for example,
          // "/mnt/sdcard/Android/data/edu.sfsu.cs.orange.ocr/files/"
          return new File(Environment.getExternalStorageDirectory().toString() + File.separator + 
                  "Android" + File.separator + "data" + File.separator + getPackageName() + 
                  File.separator + "files" + File.separator);
        }
    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
    	// We can only read the media
    	Log.e(TAG, "External storage is read-only");
      showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable for data storage.");
    } else {
    	// Something else is wrong. It may be one of many other states, but all we need
      // to know is we can neither read nor write
    	Log.e(TAG, "External storage is unavailable");
    	showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable.");
    }
    return null;
  }

  private void initOcrEngine(File storageRoot, String languageCode, String languageName) {
    //statusViewTop.setText("Recognition language: " + sourceLanguageReadable);
    
    isEngineReady = false;
    
    if (dialog != null) {
      dialog.dismiss();
    }
    dialog = new ProgressDialog(this);
    indeterminateDialog = new ProgressDialog(this);
    indeterminateDialog.setTitle("Please wait");
    indeterminateDialog.setMessage("Initializing OCR engine for " + languageName + "...");
    indeterminateDialog.setCancelable(false);
    indeterminateDialog.show();
    
    if (handler != null) {
      handler.quitSynchronously();     
    }
  
    // Start AsyncTask to install language data and init OCR
    baseApi = new TessBaseAPI();
    new OcrInitAsyncTask(this, baseApi, dialog, indeterminateDialog, languageCode, languageName)
      .execute(storageRoot.toString()); // TODO don't pass the dialog here
  }

  boolean handleOcrDecode(OcrResult ocrResult) {
    lastResult = ocrResult;
    
    try {
      // Test whether the result is null
      ocrResult.getText();
    } catch (NullPointerException e) {
      Toast toast = Toast.makeText(this, "OCR failed. Please try again.", Toast.LENGTH_SHORT);
      toast.setGravity(Gravity.TOP, 0, 0);
      toast.show();
      resumeContinuousDecoding();
      return false;
    }
    
    // Turn off capture-related UI elements
    shutterButton.setVisibility(View.GONE);
    statusViewBottom.setVisibility(View.GONE);
    statusViewTop.setVisibility(View.GONE);
    cameraButtonView.setVisibility(View.GONE);
    viewfinderView.setVisibility(View.GONE);
    resultView.setVisibility(View.VISIBLE);
    
//    // Disable light
//    CameraManager.get().disableLight();

    ImageView bitmapImageView = (ImageView) findViewById(R.id.image_view);
    lastBitmap = ocrResult.getBitmap();
    if (lastBitmap == null) {
      bitmapImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
          R.drawable.launcher_icon));
    } else {
      bitmapImageView.setImageBitmap(lastBitmap);
    }

    TextView sourceLanguageTextView = (TextView) findViewById(R.id.source_language_text_view);
    sourceLanguageTextView.setText(sourceLanguageReadable);
    TextView ocrResultTextView = (TextView) findViewById(R.id.ocr_result_text_view);
    //CharSequence displayContents = resultHandler.getDisplayContents();
    ocrResultTextView.setText(ocrResult.getText());
    // Crudely scale betweeen 22 and 32 -- bigger font for shorter text
    int scaledSize = Math.max(22, 32 - ocrResult.getText().length() / 4);
    ocrResultTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);

    TextView translationLanguageLabelTextView = (TextView) findViewById(R.id.translation_language_label_text_view);
    TextView translationLanguageTextView = (TextView) findViewById(R.id.translation_language_text_view);
    TextView translationTextView = (TextView) findViewById(R.id.translation_text_view);
    if (isTranslationActive) {
      // Handle translation text fields
      translationLanguageLabelTextView.setVisibility(View.VISIBLE);
      translationLanguageTextView.setText(targetLanguageReadable);
      translationLanguageTextView.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL), Typeface.NORMAL);
      translationLanguageTextView.setVisibility(View.VISIBLE);

      // Activate/re-activate the indeterminate progress indicator
      translationTextView.setVisibility(View.GONE);
      progressView.setVisibility(View.VISIBLE);
      setProgressBarVisibility(true);
      
      // Get the translation asynchronously
      new TranslateAsyncTask(this, sourceLanguageCodeTranslation, targetLanguageCodeTranslation, 
          ocrResult.getText()).execute();
    } else {
      translationLanguageLabelTextView.setVisibility(View.GONE);
      translationLanguageTextView.setVisibility(View.GONE);
      translationTextView.setVisibility(View.GONE);
      progressView.setVisibility(View.GONE);
      setProgressBarVisibility(false);
    }
    return true;
  }
  
  void handleOcrContinuousDecode(OcrResult ocrResult) {
   
    lastResult = ocrResult;
    
    // Send an OcrResultText object to the ViewfinderView for text rendering
    viewfinderView.addResultText(new OcrResultText(ocrResult.getText(), 
                                                   ocrResult.getWordConfidences(),
                                                   ocrResult.getMeanConfidence(),
                                                   ocrResult.getCharacterBoundingBoxes(),
                                                   ocrResult.getWordBoundingBoxes()));
    
    // Display the recognized text on the screen
    statusViewTop.setText(ocrResult.getText());
    int scaledSize = Math.max(22, 32 - ocrResult.getText().length() / 4);
    statusViewTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
    statusViewTop.setTextColor(Color.BLACK);
    statusViewTop.setBackgroundResource(R.color.white);
    Integer meanConfidence = ocrResult.getMeanConfidence();
    statusViewTop.getBackground().setAlpha(meanConfidence * (255 / 100));
    
    // Display recognition-related metadata at the bottom of the screen
    long recognitionTimeRequired = ocrResult.getRecognitionTimeRequired();
    statusViewBottom.setTextSize(14);
    statusViewBottom.setText("OCR: " + sourceLanguageReadable + " - Mean confidence: " + 
        meanConfidence.toString() + " - Time required: " + recognitionTimeRequired + " ms");
  }
  
  /**
   * Version of handleOcrContinuousDecode for failed OCR requests
   */
  void handleOcrContinuousDecode(OcrResultFailure obj) {
    lastResult = null;
    viewfinderView.removeResultText();
    
    // Reset the text in the recognized text box.
    statusViewTop.setText("");
    
    // Color text delimited by '-' as red.
    statusViewBottom.setTextSize(14);
    CharSequence cs = setSpanBetweenTokens("OCR: " + sourceLanguageReadable + " - OCR failed - Time required: " 
        + obj.getTimeRequired() + " ms", "-", new ForegroundColorSpan(0xFFFF0000));
    statusViewBottom.setText(cs);
  }
  
  /**
   * Given either a Spannable String or a regular String and a token, apply
   * the given CharacterStyle to the span between the tokens.
   * 
   * NOTE: This method was adapted from:
   *  http://www.androidengineer.com/2010/08/easy-method-for-formatting-android.html
   * 
   * <p>
   * For example, {@code setSpanBetweenTokens("Hello ##world##!", "##", new
   * ForegroundColorSpan(0xFFFF0000));} will return a CharSequence {@code
   * "Hello world!"} with {@code world} in red.
   * 
   */
  private CharSequence setSpanBetweenTokens(CharSequence text, String token,
      CharacterStyle... cs) {
    // Start and end refer to the points where the span will apply
    int tokenLen = token.length();
    int start = text.toString().indexOf(token) + tokenLen;
    int end = text.toString().indexOf(token, start);

    if (start > -1 && end > -1) {
      // Copy the spannable string to a mutable spannable string
      SpannableStringBuilder ssb = new SpannableStringBuilder(text);
      for (CharacterStyle c : cs)
        ssb.setSpan(c, start, end, 0);
      text = ssb;
    }
    return text;
  }
  
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    if (v.equals(ocrResultView)) {
      menu.add(Menu.NONE, OPTIONS_COPY_RECOGNIZED_TEXT_ID, Menu.NONE, "Copy recognized text");
    } else if (v.equals(translationView)){
      menu.add(Menu.NONE, OPTIONS_COPY_TRANSLATED_TEXT_ID, Menu.NONE, "Copy translated text");
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    switch (item.getItemId()) {

    case OPTIONS_COPY_RECOGNIZED_TEXT_ID:
        clipboardManager.setText(ocrResultView.getText());
      if (clipboardManager.hasText()) {
        Toast toast = Toast.makeText(this, "Text copied.", Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
      }
      return true;
    case OPTIONS_COPY_TRANSLATED_TEXT_ID:
        clipboardManager.setText(translationView.getText());
      if (clipboardManager.hasText()) {
        Toast toast = Toast.makeText(this, "Text copied.", Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
      }
      return true;
    default:
      return super.onContextItemSelected(item);
    }
  }

  private void resetStatusView() {
    resultView.setVisibility(View.GONE);
    statusViewBottom.setText("");
    statusViewBottom.setTextSize(14);
    statusViewBottom.setTextColor(getResources().getColor(R.color.status_text));
    statusViewBottom.setVisibility(View.VISIBLE);
    statusViewTop.setText("");
    statusViewTop.setTextSize(14);
    statusViewTop.setVisibility(View.VISIBLE);
    viewfinderView.setVisibility(View.VISIBLE);
    cameraButtonView.setVisibility(View.VISIBLE);
    shutterButton.setVisibility(View.VISIBLE);
//    torchButton.setVisibility(View.VISIBLE);
    lastResult = null;
  }
  
  void showLanguageName() {   
    Toast toast = Toast.makeText(this, "OCR: " + sourceLanguageReadable, Toast.LENGTH_LONG);
    toast.setGravity(Gravity.TOP, 0, 0);
    toast.show();
  }
  
  void setStatusViewForContinuous() {
    viewfinderView.removeResultText();
    statusViewBottom.setText("OCR: " + sourceLanguageReadable + " - waiting for OCR...");
  }
  
  void setButtonVisibility(boolean visible) {
    if (visible == true) {
      shutterButton.setVisibility(View.VISIBLE);
//      torchButton.setVisibility(View.VISIBLE);
    } else {
      shutterButton.setVisibility(View.GONE);
//      torchButton.setVisibility(View.GONE);
    }
  }
  
  void setShutterButtonClickable(boolean clickable) {
    shutterButton.setClickable(clickable);
  }

  void drawViewfinder() {
    viewfinderView.drawViewfinder();
  }
  
  @Override
  public void onShutterButtonClick(ShutterButton b) {
    if (isContinuousModeActive) {
      onShutterButtonPressContinuous();
    } else {
      if (handler != null) {
        handler.shutterButtonClick();
      } else {
        // Null handler. Why?
        showErrorMessage("Null handler error", "Please report this error along with what type of device you are using.");
      }
    }
  }

  @Override
  public void onShutterButtonFocus(ShutterButton b, boolean pressed) {
    requestDelayedAutofocus();
  }
  
  private void requestDelayedAutofocus() {
    // Wait 350 ms before focusing to avoid interfering with quick button presses when
    // the user just wants to take a picture without focusing.
    if (handler != null) {
      handler.requestDelayedAutofocus(350L, R.id.user_requested_auto_focus_done);
    }
  }
  
  /**
   * We want the help screen to be shown automatically the first time a new version of the app is
   * run. The easiest way to do this is to check android:versionCode from the manifest, and compare
   * it to a value stored as a preference.
   */
  private boolean showHelpOnFirstLaunch() {
    try {
      PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
      int currentVersion = info.versionCode;
      // Since we're paying to talk to the PackageManager anyway, it makes sense to cache the app
      // version name here for display in the about box later.
      //this.versionName = info.versionName;
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      int lastVersion = prefs.getInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, 0);
      if (currentVersion > lastVersion) {
        prefs.edit().putInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, currentVersion).commit();
        Intent intent = new Intent(this, HelpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        // Show the default page on a clean install, and the what's new page on an upgrade.
        String page = lastVersion == 0 ? HelpActivity.DEFAULT_PAGE : HelpActivity.WHATS_NEW_PAGE;
        intent.putExtra(HelpActivity.REQUESTED_PAGE_KEY, page);
        startActivity(intent);
        return true;
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
    }
    return false;
  }
  
  void showErrorMessage(String title, String message) {
	  new AlertDialog.Builder(this)
	    .setTitle(title)
	    .setMessage(message)
	    .setOnCancelListener(new FinishListener(this))
	    .setPositiveButton( "Done", new FinishListener(this))
	    .show();
  }

//  /**
//   * A valid barcode has been found, so give an indication of success and show the results.
//   *
//   * @param rawResult The contents of the barcode.
//   * @param barcode   A greyscale bitmap of the camera data which was decoded.
//   */
//  public void handleDecode(Result rawResult, Bitmap barcode) {
//    //inactivityTimer.onActivity();
//    //lastResult = rawResult; // Can't do this because we changed lastResult from being a Result to an ocrResult
//    ResultHandler resultHandler = ResultHandlerFactory.makeResultHandler(this, rawResult);
//    historyManager.addHistoryItem(rawResult, resultHandler);
//  
//    if (barcode == null) {
//      // This is from history -- no saved barcode
//      handleDecodeInternally(rawResult, resultHandler, null);
//    } else {
//      beepManager.playBeepSoundAndVibrate();
//      drawResultPoints(barcode, rawResult);
//      switch (source) {
//        case NATIVE_APP_INTENT:
//        case PRODUCT_SEARCH_LINK:
//          handleDecodeExternally(rawResult, resultHandler, barcode);
//          break;
//        case ZXING_LINK:
//          if (returnUrlTemplate == null){
//            handleDecodeInternally(rawResult, resultHandler, barcode);
//          } else {
//            handleDecodeExternally(rawResult, resultHandler, barcode);
//          }
//          break;
//        case NONE:
//          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//          if (prefs.getBoolean(PreferencesActivity.KEY_BULK_MODE, false)) {
//            Toast.makeText(this, R.string.msg_bulk_mode_scanned, Toast.LENGTH_SHORT).show();
//            // Wait a moment or else it will scan the same barcode continuously about 3 times
//            if (handler != null) {
//              handler.sendEmptyMessageDelayed(R.id.restart_preview, BULK_MODE_SCAN_DELAY_MS);
//            }
//            resetStatusView();
//          } else {
//            handleDecodeInternally(rawResult, resultHandler, barcode);
//          }
//          break;
//      }
//    }
//  }

//  /**
//   * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
//   *
//   * @param barcode   A bitmap of the captured image.
//   * @param rawResult The decoded results which contains the points to draw.
//   */
//  private void drawResultPoints(Bitmap barcode, Result rawResult) {
//    ResultPoint[] points = rawResult.getResultPoints();
//    if (points != null && points.length > 0) {
//      Canvas canvas = new Canvas(barcode);
//      Paint paint = new Paint();
//      paint.setColor(getResources().getColor(R.color.result_image_border));
//      paint.setStrokeWidth(3.0f);
//      paint.setStyle(Paint.Style.STROKE);
//      Rect border = new Rect(2, 2, barcode.getWidth() - 2, barcode.getHeight() - 2);
//      canvas.drawRect(border, paint);
//  
//      paint.setColor(getResources().getColor(R.color.result_points));
//      if (points.length == 2) {
//        paint.setStrokeWidth(4.0f);
//        drawLine(canvas, paint, points[0], points[1]);
//      } else if (points.length == 4 &&
//                 (rawResult.getBarcodeFormat().equals(BarcodeFormat.UPC_A) ||
//                  rawResult.getBarcodeFormat().equals(BarcodeFormat.EAN_13))) {
//        // Hacky special case -- draw two lines, for the barcode and metadata
//        drawLine(canvas, paint, points[0], points[1]);
//        drawLine(canvas, paint, points[2], points[3]);
//      } else {
//        paint.setStrokeWidth(10.0f);
//        for (ResultPoint point : points) {
//          canvas.drawPoint(point.getX(), point.getY(), paint);
//        }
//      }
//    }
//  }

//  private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b) {
//    canvas.drawLine(a.getX(), a.getY(), b.getX(), b.getY(), paint);
//  }

//  // Put up our own UI for how to handle the decoded contents.
//  private void handleDecodeInternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {
//    statusViewBottom.setVisibility(View.GONE);
//    statusViewTop.setVisibility(View.GONE);
//    cameraButtonView.setVisibility(View.GONE);
//    viewfinderView.setVisibility(View.GONE);
//    resultView.setVisibility(View.VISIBLE);
//  
//    ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
//    if (barcode == null) {
//      barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
//          R.drawable.launcher_icon));
//    } else {
//      barcodeImageView.setImageBitmap(barcode);
//    }
//  
//    TextView formatTextView = (TextView) findViewById(R.id.format_text_view);
//    formatTextView.setText(rawResult.getBarcodeFormat().toString());
//  
//    TextView typeTextView = (TextView) findViewById(R.id.type_text_view);
//    typeTextView.setText(resultHandler.getType().toString());
//  
//    DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
//    String formattedTime = formatter.format(new Date(rawResult.getTimestamp()));
//    TextView timeTextView = (TextView) findViewById(R.id.time_text_view);
//    timeTextView.setText(formattedTime);
//  
//  
//    TextView metaTextView = (TextView) findViewById(R.id.meta_text_view);
//    View metaTextViewLabel = findViewById(R.id.meta_text_view_label);
//    metaTextView.setVisibility(View.GONE);
//    metaTextViewLabel.setVisibility(View.GONE);
//    Map<ResultMetadataType,Object> metadata =
//        (Map<ResultMetadataType,Object>) rawResult.getResultMetadata();
//    if (metadata != null) {
//      StringBuilder metadataText = new StringBuilder(20);
//      for (Map.Entry<ResultMetadataType,Object> entry : metadata.entrySet()) {
//        if (DISPLAYABLE_METADATA_TYPES.contains(entry.getKey())) {
//          metadataText.append(entry.getValue()).append('\n');
//        }
//      }
//      if (metadataText.length() > 0) {
//        metadataText.setLength(metadataText.length() - 1);
//        metaTextView.setText(metadataText);
//        metaTextView.setVisibility(View.VISIBLE);
//        metaTextViewLabel.setVisibility(View.VISIBLE);
//      }
//    }
//  
//    TextView contentsTextView = (TextView) findViewById(R.id.contents_text_view);
//    CharSequence displayContents = resultHandler.getDisplayContents();
//    contentsTextView.setText(displayContents);
//    // Crudely scale betweeen 22 and 32 -- bigger font for shorter text
//    int scaledSize = Math.max(22, 32 - displayContents.length() / 4);
//    contentsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
//  
//    TextView supplementTextView = (TextView) findViewById(R.id.contents_supplement_text_view);
//    supplementTextView.setText("");
//    supplementTextView.setOnClickListener(null);
//    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
//        PreferencesActivity.KEY_SUPPLEMENTAL, true)) {
//      SupplementalInfoRetriever.maybeInvokeRetrieval(supplementTextView, resultHandler.getResult(),
//          handler, this);
//    }
//  
//    int buttonCount = resultHandler.getButtonCount();
//    ViewGroup buttonView = (ViewGroup) findViewById(R.id.result_button_view);
//    buttonView.requestFocus();
//    for (int x = 0; x < ResultHandler.MAX_BUTTON_COUNT; x++) {
//      TextView button = (TextView) buttonView.getChildAt(x);
//      if (x < buttonCount) {
//        button.setVisibility(View.VISIBLE);
//        button.setText(resultHandler.getButtonText(x));
//        button.setOnClickListener(new ResultButtonListener(resultHandler, x));
//      } else {
//        button.setVisibility(View.GONE);
//      }
//    }
//  
//    if (copyToClipboard && !resultHandler.areContentsSecure()) {
//      ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
//      clipboard.setText(displayContents);
//    }
//  }
//
//  // Briefly show the contents of the barcode, then handle the result outside Barcode Scanner.
//  private void handleDecodeExternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {
//    viewfinderView.drawResultBitmap(barcode);
//  
//    // Since this message will only be shown for a second, just tell the user what kind of
//    // barcode was found (e.g. contact info) rather than the full contents, which they won't
//    // have time to read.
//    statusViewBottom.setText(getString(resultHandler.getDisplayTitle()));
//    statusViewTop.setText("");
//  
//    if (copyToClipboard && !resultHandler.areContentsSecure()) {
//      ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
//      clipboard.setText(resultHandler.getDisplayContents());
//    }
//  
//    if (source == Source.NATIVE_APP_INTENT) {
//      // Hand back whatever action they requested - this can be changed to Intents.Scan.ACTION when
//      // the deprecated intent is retired.
//      Intent intent = new Intent(getIntent().getAction());
//      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
//      intent.putExtra(Intents.Scan.RESULT, rawResult.toString());
//      intent.putExtra(Intents.Scan.RESULT_FORMAT, rawResult.getBarcodeFormat().toString());
//      byte[] rawBytes = rawResult.getRawBytes();
//      if (rawBytes != null && rawBytes.length > 0) {
//        intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes);
//      }
//      Message message = Message.obtain(handler, R.id.return_scan_result);
//      message.obj = intent;
//      handler.sendMessageDelayed(message, INTENT_RESULT_DURATION);
//    } else if (source == Source.PRODUCT_SEARCH_LINK) {
//      // Reformulate the URL which triggered us into a query, so that the request goes to the same
//      // TLD as the scan URL.
//      Message message = Message.obtain(handler, R.id.launch_product_query);
//      int end = sourceUrl.lastIndexOf("/scan");
//      message.obj = sourceUrl.substring(0, end) + "?q=" +
//          resultHandler.getDisplayContents().toString() + "&source=zxing";
//      handler.sendMessageDelayed(message, INTENT_RESULT_DURATION);
//    } else if (source == Source.ZXING_LINK) {
//      // Replace each occurrence of RETURN_CODE_PLACEHOLDER in the returnUrlTemplate
//      // with the scanned code. This allows both queries and REST-style URLs to work.
//      Message message = Message.obtain(handler, R.id.launch_product_query);
//      message.obj = returnUrlTemplate.replace(RETURN_CODE_PLACEHOLDER,
//          resultHandler.getDisplayContents().toString());
//      handler.sendMessageDelayed(message, INTENT_RESULT_DURATION);
//    }
//  }
}
