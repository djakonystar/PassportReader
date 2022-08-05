package site.texnopos.djakonystar.passportreader.mlkit.text

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.text.Text
import site.texnopos.djakonystar.passportreader.mlkit.other.GraphicOverlay

class TextGraphic(overlay: GraphicOverlay?, private val text: Text.Element?) :
    GraphicOverlay.Graphic(overlay!!) {
    private val rectPaint: Paint = Paint()
    private val textPaint: Paint

    constructor(overlay: GraphicOverlay?, text: Text.Element?, textColor: Int) : this(
        overlay,
        text
    ) {
        textPaint.color = textColor
        rectPaint.color = Color.alpha(0)
        // Redraw the overlay, as this graphic has been added.
        postInvalidate()
    }

    /** Draws the text block annotations for position, size, and raw value on the supplied canvas.  */
    override fun draw(canvas: Canvas?) {
        checkNotNull(text) { "Attempting to draw a null text." }

        // Draws the bounding box around the TextBlock.
        val rect = RectF(text.boundingBox)
        rect.left = translateX(rect.left)
        rect.top = translateY(rect.top)
        rect.right = translateX(rect.right)
        rect.bottom = translateY(rect.bottom)
        canvas?.drawRect(rect, rectPaint)

        // Renders the text at the bottom of the box.
        canvas?.drawText(text.text, rect.left, rect.bottom, textPaint)
    }

    companion object {
        const val TEXT_COLOR = Color.WHITE
        private const val TEXT_SIZE = 54.0f
        private const val STROKE_WIDTH = 4.0f
    }

    init {
        rectPaint.color = TEXT_COLOR
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = STROKE_WIDTH
        textPaint = Paint()
        textPaint.color = TEXT_COLOR
        textPaint.textSize = TEXT_SIZE
        // Redraw the overlay, as this graphic has been added.
        postInvalidate()
    }
}