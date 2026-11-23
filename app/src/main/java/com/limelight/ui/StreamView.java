package com.limelight.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.limelight.LimeLog;
import com.limelight.binding.input.StreamInputConnection;
import com.limelight.preferences.PreferenceConfiguration;

public class StreamView extends SurfaceView {
    private double desiredAspectRatio;
    private InputCallbacks inputCallbacks;

    private boolean imeTextModeEnabled;
    private StreamInputConnection.HostTextSink hostTextSink;
    private StreamInputConnection activeInputConnection;

    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
    }

    public void setInputCallbacks(InputCallbacks callbacks) {
        this.inputCallbacks = callbacks;
    }

    private void initImeMode(Context context) {
        this.imeTextModeEnabled = PreferenceConfiguration.readPreferences(context).smartKeyboard;
    }

    public StreamView(Context context) {
        super(context);
        initImeMode(context);
    }

    public StreamView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initImeMode(context);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initImeMode(context);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initImeMode(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Based on code from: https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int measuredHeight, measuredWidth;
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int)(measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = widthSize;
            measuredHeight = (int)(measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // This callbacks allows us to override dumb IME behavior like when
        // Samsung's default keyboard consumes Shift+Space.
        if (inputCallbacks != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (inputCallbacks.handleKeyDown(event)) {
                    // If a physical keyboard key reached the host, reset the IME's shadow buffer
                    if ((event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == 0 && event.getDeviceId() > 0) {
                        resetImeTextBuffer();
                    }
                    return true;
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (inputCallbacks.handleKeyUp(event)) {
                    return true;
                }
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * Enables or disables "smart keyboard" mode. When enabled, the soft keyboard sees this view
     * as a regular text field, which enables glide typing, suggestions and autocorrect.
     * Text edits are translated into host keystrokes by {@link StreamInputConnection}.
     */
    public void setImeTextMode(boolean enabled, StreamInputConnection.HostTextSink sink) {
        if (this.imeTextModeEnabled == enabled && this.hostTextSink == sink) {
            return;
        }
        this.imeTextModeEnabled = enabled;
        this.hostTextSink = sink;
        this.activeInputConnection = null;

        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.restartInput(this);
        }
    }

    public boolean isImeTextModeEnabled() {
        return imeTextModeEnabled && hostTextSink != null;
    }

    /**
     * Drops the IME's shadow text buffer because the host's text field may have changed
     * (touch, mouse click, physical keyboard, keyboard hidden, ...).
     */
    public void resetImeTextBuffer() {
        if (activeInputConnection != null) {
            activeInputConnection.onExternalInput();
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return imeTextModeEnabled;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!isImeTextModeEnabled()) {
            activeInputConnection = null;
            return super.onCreateInputConnection(outAttrs);
        }

        LimeLog.info("StreamView: creating smart keyboard InputConnection");

        // Include TYPE_TEXT_FLAG_AUTO_CORRECT and TYPE_TEXT_FLAG_MULTI_LINE so Gboard/LatinIME
        // enables its suggestion strip, dictionary and glide typing (without AUTO_CORRECT or
        // MULTI_LINE on TYPE_TEXT_VARIATION_NORMAL, Gboard suppresses suggestions & glide typing).
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_NORMAL |
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE;

        // NO_EXTRACT_UI/NO_FULLSCREEN: we're in landscape, and without these the IME would
        // show a fullscreen editor that covers the stream.
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE |
                EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                EditorInfo.IME_FLAG_NO_FULLSCREEN;

        activeInputConnection = new StreamInputConnection(this, hostTextSink);
        Editable editable = activeInputConnection.getEditable();
        int selStart = editable != null ? Selection.getSelectionStart(editable) : 0;
        int selEnd = editable != null ? Selection.getSelectionEnd(editable) : 0;
        outAttrs.initialSelStart = Math.max(0, selStart);
        outAttrs.initialSelEnd = Math.max(0, selEnd);
        outAttrs.initialCapsMode = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && editable != null) {
            outAttrs.setInitialSurroundingText(editable);
        }
        return activeInputConnection;
    }

    public interface InputCallbacks {
        boolean handleKeyUp(KeyEvent event);
        boolean handleKeyDown(KeyEvent event);
    }
}
