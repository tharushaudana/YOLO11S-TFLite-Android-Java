package com.tharusha.tfliteyolo;
import android.graphics.Bitmap;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class YOLODetector {
    private final YOLOModel yoloModel;
    private final int inputImageSize;

    private float imageWidthOriginal;
    private float imageHeightOriginal;

    private final ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new NormalizeOp(0f, 255f)) // Normalize the pixel values to [0, 1]
            .add(new CastOp(DataType.FLOAT32))
            .build();

    public YOLODetector(YOLOModel model) {
        this.yoloModel = model;
        this.inputImageSize = model.inputShape[1];
    }

    public List<YOLODetection> detectObjects(Bitmap bitmap) {
        // Get the image's dimensions (for bounding box scaling)
        imageWidthOriginal = bitmap.getWidth();
        imageHeightOriginal = bitmap.getHeight();

        // Preprocess the image
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputImageSize, inputImageSize, false);
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(resizedBitmap);
        tensorImage = imageProcessor.process(tensorImage);

        // Create an output buffer (batch size = 1)
        float[][][] output = new float[1][yoloModel.outputShape[1]][yoloModel.outputShape[2]];
        yoloModel.interpreter.run(tensorImage.getBuffer(), output);

        return processYOLOOutput(output);
    }

    List<YOLODetection> processYOLOOutput(float[][][] yoloOutput) {
        List<YOLODetection> detections = new ArrayList<>();

        int numDetections = yoloModel.outputShape[2]; // Number of detected boxes
        int numClasses = yoloModel.outputShape[1] - 4; // Num channels - 4 (x, y, w, h)

        for (int i = 0; i < numDetections; i++) {
            int bestClass = -1;
            float bestClassScore = 0;

            // Loop through class probabilities (index 4 to 4 + numClasses) and find the class with the highest score
            for (int j = 4; j < 4 + numClasses; j++) {
                if (yoloOutput[0][j][i] > bestClassScore) {
                    bestClassScore = yoloOutput[0][j][i];
                    bestClass = j - 4; // Convert index to class ID
                }
            }

            // Ignore detections with low confidence
            if (bestClassScore < 0.5) {
                continue;
            }

            // Create a detection object and add it to the list
            YOLODetection detection = new YOLODetection();
            detection.classIndex = bestClass;
            detection.confidence = bestClassScore;
            detection.box_x = yoloOutput[0][0][i] * imageWidthOriginal;
            detection.box_y = yoloOutput[0][1][i] * imageHeightOriginal;
            detection.box_width = yoloOutput[0][2][i] * imageWidthOriginal;
            detection.box_height = yoloOutput[0][3][i] * imageHeightOriginal;

            if (yoloModel.classes.size() > bestClass) {
                detection.className = yoloModel.classes.get(bestClass);
            } else {
                detection.className = "";
            }

            detections.add(detection);
        }

        // Apply Non-Maximum Suppression for remove overlapping boxes
        return applyNMS(detections, 0.5f);
    }

    private List<YOLODetection> applyNMS(List<YOLODetection> detections, float iouThreshold) {
        Collections.sort(detections, new Comparator<YOLODetection>() {
            @Override
            public int compare(YOLODetection d1, YOLODetection d2) {
                return Float.compare(d2.confidence, d1.confidence);
            }
        });

        List<YOLODetection> finalDetections = new ArrayList<>();

        while (!detections.isEmpty()) {
            YOLODetection bestDetection = detections.remove(0);
            finalDetections.add(bestDetection);

            detections.removeIf(d -> computeIoU(bestDetection, d) > iouThreshold);
        }

        return finalDetections;
    }

    // IoU Calculation Function
    private float computeIoU(YOLODetection box1, YOLODetection box2) {
        float x1 = Math.max(box1.box_x, box2.box_x);
        float y1 = Math.max(box1.box_y, box2.box_y);
        float x2 = Math.min(box1.box_x + box1.box_width, box2.box_x + box2.box_width);
        float y2 = Math.min(box1.box_y + box1.box_height, box2.box_y + box2.box_height);

        float intersection = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float box1Area = box1.box_width * box1.box_height;
        float box2Area = box2.box_width * box2.box_height;

        float union = box1Area + box2Area - intersection;
        return union > 0 ? intersection / union : 0;
    }
}

