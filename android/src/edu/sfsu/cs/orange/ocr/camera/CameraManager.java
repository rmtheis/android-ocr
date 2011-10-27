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

package edu.sfsu.cs.orange.ocr.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.SurfaceHolder;
import edu.sfsu.cs.orange.ocr.CaptureActivity;
import edu.sfsu.cs.orange.ocr.PlanarYUVLuminanceSource;
import edu.sfsu.cs.orange.ocr.PreferencesActivity;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

  private static final int MIN_FRAME_WIDTH = 50; // originally 240
  private static final int MIN_FRAME_HEIGHT = 20; // originally 240
  private static final int MAX_FRAME_WIDTH = 800; // originally 480
  private static final int MAX_FRAME_HEIGHT = 600; // originally 360

  private static CameraManager cameraManager;

  static final int SDK_INT; // Later we can use Build.VERSION.SDK_INT
  static {
    int sdkInt;
    try {
      sdkInt = Integer.parseInt(Build.VERSION.SDK);
    } catch (NumberFormatException nfe) {
      // Just to be safe
      sdkInt = 10000;
    }
    SDK_INT = sdkInt;
  }

  private final CaptureActivity activity;
  private final Context context;
  private final CameraConfigurationManager configManager;
  private Camera camera;
  private Rect framingRect;
  private Rect framingRectInPreview;
  private boolean initialized;
  private boolean previewing;
  private boolean reverseImage;
  private final boolean useOneShotPreviewCallback;
  /**
   * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
   * clear the handler so it will only receive one message.
   */
  private final PreviewCallback previewCallback;

  /** Autofocus callbacks arrive here, and are dispatched to the Handler which requested them. */
  private final AutoFocusCallback autoFocusCallback;

  /**
   * Initializes this static object with the Context of the calling Activity.
   *
   * @param context The Activity which wants to use the camera.
   */
  public static void init(CaptureActivity activity) {
    if (cameraManager == null) {
      cameraManager = new CameraManager(activity);
    }
  }
  
  /**
   * Deletes all state. We don't want to keep global variables around from one launch to another.
   */
  public static void destroy() {
    cameraManager = null;
  }
  
  /**
   * Gets the CameraManager singleton instance.
   *
   * @return A reference to the CameraManager singleton.
   */
  public static CameraManager get() {
    return cameraManager;
  }

  private CameraManager(CaptureActivity activity) {

    this.activity = activity;
    this.context = activity.getBaseContext();
    this.configManager = new CameraConfigurationManager(context);

    // Camera.setOneShotPreviewCallback() has a race condition in Cupcake, so we use the older
    // Camera.setPreviewCallback() on 1.5 and earlier. For Donut and later, we need to use
    // the more efficient one shot callback, as the older one can swamp the system and cause it
    // to run out of memory. We can't use SDK_INT because it was introduced in the Donut SDK.
    useOneShotPreviewCallback = Integer.parseInt(Build.VERSION.SDK) > 3; // 3 = Cupcake

    previewCallback = new PreviewCallback(configManager, useOneShotPreviewCallback);
    autoFocusCallback = new AutoFocusCallback();

  }

  /**
   * Opens the camera driver and initializes the hardware parameters.
   *
   * @param holder The surface object which the camera will draw preview frames into.
   * @throws IOException Indicates the camera driver failed to open.
   */
  public void openDriver(SurfaceHolder holder) throws IOException {
    if (camera == null) {
      camera = Camera.open();
      if (camera == null) {
        throw new IOException();
      }
    }
    camera.setPreviewDisplay(holder);
    if (!initialized) {
      initialized = true;
      configManager.initFromCameraParameters(camera);
    }
    configManager.setDesiredCameraParameters(camera);
    
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    reverseImage = prefs.getBoolean(PreferencesActivity.KEY_REVERSE_IMAGE, false);
    //    if (prefs.getBoolean(PreferencesActivity.KEY_FRONT_LIGHT, false)) {
    //      FlashlightManager.enableFlashlight();
    //    }
    
    //enableLight();
  }

  /**
   * Closes the camera driver if still in use.
   */
  public void closeDriver() {
    if (camera != null) {
      //FlashlightManager.disableFlashlight();
//      disableLight();
      
      camera.release();
      camera = null;

      // Make sure to clear these each time we close the camera, so that any scanning rect
      // requested by intent is forgotten.
      framingRect = null;
      framingRectInPreview = null;
    }
  }

//  public void toggleLight() {
//    Camera.Parameters parameters = camera.getParameters();
//    if (parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_OFF)) {
//      parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//      camera.setParameters(parameters);
//      
//      // If necessary, turn on the button to match the state of the light
//      if (!activity.getTorchButtonState()) {
//        activity.setTorchButtonState(true);
//      }
//      
////      // Save the flash state to SharedPreferences
////      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
////      prefs.edit().putBoolean(PreferencesActivity.KEY_TOGGLE_LIGHT, true).commit();
//      
//    } else if (parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_TORCH)) {
//      parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
//      camera.setParameters(parameters);
//      
//      // If necessary, turn off the button to match the state of the light
//      if (activity.getTorchButtonState()) {
//        activity.setTorchButtonState(false);
//      }
//      
////      // Save the flash state to SharedPreferences
////      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
////      prefs.edit().putBoolean(PreferencesActivity.KEY_TOGGLE_LIGHT, false).commit(); // TODO Change to use .apply() instead for API 9 and up, for everywhere we use .commit()
////      
//    }
//  }
  
//  /**
//   * Turn the light off.
//   */
//  public void disableLight() {
//    activity.setTorchButtonState(false);
//    
//    if (camera != null) {
//      Camera.Parameters parameters = camera.getParameters();
//      parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
//      camera.setParameters(parameters);
//    }
//  }
  
//  /**
//   * Turn the light on, if it was turned on the last time we used the camera.
//   */
//  public void enableLight() {
//    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
//    boolean enabled = prefs.getBoolean(PreferencesActivity.KEY_TOGGLE_LIGHT, false);
//    if (enabled) {
//      Camera.Parameters parameters = camera.getParameters();
//      parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//      camera.setParameters(parameters);
//      
//      // If necessary, turn on the button to match the state of the light
//      if (!activity.getTorchButtonState()) {
//        activity.setTorchButtonState(true);
//      }
//         
//      Log.d(TAG, "setting state of ToggleButton to match light");
//    } else {
//      // Be sure the light is off // TODO do we need this block?
//      Camera.Parameters parameters = camera.getParameters();
//      parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
//      camera.setParameters(parameters);
//      
//      // If necessary, turn off the button to match the state of the light
//      if (activity.getTorchButtonState()) {
//        activity.setTorchButtonState(false);
//      }
//    }
//  }
  
  /**
   * Asks the camera hardware to begin drawing preview frames to the screen.
   */
  public void startPreview() {
    if (camera != null && !previewing) {
      camera.startPreview();
      previewing = true;
    }
  }

  /**
   * Tells the camera to stop drawing preview frames.
   */
  public void stopPreview() {
    if (camera != null && previewing) {
      if (!useOneShotPreviewCallback) {
        camera.setPreviewCallback(null);
      }
//      disableLight();
      camera.stopPreview();
      previewCallback.setHandler(null, 0);
      autoFocusCallback.setHandler(null, 0);
      previewing = false;
    }
  }

  /**
   * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
   * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
   * respectively.
   *
   * @param handler The handler to send the message to.
   * @param message The what field of the message to be sent.
   */
  public void requestOcrDecode(Handler handler, int message) {
    if (camera != null && previewing) {
      previewCallback.setHandler(handler, message);
      if (useOneShotPreviewCallback) {
        camera.setOneShotPreviewCallback(previewCallback);
      } else {
        camera.setPreviewCallback(previewCallback); // Android 1.5 and earlier only // TODO Take this stuff out throughout, because we're not supporting Android 1.5
      }
    }
  }
  
  /**
   * Asks the camera hardware to perform an autofocus.
   *
   * @param handler The Handler to notify when the autofocus completes.
   * @param message The message to deliver.
   */
  public void requestAutoFocus(Handler handler, int message) {
    if (camera != null && previewing) {
      autoFocusCallback.setHandler(handler, message);
      camera.autoFocus(autoFocusCallback);
    }
  }
  
  /**
   * Calculates the framing rect which the UI should draw to show the user where to place the
   * barcode. This target helps with alignment as well as forces the user to hold the device
   * far enough away to ensure the image will be in focus.
   *
   * @return The rectangle to draw on screen in window coordinates.
   */
  public Rect getFramingRect() {
    if (framingRect == null) {
      if (camera == null) {
        return null;
      }
      Point screenResolution = configManager.getScreenResolution();
      int width = screenResolution.x * 3/5;
      if (width < MIN_FRAME_WIDTH) {
        width = MIN_FRAME_WIDTH;
      } else if (width > MAX_FRAME_WIDTH) {
        width = MAX_FRAME_WIDTH;
      }
      int height = screenResolution.y * 1/5;
      if (height < MIN_FRAME_HEIGHT) {
        height = MIN_FRAME_HEIGHT;
      } else if (height > MAX_FRAME_HEIGHT) {
        height = MAX_FRAME_HEIGHT;
      }
      int leftOffset = (screenResolution.x - width) / 2;
      int topOffset = (screenResolution.y - height) / 2;
      framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
    }
    return framingRect;
  }

  /**
   * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
   * not UI / screen.
   */
  public Rect getFramingRectInPreview() {
    if (framingRectInPreview == null) {
      Rect rect = new Rect(getFramingRect());
      Point cameraResolution = configManager.getCameraResolution();
      Point screenResolution = configManager.getScreenResolution();
      rect.left = rect.left * cameraResolution.x / screenResolution.x;
      rect.right = rect.right * cameraResolution.x / screenResolution.x;
      rect.top = rect.top * cameraResolution.y / screenResolution.y;
      rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
      framingRectInPreview = rect;
    }
    return framingRectInPreview;
  }

  public void adjustFramingRect(int deltaWidth, int deltaHeight) {
    Point screenResolution = configManager.getScreenResolution();
    
    // Set maximum and minimum sizes
    if ((framingRect.width() + deltaWidth > screenResolution.x - 4) || (framingRect.width() + deltaWidth < 50)) {
      deltaWidth = 0;
    }
    if ((framingRect.height() + deltaHeight > screenResolution.y - 4) || (framingRect.height() + deltaHeight < 50)) {
      deltaHeight = 0;
    }
    
    int newWidth = framingRect.width() + deltaWidth;
    int newHeight = framingRect.height() + deltaHeight;
    int leftOffset = (screenResolution.x - newWidth) / 2;
    int topOffset = (screenResolution.y - newHeight) / 2;
    framingRect = new Rect(leftOffset, topOffset, leftOffset + newWidth, topOffset + newHeight);
    framingRectInPreview = null;
  }
  
//  /**
//   * Allows third party apps to specify the scanning rectangle dimensions, rather than determine
//   * them automatically based on screen resolution.
//   *
//   * @param width The width in pixels to scan.
//   * @param height The height in pixels to scan.
//   */
//  public void setManualFramingRect(int width, int height) {
//    Point screenResolution = configManager.getScreenResolution();
//    if (width > screenResolution.x) {
//      width = screenResolution.x;
//    }
//    if (height > screenResolution.y) {
//      height = screenResolution.y;
//    }
//    int leftOffset = (screenResolution.x - width) / 2;
//    int topOffset = (screenResolution.y - height) / 2;
//    framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
//    //Log.d(TAG, "Calculated manual framing rect: " + framingRect);
//    framingRectInPreview = null;
//  }

  /**
   * A factory method to build the appropriate LuminanceSource object based on the format
   * of the preview buffers, as described by Camera.Parameters.
   *
   * @param data A preview frame.
   * @param width The width of the image.
   * @param height The height of the image.
   * @return A PlanarYUVLuminanceSource instance.
   */
  public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
    Rect rect = getFramingRectInPreview();
    int previewFormat = configManager.getPreviewFormat();
    String previewFormatString = configManager.getPreviewFormatString();

    switch (previewFormat) {
      // This is the standard Android format which all devices are REQUIRED to support.
      // In theory, it's the only one we should ever care about.
      case PixelFormat.YCbCr_420_SP:
      // This format has never been seen in the wild, but is compatible as we only care
      // about the Y channel, so allow it.
      case PixelFormat.YCbCr_422_SP:
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
            rect.width(), rect.height(), reverseImage);
      default:
        // The Samsung Moment incorrectly uses this variant instead of the 'sp' version.
        // Fortunately, it too has all the Y data up front, so we can read it.
        if ("yuv420p".equals(previewFormatString)) {
          return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
              rect.width(), rect.height(), reverseImage);
        }
    }
    throw new IllegalArgumentException("Unsupported picture format: " +
        previewFormat + '/' + previewFormatString);
  }

}
