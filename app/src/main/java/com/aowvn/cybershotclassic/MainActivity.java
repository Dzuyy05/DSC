package com.aowvn.cybershotclassic;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Landscape-only, DSC-P-inspired camera UI, tuned for the wide Xperia 1 II display. */
public final class MainActivity extends Activity implements ClassicCamera.Listener {
    private static final int REQUEST_CAMERA = 41;
    private static final int BACKGROUND = Color.rgb(19, 24, 24);
    private static final int PANEL = Color.rgb(37, 45, 44);
    private static final int ACCENT = Color.rgb(184, 228, 169);
    private static final int SECONDARY = Color.rgb(139, 163, 151);
    private static final int LIGHT = Color.rgb(229, 233, 217);

    private TextureView texture;
    private ViewfinderOverlay overlay;
    private ClassicCamera camera;
    private TextView status;
    private TextView photoCount;
    private TextView soundButton;
    private MediaActionSound actionSound;
    private Uri lastPhoto;
    private int savedThisSession;
    private boolean sounds = true;
    private boolean resumed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        actionSound = new MediaActionSound();
        actionSound.load(MediaActionSound.SHUTTER_CLICK);
        actionSound.load(MediaActionSound.FOCUS_COMPLETE);
        buildInterface();
        camera = new ClassicCamera(this, texture, this);
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) {
                if (resumed) camera.surfaceAvailable();
            }
            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) { }
            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) { return true; }
            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) { }
        });
    }

    private int dp(float d) { return Math.round(d * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int color, int border, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String label, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "monospace", Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        return v;
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(12), dp(7), dp(12), dp(9));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView brand = text("●  Cyber-shot", 20, LIGHT, true);
        brand.setTextColor(Color.rgb(237, 221, 205));
        header.addView(brand, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView headerRight = text("CLASSIC  /  DSC-P EXPERIENCE", 10, SECONDARY, false);
        header.addView(headerRight);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout previewWell = new FrameLayout(this);
        previewWell.setBackground(shape(Color.rgb(6, 9, 9), Color.rgb(102, 115, 103), 8));
        LinearLayout.LayoutParams wellLp = new LinearLayout.LayoutParams(0, -1, 1.43f);
        wellLp.rightMargin = dp(10);
        body.addView(previewWell, wellLp);

        FrameLayout viewport = new FrameLayout(this);
        viewport.setBackgroundColor(Color.BLACK);
        previewWell.addView(viewport, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        texture = new TextureView(this);
        viewport.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        overlay = new ViewfinderOverlay(this);
        viewport.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        viewport.setOnClickListener(v -> camera.focus());
        previewWell.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int availableWidth = right - left - dp(14);
            int availableHeight = bottom - top - dp(14);
            if (availableWidth <= 0 || availableHeight <= 0) return;
            int width = Math.min(availableWidth, (availableHeight * 4) / 3);
            int height = Math.min(availableHeight, width * 3 / 4);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) viewport.getLayoutParams();
            if (params.width != width || params.height != height) {
                params.width = width;
                params.height = height;
                viewport.setLayoutParams(params);
            }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(11), dp(14), dp(11));
        panel.setBackground(shape(PANEL, Color.rgb(84, 98, 89), 8));
        body.addView(panel, new LinearLayout.LayoutParams(0, -1, .87f));

        TextView mode = text("P  PROGRAM AUTO", 18, ACCENT, true);
        panel.addView(mode, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView line = text("4:3  •  JPEG   /   MAIN LENS", 10, SECONDARY, false);
        panel.addView(line, new LinearLayout.LayoutParams(-1, dp(21)));

        status = text("WAITING FOR CAMERA...", 11, ACCENT, false);
        status.setBackground(shape(Color.rgb(13, 29, 24), Color.rgb(67, 98, 71), 3));
        status.setPadding(dp(9), 0, dp(9), 0);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, dp(31));
        statusLp.topMargin = dp(6);
        panel.addView(status, statusLp);

        TextView guide = text("HALF-PRESS → FOCUS     FULL → PHOTO", 9, SECONDARY, false);
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(-1, dp(24));
        guideLp.topMargin = dp(4);
        panel.addView(guide, guideLp);

        TextView focus = button("[  FOCUS  ]", false);
        LinearLayout.LayoutParams focusLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        focusLp.bottomMargin = dp(6);
        panel.addView(focus, focusLp);
        focus.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                camera.focus();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                v.performClick();
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_CANCEL;
        });
        focus.setOnClickListener(v -> { });

        TextView shutter = button("●   CAPTURE", true);
        panel.addView(shutter, new LinearLayout.LayoutParams(-1, 0, 1.20f));
        shutter.setOnClickListener(v -> shoot());

        LinearLayout controls = new LinearLayout(this);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, dp(37));
        controlsLp.topMargin = dp(8);
        panel.addView(controls, controlsLp);
        TextView gallery = button("PLAY ▶", false);
        controls.addView(gallery, new LinearLayout.LayoutParams(0, -1, 1f));
        gallery.setOnClickListener(v -> openGallery());
        soundButton = button("SOUND ON", false);
        LinearLayout.LayoutParams soundLp = new LinearLayout.LayoutParams(0, -1, 1f);
        soundLp.leftMargin = dp(7);
        controls.addView(soundButton, soundLp);
        soundButton.setOnClickListener(v -> {
            sounds = !sounds;
            soundButton.setText(sounds ? "SOUND ON" : "SOUND OFF");
        });

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, dp(25));
        footerLp.topMargin = dp(3);
        panel.addView(footer, footerLp);
        photoCount = text("SESSION 000", 9, SECONDARY, false);
        footer.addView(photoCount, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView hint = text("XPERIA 1 II", 9, SECONDARY, false);
        footer.addView(hint);
    }

    private TextView button(String label, boolean primary) {
        TextView v = text(label, primary ? 17 : 12, primary ? Color.rgb(21, 37, 26) : LIGHT, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(shape(primary ? ACCENT : Color.rgb(62, 73, 67),
                primary ? Color.rgb(228, 244, 211) : Color.rgb(100, 113, 100), 5));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.start();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        camera.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        actionSound.release();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != REQUEST_CAMERA) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            if (resumed) camera.start();
        } else {
            onError("Camera permission is required. Enable it in Android Settings.");
        }
    }

    private void shoot() {
        if (sounds) actionSound.play(MediaActionSound.SHUTTER_CLICK);
        camera.capture();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_FOCUS) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) camera.focus();
            return true;
        }
        if (key == KeyEvent.KEYCODE_CAMERA) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) shoot();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (lastPhoto != null) {
            intent.setDataAndType(lastPhoto, "image/jpeg");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        }
        try { startActivity(intent); }
        catch (Exception ignored) { onError("No compatible gallery application found"); }
    }

    @Override public void onStatus(String message, boolean focusActive) {
        status.setText(message);
        status.setTextColor(ACCENT);
        overlay.setFocusing(focusActive);
        if (sounds && "FOCUS LOCKED".equals(message)) actionSound.play(MediaActionSound.FOCUS_COMPLETE);
    }

    @Override public void onSaved(Uri uri) {
        lastPhoto = uri;
        savedThisSession++;
        photoCount.setText(String.format(java.util.Locale.US, "SESSION %03d", savedThisSession));
    }

    @Override public void onError(String message) {
        status.setText(message);
        status.setTextColor(Color.rgb(255, 171, 151));
        overlay.setFocusing(false);
    }
}
