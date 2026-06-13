package studio.attect.limelight.ui

import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.FrameLayout
import kotlin.math.abs

class TouchDragListener:OnTouchListener {
    private var lastX = 0f
    private var lastY = 0f
    private var lastMarginTop = 0
    private var lastMarginLeft = 0
    private var pressDownX = 0f
    private var pressDownY = 0f
    private var isClick = true
    private var pressDownTime: Long = 0

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val layoutParams = v.layoutParams as FrameLayout.LayoutParams
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                pressDownX = event.rawX
                pressDownY = event.rawY
                lastMarginTop = layoutParams.topMargin
                lastMarginLeft = layoutParams.leftMargin
                pressDownTime = System.currentTimeMillis()
                isClick = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs((event.rawX - pressDownX).toDouble()) + abs((event.rawY - pressDownY).toDouble()) > 20) {
                    isClick = false
                }

                val deltaX = event.rawX - lastX
                val deltaY = event.rawY - lastY

                layoutParams.leftMargin = deltaX.toInt() + lastMarginLeft
                layoutParams.topMargin = deltaY.toInt() + lastMarginTop
                v.setLayoutParams(layoutParams)

                lastMarginLeft = layoutParams.leftMargin
                lastMarginTop = layoutParams.topMargin
                lastX = event.rawX
                lastY = event.rawY
            }

            MotionEvent.ACTION_UP -> if (isClick) {
                if (System.currentTimeMillis() - pressDownTime > 2000) {
                    v.performLongClick()
                } else {
                    v.performClick()
                }
            }
        }
        return true
    }
}