package com.example.camera2test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_CAMERA_PERMISSION = 200;
    private final String[] requiredPermissions = {
            Manifest.permission.CAMERA
    };

    private SurfaceView previewView;
    private ImageReader imageReader = null;
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession cameraCaptureSession = null;
    private int jpegOrientation = 0;

    private final SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
            if (checkPermission()) {
                startCamera();
            }
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
            stopCamera();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        previewView.getHolder().addCallback(surfaceHolderCallback);

        Button captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(view -> takePicture());
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean pass = (grantResults.length > 0);

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    pass = false;
                    break;
                }
            }

            if (pass) {
                startCamera();
            } else {
                Toast.makeText(this,
                        getResources().getString(R.string.request_permissions_fail),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean checkPermission() {
        boolean pass = true;

        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                pass = false;
                break;
            }
        }

        if (!pass) {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CAMERA_PERMISSION);
        }

        return pass;
    }

    private String cameraFacingToString(Integer facing) {
        switch (facing) {
            case CameraCharacteristics.LENS_FACING_BACK:
                return getResources().getString(R.string.back_camera);
            case CameraCharacteristics.LENS_FACING_FRONT:
                return getResources().getString(R.string.front_camera);
            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                return getResources().getString(R.string.external_camera);
            default:
                return getResources().getString(R.string.camera);
        }
    }

    private String getFirstCameraIdFacing(CameraManager manager, Integer facing) {
        String[] ids;

        try {
            ids = manager.getCameraIdList();

            for (String id : ids) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                if (characteristics.get(CameraCharacteristics.LENS_FACING).equals(facing)) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            String message = getResources().getString(R.string.find_camera_fail) + " " + cameraFacingToString(facing);
            Toast.makeText(getBaseContext(),
                    message,
                    Toast.LENGTH_LONG).show();
            Log.e(getClass().getName(), message);
        }

        return null;
    }

    private Size getMaximumPictureSize(@NonNull CameraCharacteristics characteristics, int format) {
        StreamConfigurationMap streamConfigurationMap =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = streamConfigurationMap.getOutputSizes(format);
        Collections.sort(Arrays.asList(sizes), (Comparator<Size>) (o1, o2) ->
                (o2.getWidth() * o2.getHeight() - o1.getWidth() * o1.getHeight()));
        return sizes[0];
    }

    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }

    private final CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            cameraCaptureSession = session;
            try {
                CaptureRequest.Builder captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequest.addTarget(previewView.getHolder().getSurface());
                session.setRepeatingRequest(captureRequest.build(), null, null);
            } catch (CameraAccessException e) {
                Toast.makeText(getBaseContext(),
                        getResources().getString(R.string.preview_fail),
                        Toast.LENGTH_LONG).show();
                Log.e(getClass().getName(), "createCaptureRequest fail");
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            stopCamera();
            Toast.makeText(getBaseContext(),
                    getResources().getString(R.string.preview_fail),
                    Toast.LENGTH_LONG).show();
            Log.e(getClass().getName(), "onConfigureFailed");
        }
    };

    private final CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            cameraDevice = device;
            try {
                device.createCaptureSession(
                        Arrays.asList(
                                previewView.getHolder().getSurface(),
                                imageReader.getSurface()),
                        sessionStateCallback, null);
            } catch (CameraAccessException e) {
                Toast.makeText(getBaseContext(),
                        getResources().getString(R.string.preview_fail),
                        Toast.LENGTH_LONG).show();
                Log.e(getClass().getName(), "createCaptureSession fail");
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
            Log.d(getClass().getName(), "deviceStateCallback onDisconnected");
            stopCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice device, int i) {
            Log.e(getClass().getName(), "Error occurs on camera device : " + i);
            stopCamera();
        }
    };

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            try (Image image = imageReader.acquireLatestImage()) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                buffer.rewind();

                String fileName = getExternalFilesDir("").toString() + "/image_" +
                        new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
                try {
                    FileOutputStream fos = new FileOutputStream(fileName);
                    fos.write(bytes);
                    fos.close();

                    Toast.makeText(getBaseContext(),
                            getResources().getString(R.string.take_picture_success),
                            Toast.LENGTH_LONG).show();
                    Log.d(getClass().getName(), "Saved picture to " + fileName);
                } catch (IOException e) {
                    Toast.makeText(getBaseContext(),
                            getResources().getString(R.string.take_picture_fail),
                            Toast.LENGTH_LONG).show();
                    Log.e(getClass().getName(), "Failed to save picture to " + fileName);
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void startCamera() {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            String id = getFirstCameraIdFacing(manager, CameraCharacteristics.LENS_FACING_BACK);

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Size size = getMaximumPictureSize(characteristics, ImageFormat.JPEG);

            SparseIntArray orientations = new SparseIntArray(4);
            orientations.append(Surface.ROTATION_0, 0);
            orientations.append(Surface.ROTATION_90, 270);
            orientations.append(Surface.ROTATION_180, 180);
            orientations.append(Surface.ROTATION_270, 90);
            jpegOrientation = getJpegOrientation(characteristics,
                    orientations.get(getWindowManager().getDefaultDisplay().getRotation()));

            imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(imageAvailableListener, null);

            manager.openCamera(id, deviceStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(this,
                    getResources().getString(R.string.preview_fail),
                    Toast.LENGTH_LONG).show();
            Log.e(getClass().getName(), "startCamera fail");
        }
    }

    private void stopCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void takePicture() {
        try {
            CaptureRequest.Builder captureRequest =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest.addTarget(imageReader.getSurface());
            captureRequest.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
            cameraCaptureSession.capture(captureRequest.build(), null, null);
        } catch (CameraAccessException e) {
            Toast.makeText(this,
                    getResources().getString(R.string.take_picture_fail),
                    Toast.LENGTH_LONG).show();
            Log.e(getClass().getName(), "takePicture fail");
        }
    }
}