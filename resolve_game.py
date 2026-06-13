import re

with open('app/src/main/java/com/limelight/Game.java', 'r') as f:
    content = f.read()

# Conflict 1
c1 = """<<<<<<< HEAD
        }
        else {
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                multiFingerTapChecker(event);
                case MotionEvent.ACTION_DOWN: // first & following finger down.
                    if(prefConfig.enableEnhancedTouch) {
                        NativeTouchContext.Pointer pointer = new NativeTouchContext.Pointer(event); //create a Pointer Instance for new touch pointer, put it into the map.
                        nativeTouchPointerMap.put(pointer.getPointerId(), pointer);
                    }
                    break;
                case MotionEvent.ACTION_UP: // all fingers up
                    // toggle keyboard when all fingers lift up, just like how it works in trackpad mode.
                    if(event.getEventTime() - multiFingerDownTime < MULTI_FINGER_TAP_THRESHOLD) {
                        toggleKeyboard();
                    }
                case MotionEvent.ACTION_POINTER_UP:
                    if(prefConfig.enableEnhancedTouch) {
                        nativeTouchPointerMap.remove(event.getPointerId(event.getActionIndex()));
                    }
                    break;
            }
=======
        } else {
>>>>>>> attect/Attect-Dev"""
r1 = """        } else {
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                multiFingerTapChecker(event);
                case MotionEvent.ACTION_DOWN: // first & following finger down.
                    if(prefConfig.enableEnhancedTouch) {
                        NativeTouchContext.Pointer pointer = new NativeTouchContext.Pointer(event);
                        nativeTouchPointerMap.put(pointer.getPointerId(), pointer);
                    }
                    break;
                case MotionEvent.ACTION_UP: // all fingers up
                    if(event.getEventTime() - multiFingerDownTime < MULTI_FINGER_TAP_THRESHOLD) {
                        toggleKeyboard();
                    }
                case MotionEvent.ACTION_POINTER_UP:
                    if(prefConfig.enableEnhancedTouch) {
                        nativeTouchPointerMap.remove(event.getPointerId(event.getActionIndex()));
                    }
                    break;
            }"""
content = content.replace(c1, r1)

# Conflict 2
c2 = """<<<<<<< HEAD
        }
        else if ((eventSource & InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                 (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                 eventSource == InputDevice.SOURCE_MOUSE_RELATIVE)
        {
            // This case is for mice and non-finger touch devices, 非手指触控功能所属判断条件
=======
        } else if ((eventSource & InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) {
            // This case is for mice and non-finger touch devices
>>>>>>> attect/Attect-Dev"""
r2 = """        } else if ((eventSource & InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) {
            // This case is for mice and non-finger touch devices"""
content = content.replace(c2, r2)

# Conflict 3
c3 = """<<<<<<< HEAD
            else  //abs touch 和 屏幕虚拟手柄所属的判断条件
            {
                // TODO: Re-enable native touch when have a better solution for handling
                // cancelled touches from Android gestures and 3 finger taps to activate
                // the software keyboard.
                // 调整一下native touch passthrough的代码顺序
                if (!prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
                    // If this host supports touch events and absolute touch is enabled,
                    // send it directly as a touch event.
                    return true;
                }

=======
            else {
>>>>>>> attect/Attect-Dev"""
r3 = """            else {
                if (getNativeTouchEventMode() && !prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
                    return true;
                }
"""
content = content.replace(c3, r3)

# Conflict 4 & 5
c4 = """<<<<<<< HEAD
                /*
                if (!prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
=======
                if (getNativeTouchEventMode() && !prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
>>>>>>> attect/Attect-Dev
                    // If this host supports touch events and absolute touch is enabled,
                    // send it directly as a touch event.
                    return true;
                }

                TouchContext context = getTouchContext(actionIndex);
                if (context == null) {
                    return false;
                }

<<<<<<< HEAD
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
r4 = """                TouchContext context = getTouchContext(actionIndex);
                if (context == null) {
                    return false;
                }

                switch (event.getActionMasked()) {
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
content = content.replace(c4, r4)

with open('app/src/main/java/com/limelight/Game.java', 'w') as f:
    f.write(content)
