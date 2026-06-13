import re

with open('app/src/main/java/com/limelight/Game.java', 'r') as f:
    content = f.read()

c4 = """<<<<<<< HEAD
                /*
                if (!prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
=======
                if (getNativeTouchEventMode() && !prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
>>>>>>> attect/Attect-Dev"""
r4 = """                if (getNativeTouchEventMode() && !prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {"""
content = content.replace(c4, r4)

c5 = """<<<<<<< HEAD
                switch (event.getActionMasked())
                {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    for (TouchContext touchContext : touchContextMap) {
                        touchContext.setPointerCount(event.getPointerCount());
                    }
                    context.touchDownEvent(eventX, eventY, event.getEventTime(), true);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    if (event.getPointerCount() == 1 &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0)) {
                        // All fingers up
                        if (event.getEventTime() - multiFingerDownTime < MULTI_FINGER_TAP_THRESHOLD) {
                            // This is a 3 finger tap to bring up the keyboard
                            // multiFingerDownTime, previously threeFingerDowntime, is also used in native-touch for keyboard toggle.
                            toggleKeyboard();
                            return true;
=======
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_DOWN:
                        for (TouchContext touchContext : touchContextMap) {
                            touchContext.setPointerCount(event.getPointerCount());
                        }
>>>>>>> attect/Attect-Dev"""
r5 = """                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_DOWN:
                        for (TouchContext touchContext : touchContextMap) {
                            touchContext.setPointerCount(event.getPointerCount());
                        }
                        context.touchDownEvent(eventX, eventY, event.getEventTime(), true);
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_UP:
                        if (event.getPointerCount() == 1 &&
                                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0)) {
                            // All fingers up
                            if (event.getEventTime() - multiFingerDownTime < MULTI_FINGER_TAP_THRESHOLD) {
                                toggleKeyboard();
                                return true;"""
content = content.replace(c5, r5)

with open('app/src/main/java/com/limelight/Game.java', 'w') as f:
    f.write(content)
