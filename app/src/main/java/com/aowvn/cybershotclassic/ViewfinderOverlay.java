package com.aowvn.cybershotclassic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class ViewfinderOverlay extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean focusing;

    ViewfinderOverlay(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setFocusing(boolean active) {
        focusing = active;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float unit = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.25f * unit);
        paint.setColor(Color.argb(145, 245, 248, 234));

        float boxW = Math.min(w * .24f, 112f * unit);
        float boxH = Math.min(h * .24f, 82f * unit);
        float cx = w / 2f, cy = h / 2f;
        float left = cx - boxW / 2, top = cy - boxH / 2;
        float right = cx + boxW / 2, bottom = cy + boxH / 2;
        float tick = 11 * unit;
        paint.setColor(focusing ? Color.rgb(128, 245, 156) : Color.argb(210, 240, 243, 222));
        canvas.drawLine(left, top, left + tick, top, paint);
        canvas.drawLine(left, top, left, top + tick, paint);
        canvas.drawLine(right, top, right - tick, top, paint);
        canvas.drawLine(right, top, right, top + tick, paint);
        canvas.drawLine(left, bottom, left + tick, bottom, paint);
        canvas.drawLine(left, bottom, left, bottom - tick, paint);
        canvas.drawLine(right, bottom, right - tick, bottom, paint);
        canvas.drawLine(right, bottom, right, bottom - tick, paint);

        paint.setColor(Color.argb(150, 230, 232, 210));
        paint.setStrokeWidth(unit);
        canvas.drawLine(cx - 4 * unit, cy, cx + 4 * unit, cy, paint);
        canvas.drawLine(cx, cy - 4 * unit, cx, cy + 4 * unit, paint);

        // Subtle crop guides: a classic compact-camera viewfinder, not a grid overlay.
        float margin = 10 * unit;
        canvas.drawLine(margin, margin, margin + 17 * unit, margin, paint);
        canvas.drawLine(margin, margin, margin, margin + 17 * unit, paint);
        canvas.drawLine(w - margin, margin, w - margin - 17 * unit, margin, paint);
        canvas.drawLine(w - margin, margin, w - margin, margin + 17 * unit, paint);
        canvas.drawLine(margin, h - margin, margin + 17 * unit, h - margin, paint);
        canvas.drawLine(margin, h - margin, margin, h - margin - 17 * unit, paint);
        canvas.drawLine(w - margin, h - margin, w - margin - 17 * unit, h - margin, paint);
        canvas.drawLine(w - margin, h - margin, w - margin, h - margin - 17 * unit, paint);
    }
}
