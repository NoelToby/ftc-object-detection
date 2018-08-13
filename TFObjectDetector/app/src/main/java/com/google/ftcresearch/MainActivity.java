/*
 * Copyright (C) 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ftcresearch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.ftcresearch.tfod.util.Recognition;
import com.google.ftcresearch.tfod.detection.TFObjectDetector;
import com.google.ftcresearch.tfod.detection.TfodParameters;
import com.google.ftcresearch.tfod.util.Timer;
import com.google.ftcresearch.tfod.generators.FrameGenerator;
import com.google.ftcresearch.tfod.generators.ImageFrameGenerator;
import com.google.ftcresearch.tfod.generators.MovingImageFrameGenerator;
import com.google.ftcresearch.tfod.generators.NativeCameraFrameGenerator;
import com.google.ftcresearch.tfod.util.YuvRgbFrame;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "MainActivity";
  private static final String FRAME_GENERATOR_TYPE = "moving";

  private FrameGenerator frameGenerator;
  private TFObjectDetector tfod;

  private final Timer timer = new Timer(TAG);

  /** Handle the user giving us permission to use the camera. */
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    Log.i(TAG, "Request permission result function callback called");
    for (int i = 0; i < permissions.length; i++) {
      Log.i(TAG, String.format("Request %s was granted: %d", permissions[i], grantResults[i]));
      if (permissions[i].equals(Manifest.permission.CAMERA)) {
        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {

          // This is the same initialization as below, but must be repeated here.
          startFrameGenerator();
          initializeTfod();
        } else {
          Log.w(TAG, "Quitting because camera permission not granted.");
          finishAndRemoveTask();
        }
      }
    }
  }

  /** Perform necessary initialization to start whichever frame generator is specified. */
  private void startFrameGenerator() {
    switch (FRAME_GENERATOR_TYPE) {
      case "static": // Static image
        {
          final Bitmap bm = BitmapFactory.decodeResource(getResources(), R.raw.img_01290);
          final Bitmap bmScaled = Bitmap.createScaledBitmap(bm, 1920, 1080, true);
          frameGenerator = new ImageFrameGenerator(bmScaled);
          break;
        }
      case "moving": // Move an image around
        {
          final Bitmap bm = BitmapFactory.decodeResource(getResources(), R.raw.img_01290);
          final Bitmap bmScaled = Bitmap.createScaledBitmap(bm, 1920, 1080, true);
          frameGenerator = new MovingImageFrameGenerator(bmScaled);
          break;
        }
      case "camera": // Try to use camera 1 api (via NativeCameraFrameGenerator)
        {
          FrameLayout preview = (FrameLayout) findViewById(R.id.frameLayout);
          frameGenerator = new NativeCameraFrameGenerator(this, preview, 300, 1920.0f / 1080.0f);
          break;
        }
      default:
        throw new IllegalArgumentException("Need to choose a different frameGeneratorType");
    }
  }

  /**
   * Ask the user for permission to use the camera.
   * @return true if permission is already granted, false if a request was made to use it.
   */
  private boolean requestCameraPermission() {
    // Make sure camera permission is granted.
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
        PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
      return false;
    } else {
      Log.i(TAG, "Camera permission already granted!");
      return true;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final boolean permissionGranted;

    if (FRAME_GENERATOR_TYPE.equals("camera")) {
      setContentView(R.layout.activity_camera);
      permissionGranted = requestCameraPermission();
    } else {
      setContentView(R.layout.activity_main);
      permissionGranted = true;
    }

    if (permissionGranted) {
      startFrameGenerator();
      initializeTfod();
    }
  }

  private void initializeTfod() {
    // Create a new TFObjectDetector, and try to initialize it. This should be akin to what happens
    // in the "init" stage of the FTC competition.
    tfod =
        new TFObjectDetector(
            new TfodParameters.Builder()
                .numExecutorThreads(4)
                .numInterpreterThreads(1)
//                .trackerDisable(true)
                .build(),
            frameGenerator,
            (annotatedFrame) ->
                runOnUiThread(
                    () -> {
                      final YuvRgbFrame frame = annotatedFrame.getFrame();
                      Bitmap canvasBitmap = frame.getCopiedBitmap();

                      timer.start("Create canvas and draw debug");
                      Canvas canvas = new Canvas(canvasBitmap);
                      tfod.drawDebug(canvas);
                      timer.end();

                      timer.start("Final render onto the screen");
                      Log.v(TAG, "Drawing a new frame!");
                      ImageView im = (ImageView) findViewById(R.id.detection_window);
                      im.setImageBitmap(canvasBitmap);
                      timer.end();
                    }));

    try {
      tfod.initialize(this);
    } catch (IOException e) {
      // Failure should crash the app
      throw new RuntimeException("Could not initialize TFObjectDetector", e);
    }
  }

  @Override
  protected void onDestroy() {

    super.onDestroy();

    if (tfod != null) {
      Log.i(TAG, "Shutting down tfod");
      tfod.shutdown();
    }

    // tfod doesn't shut down the frame generator, so we do that ourselves.
    if (frameGenerator != null) {
      frameGenerator.onDestroy();
    }
  }
}