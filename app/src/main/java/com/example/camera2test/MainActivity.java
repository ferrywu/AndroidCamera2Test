package com.example.camera2test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_CAMERA_PERMISSION = 200;
    private final String[] requiredPermissions = {
            Manifest.permission.CAMERA
    };

    private SurfaceView previewView;
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession cameraCaptureSession = null;

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
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        previewView.getHolder().addCallback(surfaceHolderCallback);
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
                return "back";
            case CameraCharacteristics.LENS_FACING_FRONT:
                return "front";
            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                return "external";
            default:
                return "";
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
            Log.e(getClass().getName(),
                    "Fail to get " + cameraFacingToString(facing) + " camera");
        }

        return null;
    }

    private final CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Surface previewSurface = previewView.getHolder().getSurface();
            cameraCaptureSession = session;
            try {
                CaptureRequest.Builder captureRequest = session.getDevice()
                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequest.addTarget(previewSurface);
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
            session.close();
            cameraCaptureSession = null;
            Toast.makeText(getBaseContext(),
                    getResources().getString(R.string.preview_fail),
                    Toast.LENGTH_LONG).show();
            Log.e(getClass().getName(), "onConfigureFailed");
        }
    };

    private final CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            Surface previewSurface = previewView.getHolder().getSurface();
            cameraDevice = device;
            try {
                device.createCaptureSession(
                        Arrays.asList(new Surface[]{ previewSurface }),
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
            device.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice device, int i) {
            Log.e(getClass().getName(), "Error occurs on camera device : " + i);
            device.close();
            cameraDevice = null;
        }
    };

    @SuppressLint("MissingPermission")
    private void startCamera() {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            manager.openCamera(
                    getFirstCameraIdFacing(manager, CameraCharacteristics.LENS_FACING_BACK),
                    deviceStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(this,
                    getResources().getString(R.string.preview_fail),
                    Toast.LENGTH_LONG).show();
            Log.e(getClass().getName(), "openCamera fail");
        }
    }
}