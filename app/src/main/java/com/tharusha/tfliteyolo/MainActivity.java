package com.tharusha.tfliteyolo;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.core.app.ActivityCompat;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private LinearLayout layoutStart;
    private Button buttonStart;
    private RelativeLayout layoutDetect;
    private Button buttonDetect;
    private TextureView textureViewCamera;
    private ImageView imageViewDetection;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private YOLOModel yoloModel;
    private YOLODetector yoloDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutStart = findViewById(R.id.linearLayout_start);
        buttonStart = findViewById(R.id.button_start);
        layoutDetect = findViewById(R.id.relativeLayout_detect);
        buttonDetect = findViewById(R.id.button_detect);
        textureViewCamera = findViewById(R.id.textureView_camera);
        imageViewDetection = findViewById(R.id.imageView_detection);

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });

        buttonDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (buttonDetect.getText().toString()) {
                    case "Detect":
                        runDetectObjectsThread();
                        break;
                    case "Cancel":
                        setVisibleCameraStream();
                        break;
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                start();
            } else {
                Snackbar.make(findViewById(R.id.button_start), "Camera permission denied!", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        }
    }

    void start() {
        if (!checkCameraPermission()) {
            requestCameraPermission();
            return;
        }
        layoutStart.setVisibility(View.GONE);
        layoutDetect.setVisibility(View.VISIBLE);
        startCameraStream();
        loadModel();
    }

    void startCameraStream() {
        if (!checkCameraPermission()) {
            requestCameraPermission();
            return;
        }

        textureViewCamera.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }

    void openCamera() {
        if (!checkCameraPermission()) return;

        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void createCameraPreviewSession() {
        // Invisible the textureViewDetection
        imageViewDetection.setVisibility(View.INVISIBLE);
        // Visible the textureViewCamera
        textureViewCamera.setVisibility(View.VISIBLE);

        try {
            SurfaceTexture surfaceTexture = textureViewCamera.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(640, 640);
            Surface surface = new Surface(surfaceTexture);

            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    cameraCaptureSession = session;
                    try {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void runDetectObjectsThread() {
        buttonDetect.setText("Please wait...");
        buttonDetect.setActivated(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                detectObjects();
            }
        }).start();
    }

    void detectObjects() {
        // Get the image from the TextureView
        Bitmap image = textureViewCamera.getBitmap();

//        Bitmap image = BitmapFactory.decodeResource(getResources(), R.drawable.original);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                drawDetectionBitmap(image);
            }
        });

        if (image == null) {
            Snackbar.make(findViewById(R.id.button_detect), "Error detecting objects. Image is null!", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return;
        }

        // Detect objects in the image
        List<YOLODetection> detections = yoloDetector.detectObjects(image);
        Bitmap outputImage = drawBoundingBox(image, detections);

        // Draw the detection bitmap

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                drawDetectionBitmap(outputImage);
                buttonDetect.setText("Cancel");
                buttonDetect.setActivated(true);
            }
        });

        Snackbar.make(findViewById(R.id.button_detect), "Objects detected", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    public Bitmap drawBoundingBox(Bitmap bitmap, List<YOLODetection> detections) {
        System.out.println("Num detections: " + detections.size());

        // Create a mutable copy of the bitmap to draw on
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        // Create a Canvas object for drawing
        Canvas canvas = new Canvas(mutableBitmap);

        // Create a Paint object for styling
        Paint paint = new Paint();
        paint.setColor(Color.GREEN); // Box color
        paint.setStyle(Paint.Style.STROKE); // Only stroke (no fill)

        // Loop through the detections and draw bounding boxes
        for (YOLODetection detection : detections) {
            float x = detection.box_x;
            float y = detection.box_y;
            float width = detection.box_width;
            float height = detection.box_height;

            float left = x - width / 2;
            float top = y - height / 2;
            float right = x + width / 2;
            float bottom = y + height / 2;

            // Create a rectangle for the bounding box
            paint.setStrokeWidth(10);
            canvas.drawRect(new RectF(left, top, right, bottom), paint);

            // Create class label
            String label = detection.className + " (" + String.format("%.2f", detection.confidence * 100) + "%)";
            paint.setStrokeWidth(2);
            paint.setTextSize(20);
            canvas.drawText(label, left, top - 10, paint);

            System.out.println("ClassIndex: " + detection.classIndex + ", ClassName: " + detection.className + ", Confidence: " + detection.confidence);
        }

        return mutableBitmap;
    }

    void loadModel() {
        yoloModel = new YOLOModel(this, Constants.MODEL_PATH, Constants.CLASSES_PATH);
        yoloDetector = new YOLODetector(yoloModel);

        Snackbar.make(findViewById(R.id.button_start), "Model loaded", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    boolean checkCameraPermission() {
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    void setVisibleCameraStream() {
        textureViewCamera.setVisibility(View.VISIBLE);
        imageViewDetection.setVisibility(View.INVISIBLE);
        buttonDetect.setText("Detect");
    }

    void drawDetectionBitmap(Bitmap bitmap) {
        textureViewCamera.setVisibility(View.INVISIBLE);
        imageViewDetection.setVisibility(View.VISIBLE);
        imageViewDetection.setImageBitmap(bitmap);
    }
}