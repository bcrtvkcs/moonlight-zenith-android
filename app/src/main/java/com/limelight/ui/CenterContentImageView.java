package com.limelight.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * An ImageView that behaves like centerCrop for full-bleed cover art,
 * while automatically detecting the visual foreground bounds (such as
 * Sunshine's Desktop and Steam icons + labels on solid/gradient backgrounds)
 * and centering them vertically and horizontally within the card rectangle.
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

        float normCenterX = 0.5f;
        float normCenterY = 0.5f;

        if (d instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) d).getBitmap();
            if (bmp != null && !bmp.isRecycled()) {
                float[] visualCenter = getNormalizedVisualCenter(bmp);
                if (visualCenter != null) {
                    normCenterX = visualCenter[0];
                    normCenterY = visualCenter[1];
                }
            }
        }

        float centerX = normCenterX * dWidth;
        float centerY = normCenterY * dHeight;

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

    /**
     * Returns normalized [centerX, centerY] in [0.0f .. 1.0f] relative to the bitmap's width and height.
     */
    private static float[] getNormalizedVisualCenter(Bitmap bmp) {
        float[] cached = CENTER_CACHE.get(bmp);
        if (cached != null) {
            return cached;
        }

        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float[] result = new float[]{0.5f, 0.5f};

        if (w < 24 || h < 24) {
            CENTER_CACHE.put(bmp, result);
            return result;
        }

        Bitmap readableBmp = bmp;
        boolean copied = false;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.getConfig() == Bitmap.Config.HARDWARE) {
                readableBmp = bmp.copy(Bitmap.Config.ARGB_8888, false);
                if (readableBmp == null) {
                    CENTER_CACHE.put(bmp, result);
                    return result;
                }
                copied = true;
            }

            int marginX = Math.max(3, w / 25);
            int marginY = Math.max(3, h / 25);

            int minY = h;
            int maxY = -1;
            int minX = w;
            int maxX = -1;
            int smoothBorderRows = 0;
            int totalSampledRows = 0;

            int prevLR = -1, prevLG = 0, prevLB = 0;
            int prevRR = 0, prevRG = 0, prevRB = 0;

            int spanX = Math.max(1, (w - 1 - marginX) - marginX);

            for (int y = marginY; y < h - marginY; y += 2) {
                totalSampledRows++;
                int leftColor = readableBmp.getPixel(marginX, y);
                int rightColor = readableBmp.getPixel(w - 1 - marginX, y);

                int lR = (leftColor >> 16) & 0xFF;
                int lG = (leftColor >> 8) & 0xFF;
                int lB = leftColor & 0xFF;

                int rR = (rightColor >> 16) & 0xFF;
                int rG = (rightColor >> 8) & 0xFF;
                int rB = rightColor & 0xFF;

                if (prevLR >= 0) {
                    int vDiffL = Math.abs(lR - prevLR) + Math.abs(lG - prevLG) + Math.abs(lB - prevLB);
                    int vDiffR = Math.abs(rR - prevRR) + Math.abs(rG - prevRG) + Math.abs(rB - prevRB);
                    if (vDiffL <= 28 && vDiffR <= 28) {
                        smoothBorderRows++;
                    }
                } else {
                    smoothBorderRows++;
                }

                prevLR = lR;
                prevLG = lG;
                prevLB = lB;
                prevRR = rR;
                prevRG = rG;
                prevRB = rB;

                int rowFgPixels = 0;
                int rowMinX = w;
                int rowMaxX = -1;

                for (int x = marginX + 4; x < w - marginX - 4; x += 2) {
                    int c = readableBmp.getPixel(x, y);
                    int cR = (c >> 16) & 0xFF;
                    int cG = (c >> 8) & 0xFF;
                    int cB = c & 0xFF;

                    int relX = x - marginX;
                    int bgR = lR + ((rR - lR) * relX) / spanX;
                    int bgG = lG + ((rG - lG) * relX) / spanX;
                    int bgB = lB + ((rB - lB) * relX) / spanX;

                    int diff = Math.abs(cR - bgR) + Math.abs(cG - bgG) + Math.abs(cB - bgB);
                    if (diff > 72) {
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
                    && smoothBorderRows >= (totalSampledRows * 8) / 10
                    && minY < maxY
                    && (maxY - minY) >= h / 8
                    && (maxY - minY) <= (h * 88) / 100) {
                result[1] = ((minY + maxY) * 0.5f) / (float) h;
                if (minX < maxX && (maxX - minX) >= w / 8 && (maxX - minX) <= (w * 88) / 100) {
                    result[0] = ((minX + maxX) * 0.5f) / (float) w;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (copied && readableBmp != null && readableBmp != bmp) {
                readableBmp.recycle();
            }
        }

        CENTER_CACHE.put(bmp, result);
        return result;
    }
}
