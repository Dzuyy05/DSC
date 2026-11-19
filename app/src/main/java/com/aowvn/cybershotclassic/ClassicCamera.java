package com.aowvn.cybershotclassic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** Camera2 engine: back camera, 4:3 preview, JPEG capture and MediaStore save. */
final class ClassicCamera {
    interface Listener {
        void onStatus(String message, boolean focusActive);
        void onSaved(Uri uri);
        void onError(String message);
    }

    private final Activity activity;
    private final TextureView texture;
    private final Listener listener;
    private final CameraManager manager;

    private HandlerThread thread;
    private Handler worker;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader jpegReader;
    private Surface previewSurface;
    private String cameraId;
    private Size previewSize;
    private Size jpegSize;
    private int sensorOrientation = 90;
    private boolean started;
    private boolean opening;
    private boolean capturing;
    private boolean focusActive;
    private int generation;

    ClassicCamera(Activity activity, TextureView texture, Listener listener) {
        this.activity = activity;
        this.texture = texture;
        this.listener = listener;
        manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    }

    void start() {
        if (started) return;
        started = true;
        thread = new HandlerThread("CyberShotCamera");
        thread.start();
        worker = new Handler(thread.getLooper());
        if (texture.isAvailable()) openCamera();
    }

    void surfaceAvailable() {
        if (started) openCamera();
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (!started || opening || device != null || !texture.isAvailable() ||
                activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            cameraId = findBackCamera();
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer angle = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (angle != null) sensorOrientation = angle;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "No stream configuration");
            jpegSize = selectJpeg(map.getOutputSizes(ImageFormat.JPEG));
            previewSize = selectPreview(map.getOutputSizes(SurfaceTexture.class));
            jpegReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(this::saveJpeg, worker);
            opening = true;
            final int requestGeneration = generation;
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice opened) {
                    if (!started || generation != requestGeneration) {
                        opened.close();
                        return;
                    }
                    device = opened;
                    opening = false;
                    createPreview();
                }
                @Override public void onDisconnected(CameraDevice disconnected) {
                    disconnected.close();
                    if (device == disconnected) device = null;
                    opening = false;
                    status("DISCONNECTED", false);
                }
                @Override public void onError(CameraDevice bad, int code) {
                    bad.close();
                    if (device == bad) device = null;
                    opening = false;
                    error("Camera error " + code + ". Close other camera apps and retry.");
                }
            }, worker);
        } catch (Exception e) {
            opening = false;
            error("Cannot open camera: " + e.getMessage());
        }
    }

    private String findBackCamera() throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "No rear camera");
    }

    private static boolean isFourThree(Size size) {
        int wide = Math.max(size.getWidth(), size.getHeight());
        int tall = Math.min(size.getWidth(), size.getHeight());
        return Math.abs(wide * 3 - tall * 4) <= Math.max(4, tall / 60);
    }

    private static Size selectJpeg(Size[] sizes) {
        if (sizes == null || sizes.length == 0) throw new IllegalStateException("No JPEG sizes");
        Size[] choices = Arrays.stream(sizes).filter(ClassicCamera::isFourThree)
                .sorted(Comparator.comparingLong(s -> -(long) s.getWidth() * s.getHeight())).toArray(Size[]::new);
        if (choices.length == 0) choices = sizes;
        for (Size size : choices) {
            long pixels = (long) size.getWidth() * size.getHeight();
            if (pixels <= 13_000_000) return size;
        }
        return choices[choices.length - 1];
    }

    private static Size selectPreview(Size[] sizes) {
        if (sizes == null || sizes.length == 0) throw new IllegalStateException("No preview sizes");
        Size best = null;
        for (Size size : sizes) {
            if (!isFourThree(size)) continue;
            long px = (long) size.getWidth() * size.getHeight();
            if (px <= 1920L * 1440 && (best == null || px > (long) best.getWidth() * best.getHeight())) best = size;
        }
        if (best != null) return best;
        for (Size size : sizes) if (isFourThree(size)) return size;
        return sizes[0];
    }

    private void createPreview() {
        if (device == null || !texture.isAvailable() || jpegReader == null) return;
        try {
            SurfaceTexture surfaceTexture = texture.getSurfaceTexture();
            if (surfaceTexture == null) return;
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(surfaceTexture);
            previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            device.createCaptureSession(Arrays.asList(previewSurface, jpegReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession configured) {
                            if (!started || device == null) {
                                configured.close();
                                return;
                            }
                            session = configured;
                            try {
                                session.setRepeatingRequest(previewBuilder.build(), focusCallback, worker);
                                status("READY  •  4:3 JPEG", false);
                            } catch (CameraAccessException e) { error("Preview failed: " + e.getMessage()); }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession failed) {
                            error("Camera preview configuration failed");
                        }
                    }, worker);
        } catch (CameraAccessException e) { error("Camera preview error: " + e.getMessage()); }
    }

    private final CameraCaptureSession.CaptureCallback focusCallback = new CameraCaptureSession.CaptureCallback() {
        @Override public void onCaptureProgressed(CameraCaptureSession s, CaptureRequest request, CaptureResult result) {
            updateFocus(result);
        }
        @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest request, TotalCaptureResult result) {
            updateFocus(result);
        }
    };

    private void updateFocus(CaptureResult result) {
        if (!focusActive) return;
        Integer state = result.get(CaptureResult.CONTROL_AF_STATE);
        if (state == null) return;
        if (state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) {
            focusActive = false;
            status("FOCUS LOCKED", true);
        } else if (state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
            focusActive = false;
            status("FOCUS NOT LOCKED", false);
        }
    }

    void focus() {
        if (worker == null) return;
        worker.post(() -> {
            if (session == null || previewBuilder == null) return;
            try {
                focusActive = true;
                status("FOCUSING...", true);
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                session.capture(previewBuilder.build(), focusCallback, worker);
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            } catch (CameraAccessException | IllegalStateException e) {
                focusActive = false;
                error("Focus failed: " + e.getMessage());
            }
        });
    }

    void capture() {
        if (worker == null) return;
        worker.post(() -> {
            if (session == null || device == null || jpegReader == null || capturing) return;
            capturing = true;
            try {
                CaptureRequest.Builder still = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                still.addTarget(jpegReader.getSurface());
                still.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                still.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
                session.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override public void onCaptureFailed(CameraCaptureSession s, CaptureRequest req, CaptureFailure failure) {
                        capturing = false;
                        error("Capture failed; try again");
                    }
                }, worker);
                status("CAPTURING...", true);
                // Avoid a permanently locked shutter if a HAL fails to deliver JPEG data.
                worker.postDelayed(() -> {
                    if (capturing) {
                        capturing = false;
                        error("No photo received from camera");
                    }
                }, 5000);
            } catch (CameraAccessException | IllegalStateException e) {
                capturing = false;
                error("Shutter failed: " + e.getMessage());
            }
        });
    }

    private int jpegOrientation() {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees;
        switch (rotation) {
            case Surface.ROTATION_90: degrees = 0; break;
            case Surface.ROTATION_180: degrees = 270; break;
            case Surface.ROTATION_270: degrees = 180; break;
            default: degrees = 90;
        }
        return (degrees + sensorOrientation + 270) % 360;
    }

    private void saveJpeg(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireNextImage();
            if (image == null) return;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] jpeg = new byte[buffer.remaining()];
            buffer.get(jpeg);
            ContentResolver resolver = activity.getContentResolver();
            ContentValues values = new ContentValues();
            String name = "CSC_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".jpg";
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CyberShotClassic");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (item == null) throw new IOException("MediaStore insert failed");
            try {
                try (OutputStream out = resolver.openOutputStream(item)) {
                    if (out == null) throw new IOException("Cannot write image");
                    out.write(jpeg);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(item, ready, null, null);
                activity.runOnUiThread(() -> listener.onSaved(item));
            } catch (Exception e) {
                resolver.delete(item, null, null);
                throw e;
            }
            status("SAVED  •  " + jpegSize.getWidth() + "×" + jpegSize.getHeight(), false);
        } catch (Exception e) {
            error("Saving photo failed: " + e.getMessage());
        } finally {
            if (image != null) image.close();
            capturing = false;
        }
    }

    private void status(String message, boolean focus) {
        activity.runOnUiThread(() -> listener.onStatus(message, focus));
    }

    private void error(String message) {
        activity.runOnUiThread(() -> listener.onError(message));
    }

    void stop() {
        started = false;
        ++generation;
        if (session != null) { session.close(); session = null; }
        if (device != null) { device.close(); device = null; }
        if (jpegReader != null) { jpegReader.close(); jpegReader = null; }
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        previewBuilder = null;
        capturing = false;
        opening = false;
        focusActive = false;
        if (thread != null) {
            thread.quitSafely();
            try { thread.join(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            thread = null;
            worker = null;
        }
    }
}
