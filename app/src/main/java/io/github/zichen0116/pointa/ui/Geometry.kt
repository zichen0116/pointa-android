package io.github.zichen0116.pointa.ui

import kotlin.math.min

enum class Edge { LEFT, RIGHT, TOP, BOTTOM }
data class Point(val x: Float, val y: Float)
data class Rect(val x: Float, val y: Float, val width: Float, val height: Float)
data class OneHandGeometry(val touch: Rect, val rail: Rect, val containerWidth: Float)

private fun Float.safe() = if (isFinite()) this else 0f

fun Margins.normalized(): Margins {
    val l = left.safe().coerceIn(0f, .58f)
    val r = right.safe().coerceIn(0f, .58f - l)
    val t = top.safe().coerceIn(0f, .6f)
    return Margins(l, r, t, bottom.safe().coerceIn(0f, .6f - t))
}

fun Margins.withEdge(edge: Edge, value: Float): Margins {
    val m = normalized()
    return when (edge) {
        Edge.LEFT -> m.copy(left = value.safe().coerceIn(0f, .58f - m.right))
        Edge.RIGHT -> m.copy(right = value.safe().coerceIn(0f, .58f - m.left))
        Edge.TOP -> m.copy(top = value.safe().coerceIn(0f, .6f - m.bottom))
        Edge.BOTTOM -> m.copy(bottom = value.safe().coerceIn(0f, .6f - m.top))
    }
}

fun Margins.valueFor(edge: Edge): Float = when (edge) { Edge.LEFT -> left; Edge.RIGHT -> right; Edge.TOP -> top; Edge.BOTTOM -> bottom }

fun oneHandGeometry(width: Float, height: Float, gap: Float, leftHand: Boolean, margins: Margins): OneHandGeometry {
    val w = width.safe().coerceAtLeast(1f)
    val h = height.safe().coerceAtLeast(1f)
    val g = gap.safe().coerceIn(0f, w * .1f)
    val base = w * .24f
    val container = w - base - g
    val m = margins.normalized()
    val origin = if (leftHand) 0f else base + g
    val touch = Rect(origin + container * m.left, h * m.top,
        container * (1f - m.left - m.right), h * (1f - m.top - m.bottom))
    val rail = if (leftHand) Rect(touch.x + touch.width + g, 0f, base + container * m.right, h)
        else Rect(0f, 0f, base + container * m.left, h)
    return OneHandGeometry(touch, rail, container)
}

fun fitAspect(width: Float, height: Float, aspect: Float?): Rect {
    val w = width.safe().coerceAtLeast(1f)
    val h = height.safe().coerceAtLeast(1f)
    if (aspect == null || !aspect.isFinite() || aspect <= 0) return Rect(0f, 0f, w, h)
    val fitWidth = min(w, h * aspect)
    val fitHeight = fitWidth / aspect
    return Rect((w - fitWidth) / 2f, (h - fitHeight) / 2f, fitWidth, fitHeight)
}

fun absolutePoint(x: Float, y: Float, canvas: Rect): Point = Point(
    ((x.safe() - canvas.x) / canvas.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
    ((y.safe() - canvas.y) / canvas.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
)

fun relativePoint(previous: Point, dx: Float, dy: Float, width: Float, height: Float, sensitivity: Int): Point {
    val gain = sensitivity.coerceIn(10, 100) / 60f
    return Point(
        (previous.x.safe() + dx.safe() / width.coerceAtLeast(1f) * gain).coerceIn(0f, 1f),
        (previous.y.safe() + dy.safe() / height.coerceAtLeast(1f) * gain).coerceIn(0f, 1f),
    )
}

fun Preferences.withVolumeMapping(previous: Boolean, key: String): Preferences {
    val valid = key.takeIf { it == "+" || it == "-" } ?: "0"
    return if (previous) copy(prevVolume = valid, nextVolume = if (valid != "0" && valid == nextVolume) "0" else nextVolume)
    else copy(nextVolume = valid, prevVolume = if (valid != "0" && valid == prevVolume) "0" else prevVolume)
}
