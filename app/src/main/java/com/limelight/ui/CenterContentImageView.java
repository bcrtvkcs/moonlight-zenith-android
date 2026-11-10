package com.limelight.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * An ImageView that behaves like centerCrop for full-bleed cover art,
 * while automatically detecting the visual foreground bounds (such as
 * Sunshine's Desktop and Steam icons + labels) and centering them
 * vertically and horizontally within the card rectangle.
 */
public class CenterContentImageView extends ImageView {
    private static final Map<Bitmap, float[]> CENTER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<Bitmap, float[]>());

    private final Matrix drawMatrix = new Matrix();

    public CenterContentImageView(Context context) {
        super(context);
        init();
    }

    public CenterContentImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CenterContentImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        super.setScaleType(ScaleType.MATRIX);
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        super.setScaleType(ScaleType.MATRIX);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        updateImageMatrix();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        updateImageMatrix();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        updateImageMatrix();
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean changed = super.setFrame(l, t, r, b);
        updateImageMatrix();
        return changed;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateImageMatrix();
    }

    private void updateImageMatrix() {
        Drawable d = getDrawable();
        if (d == null) {
            return;
        }

        int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        int dWidth = d.getIntrinsicWidth();
        int dHeight = d.getIntrinsicHeight();
        if (dWidth <= 0 || dHeight <= 0) {
            return;
        }

        float centerX = dWidth * 0.5f;
        float centerY = dHeight * 0.5f;

        if (d instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) d).getBitmap();
            if (bmp != null && !bmp.isRecycled()) {
                float[] visualCenter = getVisualCenter(bmp);
                if (visualCenter != null) {
                    centerX = visualCenter[0];
                    centerY = visualCenter[1];
                }
            }
        }

        float scale = Math.max((float) viewWidth / (float) dWidth,
                (float) viewHeight / (float) dHeight);

        float dx = (viewWidth * 0.5f) - (centerX * scale);
        float dy = (viewHeight * 0.5f) - (centerY * scale);

        float minDx = viewWidth - dWidth * scale;
        float minDy = viewHeight - dHeight * scale;

        dx = Math.max(minDx, Math.min(0f, dx));
        dy = Math.max(minDy, Math.min(0f, dy));

        drawMatrix.reset();
        drawMatrix.setScale(scale, scale);
        drawMatrix.postTranslate(Math.round(dx), Math.round(dy));
        setImageMatrix(drawMatrix);
    }

    private static float[] getVisualCenter(Bitmap bmp) {
        float[] cached = CENTER_CACHE.get(bmp);
        if (cached != null) {
            return cached;
        }

        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float[] result = new float[]{w * 0.5f, h * 0.5f};

        if (w < 24 || h < 24) {
            CENTER_CACHE.put(bmp, result);
            return result;
        }

        try {
            int marginX = Math.max(3, w / 50);
            int marginY = Math.max(3, h / 50);

            int minY = h;
            int maxY = -1;
            int minX = w;
            int maxX = -1;
            int uniformBorderRows = 0;
            int totalSampledRows = 0;

            int spanX = Math.max(1, (w - 1 - marginX) - marginX);

            for (int y = marginY; y < h - marginY; y += 2) {
                totalSampledRows++;
                int leftColor = bmp.getPixel(marginX, y);
                int rightColor = bmp.getPixel(w - 1 - marginX, y);

                int lR = (leftColor >> 16) & 0xFF;
                int lG = (leftColor >> 8) & 0xFF;
                int lB = leftColor & 0xFF;

                int rR = (rightColor >> 16) & 0xFF;
                int rG = (rightColor >> 8) & 0xFF;
                int rB = rightColor & 0xFF;

                int edgeDiff = Math.abs(lR - rR) + Math.abs(lG - rG) + Math.abs(lB - rB);
                if (edgeDiff > 60) {
                    continue;
                }
                uniformBorderRows++;

                int rowFgPixels = 0;
                int rowMinX = w;
                int rowMaxX = -1;

                for (int x = marginX + 4; x < w - marginX - 4; x += 2) {
                    int c = bmp.getPixel(x, y);
                    int cR = (c >> 16) & 0xFF;
                    int cG = (c >> 8) & 0xFF;
                    int cB = c & 0xFF;

                    int relX = x - marginX;
                    int bgR = lR + ((rR - lR) * relX) / spanX;
                    int bgG = lG + ((rG - lG) * relX) / spanX;
                    int bgB = lB + ((rB - lB) * relX) / spanX;

                    int diff = Math.abs(cR - bgR) + Math.abs(cG - bgG) + Math.abs(cB - bgB);
                    if (diff > 48) {
                        rowFgPixels++;
                        if (x < rowMinX) rowMinX = x;
                        if (x > rowMaxX) rowMaxX = x;
                    }
                }

                if (rowFgPixels >= 2) {
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    if (rowMinX < minX) minX = rowMinX;
                    if (rowMaxX > maxX) maxX = rowMaxX;
                }
            }

            if (totalSampledRows > 0
                    && uniformBorderRows >= (totalSampledRows * 7) / 10
                    && minY < maxY
                    && (maxY - minY) >= h / 8
                    && (maxY - minY) <= (h * 88) / 100) {
                result[1] = (minY + maxY) * 0.5f;
                if (minX < maxX && (maxX - minX) >= w / 8) {
                    result[0] = (minX + maxX) * 0.5f;
                }
            }
        } catch (Exception ignored) {
        }

        CENTER_CACHE.put(bmp, result);
        return result;
    }
}
