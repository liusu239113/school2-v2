package com.arktools.xiao.ui.campus

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.R
import com.arktools.xiao.domain.model.ClassOfficerRole
import com.arktools.xiao.domain.model.FacilityType
import com.arktools.xiao.domain.policy.CollegeType
import com.arktools.xiao.ui.theme.PrimaryDark
import com.arktools.xiao.ui.campus.CampusBuildTypes as BT

/**
 * 瓦片自由建造校园：
 * - 48×32 网格，等级分阶段开地
 * - 单指拖动平移，点格放置/搬移/铺装
 * - 建造抽屉（建筑 + 地面装扮），消息显示在触发容器内
 * - 首次进入显示四步新手引导（随存档记忆）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusView(
    onNavigateTo: (Int) -> Unit = {},
    viewModel: CampusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current.density

    // 双指缩放（0.55x ~ 2.4x），cell 随缩放变化，全地图统一
    var zoom by remember { mutableStateOf(1f) }
    val baseCell = 48.dp.value * density

    val cell = baseCell * zoom
    val worldW = BT.GRID_W * cell
    val worldH = BT.GRID_H * cell
    var camera by remember { mutableStateOf(Offset(0f, 0f)) }

    var pendingSpec by remember { mutableStateOf<BT.Spec?>(null) }
    var pendingTile by remember { mutableStateOf<BT.TileKind?>(null) }
    var moveTarget by remember { mutableStateOf<BT.PlacedBuilding?>(null) }
    // 摆放/铺装/搬移的幽灵位置（格子坐标）
    var ghost by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var ghostDragRemain by remember { mutableStateOf(Offset.Zero) }
    val inPlacementMode = pendingSpec != null || pendingTile != null || moveTarget != null

    // 幽灵位置合法性：与 ViewModel.canPlaceAt 同规则（边界/解锁区/地形/重叠/搬移豁免）
    fun canPlaceGhostAt(cx: Int, cy: Int, spec: BT.Spec): Boolean {
        // 搬移模式下豁免建筑自身
        val ignoreId = moveTarget?.let { it.facilityId.ifBlank { it.key } }
        for (dy in 0 until spec.h) for (dx in 0 until spec.w) {
            val cx2 = cx + dx
            val cy2 = cy + dy
            if (cx2 < 0 || cy2 < 0 || cx2 >= BT.GRID_W || cy2 >= BT.GRID_H) return false
            if (!BT.inUnlockedArea(cx2, cy2, state.campusLevel)) return false
            val t = state.terrain[cy2 * 1000L + cx2]
            if (t != null) return false
            val blocked = state.placed.any { p ->
                if (ignoreId != null && (p.facilityId == ignoreId || p.key == ignoreId)) return@any false
                // 与 canPlaceAt 一致：行政楼重建可落回原位
                if (spec.key == "ADMIN" && p.key == "ADMIN") return@any false
                val ps = BT.specByKey(p.key)
                ps != null && BT.occupies(p, ps, cx2, cy2)
            }
            if (blocked) return false
        }
        return true
    }

    val bitmaps = remember {
        val ids = listOf(
            R.drawable.bld_admin,
            R.drawable.bld_liberal,
            R.drawable.bld_generic,
            R.drawable.bld_library,
            R.drawable.bld_dorm,
            R.drawable.bld_art,
            R.drawable.bld_medicine,
            R.drawable.bld_hospital,
            R.drawable.bld_conference,
            R.drawable.bld_employment,
            R.drawable.bld_classroom,
            R.drawable.bld_canteen,
            R.drawable.bld_multimedia,
            R.drawable.bld_garden,
            R.drawable.bld_gate,
            R.drawable.bld_sports,
            R.drawable.bld_lab,
            R.drawable.bld_computer,
            R.drawable.bld_studio,
            R.drawable.bld_auditorium,
            R.drawable.deco_flowerbed,
            R.drawable.deco_tree,
            R.drawable.deco_lantern,
            R.drawable.deco_bench,
            R.drawable.deco_statue,
            R.drawable.deco_water,
            R.drawable.deco_cherry,
            R.drawable.deco_memorial,
            R.drawable.deco_school_sign,
            R.drawable.deco_fountain,
            R.drawable.deco_ginkgo,
            R.drawable.deco_bamboo,
            R.drawable.deco_lamp,
            R.drawable.deco_pavilion,
            R.drawable.deco_parcel,
            R.drawable.deco_fitness,
            R.drawable.bld_incubator,
            R.drawable.bld_intl,
            R.drawable.bld_logistics
        )
        ids.associateWith { res ->
            BitmapFactory.decodeResource(context.resources, res).asImageBitmap()
        }
    }
    val grassTile = remember(R.drawable.tile_grass) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_grass).asImageBitmap()
    }
    val pathTile = remember(R.drawable.tile_path) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_path).asImageBitmap()
    }
    // 楼名标签画笔（世界坐标系内绘制，避免 Compose 元素跟随拖动时漂移）
    val labelTextPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 11.sp.value * density
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val labelBgPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#CC0B2038")
            style = android.graphics.Paint.Style.FILL
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight

        fun clampCamera() {
            val minX = (screenW - worldW).coerceAtMost(0f)
            val minY = (screenH - worldH).coerceAtMost(0f)
            camera = Offset(
                camera.x.coerceIn(minX, 0f),
                camera.y.coerceIn(minY, 0f)
            )
        }

        // 以 focus 点为锚缩放（focus 指向的世界点保持不动）
        fun zoomBy(factor: Float, focus: Offset) {
            val oldCell = baseCell * zoom
            zoom = (zoom * factor).coerceIn(0.35f, 4.0f)
            val newCell = baseCell * zoom
            if (newCell != oldCell) {
                camera = Offset(
                    focus.x - (focus.x - camera.x) * (newCell / oldCell),
                    focus.y - (focus.y - camera.y) * (newCell / oldCell)
                )
                clampCamera()
            }
        }

        // 初始镜头：对准解锁区中心（大地图不要从左上角荒地开始）
        var cameraReady by remember { mutableStateOf(false) }
        LaunchedEffect2(state.campusLevel) {
            if (cameraReady) return@LaunchedEffect2
            val rect = BT.unlockedRect(state.campusLevel)
            val cx = (rect.x0 + rect.x1) / 2f * cell
            val cy = (rect.y0 + rect.y1) / 2f * cell
            camera = Offset(
                (screenW / 2f - cx).coerceIn((screenW - worldW).coerceAtMost(0f), 0f),
                (screenH / 2f - cy).coerceIn((screenH - worldH).coerceAtMost(0f), 0f)
            )
            cameraReady = true
        }

        // 进入摆放/铺装/搬移模式时，幽灵自动出现在屏幕中心的格子，立刻可见
        LaunchedEffect2(listOf(inPlacementMode, pendingSpec, pendingTile, moveTarget)) {
            if (inPlacementMode && ghost == null) {
                val world = Offset(screenW / 2f, screenH / 2f) - camera
                val rect = BT.unlockedRect(state.campusLevel)
                val spec = pendingSpec
                val maxX = (rect.x1 - (spec?.w ?: 1)).coerceAtLeast(rect.x0)
                val maxY = (rect.y1 - (spec?.h ?: 1)).coerceAtLeast(rect.y0)
                val cx = (world.x / cell).toInt().coerceIn(rect.x0, maxX)
                val cy = (world.y / cell).toInt().coerceIn(rect.y0, maxY)
                ghost = cx to cy
                ghostDragRemain = Offset.Zero
            }
            if (!inPlacementMode) {
                ghost = null
                ghostDragRemain = Offset.Zero
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(inPlacementMode, state.campusLevel, pendingSpec) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDrag = Offset.Zero
                        var dragged = false
                        var lastCentroid = down.position
                        var lastPointerCount = 1
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val centroid = Offset(
                                pressed.map { it.position.x }.average().toFloat(),
                                pressed.map { it.position.y }.average().toFloat()
                            )
                            if (pressed.size >= 2) {
                                val dist = (pressed[0].position - pressed[1].position).getDistance()
                                val prev = event.changes.mapNotNull { ch ->
                                    if (ch.previousPressed) ch.previousPosition else null
                                }
                                val oldDist = if (prev.size >= 2) {
                                    (prev[0] - prev[1]).getDistance()
                                } else 0f
                                if (oldDist > 12f && dist > 12f) {
                                    val raw = dist / oldDist
                                    val boosted = 1f + (raw - 1f) * 1.8f
                                    zoomBy(boosted, centroid)
                                    dragged = true
                                }
                            } else {
                                val delta = centroid - lastCentroid
                                // 手指数量变化（如捏合后抬起一指）时质心会瞬移，
                                // 这一帧不计入拖动，否则相机猛跳、点击判定随之错位
                                if (pressed.size != lastPointerCount) {
                                    totalDrag = Offset.Zero
                                } else {
                                    totalDrag += delta
                                    if (totalDrag.getDistance() > 12f) {
                                        dragged = true
                                        camera = Offset(camera.x + delta.x, camera.y + delta.y)
                                        clampCamera()
                                    }
                                }
                            }
                            lastPointerCount = pressed.size
                            lastCentroid = centroid
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (!dragged) {
                            val world = Offset(down.position.x - camera.x, down.position.y - camera.y)
                            val cx = (world.x / cell).toInt().coerceIn(0, BT.GRID_W - 1)
                            val cy = (world.y / cell).toInt().coerceIn(0, BT.GRID_H - 1)
                            if (inPlacementMode) {
                                val rect = BT.unlockedRect(state.campusLevel)
                                val spec = pendingSpec
                                val maxX = (rect.x1 - (spec?.w ?: 1)).coerceAtLeast(rect.x0)
                                val maxY = (rect.y1 - (spec?.h ?: 1)).coerceAtLeast(rect.y0)
                                ghost = cx.coerceIn(rect.x0, maxX) to cy.coerceIn(rect.y0, maxY)
                                ghostDragRemain = Offset.Zero
                            } else {
                                viewModel.onCellTapped(cx, cy)
                            }
                        }
                    }
                }
        ) {
            translate(left = camera.x, top = camera.y) {
                // 草地：世界坐标逐格绘制，与道路/建筑同一坐标系（拖动时整张地图一起动）
                val minCx = (((-camera.x) / cell).toInt() - 1).coerceAtLeast(-1)
                val minCy = (((-camera.y) / cell).toInt() - 1).coerceAtLeast(-1)
                val maxCx = (((-camera.x + size.width) / cell).toInt() + 1).coerceAtMost(BT.GRID_W)
                val maxCy = (((-camera.y + size.height) / cell).toInt() + 1).coerceAtMost(BT.GRID_H)
                for (cy in minCy..maxCy) {
                    for (cx in minCx..maxCx) {
                        drawImage(
                            image = grassTile,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(grassTile.width, grassTile.height),
                            dstOffset = IntOffset((cx * cell).toInt(), (cy * cell).toInt()),
                            dstSize = IntSize(cell.toInt(), cell.toInt()),
                            filterQuality = FilterQuality.None
                        )
                    }
                }

                // 瓦片网格线：让"一个格子"肉眼可见，建造吸附一目了然
                val gridLine = Color(0x1A000000)
                for (cx in minCx..maxCx) {
                    drawLine(gridLine, Offset(cx * cell, minCy * cell), Offset(cx * cell, (maxCy + 1) * cell), 1f)
                }
                for (cy in minCy..maxCy) {
                    drawLine(gridLine, Offset(minCx * cell, cy * cell), Offset((maxCx + 1) * cell, cy * cell), 1f)
                }

                // 地形瓦片
                state.terrain.forEach { (key, kind) ->
                    val tx = (key % 1000L).toInt() * cell
                    val ty = (key / 1000L).toInt() * cell
                    when (kind) {
                        BT.TileKind.ROAD -> drawImage(
                            image = pathTile,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(pathTile.width, pathTile.height),
                            dstOffset = IntOffset(tx.toInt(), ty.toInt()),
                            dstSize = IntSize(cell.toInt(), cell.toInt()),
                            filterQuality = FilterQuality.None
                        )
                        else -> {
                            val deco = bitmaps[kind.drawableRes]
                            if (deco != null) {
                                val aspect = deco.width.toFloat() / deco.height.toFloat().coerceAtLeast(1f)
                                var dw = cell * when (kind) {
                                    BT.TileKind.TREE -> 0.95f
                                    BT.TileKind.STATUE -> 0.72f
                                    BT.TileKind.LANTERN -> 0.62f
                                    BT.TileKind.BENCH -> 0.92f
                                    BT.TileKind.FLOWERBED -> 0.92f
                                    BT.TileKind.CHERRY_TREE -> 0.95f
                                    BT.TileKind.GINKGO -> 0.95f
                                    BT.TileKind.BAMBOO -> 0.8f
                                    BT.TileKind.LAMP -> 0.6f
                                    BT.TileKind.MEMORIAL -> 0.78f
                                    BT.TileKind.SCHOOL_SIGN -> 0.9f
                                    BT.TileKind.PAVILION -> 1.0f
                                    BT.TileKind.PARCEL -> 0.85f
                                    BT.TileKind.FITNESS -> 0.88f
                                    BT.TileKind.FOUNTAIN -> 1.0f
                                    else -> 0.88f
                                }
                                var dh = dw / aspect
                                if (dh > cell) {
                                    dh = cell
                                    dw = dh * aspect
                                }
                                val dx = tx + (cell - dw) / 2f
                                val dy = ty + (cell - dh)
                                drawImage(
                                    image = deco,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(deco.width, deco.height),
                                    dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                                    dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
                                    filterQuality = FilterQuality.None
                                )
                            }
                        }
                    }
                }

                // 锁定区域遮罩（加深蒙层与解锁区形成明显对比 + 金色边界 + 提示文字）
                val rect = BT.unlockedRect(state.campusLevel)
                val ux0 = rect.x0 * cell
                val uy0 = rect.y0 * cell
                val ux1 = rect.x1 * cell
                val uy1 = rect.y1 * cell
                drawRect(Color(0x52000000), Offset(0f, 0f), Size(worldW, uy0))
                drawRect(Color(0x52000000), Offset(0f, uy1), Size(worldW, worldH - uy1))
                drawRect(Color(0x52000000), Offset(0f, uy0), Size(ux0, uy1 - uy0))
                drawRect(Color(0x52000000), Offset(ux1, uy0), Size(worldW - ux1, uy1 - uy0))
                drawRect(Color(0xB3FFD54F), Offset(ux0, uy0), Size(ux1 - ux0, uy1 - uy0), style = Stroke(3f))
                drawContext.canvas.nativeCanvas.apply {
                    val hintPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        alpha = 160
                        textSize = cell * 0.45f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    if (uy0 > cell * 0.8f) {
                        drawText("升级校园解锁更多土地", worldW / 2f, uy0 / 2f, hintPaint)
                    }
                    if (worldH - uy1 > cell * 0.8f) {
                        drawText("升级校园解锁更多土地", worldW / 2f, (uy1 + worldH) / 2f, hintPaint)
                    }
                }

                // 建筑：占地格子保留，贴图按原图比例落在格子里，不硬拉扁
                fun drawSpriteInFootprint(
                    bmp: androidx.compose.ui.graphics.ImageBitmap,
                    originX: Float,
                    originY: Float,
                    footW: Float,
                    footH: Float,
                    alpha: Float = 1f
                ) {
                    val aspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                    var dw = footW
                    var dh = dw / aspect
                    if (dh > footH) {
                        dh = footH
                        dw = dh * aspect
                    }
                    val dx = originX + (footW - dw) / 2f
                    val dy = originY + (footH - dh)
                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
                        alpha = alpha,
                        filterQuality = FilterQuality.None
                    )
                }
                state.placed.forEach { placed ->
                    val spec = BT.specByKey(placed.key) ?: return@forEach
                    val bmp = bitmaps[spec.drawableRes] ?: return@forEach
                    val footW = spec.w * cell
                    val footH = spec.h * cell
                    drawRect(Color(0x220B2038), Offset(placed.x * cell, placed.y * cell), Size(footW, footH))
                    drawSpriteInFootprint(bmp, placed.x * cell, placed.y * cell, footW, footH)
                    if (placed.level >= 2) {
                        drawCircle(Color(0xFFFFE082), 3f, Offset(placed.x * cell + 4f, placed.y * cell + footH * 0.55f))
                        drawCircle(Color(0xFFFFE082), 3f, Offset(placed.x * cell + footW - 4f, placed.y * cell + footH * 0.55f))
                    }
                    if (placed.level >= 3) {
                        val poleX = placed.x * cell + footW - 8f
                        val poleY = placed.y * cell + footH * 0.12f
                        drawRect(Color(0xFF9AA8B5), Offset(poleX, poleY), Size(2f, footH * 0.12f))
                        drawRect(Color(0xFF1E96C8), Offset(poleX + 2f, poleY + 2f), Size(12f, 7f))
                    }
                    if (placed.isConstructing) {
                        drawRect(Color(0x990B2038), Offset(placed.x * cell, placed.y * cell), Size(footW, footH))
                        drawRect(Color(0xFFFFD54F), Offset(placed.x * cell, placed.y * cell), Size(footW, footH), style = Stroke(3f))
                    }
                }

                // 摆放/铺装/搬移幽灵预览：绿=可放，红=不可放（粗描边+四角标记，醒目）
                ghost?.let { (gx, gy) ->
                    val spec = pendingSpec
                    val gw = (spec?.w ?: 1) * cell
                    val gh = (spec?.h ?: 1) * cell
                    val valid = spec?.let { canPlaceGhostAt(gx, gy, it) } ?: true
                    val frameColor = if (valid) Color(0xFF00C853) else Color(0xFFFF1744)
                    drawRect(
                        if (valid) Color(0x7A00E676) else Color(0x7AFF5252),
                        Offset(gx * cell, gy * cell),
                        Size(gw, gh)
                    )
                    drawRect(frameColor, Offset(gx * cell, gy * cell), Size(gw, gh), style = Stroke(4f))
                    val cs = 12f
                    listOf(
                        Offset(gx * cell, gy * cell),
                        Offset(gx * cell + gw, gy * cell),
                        Offset(gx * cell, gy * cell + gh),
                        Offset(gx * cell + gw, gy * cell + gh)
                    ).forEach { corner ->
                        drawRect(frameColor, Offset(corner.x - cs / 2, corner.y - cs / 2), Size(cs, cs))
                    }
                    val previewBmp = spec?.let { bitmaps[it.drawableRes] }
                        ?: pendingTile?.let { bitmaps[it.drawableRes] }
                    if (previewBmp != null) {
                        drawSpriteInFootprint(previewBmp, gx * cell, gy * cell, gw, gh, 0.78f)
                    }
                }

                // 楼名标签：世界坐标系内绘制，与地图绝对同步（拖动/缩放零漂移）
                state.placed.forEach { placed ->
                    val spec = BT.specByKey(placed.key) ?: return@forEach
                    val text = if (placed.isConstructing) {
                        "${spec.displayName} · 施工${placed.constructionDaysLeft}天"
                    } else spec.displayName
                    val tw = labelTextPaint.measureText(text)
                    val centerX = placed.x * cell + spec.w * cell / 2f
                    val bottomY = placed.y * cell + spec.h * cell
                    val padH = 5f * density
                    val textH = labelTextPaint.textSize
                    val bgTop = bottomY + 2f * density
                    drawContext.canvas.nativeCanvas.apply {
                        drawRect(
                            centerX - tw / 2f - padH,
                            bgTop,
                            centerX + tw / 2f + padH,
                            bgTop + textH + 4f * density,
                            labelBgPaint
                        )
                        drawText(text, centerX, bgTop + textH + 1.5f * density, labelTextPaint)
                    }
                }
            }
        }

        // 楼名标签已绘制在 Canvas 世界坐标系内（与地图绝对同步，不再漂移）；
        // 点击建筑本体仍可打开面板。

        // 校园氛围条：把建造结果变成持续可见的经营数字
        if (!inPlacementMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 16.dp, end = 96.dp)
                    .background(Color(0xCC0B2038))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    "满意度 ${state.avgSatisfaction.toInt()}  床位 ${state.studentCount}/${state.dormBeds.coerceAtLeast(0)}  餐位 ${state.canteenSeats}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "班槽 ${state.classSlots} · 阅览 ${state.librarySeats} · 实验台 ${state.labBenches} · 机位 ${state.computerSeats}",
                    color = Color(0xFFB8C7D6),
                    fontSize = 10.sp
                )
                Text(
                    "用地 ${state.unlockedCells}/${state.totalCells} · 建筑 ${state.facilities.size}/${state.maxFacilities} · 装扮 ${state.decorCount}",
                    color = Color(0xFFB8C7D6),
                    fontSize = 10.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xCC0B2038))
                        .clickable {
                            val focus = Offset(screenW / 2f, screenH / 2f)
                            zoomBy(0.7f, focus)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("－", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Box(
                    modifier = Modifier
                        .background(Color(0xCC0B2038))
                        .clickable {
                            val focus = Offset(screenW / 2f, screenH / 2f)
                            zoomBy(1.4f, focus)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("＋", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
            FloatingActionButton(
                onClick = { viewModel.openBuildMenu() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("建造", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // 模式提示 + 操作结果：纵向堆叠在同一容器内，永不互相遮挡
        val modeHint = when {
            pendingSpec != null -> "摆放模式：拖动/点击选择位置，绿框可放、红框不可放；点「建在这里」确认。点此取消"
            pendingTile != null -> "铺装模式：拖动/点击选格，点「铺设」确认（${pendingTile?.costWan}万/格）。点此取消"
            moveTarget != null -> "搬移模式：拖动选择新位置，点「搬到这里」确认。点此取消"
            else -> null
        }
        val officerMessage by viewModel.officerMessage.collectAsState()
        if (modeHint != null || state.message != null || officerMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                modeHint?.let { hint ->
                    Text(
                        text = hint,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(Color(0xCC0B2038))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable {
                                pendingSpec = null
                                pendingTile = null
                                moveTarget = null
                                ghost = null
                                viewModel.cancelPlacement()
                            },
                        textAlign = TextAlign.Center
                    )
                }
                (officerMessage ?: state.message)?.let { msg ->
                    Text(
                        msg,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(Color(0xCC14648C))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable { viewModel.consumeMessage(); viewModel.consumeOfficerMessage() },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 摆放确认栏：显示费用，钱不够时置红禁用
        if (inPlacementMode && ghost != null) {
            val (gx, gy) = ghost!!
            val spec = pendingSpec
            val placeCost = spec?.facility?.let { type ->
                com.arktools.xiao.domain.model.FacilityCapacity.repeatCost(
                    type,
                    state.facilities.count { it.type == type }
                )
            } ?: spec?.costWan ?: 0.0
            val costText = when {
                spec != null -> "${placeCost.toInt()}万"
                pendingTile != null -> "${pendingTile?.costWan}万"
                else -> ""
            }
            val insufficient = spec != null && moveTarget == null && state.cash < placeCost
            val positionInvalid = spec != null && !canPlaceGhostAt(gx, gy, spec)
            val blocked = insufficient || positionInvalid
            val verb = when {
                spec != null -> "建在这里"
                moveTarget != null -> "搬到这里"
                else -> "铺在这里"
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.onCellTapped(gx, gy)
                        ghost = null
                        // 一次选择只落一个：结果（成功/失败原因）显示在顶部提示条
                        pendingSpec = null
                        pendingTile = null
                        moveTarget = null
                    },
                    enabled = !blocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (blocked) Color(0xFF8C2F2F) else MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color(0xFF8C2F2F)
                    )
                ) {
                    Text(
                        when {
                            insufficient -> "经费不足（需$costText）"
                            positionInvalid -> "此处不可建造"
                            else -> "$verb · $costText"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                OutlinedButton(onClick = {
                    pendingSpec = null
                    pendingTile = null
                    moveTarget = null
                    ghost = null
                    viewModel.cancelPlacement()
                }) {
                    Text("取消", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // 面板内消息（建筑面板顶部显示）
        state.selected?.let { building ->
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelection() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    state.message?.let { msg ->
                        Text(
                            msg,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xCC14648C))
                                .padding(8.dp)
                                .clickable { viewModel.consumeMessage() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    BuildingPanelContent(
                        viewModel = viewModel,
                        building = building,
                        state = state,
                        placed = state.selectedPlaced,
                        onUpgradeFacility = { viewModel.upgradeFacility(building.facility?.id ?: "") },
                        onUpgradeCampus = { viewModel.upgradeCampus() },
                        onOpenTeaching = { onNavigateTo(40) },
                        onOpenResearch = { onNavigateTo(41) },
                        onOpenConference = { onNavigateTo(23) },
                        onOpenStudentLife = { onNavigateTo(21) },
                        onOpenClub = { onNavigateTo(17) },
                        onOpenScholarship = { onNavigateTo(29) },
                        onOpenEmployment = { onNavigateTo(15) },
                        onOpenHiring = { onNavigateTo(2) },
                        onOpenActivities = { onNavigateTo(18) },
                        chainSummary = viewModel.libraryChainSummary(),
                        onMove = {
                            state.selectedPlaced?.let { placed ->
                                viewModel.clearSelection()
                                moveTarget = placed
                                viewModel.startMove(placed)
                            }
                        },
                        onRemove = {
                            state.selectedPlaced?.let { placed ->
                                viewModel.removePlaced(placed)
                            }
                        }
                    )
                }
            }
        }

        // 道路/装扮详情面板：查看效果并可拆除
        state.selectedTile?.let { tile ->
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearTileSelection() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tile.drawableRes != 0) {
                            Image(
                                painter = painterResource(id = tile.drawableRes),
                                contentDescription = tile.displayName,
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tile.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF182635)
                            )
                            Text(
                                "造价 ${tile.costWan}万 · 月维护 ${tile.monthlyMaintenanceWan}万",
                                fontSize = 12.sp,
                                color = Color(0xFF617386)
                            )
                            if (tile.satisfactionBonus > 0f) {
                                Text(
                                    "学生满意度 +${(tile.satisfactionBonus * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E9B78)
                                )
                            }
                            if (tile.reputationBonus > 0) {
                                Text(
                                    "每月声誉 +${tile.reputationBonus}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E96C8)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.clearTileSelection() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("关闭")
                        }
                        Button(
                            onClick = { viewModel.removeSelectedTile() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("拆除（返还${"%.1f".format(tile.costWan * 0.5)}万）")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        val pickingAdvisor by viewModel.pickingAdvisorClass.collectAsState()
        pickingAdvisor?.let { classId ->
            val options by viewModel.advisorOptions.collectAsState()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.closePickers() },
                title = { Text("指派学业导师") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (options.isEmpty()) Text("暂无在职教师", fontSize = 13.sp)
                        options.forEach { (tid, name) ->
                            Text(
                                name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.assignAdvisor(classId, tid) }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Text(
                        "关闭",
                        modifier = Modifier.clickable { viewModel.closePickers() }.padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }

        val pickingOfficer by viewModel.pickingOfficer.collectAsState()
        pickingOfficer?.let { target ->
            val options by viewModel.studentOptions.collectAsState()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.closePickers() },
                title = { Text("任命${target.role.displayName}") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (options.isEmpty()) Text("本班暂无学生", fontSize = 13.sp)
                        options.forEach { student ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = student.eligible) {
                                        viewModel.appointOfficer(target.classId, target.role, student.id)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    student.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (student.eligible) Color(0xFF182635) else Color(0xFF9AA5AF)
                                )
                                Text(
                                    "资格分 ${student.qualificationScore} · ${if (student.eligible) "符合要求" else "未达到要求"}",
                                    fontSize = 11.sp,
                                    color = if (student.eligible) Color(0xFF14648C) else Color(0xFFB15A54)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Text(
                        "关闭",
                        modifier = Modifier.clickable { viewModel.closePickers() }.padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }

        // 建造抽屉
        if (state.showBuildMenu) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeBuildMenu() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    state.message?.let { msg ->
                        Text(
                            msg,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xCC14648C))
                                .padding(8.dp)
                                .clickable { viewModel.consumeMessage() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    BuildMenuContent(
                        state = state,
                        onFoundCollege = { spec ->
                            viewModel.closeBuildMenu()
                            viewModel.startPlace(spec)
                            pendingSpec = spec
                        },
                        onBuyFacility = { spec ->
                            viewModel.closeBuildMenu()
                            viewModel.startPlace(spec)
                            pendingSpec = spec
                        },
                        onPaintTile = { tile ->
                            viewModel.closeBuildMenu()
                            viewModel.startPaint(tile)
                            pendingTile = tile
                        }
                    )
                }
            }
        }

    }
}

/** 简单的 LaunchedEffect 包装（避免额外 import 混乱） */
@Composable
private fun LaunchedEffect2(key: Any?, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { block() }
}

/** 建筑面板内容（实底白卡 + 搬移/拆除） */
@Composable
private fun BuildingPanelContent(
    viewModel: CampusViewModel,
    building: CampusViewModel.CampusBuilding,
    state: CampusViewModel.CampusUiState,
    placed: BT.PlacedBuilding?,
    onUpgradeFacility: () -> Unit,
    onUpgradeCampus: () -> Unit,
    onOpenTeaching: () -> Unit,
    onOpenResearch: () -> Unit = {},
    onOpenConference: () -> Unit = {},
    onOpenStudentLife: () -> Unit = {},
    onOpenClub: () -> Unit = {},
    onOpenScholarship: () -> Unit = {},
    onOpenEmployment: () -> Unit = {},
    onOpenHiring: () -> Unit = {},
    onOpenActivities: () -> Unit = {},
    chainSummary: String = "",
    onMove: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(building.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF182635))
        if (placed?.isConstructing == true) {
            Text(
                "施工中，还需 ${placed.constructionDaysLeft} 天竣工。竣工前不提供容量和加成。",
                fontSize = 13.sp,
                color = Color(0xFFB0413E)
            )
        }

        when (building.kind) {
            CampusViewModel.CampusBuilding.Kind.ADMIN -> {
                Text("校园等级 Lv.${state.campusLevel}", fontSize = 14.sp, color = Color(0xFF182635))
                Text(
                    "在校 ${state.studentCount} 人 · 在编教师 ${state.teacherCount} 人 · 声誉 ${state.reputation}",
                    fontSize = 13.sp,
                    color = Color(0xFF617386)
                )
                Text(
                    "满意度 ${state.avgSatisfaction.toInt()} · 住宿 ${state.avgDormSatisfaction.toInt()} · 餐标 ${state.avgMealQuality.toInt()}",
                    fontSize = 12.sp,
                    color = Color(0xFF14648C)
                )
                Text(
                    "装扮 ${state.decorCount} 件（每 8 件约 +0.2 满意度，上限 +3）",
                    fontSize = 12.sp,
                    color = Color(0xFF14648C)
                )
                Text(
                    "已解锁用地 ${state.unlockedCells}/${state.totalCells} 格 · 升级校园向外开地",
                    fontSize = 12.sp,
                    color = Color(0xFF14648C)
                )
                val seasonHint = when (state.currentMonth) {
                    8 -> "8月建校窗口：教室、宿舍、食堂都要落在地图上。没有宿舍，9月招不到人。"
                    9 -> "9月迎新季：床位满了就招不进来。扩招 = 再盖一栋宿舍，不是点一次升级完事。"
                    6, 7 -> "毕业与就业季：就业中心、竞赛和校友网络会决定这一年的口碑。"
                    1, 2 -> "寒假窗口：适合维修、扩建设施，少处理突发事件。"
                    else -> "日常经营：点建筑进入对应系统，月底会出校园周报。"
                }
                Text(seasonHint, fontSize = 12.sp, color = Color(0xFF617386))
                if (state.campusLevel < com.arktools.xiao.domain.engine.GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                    Text(
                        "升级到 Lv.${state.campusLevel + 1} 需要 ${state.upgradeCampusCost.toInt()} 万（还需满足声誉/师生等条件）",
                        fontSize = 13.sp,
                        color = Color(0xFF617386)
                    )
                    PanelButton("升级校园") { onUpgradeCampus() }
                } else {
                    Text("校园已满级。后期靠连盖宿舍/分馆、开下一轮课题和铺满地图，而不是再升一级。", fontSize = 13.sp, color = Color(0xFF2E9B78))
                }
                PanelButton("人事招聘") { onOpenHiring() }
                PanelButton("举办校园活动") { onOpenActivities() }
            }
            CampusViewModel.CampusBuilding.Kind.COLLEGE -> {
                val college = building.college
                if (college != null) {
                    Text(college.description, fontSize = 13.sp, color = Color(0xFF182635))
                    val enrollPct = ((college.enrollmentBonus) * 100).toInt()
                    val employPct = ((college.employmentBonus) * 100).toInt()
                    Text(
                        "招生 +$enrollPct% · 就业 +$employPct% · 月运营 ${college.monthlyCostWan}万",
                        fontSize = 13.sp,
                        color = Color(0xFF617386)
                    )
                    if (placed?.isConstructing != true) {
                        PanelButton("教学与招生管理") { onOpenTeaching() }
                    }
                    if (college == CollegeType.MEDICINE) {
                        Text(
                            "附属医院可在下方「建造」菜单扩建成后出现",
                            fontSize = 11.sp,
                            color = Color(0xFF617386)
                        )
                    }
                }
            }
            CampusViewModel.CampusBuilding.Kind.HOSPITAL -> {
                Text("附属医院投入后每月提供诊疗收入与声誉加成。", fontSize = 13.sp, color = Color(0xFF182635))
            }
            CampusViewModel.CampusBuilding.Kind.FACILITY -> {
                val facility = building.facility
                if (facility != null) {
                    Text(
                        "等级 Lv.${facility.level}/${facility.type.maxLevel}",
                        fontSize = 14.sp,
                        color = Color(0xFF182635)
                    )
                    Text(facility.type.description, fontSize = 13.sp, color = Color(0xFF617386))
                    when (facility.type) {
                        FacilityType.DORMITORY -> {
                            Text(
                                "床位 ${state.dormBeds} · 在校 ${state.studentCount} 人 · 住宿满意度 ${state.avgDormSatisfaction.toInt()}",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            Text("床位不够会直接卡招生，超员每天扣满意度和住宿分。可再建造一栋宿舍扩容。", fontSize = 12.sp, color = Color(0xFF617386))
                            PanelButton("学生生活") { onOpenStudentLife() }
                            PanelButton("奖学金/助学金") { onOpenScholarship() }
                        }
                        FacilityType.CANTEEN -> {
                            Text(
                                "餐位 ${state.canteenSeats} · 在校 ${state.studentCount} 人 · 餐标 ${state.avgMealQuality.toInt()}",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            Text("餐位不够会扣餐标和满意度。可再建造一栋食堂扩容。", fontSize = 12.sp, color = Color(0xFF617386))
                            PanelButton("学生生活") { onOpenStudentLife() }
                        }
                        FacilityType.SPORTS_FIELD -> {
                            Text(
                                "容纳 ${state.sportsCapacity} 人 · 社团 ${state.clubCount} 个 · 可再建场馆",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            Text("体育馆同时服务体育课、社团活动和季节赛事。", fontSize = 12.sp, color = Color(0xFF617386))
                            PanelButton("社团管理") { onOpenClub() }
                        }
                        FacilityType.EMPLOYMENT_CENTER -> {
                            Text(
                                "就业率 ${(state.employmentRate * 100).toInt()}% · 奖学金 ${state.scholarshipRecipientCount} 人",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            Text("就业中心与政府评级会共同影响毕业去向质量、薪资档位和雇主反馈。", fontSize = 12.sp, color = Color(0xFF617386))
                            PanelButton("就业与校友") { onOpenEmployment() }
                        }
                        FacilityType.CONFERENCE_CENTER -> {
                            Text(
                                "声誉增长 +${(state.campusLevel).coerceAtLeast(1) * 8}%/级 · 会议中心承接学术会议",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            PanelButton("举办学术会议") { onOpenConference() }
                        }
                        FacilityType.LIBRARY -> {
                            Text(
                                "阅览席 ${state.librarySeats} · 科研加速 +${(state.researchBonus * 100).toInt()}% · 可建分馆",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            if (chainSummary.isNotEmpty()) {
                                Text(chainSummary, fontSize = 12.sp, color = Color(0xFF14648C))
                            } else {
                                Text("建好图书馆后课题链会在这里显示，科研日会加快。", fontSize = 12.sp, color = Color(0xFF617386))
                            }
                            PanelButton("进入科研") { onOpenResearch() }
                        }
                        FacilityType.CLASSROOM -> {
                            val myClasses = viewModel.classesInBuilding(building.id)
                            val roomLevel = facility.level
                            val roomCapacity = com.arktools.xiao.domain.model.FacilityCapacity.classSlots(roomLevel)
                            Text(
                                "本楼 Lv.$roomLevel · 容纳 $roomCapacity 个教学班 · 已绑定 ${myClasses.size} 班",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            Text(
                                "容纳规则：教室 Lv1=3班 Lv2=4班 Lv3=6班 Lv4=7班 Lv5+=9班。可重复建造扩班。",
                                fontSize = 11.sp,
                                color = Color(0xFF617386)
                            )
                            if (myClasses.isEmpty()) {
                                Text("本楼暂无绑定班级（新班会按实际教室容量自动分配）", fontSize = 11.sp, color = Color(0xFF617386))
                            }
                            myClasses.forEach { row ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF0F4F8))
                                        .padding(8.dp)
                                ) {
                                    Text(row.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
                                    Text(
                                        (row.advisorName?.let { "导师：$it" } ?: "导师：未安排") +
                                            " · ${row.studentCount} 人",
                                        fontSize = 11.sp,
                                        color = Color(0xFF617386)
                                    )
                                    val officerSummary = ClassOfficerRole.entries.mapNotNull { role ->
                                        row.officers[role]?.let { "${role.displayName}：$it" }
                                    }
                                    Text(
                                        if (officerSummary.isEmpty()) "班干部：尚未任命" else officerSummary.joinToString(" · "),
                                        fontSize = 11.sp,
                                        color = Color(0xFF617386)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PanelButtonSmall("换导师") { viewModel.openAdvisorPicker(row.classId) }
                                    }
                                    ClassOfficerRole.entries.forEach { role ->
                                        val currentOfficer = row.officers[role]
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${role.displayName}：${currentOfficer ?: "未任命"}",
                                                modifier = Modifier.weight(1f),
                                                fontSize = 11.sp,
                                                color = Color(0xFF617386)
                                            )
                                            PanelButtonSmall(if (currentOfficer == null) "任命" else "更换") {
                                                viewModel.openOfficerPicker(row.classId, role)
                                            }
                                            if (currentOfficer != null) {
                                                PanelButtonSmall("撤销") {
                                                    viewModel.removeOfficer(row.classId, role)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            PanelButton("教学与招生管理") { onOpenTeaching() }
                        }
                        FacilityType.MULTIMEDIA_ROOM, FacilityType.LABORATORY, FacilityType.COMPUTER_LAB -> {
                            Text(
                                "实验台 ${state.labBenches} · 机位 ${state.computerSeats} · 教学质量 +${(state.teachingQualityBonus * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            Text("可重复建造。第二栋起边际收益递减，但容量仍会涨。", fontSize = 12.sp, color = Color(0xFF617386))
                            PanelButton("教学配置") { onOpenTeaching() }
                        }
                        FacilityType.ART_STUDIO -> {
                            Text(
                                "工位 ${state.studioCapacity} · 平均创造力 ${state.avgCreativity.toInt()}",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            PanelButton("教学配置") { onOpenTeaching() }
                        }
                        FacilityType.GARDEN, FacilityType.AUDITORIUM, FacilityType.GATE -> {
                            Text(
                                "平均社交 ${state.avgSocial.toInt()} · 全校满意度 ${state.avgSatisfaction.toInt()}",
                                fontSize = 12.sp,
                                color = Color(0xFF14648C)
                            )
                            Text("花园、礼堂和校门塑造校园文化，缺少它们社交会缓慢下滑。", fontSize = 12.sp, color = Color(0xFF617386))
                            PanelButton("学生生活") { onOpenStudentLife() }
                            PanelButton("社团管理") { onOpenClub() }
                        }
                        else -> {}
                    }
                    Text(
                        "月维护 ${facility.type.baseMaintenance}万",
                        fontSize = 13.sp,
                        color = Color(0xFF617386)
                    )
                    if (facility.level < facility.type.maxLevel) {
                        val panelButtonText = if (placed?.isConstructing == true) {
                            "施工中：还需 ${placed.constructionDaysLeft} 天"
                        } else {
                            "升级"
                        }
                        PanelButton(panelButtonText) { if (placed?.isConstructing != true) onUpgradeFacility() }
                    } else {
                        Text("已达最大等级", fontSize = 13.sp, color = Color(0xFF2E9B78))
                    }
                }
            }
        }

        // 搬移 / 拆除
        if (placed != null) {
            val spec = placed.let { BT.specByKey(it.key) }
            if (spec != null && spec.movable) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF4F7FA))
                            .clickable(onClick = onMove)
                            .padding(vertical = 8.dp, horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("搬移", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
                    }
                    if (spec.removable) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFD95C5C))
                                .clickable(onClick = onRemove)
                                .padding(vertical = 8.dp, horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("拆除（返30%）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 建造抽屉内容（建筑 + 地面装扮两个分区） */
@Composable
private fun BuildMenuContent(
    state: CampusViewModel.CampusUiState,
    onFoundCollege: (BT.Spec) -> Unit,
    onBuyFacility: (BT.Spec) -> Unit,
    onPaintTile: (BT.TileKind) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("建造", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF182635))
        Text(
            "当前经费 ${state.cash.toInt()}万 · 校园 Lv.${state.campusLevel} · 用地 ${state.unlockedCells}/${state.totalCells}",
            fontSize = 13.sp,
            color = Color(0xFF617386)
        )

        Text("学院", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        BT.COLLEGE_SPECS.forEach { spec ->
            val college = spec.college ?: return@forEach
            val founded = state.foundedColleges.contains(college)
            val constructionDays = state.constructingColleges[college]
            val shortOfCash = !founded && constructionDays == null && state.cash < spec.costWan
            val levelLocked = !founded && constructionDays == null && state.campusLevel < spec.unlockLevel
            val locked = shortOfCash || levelLocked || constructionDays != null
            val lockedText = when {
                founded -> null
                constructionDays != null -> "施工中 ${constructionDays}天"
                levelLocked -> "校园 Lv.${spec.unlockLevel}"
                shortOfCash -> "钱不够"
                else -> null
            }
            BuildRow(
                title = spec.displayName,
                subtitle = "${college.description} · 占地 ${spec.w}×${spec.h}",
                rightText = "${spec.costWan.toInt()}万",
                locked = locked,
                lockedText = lockedText,
                done = founded,
                previewRes = spec.drawableRes,
                onClick = { if (!founded && !locked) onFoundCollege(spec) }
            )
        }

        Text("功能建筑", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        Text(
            "已建 ${state.facilities.size}/${state.maxFacilities} · 点建筑打开对应系统",
            fontSize = 12.sp,
            color = Color(0xFF617386)
        )
        val hospitalLocked = state.campusLevel < BT.HOSPITAL.unlockLevel ||
            CollegeType.MEDICINE !in state.foundedColleges || state.affiliatedHospital ||
            state.cash < BT.HOSPITAL.costWan
        val hospitalLockText = when {
            state.affiliatedHospital -> "已建成"
            CollegeType.MEDICINE !in state.foundedColleges -> "需医学院竣工"
            state.campusLevel < BT.HOSPITAL.unlockLevel -> "校园 Lv.${BT.HOSPITAL.unlockLevel}"
            state.cash < BT.HOSPITAL.costWan -> "钱不够"
            else -> null
        }
        BuildRow(
            title = BT.HOSPITAL.displayName,
            subtitle = "医学院临床实习与诊疗收入 · 占地 ${BT.HOSPITAL.w}×${BT.HOSPITAL.h}",
            rightText = "300万",
            locked = hospitalLocked,
            lockedText = hospitalLockText,
            done = state.affiliatedHospital,
            previewRes = BT.HOSPITAL.drawableRes,
            onClick = { if (!hospitalLocked) onBuyFacility(BT.HOSPITAL) }
        )
        BT.FACILITY_SPECS.forEach { spec ->
            val type = spec.facility ?: return@forEach
            val owned = state.facilities.count { it.type == type }
            val uniqueDone = owned > 0 && !type.repeatable
            val nextCost = com.arktools.xiao.domain.model.FacilityCapacity.repeatCost(type, owned)
            val shortOfCash = !uniqueDone && state.cash < nextCost
            val levelLocked = owned == 0 && state.campusLevel < spec.unlockLevel
            val capLocked = !uniqueDone && state.facilities.size >= state.maxFacilities
            val prerequisiteCollege = spec.prerequisiteColleges.firstOrNull { it !in state.foundedColleges }
            val prerequisiteFacility = spec.prerequisiteFacilities.firstOrNull { required ->
                state.facilities.none { it.type == required && it.isOperational }
            }
            val prerequisiteLocked = prerequisiteCollege != null || prerequisiteFacility != null
            val locked = uniqueDone || shortOfCash || levelLocked || capLocked || prerequisiteLocked
            val lockedText = when {
                uniqueDone -> "已建成"
                levelLocked -> "校园 Lv.${spec.unlockLevel}"
                prerequisiteCollege != null -> "需${prerequisiteCollege.displayName}竣工"
                prerequisiteFacility != null -> "需${prerequisiteFacility.displayName}"
                capLocked -> "建筑已满"
                shortOfCash -> "钱不够"
                else -> null
            }
            val capacityHint = when (type) {
                FacilityType.CLASSROOM -> "班槽 ${state.classSlots} · 已建 ${owned} 栋"
                FacilityType.DORMITORY -> "在校 ${state.studentCount}/${state.dormBeds} 床 · 已建 ${owned} 栋"
                FacilityType.CANTEEN -> "餐位 ${state.canteenSeats} · 已建 ${owned} 栋"
                FacilityType.LIBRARY -> "阅览 ${state.librarySeats} · 已建 ${owned} 栋"
                FacilityType.LABORATORY -> "实验台 ${state.labBenches} · 已建 ${owned} 栋"
                FacilityType.COMPUTER_LAB -> "机位 ${state.computerSeats} · 已建 ${owned} 栋"
                FacilityType.SPORTS_FIELD -> "容纳 ${state.sportsCapacity} · 已建 ${owned} 栋"
                FacilityType.ART_STUDIO -> "工位 ${state.studioCapacity} · 已建 ${owned} 栋"
                else -> type.description
            }
            BuildRow(
                title = spec.displayName,
                subtitle = "$capacityHint · 占地 ${spec.w}×${spec.h}" +
                    (spec.downstream.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                rightText = if (type.repeatable && owned > 0) "再建 ${nextCost.toInt()}万" else "${nextCost.toInt()}万",
                locked = locked,
                lockedText = lockedText,
                done = uniqueDone,
                previewRes = spec.drawableRes,
                onClick = { if (!locked) onBuyFacility(spec) }
            )
        }

        Text("地面与装扮", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        BT.TileKind.entries.forEach { tile ->
            val shortOfCash = state.cash < tile.costWan
            val levelLocked = state.campusLevel < tile.unlockLevel
            BuildRow(
                title = tile.displayName,
                subtitle = if (levelLocked) "校园 Lv.${tile.unlockLevel} 解锁" else "1×1 格铺设",
                rightText = "${tile.costWan}万",
                locked = levelLocked || shortOfCash,
                lockedText = if (!levelLocked && shortOfCash) "钱不够" else null,
                done = false,
                previewRes = tile.drawableRes.takeIf { it != 0 },
                onClick = { if (!levelLocked && !shortOfCash) onPaintTile(tile) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun BuildRow(
    title: String,
    subtitle: String,
    rightText: String,
    locked: Boolean,
    done: Boolean,
    onClick: () -> Unit,
    lockedText: String? = null,
    previewRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF4F7FA))
            .clickable(enabled = !locked && !done, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (previewRes != null) {
            Image(
                painter = painterResource(id = previewRes),
                contentDescription = title,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 8.dp),
                contentScale = ContentScale.Crop
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF617386), maxLines = 2)
        }
        Text(
            text = when {
                done -> "已建成"
                locked && lockedText != null -> lockedText
                else -> rightText
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                done -> Color(0xFF2E9B78)
                locked && lockedText != null -> Color(0xFFD95C5C)
                locked -> Color(0xFF9AA8B5)
                else -> Color(0xFF1E96C8)
            }
        )
    }
}

@Composable
private fun PanelButtonSmall(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1E96C8))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun PanelButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
