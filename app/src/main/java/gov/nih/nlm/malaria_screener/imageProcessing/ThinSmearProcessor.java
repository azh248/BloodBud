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

package gov.nih.nlm.malaria_screener.imageProcessing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import gov.nih.nlm.malaria_screener.R;
import gov.nih.nlm.malaria_screener.custom.Utils.UtilsCustom;
import gov.nih.nlm.malaria_screener.imageProcessing.Segmentation.MarkerBasedWatershed;

public class ThinSmearProcessor {

    private static final String TAG = "MyDebug";

    Mat watershedMask;

    File pictureFileCopy;

    Canvas canvas;
    Paint paint;

    int[][] cellLocation;
    private int cellCount = 0;
    private int infectedCount = 0;

    Context context;

    public ThinSmearProcessor(Context context){
        this.context = context;
    }

    public int[] processImage(Mat resizedMat, int orientation, float RV, boolean takenFromCam, File pictureFileCopy){

        long startTime_w = System.currentTimeMillis();

        this.pictureFileCopy = pictureFileCopy;

        // put resized image on canvas for drawing results after image processing
        UtilsCustom.canvasBitmap = Bitmap.createBitmap(resizedMat.width(), resizedMat.height(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(resizedMat, UtilsCustom.canvasBitmap);

        canvas = new Canvas(UtilsCustom.canvasBitmap);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(5);
        paint.setColor(Color.BLACK);

        MarkerBasedWatershed watershed = new MarkerBasedWatershed();
        watershed.runMarkerBasedWatershed(resizedMat, RV);
        resizedMat.release();

        long endTime_w = System.currentTimeMillis();
        long totalTime_w = endTime_w - startTime_w;
        Log.d(TAG, "Watershed Time: " + totalTime_w);

        if (watershed.getRetakeFlag()) { // take care of the case (avoid crash) when segmentation failed due to a plain black image was taken

            Log.d(TAG, "here");

            return null;
        } else {

            watershedMask = watershed.watershed_result.clone(); //when segmentation is successful, copy seg mask to later save it in worker thread

            long startTime_C = System.currentTimeMillis();

            Cells c = new Cells();
            c.runCells(watershed.watershed_result, watershed.output_WBCMask);

            watershed = null;
            c = null;

            long endTime_C = System.currentTimeMillis();
            long totalTime_C = endTime_C - startTime_C;
            Log.d(TAG, "Cell Time: " + totalTime_C);

            if (UtilsCustom.cellLocation.length == 0) {  //take care of the case(avoid crash) when segmentation passed but no cell chips extracted
                Log.d(TAG, "here1");

                return null;
            } else {

                cellLocation = UtilsCustom.cellLocation;

                //reset
                cellCount = 0;
                infectedCount = 0;

                cellCount = UtilsCustom.cellCount;
            }

            drawAll(orientation, RV, takenFromCam);

            // 12/17/2020, not saving mask images anymore. by Hang
            //saveMaskImageHandler.sendEmptyMessage(0);

            // get image confidence
            cal_image_conf();

            int[] res = new int[2];

            res[0] = infectedCount;
            res[1] = cellCount;

            return res;
        }

    }

    //calculate  image confidence
    private void cal_image_conf(){

        // average
            /*if (infectedCount > 0) {
                float conf_im = 0;
                for (int i = 0; i < cellCount; i++) {

                    if (UtilsCustom.results.get(i) == 1) {
                        conf_im += UtilsCustom.confs_patch.get(i);
                    }
                }
                conf_im = conf_im / (float) infectedCount;
                UtilsCustom.pos_confs_im.add(conf_im);
            }*/

        // median
        float conf_im = 0;
        if (infectedCount > 0) {
            // get confs of positive patches
            ArrayList<Float> confs_pos_patch = new ArrayList<>();
            for (int i = 0; i < cellCount; i++) {

                if (UtilsCustom.results.get(i) == 1) {
                    confs_pos_patch.add(UtilsCustom.confs_patch.get(i));
                }
            }

            // get the median
            conf_im = UtilsCustom.cal_median(confs_pos_patch);
        }
        // add to image conf list
        UtilsCustom.pos_confs_im.add(conf_im);
    }

    public void drawAll(int orientation, float RV, boolean takenFromCam) {

        for (int i = 0; i < UtilsCustom.results.size(); i++) {

            if (UtilsCustom.results.get(i) == 0) {
                //infectedNum++;
                /*paint.setColor(Color.BLUE); // not infected
                canvas.drawCircle(UtilsCustom.cellLocation[i][1] / RV, UtilsCustom.cellLocation[i][0] / RV, 2, paint);*/
                //canvas.drawText(String.valueOf(infectedNum), cellLocation[i][1] - 7, cellLocation[i][0] - 7, paint);
            } else if (UtilsCustom.results.get(i) == 1) {
                infectedCount++;

                //draw color according to confidence level
                if (UtilsCustom.confs_patch.get(i) > 0.5 && UtilsCustom.confs_patch.get(i) <= 0.6){             // level 1
                    paint.setColor(context.getResources().getColor(R.color.level_1));
                } else if (UtilsCustom.confs_patch.get(i) > 0.6 && UtilsCustom.confs_patch.get(i) <= 0.7){      // level 2
                    paint.setColor(context.getResources().getColor(R.color.level_2));
                } else if (UtilsCustom.confs_patch.get(i) > 0.7 && UtilsCustom.confs_patch.get(i) <= 0.8){      // level 3
                    paint.setColor(context.getResources().getColor(R.color.level_3));
                } else if (UtilsCustom.confs_patch.get(i) > 0.8 && UtilsCustom.confs_patch.get(i) <= 0.9){      // level 4
                    paint.setColor(context.getResources().getColor(R.color.level_4));
                } else if (UtilsCustom.confs_patch.get(i) > 0.9 && UtilsCustom.confs_patch.get(i) <= 1.0){      // level 4
                    paint.setColor(context.getResources().getColor(R.color.level_5));
                } else {
                    paint.setColor(context.getResources().getColor(R.color.level_0));
                }

                canvas.drawCircle(UtilsCustom.cellLocation[i][1] / RV, UtilsCustom.cellLocation[i][0] / RV, 2, paint);

                if (takenFromCam) { // test this canvas rotate
                    canvas.save();
                    // draw texts according to phone rotation while image was taken
                    if (orientation == Surface.ROTATION_0) {                //portrait
                        canvas.rotate(270, UtilsCustom.cellLocation[i][1] / RV, UtilsCustom.cellLocation[i][0] / RV);
                    } else if (orientation == Surface.ROTATION_270) {      //reverse landscape
                        canvas.rotate(180, UtilsCustom.cellLocation[i][1] / RV, UtilsCustom.cellLocation[i][0] / RV);
                    } else if (orientation == Surface.ROTATION_180) {      //reverse portrait
                        canvas.rotate(90, UtilsCustom.cellLocation[i][1] / RV, UtilsCustom.cellLocation[i][0] / RV);
                    } else if (orientation == Surface.ROTATION_90) {       //landscape
                        canvas.rotate(0, UtilsCustom.cellLocation[i][1] / RV, UtilsCustom.cellLocation[i][0] / RV);
                    }
                    canvas.drawText(String.valueOf(infectedCount), UtilsCustom.cellLocation[i][1] / RV - 7, UtilsCustom.cellLocation[i][0] / RV - 7, paint);
                    canvas.restore();
                } else {
                    canvas.drawText(String.valueOf(infectedCount), UtilsCustom.cellLocation[i][1] / RV - 7, UtilsCustom.cellLocation[i][0] / RV - 7, paint);
                }
            }

        }
    }

    private Handler saveMaskImageHandler = new Handler(Looper.getMainLooper()) {

        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    //SaveImage saveImage = new SaveImage(pictureFileCopy, dataCopy);

                    saveMaskImage();

                }
            };

            Thread saveMaskImgThread = new Thread(r);
            saveMaskImgThread.start();

        }
    };

    public void saveMaskImage() {

        String file_name = null;
        try {
            file_name = createImageFile().toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Imgcodecs.imwrite(file_name, watershedMask);
        watershedMask.release();
    }

    private File createImageFile() throws IOException {

        File direct = new File(Environment.getExternalStorageDirectory(), "NLM_Malaria_Screener/New");

        if (!direct.exists()) {
            direct.mkdirs();
        }

        // get image name
        String imgStr = pictureFileCopy.toString().substring(pictureFileCopy.toString().lastIndexOf("/") + 1);
        int endIndex = imgStr.lastIndexOf(".");
        String imageName = imgStr.substring(0, endIndex);

        File imgFile = new File(new File(Environment.getExternalStorageDirectory(), "NLM_Malaria_Screener/New"), imageName + "_mask.png");

        return imgFile;
    }

}


