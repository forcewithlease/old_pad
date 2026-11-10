package com.oldpad.app.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/** 正方形照片格子 */
class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        )
    }
}
