package io.github.zichen0116.pointa.ui

import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun independentEdgesKeepTheMinimumCanvas() {
        val m = Margins(left = .2f, top = .1f).withEdge(Edge.RIGHT, .9f).withEdge(Edge.BOTTOM, .9f)
        assertEquals(.2f, m.left, .0001f)
        assertEquals(.38f, m.right, .0001f)
        assertEquals(.5f, m.bottom, .0001f)
        assertEquals(Margins(), Margins(Float.NaN, -1f, Float.POSITIVE_INFINITY, -1f).normalized())
    }

    @Test fun railFollowsOnlyTheAdjacentEdgeAndMirrors() {
        val right = oneHandGeometry(300f, 500f, 9f, false, Margins(left = .25f))
        val left = oneHandGeometry(300f, 500f, 9f, true, Margins(right = .25f))
        assertEquals(72f + 219f * .25f, right.rail.width, .001f)
        assertEquals(right.rail.width, left.rail.width, .001f)
        assertEquals(9f, right.touch.x - right.rail.width, .001f)
        assertEquals(9f, left.rail.x - left.touch.width, .001f)
        assertEquals(72f, oneHandGeometry(300f, 500f, 9f, false, Margins(right = .25f)).rail.width, .001f)
        assertEquals(right.touch.width, left.touch.width, .001f)
    }

    @Test fun absoluteMappingUsesMonitorAspectAndReachesEveryCorner() {
        val bounds = fitAspect(300f, 400f, 16f / 9)
        assertEquals(300f, bounds.width, .001f)
        assertEquals(168.75f, bounds.height, .001f)
        assertEquals(Point(0f, 0f), absolutePoint(bounds.x, bounds.y, bounds))
        assertEquals(Point(1f, 1f), absolutePoint(bounds.x + bounds.width, bounds.y + bounds.height, bounds))
        assertEquals(Point(1f, 0f), absolutePoint(1000f, -1000f, bounds))
        assertEquals(Rect(0f, 0f, 300f, 400f), fitAspect(300f, 400f, Float.NaN))
    }

    @Test fun relativeMappingContinuesFromLastPositionAndClamps() {
        val start = Point(.3f, .6f)
        assertEquals(start, relativePoint(start, 0f, 0f, 200f, 400f, 60))
        val moved = relativePoint(start, 20f, 40f, 200f, 400f, 60)
        assertEquals(.4f, moved.x, .0001f)
        assertEquals(.7f, moved.y, .0001f)
        assertEquals(Point(0f, 1f), relativePoint(start, -500f, 900f, 200f, 400f, 100))
    }

    @Test fun volumeMappingRemovesOnlyPhysicalKeyConflicts() {
        val p = Preferences().withVolumeMapping(previous = true, key = "+")
        assertEquals("+", p.prevVolume)
        assertEquals("0", p.nextVolume)
        val disabled = p.withVolumeMapping(previous = false, key = "0")
        assertEquals("+", disabled.prevVolume)
        assertEquals("0", disabled.nextVolume)
        assertEquals("0", p.withVolumeMapping(true, "unknown").prevVolume)
    }
}
