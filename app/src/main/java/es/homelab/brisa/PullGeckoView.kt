package es.homelab.brisa

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController

/**
 * GeckoView con «estirar hacia abajo para recargar». Solo se activa si Gecko dice que la web NO ha
 * usado el toque y que la página puede pasarse por arriba (no en mapas, chats o carruseles con
 * scroll propio). Es lo mismo que hace Firefox para Android (SwipeRefreshFeature).
 */
class PullGeckoView(ctx: Context) : GeckoView(ctx) {
    /** Distancia estirada en píxeles (0 = nada). */
    var onPull: ((Float) -> Unit)? = null
    /** Al soltar, con la distancia final. */
    var onRelease: ((Float) -> Unit)? = null
    var pullEnabled = true

    private var startY = 0f
    private var canPull = false
    private var pulling = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y; canPull = false; pulling = false
                if (pullEnabled) {
                    // Este método ya entrega el toque a Gecko y además dice qué ha hecho con él
                    onTouchEventForDetailResult(ev).accept { d ->
                        if (d != null) canPull =
                            d.handledResult() != PanZoomController.INPUT_RESULT_HANDLED_CONTENT &&
                            (d.scrollableDirections() and PanZoomController.SCROLLABLE_FLAG_TOP) == 0 &&
                            (d.overscrollDirections() and PanZoomController.OVERSCROLL_FLAG_VERTICAL) != 0
                    }
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (canPull) {
                val dy = ev.y - startY
                if (dy > 0) { pulling = true; onPull?.invoke(dy) }
                else if (pulling) { pulling = false; onPull?.invoke(0f) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pulling) {
                pulling = false
                onRelease?.invoke(if (ev.actionMasked == MotionEvent.ACTION_UP) ev.y - startY else 0f)
            }
        }
        return super.onTouchEvent(ev)
    }
}
