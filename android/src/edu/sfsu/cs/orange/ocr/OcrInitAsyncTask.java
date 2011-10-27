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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.googlecode.tesseract.android.TessBaseAPI;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Asynchronously installs the language data required by the OCR engine, and initializes
 * the OCR engine.
 */
final class OcrInitAsyncTask extends AsyncTask<String, String, Boolean> {
  private static final String TAG = OcrInitAsyncTask.class.getSimpleName();

  private static final String DOWNLOAD_BASE = "http://tesseract-ocr.googlecode.com/files/";

  private CaptureActivity activity;
  private Context context;
  private TessBaseAPI baseApi;
  private ProgressDialog dialog;
  private ProgressDialog indeterminateDialog;
  private final String languageCode;
  private final String languageName;

  /**
   * 
   * @param context
   * @param baseApi 
   * @param dialog
   * @param indeterminateDialog 
   * @param languageName
   */
  OcrInitAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, ProgressDialog dialog, 
      ProgressDialog indeterminateDialog, String languageCode, String languageName) {
    this.activity = activity;
    this.context = activity.getBaseContext();
    this.baseApi = baseApi;
    this.dialog = dialog;
    this.indeterminateDialog = indeterminateDialog;
    this.languageCode = languageCode;
    this.languageName = languageName;
  }

  /**
   * 
   * @param params[0]
   *          pathname for the directory for storing the file to the SD card
   */
  protected Boolean doInBackground(String... params) {
    boolean installSuccess = false;
    File languageData;
    String destinationDirBase = params[0];
    String destinationFilename = languageCode + ".traineddata";

    // Check for, and create if necessary, folder to hold model data
    File modelRoot = new File(destinationDirBase + File.separator + "tessdata");
    if (!modelRoot.exists() && !modelRoot.mkdirs()) {
      Log.e(TAG, "Couldn't make directory " + modelRoot);
      return false;
    }

    // Create file to hold model data
    languageData = new File(modelRoot, destinationFilename);
    
    // Check if an incomplete download is present. If a *.download.gz file is there, delete it and
    // any half-unzipped language data file that may be there.
    File tempFile = new File(modelRoot, destinationFilename + ".download.gz");
    if (tempFile.exists()) {
      tempFile.delete();
      if (languageData.exists()) {
        languageData.delete();
      }
    }
    
    // Check whether model data already exists in the folder

    if (!languageData.exists()) {
      Log.i(TAG, "Language data for " + languageCode + " not found in " + modelRoot.toString());

      // Check assets for language data to install. If not present, download from Internet
      installSuccess = false;
      try {
        Log.i(TAG, "Checking for language data in application assets...");
        installSuccess = installFromAssets(destinationFilename + ".zip", modelRoot, languageData);
      } catch (IOException e) {
        Log.e(TAG, "IOException", e);
      } catch (Exception e) {
        Log.e(TAG, "Got exception", e);
      }

      if (!installSuccess) {
        // File was not packaged in assets, so download it
        Log.i(TAG, "Language Data not found in assets. Downloading...");
        try {
          installSuccess = downloadFile(destinationFilename, modelRoot, languageData);
          if (!installSuccess) {
            Log.e(TAG, "Download failed");
            return false;
          }
        } catch (IOException e) {
          Log.e(TAG, "IOException received in doInBackground. Is a network connection available?");
          return false;
        }
      }
    } else {
      Log.i(TAG, "Language data for " + languageCode + " already installed in " + modelRoot.toString());
      installSuccess = true;
    }
    
    // Dismiss the progress dialog box, revealing the indeterminate dialog box behind it
    dialog.dismiss();
    
    // Initialize the Tesseract OCR engine
    if (baseApi.init(destinationDirBase + File.separator, languageCode)) {
      return installSuccess;
    }
    return false;
  }  

  private boolean downloadFile(String sourceFilename, File modelRoot, File destinationFile) throws IOException {
    try {
      return downloadGzippedFileHttp(new URL(DOWNLOAD_BASE + sourceFilename + ".gz"), destinationFile);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Bad URL string.");
    }
  }

  private boolean downloadGzippedFileHttp(URL url, File destinationFile) throws IOException {
    Log.d(TAG, "downloadGzippedFileHttp(): url: " + url);
    Log.d(TAG, "downloadGzippedFileHttp(): destinationFilename: " + destinationFile.toString());

    // Send an HTTP GET request for the file
    Log.d(TAG, "downloadGzippedFileHttp(): Sending GET request...");
    publishProgress("Downloading language data for " + languageName + "...", "0");
    HttpURLConnection urlConnection = null;
    urlConnection = (HttpURLConnection) url.openConnection();
    urlConnection.setAllowUserInteraction(false);
    urlConnection.setInstanceFollowRedirects(true);
    urlConnection.setRequestMethod("GET");
    urlConnection.setDoOutput(true);
    urlConnection.connect();
    if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
      Log.e(TAG, "Did not get HTTP_OK response.");
      return false;
    }
    int fileSize = urlConnection.getContentLength();
    InputStream inputStream = urlConnection.getInputStream();
    File tempFile = new File(destinationFile.toString() + ".download.gz");

    // Stream the file contents to a local file temporarily
    Log.d(TAG, "downloadGzippedFileHttp(): Streaming download to a local file...");
    final int BUFFER = 8192;
    FileOutputStream fileOutputStream = null;
    Integer percentComplete;
    int percentCompleteLast = 0;
    try {
      fileOutputStream = new FileOutputStream(tempFile);
    } catch (FileNotFoundException e) {
      Log.e(TAG, "Exception received when opening FileOutputStream.", e);
    }
    int downloaded = 0;
    byte[] buffer = new byte[BUFFER];
    int bufferLength = 0;
    while ((bufferLength = inputStream.read(buffer, 0, BUFFER)) > 0) {
      fileOutputStream.write(buffer, 0, bufferLength);
      downloaded += bufferLength;
      percentComplete = (int) ((downloaded / (float) fileSize) * 100);
      if (percentComplete > percentCompleteLast) {    
        publishProgress("Downloading language data for " + languageName + "...", percentComplete.toString());
        percentCompleteLast = percentComplete;
      }
    }
    fileOutputStream.close();
    urlConnection.disconnect();

    // Uncompress the downloaded temporary file into place, and remove the temporary file
    try {
      return gunzip(tempFile, tempFile.toString().replace(".download.gz", ""));
    } catch (FileNotFoundException e) {
      Log.e(TAG, "File not available for unzipping.");
    } catch (IOException e) {
      Log.e(TAG, "Problem unzipping file.");
    }
    return false;
  }

  /**
   * Unzips the given Gzipped file to the given destination, and deletes the gzipped file.
   * 
   * @param zippedFile
   *          the gzipped file to be uncompressed
   * @param outFilePath
   *          full pathname to where the unzipped file should be saved
   * @return
   * @throws FileNotFoundException
   * @throws IOException
   */
  private boolean gunzip(File zippedFile, String outFilePath) throws FileNotFoundException, IOException {    
    publishProgress("Uncompressing language data for " + languageName + "...", "0");
    int uncompressedFileSize = getGzipSizeUncompressed(zippedFile);
    Integer percentComplete;
    int percentCompleteLast = 0;
    GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(zippedFile));
    OutputStream outputStream = new FileOutputStream(outFilePath);

    int unzippedBytes = 0;
    final int BUFFER = 8192;
    byte[] data = new byte[BUFFER];
    int len;
    percentCompleteLast = 0;
    while ((len = gzipInputStream.read(data, 0, BUFFER)) > 0) {
      outputStream.write(data, 0, len);
      unzippedBytes += len;
      percentComplete = (int) ((unzippedBytes / (float) uncompressedFileSize) * 100);
      if (percentComplete > percentCompleteLast) {      
        publishProgress("Uncompressing language data for " + languageName + "...", percentComplete.toString());
        percentCompleteLast = percentComplete;
      }
    }
    gzipInputStream.close();
    outputStream.close();
    Log.d(TAG, "downloadGzippedFileHttp(): Downloading and unzipping complete. Removing zipped file...");
    if (zippedFile.exists()) {
      zippedFile.delete();
    }    
    return true;
  }
  
  /**
   * Returns the uncompressed size for a Gzipped file. Works for file sizes < 4GB.
   * 
   * @param file
   *          a Gzipped file
   * @return file 
   *          size when uncompressed, in bytes
   * @throws IOException
   */
  private int getGzipSizeUncompressed(File file) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(file, "r");
    raf.seek(raf.length() - 4);
    int b4 = raf.read();
    int b3 = raf.read();
    int b2 = raf.read();
    int b1 = raf.read();
    raf.close();
    return (b1 << 24) | (b2 << 16) + (b3 << 8) + b4;
  }
  
  /**
   * Calls the appropriate unzipping method depending on the file's extension.
   * 
   * @param sourceFilename
   *          filename in assets to install
   * @param modelRoot
   *          directory on SD card to install the file to
   * @param destinationFile
   *          desired target filename
   * @return
   * @throws IOException
   */
  private boolean installFromAssets(String sourceFilename, File modelRoot, File destinationFile) throws IOException {
    String extension = sourceFilename.substring(sourceFilename.lastIndexOf('.'), sourceFilename.length());
    try {
      if (extension.equals(".zip")) {
        return installZipFromAssets(sourceFilename, modelRoot, destinationFile);
      }
    } catch (FileNotFoundException e) {
      Log.i(TAG, "File not found in assets. Filename: " + sourceFilename);
    }
    return false;
  }

  private boolean installZipFromAssets(String sourceFilename, File modelRoot, File destinationFile)
        throws IOException, FileNotFoundException {
    // Attempt to open the zip archive
    publishProgress("Uncompressing language data for " + languageName + "...", "0");
    ZipInputStream inputStream = new ZipInputStream(context.getAssets().open(sourceFilename));

    // Loop through all the files and folders in the zip archive (but there should just be one)
    for (ZipEntry entry = inputStream.getNextEntry(); entry != null; entry = inputStream.getNextEntry()) {
      destinationFile = new File(modelRoot, entry.getName());

      if (entry.isDirectory()) {
        destinationFile.mkdirs();
      } else {
        long zippedFileSize = entry.getSize(); // Note getSize() returns -1 when the zipfile does not have the size set

        // Create a file output stream
        FileOutputStream outputStream = new FileOutputStream(destinationFile);
        final int BUFFER = 8192;

        // Buffer the output to the file
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, BUFFER);
        int unzippedSize = 0;

        // Write the contents
        int count = 0;
        Integer percentComplete = 0;
        Integer percentCompleteLast = 0;
        byte[] data = new byte[BUFFER];
        while ((count = inputStream.read(data, 0, BUFFER)) != -1) {
          bufferedOutputStream.write(data, 0, count);
          unzippedSize += count;
          percentComplete = (int) ((unzippedSize / (long) zippedFileSize) * 100);
          if (percentComplete > percentCompleteLast) {
            publishProgress("Uncompressing language data for " + languageName + "...", percentComplete.toString(), "0");
            percentCompleteLast = percentComplete;
          }
        }
        bufferedOutputStream.close();
      }
      inputStream.closeEntry();
    }
    inputStream.close();
    return true;
  }

  @Override
  protected synchronized void onPreExecute() {
    super.onPreExecute();
    dialog.setTitle("Please wait");
    dialog.setMessage("Checking for language data installation...");
    dialog.setIndeterminate(false);
    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    dialog.setCancelable(false);
    dialog.show();
    
    activity.setButtonVisibility(false);
  }

  /**
   * Update the dialog box with the latest incremental progress.
   * 
   * @param message[0]
   *         the text to be displayed
   * @param message[1]
   *         the value for the progress
   */
  @Override
  protected void onProgressUpdate(String... message) {
    super.onProgressUpdate(message);
    int percentComplete = 0;

    percentComplete = Integer.parseInt(message[1]);
    dialog.setMessage(message[0]);
    dialog.setProgress(percentComplete);
    dialog.show();
  }

  @Override
  protected synchronized void onPostExecute(Boolean result) {
    super.onPostExecute(result);
    indeterminateDialog.dismiss();
    
    if (result) {
      // Restart recognition
      activity.resumeOCR();
      activity.showLanguageName();
    } else {
      activity.showErrorMessage("Error", "Network is unreachable - cannot download language data. "
          + "Please enable network access and restart this app.");
    }
  }
}