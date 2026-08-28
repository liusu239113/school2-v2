package com.arktools.xiaozhang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * 像素九宫格切片：边角按原始像素 1:1 绘制（FilterQuality.None 关闭平滑），
 * 仅边缘与中心区域拉伸。用于所有带像素装饰边框的按钮/卡片/弹窗背景，
 * 避免角花被 FillBounds 拉伸变形。
 *
 * @param res   drawable 资源
 * @param slice 源图切片边长（源图像素）
 */
@Composable
fun PixelNineSlice(
    res: Int,
    modifier: Modifier = Modifier,
    slice: Int = 32,
    alpha: Float = 1f,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    val bitmap = imageResource(res)
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawPixelNineSlice(bitmap, slice, alpha)
        }
        if (content != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.matchParentSize(),
                content = content
            )
        }
    }
}

private fun DrawScope.drawPixelNineSlice(bmp: ImageBitmap, slice: Int, alpha: Float) {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    val bw = bmp.width
    val bh = bmp.height
    val s = slice.coerceAtMost(bw / 2).coerceAtMost(bh / 2).coerceAtMost(w / 2).coerceAtLeast(1)
    val dw = w - 2 * s
    val dh = h - 2 * s
    val sw = bw - 2 * s
    val sh = bh - 2 * s

    fun draw(srcX: Int, srcY: Int, srcW: Int, srcH: Int, dstX: Int, dstY: Int, dstW: Int, dstH: Int) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return
        drawImage(
            image = bmp,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(dstX, dstY),
            dstSize = IntSize(dstW, dstH),
            alpha = alpha,
            filterQuality = FilterQuality.None
        )
    }

    // 四角 1:1
    draw(0, 0, s, s, 0, 0, s, s)
    draw(bw - s, 0, s, s, w - s, 0, s, s)
    draw(0, bh - s, s, s, 0, h - s, s, s)
    draw(bw - s, bh - s, s, s, w - s, h - s, s, s)
    // 上/下边
    if (dw > 0) {
        draw(s, 0, sw, s, s, 0, dw, s)
        draw(s, bh - s, sw, s, s, h - s, dw, s)
    }
    // 左/右边
    if (dh > 0) {
        draw(0, s, s, sh, 0, s, s, dh)
        draw(bw - s, s, s, sh, w - s, s, s, dh)
    }
    // 中心
    if (dw > 0 && dh > 0) {
        draw(s, s, sw, sh, s, s, dw, dh)
    }
}
