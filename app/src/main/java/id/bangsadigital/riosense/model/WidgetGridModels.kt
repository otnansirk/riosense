package id.bangsadigital.riosense.model

const val MAX_WIDGET_PAGES = 5
const val WIDGET_GRID_ROWS = 4

enum class WidgetItemKind {
    BUILT_IN,
    APP_WIDGET
}

enum class BuiltInWidgetType(
    val minColSpan: Int,
    val minRowSpan: Int,
    val defaultColSpan: Int,
    val defaultRowSpan: Int
) {
    CLOCK(minColSpan = 2, minRowSpan = 1, defaultColSpan = 2, defaultRowSpan = 2),
    MUSIC(minColSpan = 2, minRowSpan = 2, defaultColSpan = 2, defaultRowSpan = 2),
    WEATHER(minColSpan = 2, minRowSpan = 2, defaultColSpan = 4, defaultRowSpan = 2),
    VEHICLE(minColSpan = 2, minRowSpan = 2, defaultColSpan = 4, defaultRowSpan = 2)
}

data class PlacedWidgetItem(
    val id: String,
    val pageIndex: Int,
    val kind: WidgetItemKind,
    val builtInType: BuiltInWidgetType? = null,
    val appWidgetId: Int = -1,
    val providerComponent: String? = null,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int
)

data class WidgetWorkspaceState(
    val pageCount: Int = 1,
    val items: List<PlacedWidgetItem> = emptyList()
)

fun defaultWidgetWorkspace(gridCols: Int): WidgetWorkspaceState {
    val safeCols = gridCols.coerceAtLeast(4)
    val rightWidth = (safeCols - 2).coerceAtLeast(2)
    return WidgetWorkspaceState(
        pageCount = 1,
        items = listOf(
            PlacedWidgetItem(
                id = "builtin_clock_0",
                pageIndex = 0,
                kind = WidgetItemKind.BUILT_IN,
                builtInType = BuiltInWidgetType.CLOCK,
                col = 0,
                row = 0,
                colSpan = 2,
                rowSpan = 2
            ),
            PlacedWidgetItem(
                id = "builtin_music_0",
                pageIndex = 0,
                kind = WidgetItemKind.BUILT_IN,
                builtInType = BuiltInWidgetType.MUSIC,
                col = 0,
                row = 2,
                colSpan = 2,
                rowSpan = 2
            ),
            PlacedWidgetItem(
                id = "builtin_weather_0",
                pageIndex = 0,
                kind = WidgetItemKind.BUILT_IN,
                builtInType = BuiltInWidgetType.WEATHER,
                col = 2,
                row = 0,
                colSpan = rightWidth,
                rowSpan = 2
            ),
            PlacedWidgetItem(
                id = "builtin_vehicle_0",
                pageIndex = 0,
                kind = WidgetItemKind.BUILT_IN,
                builtInType = BuiltInWidgetType.VEHICLE,
                col = 2,
                row = 2,
                colSpan = rightWidth,
                rowSpan = 2
            )
        )
    )
}

fun canPlaceWidget(
    items: List<PlacedWidgetItem>,
    pageIndex: Int,
    ignoreId: String?,
    col: Int,
    row: Int,
    colSpan: Int,
    rowSpan: Int,
    gridCols: Int,
    gridRows: Int
): Boolean {
    if (col < 0 || row < 0 || colSpan < 1 || rowSpan < 1) return false
    if (col + colSpan > gridCols || row + rowSpan > gridRows) return false

    val right = col + colSpan
    val bottom = row + rowSpan

    for (other in items) {
        if (other.pageIndex != pageIndex || other.id == ignoreId) continue
        val oLeft = other.col
        val oTop = other.row
        val oRight = other.col + other.colSpan
        val oBottom = other.row + other.rowSpan

        val overlapsX = col < oRight && right > oLeft
        val overlapsY = row < oBottom && bottom > oTop
        if (overlapsX && overlapsY) return false
    }
    return true
}

fun findFirstFreeSlot(
    items: List<PlacedWidgetItem>,
    pageIndex: Int,
    colSpan: Int,
    rowSpan: Int,
    gridCols: Int,
    gridRows: Int
): Pair<Int, Int>? {
    val safeW = colSpan.coerceIn(1, gridCols)
    val safeH = rowSpan.coerceIn(1, gridRows)
    for (r in 0..(gridRows - safeH)) {
        for (c in 0..(gridCols - safeW)) {
            if (canPlaceWidget(items, pageIndex, null, c, r, safeW, safeH, gridCols, gridRows)) {
                return c to r
            }
        }
    }
    return null
}

private fun rectsOverlap(
    c1: Int,
    r1: Int,
    w1: Int,
    h1: Int,
    c2: Int,
    r2: Int,
    w2: Int,
    h2: Int
): Boolean {
    return c1 < c2 + w2 && c1 + w1 > c2 && r1 < r2 + h2 && r1 + h1 > r2
}

/**
 * Resolves a smooth iOS/iPadOS-style widget reflow on a single page when [movingId] moves or
 * resizes to `(targetCol, targetRow, targetColSpan, targetRowSpan)`. Overlapping neighbors slide
 * to the nearest non-overlapping slots, falling back to full-page repacking so tight grids swap cleanly.
 */
fun resolveWidgetReflow(
    pageItems: List<PlacedWidgetItem>,
    movingId: String,
    targetCol: Int,
    targetRow: Int,
    targetColSpan: Int,
    targetRowSpan: Int,
    gridCols: Int,
    gridRows: Int
): List<PlacedWidgetItem>? {
    if (targetCol < 0 || targetRow < 0 || targetColSpan < 1 || targetRowSpan < 1) return null
    if (targetCol + targetColSpan > gridCols || targetRow + targetRowSpan > gridRows) return null

    val movingOrig = pageItems.firstOrNull { it.id == movingId } ?: return null
    val movedItem = movingOrig.copy(
        col = targetCol,
        row = targetRow,
        colSpan = targetColSpan,
        rowSpan = targetRowSpan
    )

    val others = pageItems.filter { it.id != movingId }
    val colliding = others.filter {
        rectsOverlap(targetCol, targetRow, targetColSpan, targetRowSpan, it.col, it.row, it.colSpan, it.rowSpan)
    }
    if (colliding.isEmpty()) {
        return pageItems.map { if (it.id == movingId) movedItem else it }
    }

    val nonColliding = others.filterNot {
        rectsOverlap(targetCol, targetRow, targetColSpan, targetRowSpan, it.col, it.row, it.colSpan, it.rowSpan)
    }

    // Attempt 1: Keep non-colliding widgets anchored and slide only directly overlapping widgets.
    val placedPartial = mutableListOf(movedItem)
    placedPartial.addAll(nonColliding)
    var partialOk = true
    val sortedColliding = colliding.sortedByDescending { it.colSpan * it.rowSpan }
    for (w in sortedColliding) {
        val safeW = w.colSpan.coerceIn(1, gridCols)
        val safeH = w.rowSpan.coerceIn(1, gridRows)
        val best = findNearestValidSlot(
            placed = placedPartial,
            item = w,
            width = safeW,
            height = safeH,
            gridCols = gridCols,
            gridRows = gridRows,
            preferCol = movingOrig.col,
            preferRow = movingOrig.row
        )
        if (best == null) {
            partialOk = false
            break
        }
        placedPartial.add(w.copy(col = best.first, row = best.second, colSpan = safeW, rowSpan = safeH))
    }
    if (partialOk) {
        val byId = placedPartial.associateBy { it.id }
        return pageItems.map { byId[it.id] ?: it }
    }

    // Attempt 2: Full-page repack around the moved/resized widget so dense 100%-full grids swap cleanly.
    val placedFull = mutableListOf(movedItem)
    val sortedOthers = others.sortedWith(
        compareByDescending<PlacedWidgetItem> { it.colSpan * it.rowSpan }
            .thenBy { kotlin.math.abs(it.col - movingOrig.col) + kotlin.math.abs(it.row - movingOrig.row) }
    )
    for (w in sortedOthers) {
        val safeW = w.colSpan.coerceIn(1, gridCols)
        val safeH = w.rowSpan.coerceIn(1, gridRows)
        val best = findNearestValidSlot(
            placed = placedFull,
            item = w,
            width = safeW,
            height = safeH,
            gridCols = gridCols,
            gridRows = gridRows,
            preferCol = movingOrig.col,
            preferRow = movingOrig.row
        ) ?: return null
        placedFull.add(w.copy(col = best.first, row = best.second, colSpan = safeW, rowSpan = safeH))
    }
    val byId = placedFull.associateBy { it.id }
    return pageItems.map { byId[it.id] ?: it }
}

/**
 * Tries [resolveWidgetReflow] at `(desiredCol, desiredRow)` and, on tight rows/columns where an
 * intermediate column leaves insufficient contiguous space for a wide neighbor, searches nearby
 * snapped slots biased toward `(dragDirX, dragDirY)` so nudging onto a neighbor always flows smoothly.
 */
fun resolveWidgetReflowWithNudge(
    pageItems: List<PlacedWidgetItem>,
    movingId: String,
    desiredCol: Int,
    desiredRow: Int,
    targetColSpan: Int,
    targetRowSpan: Int,
    dragDirX: Float,
    dragDirY: Float,
    gridCols: Int,
    gridRows: Int
): Pair<GridCellPos, List<PlacedWidgetItem>>? {
    val maxCol = (gridCols - targetColSpan).coerceAtLeast(0)
    val maxRow = (gridRows - targetRowSpan).coerceAtLeast(0)
    val clampedCol = desiredCol.coerceIn(0, maxCol)
    val clampedRow = desiredRow.coerceIn(0, maxRow)

    resolveWidgetReflow(
        pageItems = pageItems,
        movingId = movingId,
        targetCol = clampedCol,
        targetRow = clampedRow,
        targetColSpan = targetColSpan,
        targetRowSpan = targetRowSpan,
        gridCols = gridCols,
        gridRows = gridRows
    )?.let { return GridCellPos(clampedCol, clampedRow) to it }

    val candidates = mutableListOf<GridCellPos>()
    for (r in 0..maxRow) {
        for (c in 0..maxCol) {
            if (c == clampedCol && r == clampedRow) continue
            candidates.add(GridCellPos(c, r))
        }
    }

    val dirSignX = if (dragDirX > 12f) 1 else if (dragDirX < -12f) -1 else 0
    val dirSignY = if (dragDirY > 12f) 1 else if (dragDirY < -12f) -1 else 0

    candidates.sortWith(
        compareBy<GridCellPos> { cand ->
            val dx = cand.col - clampedCol
            val dy = cand.row - clampedRow
            val manhattan = kotlin.math.abs(dx) + kotlin.math.abs(dy)
            val againstDirPenalty =
                (if (dirSignX != 0 && dx * dirSignX < 0) 2 else 0) +
                    (if (dirSignY != 0 && dy * dirSignY < 0) 2 else 0)
            manhattan * 4 + againstDirPenalty
        }
    )

    for (cand in candidates) {
        val resolved = resolveWidgetReflow(
            pageItems = pageItems,
            movingId = movingId,
            targetCol = cand.col,
            targetRow = cand.row,
            targetColSpan = targetColSpan,
            targetRowSpan = targetRowSpan,
            gridCols = gridCols,
            gridRows = gridRows
        )
        if (resolved != null) {
            return cand to resolved
        }
    }
    return null
}

data class GridCellPos(val col: Int, val row: Int)

private fun findNearestValidSlot(
    placed: List<PlacedWidgetItem>,
    item: PlacedWidgetItem,
    width: Int,
    height: Int,
    gridCols: Int,
    gridRows: Int,
    preferCol: Int,
    preferRow: Int
): Pair<Int, Int>? {
    var bestSlot: Pair<Int, Int>? = null
    var bestScore = Int.MAX_VALUE
    for (r in 0..(gridRows - height)) {
        for (c in 0..(gridCols - width)) {
            val overlaps = placed.any { other ->
                rectsOverlap(c, r, width, height, other.col, other.row, other.colSpan, other.rowSpan)
            }
            if (!overlaps) {
                val origDist = kotlin.math.abs(c - item.col) + kotlin.math.abs(r - item.row)
                val swappedIntoOriginBonus = if (c == preferCol && r == preferRow) -1 else 0
                val score = origDist * 10 + swappedIntoOriginBonus
                if (score < bestScore) {
                    bestScore = score
                    bestSlot = c to r
                }
            }
        }
    }
    return bestSlot
}
