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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;

public final class OcrResult {
  private final Bitmap bitmap;
  private final String text;
  
  private final int[] wordConfidences;
  private final int meanConfidence;
  
  private final List<Rect> wordBoundingBoxes;
  private final List<Rect> characterBoundingBoxes;
  private final List<Rect> textlineBoundingBoxes;
  
  private final long timestamp;
  private final long recognitionTimeRequired;

  private final Paint paint;
  
  public OcrResult(Bitmap bitmap,
                   String text,
                   int[] wordConfidences,
                   int meanConfidence,
                   List<Rect> characterBoundingBoxes,
                   List<Rect> textlineBoundingBoxes,
                   List<Rect> wordBoundingBoxes,
                   long recognitionTimeRequired) {
    this.bitmap = bitmap;
    this.text = text;
    this.wordConfidences = wordConfidences;
    this.meanConfidence = meanConfidence;
    this.characterBoundingBoxes = characterBoundingBoxes;
    this.textlineBoundingBoxes = textlineBoundingBoxes;
    this.wordBoundingBoxes = wordBoundingBoxes;
    this.recognitionTimeRequired = recognitionTimeRequired;
    this.timestamp = System.currentTimeMillis();
    
    this.paint = new Paint();
  }

  public Bitmap getBitmap() {
    if (characterBoundingBoxes.isEmpty()) {
      return bitmap;
    } else {
      return getAnnotatedBitmap();
    }
  }
  
  private Bitmap getAnnotatedBitmap() {
    Canvas canvas = new Canvas(bitmap);
    
    // Draw bounding boxes around each word
    for (int i = 0; i < wordBoundingBoxes.size(); i++) {
      paint.setAlpha(0xA0);
      paint.setColor(0xFF00CCFF);
      paint.setStyle(Style.STROKE);
      paint.setStrokeWidth(3);
      Rect r = wordBoundingBoxes.get(i);
      canvas.drawRect(r, paint);
    }    
    
    // Draw bounding boxes around each character
    for (int i = 0; i < characterBoundingBoxes.size(); i++) {
      paint.setAlpha(0xA0);
      paint.setColor(0xFF00FF00);
      paint.setStyle(Style.STROKE);
      paint.setStrokeWidth(3);
      Rect r = characterBoundingBoxes.get(i);
      canvas.drawRect(r, paint);
    }
    
    return bitmap;
  }
  
  public String getText() {
    return text;
  }

  public int[] getWordConfidences() {
    return wordConfidences;
  }

  public int getMeanConfidence() {
    return meanConfidence;
  }

  public long getRecognitionTimeRequired() {
    return recognitionTimeRequired;
  }
  
  public List<Rect> getCharacterBoundingBoxes() {
    return characterBoundingBoxes;
  }
  
  public List<Rect> getTextlineBoundingBoxes() {
    return textlineBoundingBoxes;
  }
  
  public List<Rect> getWordBoundingBoxes() {
    return wordBoundingBoxes;
  }
  
  public long getTimestamp() {
    return timestamp;
  }
  
  @Override
  public String toString() {
    return text + " " + meanConfidence + " " + recognitionTimeRequired + " " + timestamp;
  }
}
