/* Copyright 2020 The Malaria Screener Authors. All Rights Reserved.

This software was developed under contract funded by the National Library of Medicine,
which is part of the National Institutes of Health, an agency of the Department of Health and Human
Services, United States Government.

Licensed under GNU General Public License v3.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.gnu.org/licenses/gpl-3.0.html

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package gov.nih.nlm.malaria_screener.camera;

import android.content.Context;
import android.hardware.Camera;

import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


/**
 * Class to create view for camera preview
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "MyDebug";
    private SurfaceHolder sHolder;
    private Camera cam;
    private Display display;
    private double picRatio;

    public CameraPreview(Context context, Camera camera, double picRatio) {
        super(context);
        //Log.d(TAG, "Creating camera preview object...");
        cam = camera;
        sHolder = getHolder();
        sHolder.addCallback(this);
        sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        display = wm.getDefaultDisplay();
        this.picRatio = picRatio;

    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            cam.setPreviewDisplay(holder);
            cam.startPreview();
            cam.setDisplayOrientation(90);
            //Log.d(TAG, "Starting camera preview...");

        } catch (IOException e) {
            //Log.d(TAG, "Error displaying camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (cam != null) {
            cam.stopPreview();
            cam.setPreviewCallback(null);
            //Log.d(TAG, "Preview destroyed.");
            cam.release();
            cam = null;
            //Log.d(TAG, "Camera released.");

        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

        // ---------------------- set preview ratio according to picture ratio ---------------------
        Camera.Parameters parameters = cam.getParameters();

        List<Camera.Size> preview_sizes = parameters.getSupportedPreviewSizes();

        double[] ratio_diff = new double[preview_sizes.size()];

        // compute all available preview ratios and their differences with the pic ratio
        for (int i=0;i<preview_sizes.size();i++) {

            double height = preview_sizes.get(i).width;
            double width = preview_sizes.get(i).height;

            Log.d(TAG, " Available preview size: " + width + " " + height);

            double providedRatio = height/width;

            ratio_diff[i] = Math.abs(picRatio - providedRatio);
        }

        // get the biggest preview with closest ratio to pic ratio
        double min = ratio_diff[0];
        int minIndex = 0;
        for (int i=0;i<preview_sizes.size();i++){
            if (ratio_diff[i] < min && preview_sizes.get(i).height >1000 && preview_sizes.get(i).width>1000){
                min = ratio_diff[i];
                minIndex = i;
            }
        }

        if (sHolder.getSurface() == null) {
            //Log.d(TAG, "No surface.");
            return;
        }

        try {
            cam.stopPreview();
            //Log.d(TAG, "Stopping camera preview...");
        } catch (Exception e) {
            //Log.d(TAG, "Error stopping camera preview: " + e.getMessage());
        }

        //set preview size and make any resize, rotate or
        //reformatting changes here
        Camera.Size best_size = preview_sizes.get(minIndex);
        Log.d(TAG, "Best size: " + best_size.height + " " + best_size.width);
        parameters.setPreviewSize(best_size.width, best_size.height);
        cam.setParameters(parameters);

        // start preview with new settings

        try { // rotate camera preview according to phone orientation
            if (display.getRotation() == Surface.ROTATION_90){ // reverse landscape
                cam.setDisplayOrientation(0);
            } else if (display.getRotation() == Surface.ROTATION_180) { //reverse portrait
                cam.setDisplayOrientation(270);
            } else if (display.getRotation() == Surface.ROTATION_270) { //landscape
                cam.setDisplayOrientation(180);
            } else if (display.getRotation() == Surface.ROTATION_0) { //portrait
                cam.setDisplayOrientation(90);
            }
            cam.setPreviewDisplay(holder);
            cam.startPreview();


            //Log.d(TAG, "Restarting camera preview...");
        } catch (Exception e) {
            //Log.d(TAG, "Error starting camera preview from surfaceChanged: " + e.getMessage());
        }

    }


}