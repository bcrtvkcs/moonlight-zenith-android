package com.limelight.binding.input;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.Selection;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.TextAttribute;

/**
 * An InputConnection that lets text-based IME features (Gboard glide typing, suggestions,
 * autocorrect, clipboard, etc.) work against a remote host that only understands keystrokes.
 *
 * It keeps a local "shadow" Editable that the IME edits like a normal text field. After every
 * edit (or batch of edits) the shadow text is diffed against what we already sent to the host
 * and the difference is replayed as cursor moves + backspaces + typed text.
 *
 * Since we cannot observe the host's real text field, the shadow buffer is reset whenever the
 * two may have diverged (Enter, touch/click on the stream, physical key presses, IME hidden).
 */
public class StreamInputConnection extends BaseInputConnection {

    public interface HostTextSink {
        /** Types printable text (no line breaks) on the host. */
        void sendText(String text);
        void sendBackspaces(int count);
        void sendForwardDeletes(int count);
        /** Moves the host caret by the given number of characters (negative = left). */
        void sendCursorMoves(int delta);
        void sendEnter();
        /** Delivers a raw key event coming from the IME. Returns true if handled. */
        boolean sendImeKeyEvent(KeyEvent event);
    }

    // Reset the shadow buffer when it grows beyond this to keep diffs cheap
    private static final int MAX_BUFFER_LENGTH = 512;

    private final View targetView;
    private final HostTextSink sink;
    private final InputMethodManager imm;

    // Text as it currently exists on the host (relative to the start of our buffer)
    private String sentText = "";
    // Host caret position in UTF-16 units within sentText
    private int hostCursor = 0;

    private int batchDepth = 0;
    private boolean pendingSync = false;
    private boolean closed = false;
    private boolean restartPending = false;

    public StreamInputConnection(View targetView, HostTextSink sink) {
        super(targetView, true);
        this.targetView = targetView;
        this.sink = sink;
        this.imm = (InputMethodManager) targetView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /** True if the shadow buffer holds text that could go stale. */
    public boolean hasBufferedText() {
        Editable e = getEditable();
        return e != null && e.length() > 0;
    }

    /**
     * Called when input reached the host outside of this connection (touch, click,
     * physical keyboard, IME hidden). The host caret/field may have changed, so drop our state.
     */
    public void onExternalInput() {
        if (!closed && hasBufferedText()) {
            resetBuffer(true);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Batch handling
    // ---------------------------------------------------------------------------------------

    @Override
    public boolean beginBatchEdit() {
        batchDepth++;
        return true;
    }

    @Override
    public boolean endBatchEdit() {
        if (batchDepth > 0) {
            batchDepth--;
        }
        if (batchDepth == 0) {
            if (pendingSync) {
                syncToHost();
            }
            notifySelection();
        }
        return batchDepth > 0;
    }

    private void afterEdit() {
        if (batchDepth > 0) {
            pendingSync = true;
            return;
        }
        syncToHost();
        notifySelection();
    }

    // ---------------------------------------------------------------------------------------
    // Text editing operations
    // ---------------------------------------------------------------------------------------

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (text != null && containsLineBreak(text)) {
            commitTextWithLineBreaks(text.toString(), newCursorPosition);
            return true;
        }
        boolean ret = super.commitText(text, newCursorPosition);
        afterEdit();
        return ret;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        boolean ret = super.setComposingText(text, newCursorPosition);
        afterEdit();
        return ret;
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        boolean ret = super.setComposingRegion(start, end);
        afterEdit();
        return ret;
    }

    @Override
    public boolean finishComposingText() {
        boolean ret = super.finishComposingText();
        afterEdit();
        return ret;
    }

    @Override
    public boolean setSelection(int start, int end) {
        boolean ret = super.setSelection(start, end);
        afterEdit();
        return ret;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        Editable e = getEditable();
        int extraBefore = 0, extraAfter = 0;
        if (e != null) {
            int a = Selection.getSelectionStart(e);
            int b = Selection.getSelectionEnd(e);
            int selMin = Math.max(0, Math.min(a, b));
            int selMax = Math.max(0, Math.max(a, b));
            extraBefore = Math.max(0, beforeLength - selMin);
            extraAfter = Math.max(0, afterLength - (e.length() - selMax));
        }

        boolean ret = super.deleteSurroundingText(beforeLength, afterLength);

        if (extraBefore > 0 || extraAfter > 0) {
            // The IME wants to delete text that lives on the host but not in our buffer
            // (e.g. backspace on an empty buffer). Apply the in-buffer part first so the host
            // caret is at the buffer edge, then delete the rest directly on the host.
            syncToHost();
            if (extraBefore > 0) {
                sink.sendBackspaces(extraBefore);
            }
            if (extraAfter > 0) {
                sink.sendForwardDeletes(extraAfter);
            }
            if (batchDepth == 0) {
                notifySelection();
            }
        } else {
            afterEdit();
        }
        return ret;
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        boolean ret = super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        afterEdit();
        return ret;
    }

    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Override
    public boolean replaceText(int start, int end, CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        boolean ret = super.replaceText(start, end, text, newCursorPosition, textAttribute);
        afterEdit();
        return ret;
    }

    @Override
    public boolean performEditorAction(int actionCode) {
        flushAll();
        sink.sendEnter();
        resetBuffer(false);
        return true;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (closed) {
            return super.sendKeyEvent(event);
        }

        // Make sure any pending text reaches the host before this key does
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            flushAll();
        }

        boolean handled = sink.sendImeKeyEvent(event);

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mirrorKeyIntoBuffer(event);
        }
        else if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            // Unknown text delivered as a key event; our buffer no longer matches the host
            onExternalInput();
        }

        return handled || super.sendKeyEvent(event);
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        Editable e = getEditable();
        if (e == null) {
            return null;
        }
        ExtractedText et = new ExtractedText();
        et.text = e.toString();
        et.startOffset = 0;
        et.partialStartOffset = -1;
        et.partialEndOffset = -1;
        et.selectionStart = Selection.getSelectionStart(e);
        et.selectionEnd = Selection.getSelectionEnd(e);
        et.flags = 0;
        return et;
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void closeConnection() {
        // Composing text was already sent live, so there's nothing to flush. Mark closed first
        // so the finishComposingText() done by the base class doesn't talk to the IME/host.
        closed = true;
        super.closeConnection();
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private static boolean containsLineBreak(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return true;
            }
        }
        return false;
    }

    private void commitTextWithLineBreaks(String text, int newCursorPosition) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i == 0 || !parts[i].isEmpty()) {
                // The first part also replaces any active composing text
                super.commitText(parts[i], i == parts.length - 1 ? newCursorPosition : 1);
            }
            if (i < parts.length - 1) {
                syncToHost();
                sink.sendEnter();
                resetBuffer(false);
            }
        }
        afterEdit();
    }

    /** Sends any pending changes regardless of batch state. */
    private void flushAll() {
        syncToHost();
    }

    private void syncToHost() {
        pendingSync = false;
        Editable e = getEditable();
        if (e == null || closed) {
            return;
        }

        String now = e.toString();
        int selA = Selection.getSelectionStart(e);
        int selB = Selection.getSelectionEnd(e);
        if (selA < 0) selA = now.length();
        if (selB < 0) selB = selA;
        int selMin = Math.min(selA, selB);
        int caretTarget = clamp(selB, 0, now.length());

        String old = sentText;
        if (!old.equals(now)) {
            // Find the changed region. Cap the common prefix at the old and new caret positions
            // so that ambiguous edits (e.g. "aa" -> "aaa") are applied where the user typed.
            int maxPrefix = Math.min(Math.min(old.length(), now.length()), Math.min(hostCursor, selMin));
            int prefix = 0;
            while (prefix < maxPrefix && old.charAt(prefix) == now.charAt(prefix)) {
                prefix++;
            }
            if (prefix > 0 && Character.isHighSurrogate(old.charAt(prefix - 1))) {
                prefix--;
            }

            int maxSuffix = Math.min(old.length(), now.length()) - prefix;
            int suffix = 0;
            while (suffix < maxSuffix &&
                    old.charAt(old.length() - 1 - suffix) == now.charAt(now.length() - 1 - suffix)) {
                suffix++;
            }
            if (suffix > 0 && Character.isLowSurrogate(old.charAt(old.length() - suffix))) {
                suffix--;
            }

            int oldEnd = old.length() - suffix;
            int newEnd = now.length() - suffix;

            moveHostCursorTo(oldEnd, old);

            int deleteCount = old.codePointCount(prefix, oldEnd);
            if (deleteCount > 0) {
                sink.sendBackspaces(deleteCount);
            }

            String inserted = now.substring(prefix, newEnd);
            if (!inserted.isEmpty()) {
                sink.sendText(inserted);
            }

            hostCursor = newEnd;
            sentText = now;
        }

        moveHostCursorTo(caretTarget, now);

        // Keep the buffer small. Only reset when the IME isn't in the middle of composing.
        if (now.length() > MAX_BUFFER_LENGTH && getComposingSpanStart(e) < 0 && batchDepth == 0) {
            resetBuffer(true);
        }
    }

    private void moveHostCursorTo(int target, String text) {
        target = clamp(target, 0, text.length());
        int current = clamp(hostCursor, 0, text.length());
        if (target > current) {
            sink.sendCursorMoves(text.codePointCount(current, target));
        }
        else if (target < current) {
            sink.sendCursorMoves(-text.codePointCount(target, current));
        }
        hostCursor = target;
    }

    /**
     * The IME sent a raw key that the host has already received via sendImeKeyEvent().
     * Mirror its effect in our buffer when we can, otherwise drop the buffer.
     */
    private void mirrorKeyIntoBuffer(KeyEvent event) {
        Editable e = getEditable();
        if (e == null) {
            return;
        }

        int keyCode = event.getKeyCode();
        int caret = clamp(Selection.getSelectionEnd(e), 0, e.length());
        boolean hasSelection = Selection.getSelectionStart(e) != Selection.getSelectionEnd(e);

        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
            case KeyEvent.KEYCODE_CAPS_LOCK:
                return;

            case KeyEvent.KEYCODE_DEL:
                if (e.length() == 0) {
                    // Nothing buffered, the host simply deleted a character we don't track
                    return;
                }
                if (!hasSelection && caret > 0) {
                    int start = Character.offsetByCodePoints(e, caret, -1);
                    removeComposingSpans(e);
                    e.delete(start, caret);
                    Selection.setSelection(e, start);
                    sentText = e.toString();
                    hostCursor = start;
                    notifySelection();
                    return;
                }
                break;

            case KeyEvent.KEYCODE_FORWARD_DEL:
                if (e.length() == 0) {
                    return;
                }
                if (!hasSelection && caret < e.length()) {
                    int end = Character.offsetByCodePoints(e, caret, 1);
                    removeComposingSpans(e);
                    e.delete(caret, end);
                    Selection.setSelection(e, caret);
                    sentText = e.toString();
                    hostCursor = caret;
                    notifySelection();
                    return;
                }
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (e.length() == 0) {
                    return;
                }
                if (!hasSelection && caret > 0) {
                    int pos = Character.offsetByCodePoints(e, caret, -1);
                    removeComposingSpans(e);
                    Selection.setSelection(e, pos);
                    hostCursor = pos;
                    notifySelection();
                    return;
                }
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (e.length() == 0) {
                    return;
                }
                if (!hasSelection && caret < e.length()) {
                    int pos = Character.offsetByCodePoints(e, caret, 1);
                    removeComposingSpans(e);
                    Selection.setSelection(e, pos);
                    hostCursor = pos;
                    notifySelection();
                    return;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                break;

            default:
                if (!hasSelection && !event.isCtrlPressed() && !event.isAltPressed() && !event.isMetaPressed()) {
                    int unicode = event.getUnicodeChar();
                    if ((unicode & KeyCharacterMap.COMBINING_ACCENT) == 0) {
                        int codePoint = unicode & KeyCharacterMap.COMBINING_ACCENT_MASK;
                        if (codePoint >= 0x20) {
                            String ch = new String(Character.toChars(codePoint));
                            removeComposingSpans(e);
                            e.insert(caret, ch);
                            int newCaret = caret + ch.length();
                            Selection.setSelection(e, newCaret);
                            sentText = e.toString();
                            hostCursor = newCaret;
                            notifySelection();
                            return;
                        }
                    }
                }
                break;
        }

        // Enter, Tab, Esc, up/down arrows, caret leaving the buffer, etc.
        if (e.length() > 0 || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            resetBuffer(false);
        }
    }

    private void resetBuffer(boolean restartIme) {
        Editable e = getEditable();
        if (e != null) {
            removeComposingSpans(e);
            e.clear();
            Selection.setSelection(e, 0);
        }
        sentText = "";
        hostCursor = 0;
        pendingSync = false;
        notifySelection();

        // When external input changed the host's focus/caret, also restart the input session
        // so the IME drops any active composing word or cached surrounding text.
        // Posted so we never restart in the middle of an IME call.
        if (restartIme && !restartPending && !closed) {
            restartPending = true;
            targetView.post(() -> {
                restartPending = false;
                if (!closed && imm != null) {
                    imm.restartInput(targetView);
                }
            });
        }
    }

    private void notifySelection() {
        if (closed || imm == null) {
            return;
        }
        Editable e = getEditable();
        if (e == null) {
            return;
        }
        imm.updateSelection(targetView,
                Selection.getSelectionStart(e), Selection.getSelectionEnd(e),
                getComposingSpanStart(e), getComposingSpanEnd(e));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
