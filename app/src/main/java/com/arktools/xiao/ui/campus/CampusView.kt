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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

    // 双指缩放（0.35x ~ 4x），cell 随缩放变化，全地图统一
    var zoom by remember { mutableFloatStateOf(1f) }
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
    val zoomNow = rememberUpdatedState(zoom)
    val cameraNow = rememberUpdatedState(camera)
    val cellNow = rememberUpdatedState(cell)
    val placementNow = rememberUpdatedState(inPlacementMode)
    val pendingSpecNow = rememberUpdatedState(pendingSpec)
    val pendingTileNow = rememberUpdatedState(pendingTile)
    val campusLevelNow = rememberUpdatedState(state.campusLevel)

    // 幽灵位置合法性：与 ViewModel.canPlaceAt 同规则（边界/解锁区/地形/重叠/搬移豁免）
    fun canPlaceGhostAt(cx: Int, cy: Int, spec: BT.Spec): Boolean {
        // 搬移模式下豁免建筑自身
        val ignoreId = moveTarget?.let { it.facilityId.ifBlank { it.key } }
        for (dy in 0 until spec.h) for (dx in 0 until spec.w) {
            val cx2 = cx + dx
            val cy2 = cy + dy
            if (cx2 < 0 || cy2 < 0 || cx2 >= BT.GRID_W || cy2 >= BT.GRID_H) return false
            if (!BT.inUnlockedArea(cx2, cy2, state.campusLevel)) return false
            val k2 = cy2 * 1000L + cx2
            if (state.terrain[k2] != null || state.decor[k2] != null) return false
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
            R.drawable.bld_science,
            R.drawable.bld_engineering,
            R.drawable.bld_business,
            R.drawable.bld_library,
            R.drawable.bld_dorm,
            R.drawable.bld_art,
            R.drawable.bld_medicine,
            R.drawable.bld_hospital,
            R.drawable.bld_clinic,
            R.drawable.bld_counseling,
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
    val plazaTile = remember(R.drawable.tile_plaza) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_plaza).asImageBitmap()
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
            val liveCell = baseCell * zoom
            val liveWorldW = BT.GRID_W * liveCell
            val liveWorldH = BT.GRID_H * liveCell
            // 世界比屏幕大：只能拖到边缘。世界比屏幕小：整张图可在屏幕里滑动，不能锁死在原点。
            val loX = minOf(0f, screenW - liveWorldW)
            val hiX = maxOf(0f, screenW - liveWorldW)
            val loY = minOf(0f, screenH - liveWorldH)
            val hiY = maxOf(0f, screenH - liveWorldH)
            val cam = camera
            camera = Offset(
                cam.x.coerceIn(loX, hiX),
                cam.y.coerceIn(loY, hiY)
            )
        }

        // 以 focus 点为锚缩放（focus 指向的世界点保持不动）
        fun zoomBy(factor: Float, focus: Offset) {
            val oldZoom = zoomNow.value
            val oldCell = baseCell * oldZoom
            val newZoom = (oldZoom * factor).coerceIn(0.35f, 4.0f)
            val newCell = baseCell * newZoom
            zoom = newZoom
            if (newCell != oldCell && oldCell > 0f) {
                val cam = cameraNow.value
                camera = Offset(
                    focus.x - (focus.x - cam.x) * (newCell / oldCell),
                    focus.y - (focus.y - cam.y) * (newCell / oldCell)
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
            camera = Offset(screenW / 2f - cx, screenH / 2f - cy)
            clampCamera()
            cameraReady = true
        }

        // ===== 行走学生小人：素材与状态 =====
        val walkerBitmaps = remember {
            listOf(
                R.drawable.student_walk_0,
                R.drawable.student_walk_1,
                R.drawable.student_walk_2,
                R.drawable.student_walk_3,
                R.drawable.student_walk_4,
                R.drawable.student_walk_5,
                R.drawable.student_walk_6,
                R.drawable.student_walk_7
            ).map { res -> BitmapFactory.decodeResource(context.resources, res) }
        }
        val walkerPaint = remember {
            android.graphics.Paint().apply { isFilterBitmap = false }
        }
        val walkableSet = remember(state.placed, state.terrain, state.decor, state.campusLevel) {
            buildWalkableSet(state)
        }
        var walkers by remember { mutableStateOf(emptyList<Walker>()) }
        var lastPaintCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        // 升级校园后新解锁地块的高亮提示（金色边框渐隐 9 秒）
        var unlockFlashUntil by remember { mutableStateOf(0L) }
        var lastLevelSeen by remember { mutableStateOf(state.campusLevel) }
        LaunchedEffect2(state.campusLevel) {
            if (state.campusLevel > lastLevelSeen) {
                unlockFlashUntil = System.currentTimeMillis() + 9000L
            }
            lastLevelSeen = state.campusLevel
        }

        val panelOpen = state.selected != null
        // 人数驱动：每 10 名在校生 1 个小人。弹窗打开时冻结，避免点建筑被刷新打断。
        LaunchedEffect2("${state.studentCount}|${walkableSet.size}|$panelOpen") {
            if (panelOpen) return@LaunchedEffect2
            val target = (state.studentCount / 10).coerceIn(0, WALKER_MAX)
            val cur = walkers.toMutableList()
            while (cur.size < target) {
                val k = walkableSet.randomOrNull() ?: break
                val gx = (k % 1000L).toInt()
                val gy = (k / 1000L).toInt()
                cur += Walker(
                    role = (0..7).random(),
                    fx = gx + 0.5f, fy = gy + 0.9f,
                    tx = gx, ty = gy,
                    facingRight = (0..1).random() == 0
                )
            }
            while (cur.size > target) cur.removeAt(cur.size - 1)
            walkers = cur
        }

        // 走路循环：约 20fps，弹窗打开时停步，点建筑不再被重绘抢焦点
        LaunchedEffect2("${walkableSet.size}|$panelOpen") {
            if (panelOpen) return@LaunchedEffect2
            while (true) {
                kotlinx.coroutines.delay(WALKER_TICK_MS)
                if (state.selected != null) break
                val viewL = -camera.x - cell
                val viewR = -camera.x + screenW + cell
                val viewT = -camera.y - cell
                val viewB = -camera.y + screenH + cell
                walkers = advanceWalkers(walkers, walkableSet, viewL, viewR, viewT, viewB, cell)
            }
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
                // 不能把 zoom/camera 当 key：拖动会改镜头，手势块会被重启，地图就拖不动、捏合也失效
                .pointerInput(inPlacementMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        lastPaintCell = null
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
                                    val oldZoom = zoom
                                    val oldCell = baseCell * oldZoom
                                    val newZoom = (oldZoom * boosted).coerceIn(0.35f, 4.0f)
                                    val newCell = baseCell * newZoom
                                    zoom = newZoom
                                    if (newCell != oldCell && oldCell > 0f) {
                                        val cam = camera
                                        camera = Offset(
                                            centroid.x - (centroid.x - cam.x) * (newCell / oldCell),
                                            centroid.y - (centroid.y - cam.y) * (newCell / oldCell)
                                        )
                                        clampCamera()
                                    }
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
                                    if (totalDrag.getDistance() > 8f) {
                                        dragged = true
                                        if (pendingTileNow.value != null) {
                                            val liveCell = cellNow.value
                                            val liveCam = cameraNow.value
                                            if (liveCell > 0f) {
                                                val cx = kotlin.math.floor((centroid.x - liveCam.x) / liveCell).toInt().coerceIn(0, BT.GRID_W - 1)
                                                val cy = kotlin.math.floor((centroid.y - liveCam.y) / liveCell).toInt().coerceIn(0, BT.GRID_H - 1)
                                                val prev = lastPaintCell
                                                if (prev == null) {
                                                    lastPaintCell = cx to cy
                                                } else if (prev.first != cx || prev.second != cy) {
                                                    viewModel.paintTiles(bresenhamLine(prev.first, prev.second, cx, cy))
                                                    lastPaintCell = cx to cy
                                                }
                                            }
                                        } else {
                                            camera = Offset(camera.x + delta.x, camera.y + delta.y)
                                            clampCamera()
                                        }
                                    }
                                }
                            }
                            lastPointerCount = pressed.size
                            lastCentroid = centroid
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (!dragged) {
                            val liveCell = cellNow.value
                            val liveCam = cameraNow.value
                            val world = Offset(
                                down.position.x - liveCam.x,
                                down.position.y - liveCam.y
                            )
                            if (liveCell <= 0f) return@awaitEachGesture
                            val cx = kotlin.math.floor(world.x / liveCell).toInt()
                                .coerceIn(0, BT.GRID_W - 1)
                            val cy = kotlin.math.floor(world.y / liveCell).toInt()
                                .coerceIn(0, BT.GRID_H - 1)
                            if (placementNow.value) {
                                val rect = BT.unlockedRect(campusLevelNow.value)
                                val spec = pendingSpecNow.value
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

                // 地面瓦片（水泥路/广场砖）
                state.terrain.forEach { (key, kind) ->
                    val tx = (key % 1000L).toInt() * cell
                    val ty = (key / 1000L).toInt() * cell
                    val image = when (kind) {
                        BT.TileKind.ROAD -> pathTile
                        BT.TileKind.PLAZA -> plazaTile
                        else -> null
                    }
                    if (image != null) {
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(tx.toInt(), ty.toInt()),
                            dstSize = IntSize(cell.toInt(), cell.toInt()),
                            filterQuality = FilterQuality.None
                        )
                    }
                }
                // 装饰瓦片（叠放在地面上）
                state.decor.forEach { (key, kind) ->
                    val tx = (key % 1000L).toInt() * cell
                    val ty = (key / 1000L).toInt() * cell
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
                    val poleX = placed.x * cell + footW - (8f * (cell / 48f).coerceIn(0.6f, 2.2f))
                    val poleY = placed.y * cell + footH * 0.08f
                    val poleH = (footH * 0.18f).coerceAtLeast(8f)
                    val flagW = (14f * (cell / 48f)).coerceIn(8f, 22f)
                    val flagH = (8f * (cell / 48f)).coerceIn(5f, 12f)
                    val festivalOn = state.festivalName.isNotBlank()
                    val flagColor = when {
                        festivalOn && state.festivalPhase == "进行中" -> Color(0xFFE53935)
                        festivalOn -> Color(0xFFFFB300)
                        placed.level >= 3 -> Color(0xFF1E96C8)
                        else -> Color(0xFF64B5F6)
                    }
                    drawRect(Color(0xFF9AA8B5), Offset(poleX, poleY), Size(2f, poleH))
                    drawRect(flagColor, Offset(poleX + 2f, poleY + 1f), Size(flagW, flagH))
                    if (festivalOn) {
                        drawRect(Color(0xFFFFF59D), Offset(poleX + 3f, poleY + 3f), Size(flagW * 0.35f, 2f))
                    }
                    if (placed.isConstructing) {
                        drawRect(Color(0x990B2038), Offset(placed.x * cell, placed.y * cell), Size(footW, footH))
                        drawRect(Color(0xFFFFD54F), Offset(placed.x * cell, placed.y * cell), Size(footW, footH), style = Stroke(3f))
                    }
                }
                if (state.festivalName.isNotBlank()) {
                    val deco = state.festivalDecoration.coerceIn(0, 3)
                    val plaza = state.placed.firstOrNull { it.key == "F_SPORTS_FIELD" || it.key == "F_AUDITORIUM" }
                        ?: state.placed.firstOrNull { it.key == "ADMIN" }
                    if (plaza != null) {
                        val spec = BT.specByKey(plaza.key)
                        val ox = plaza.x * cell
                        val oy = plaza.y * cell
                        val fw = (spec?.w ?: 3) * cell
                        val fh = (spec?.h ?: 2) * cell
                        if (deco >= 1) {
                            drawRect(Color(0xCCE53935), Offset(ox + fw * 0.08f, oy + fh * 0.12f), Size(fw * 0.84f, fh * 0.10f))
                            drawRect(Color(0xCCFFD54F), Offset(ox + fw * 0.08f, oy + fh * 0.20f), Size(fw * 0.84f, fh * 0.04f))
                        }
                        if (deco >= 2) {
                            drawRect(Color(0xCC8D6E63), Offset(ox + fw * 0.12f, oy + fh * 0.62f), Size(fw * 0.22f, fh * 0.22f))
                            drawRect(Color(0xCC8D6E63), Offset(ox + fw * 0.66f, oy + fh * 0.62f), Size(fw * 0.22f, fh * 0.22f))
                        }
                        if (deco >= 3 || state.festivalPhase == "进行中") {
                            drawRect(Color(0xCC5D4037), Offset(ox + fw * 0.32f, oy + fh * 0.38f), Size(fw * 0.36f, fh * 0.18f))
                            drawRect(Color(0xAAFFFFFF), Offset(ox + fw * 0.34f, oy + fh * 0.32f), Size(fw * 0.32f, fh * 0.08f))
                        }
                    }
                }

                // 新解锁地块高亮：升级后 9 秒内金色边框+淡金填充渐隐
                val nowMs = System.currentTimeMillis()
                if (nowMs < unlockFlashUntil && state.campusLevel > 1) {
                    val nr = BT.unlockedRect(state.campusLevel)
                    val flashAlpha = ((unlockFlashUntil - nowMs) / 9000f).coerceIn(0f, 1f)
                    drawRect(
                        Color(0x33FFD54F).copy(alpha = 0.30f * flashAlpha),
                        Offset(nr.x0 * cell, nr.y0 * cell),
                        Size((nr.x1 - nr.x0) * cell, (nr.y1 - nr.y0) * cell)
                    )
                    drawRect(
                        Color(0xFFFFD54F).copy(alpha = flashAlpha),
                        Offset(nr.x0 * cell, nr.y0 * cell),
                        Size((nr.x1 - nr.x0) * cell, (nr.y1 - nr.y0) * cell),
                        style = Stroke(5f)
                    )
                }

                // 行走学生小人：脚底对齐格底，比路灯略小；像素硬边渲染
                val viewL = -camera.x - cell
                val viewR = -camera.x + screenW + cell
                val viewT = -camera.y - cell
                val viewB = -camera.y + screenH + cell
                if (state.showWalkers) walkers.forEach { w ->
                    val wx = w.fx * cell
                    val wy = w.fy * cell
                    if (wx < viewL || wx > viewR || wy < viewT || wy > viewB) return@forEach
                    val sheet = walkerBitmaps[w.role % walkerBitmaps.size]
                    val cols = WALKER_FRAME_COLS
                    val fw = sheet.width / cols
                    val fh = sheet.height
                    val cycle = if (w.moving) w.phase else 0
                    val key = ((cycle / WALKER_SUB) % cols + cols) % cols
                    val hPx = cell * 0.60f
                    val wPx = hPx * fw / fh
                    val cx = wx
                    val bottom = wy
                    val dstRect = android.graphics.RectF(cx - wPx / 2f, bottom - hPx, cx + wPx / 2f, bottom)
                    val nc = drawContext.canvas.nativeCanvas
                    walkerPaint.alpha = 255
                    walkerPaint.color = android.graphics.Color.WHITE
                    if (!w.facingRight) {
                        nc.save()
                        nc.scale(-1f, 1f, cx, bottom - hPx / 2f)
                    }
                    nc.drawBitmap(sheet, walkerSrcRect(fw, fh, key, cols), dstRect, walkerPaint)
                    if (!w.facingRight) nc.restore()
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

                // 楼名只在选中或施工时显示，避免和点击热区抢视觉
                state.placed.forEach { placed ->
                    val selected = state.selectedPlaced?.let { sel ->
                        val sameId = sel.facilityId.isNotBlank() &&
                            sel.facilityId == placed.facilityId
                        val sameSpot = sel.key == placed.key &&
                            sel.x == placed.x && sel.y == placed.y
                        sameId || sameSpot
                    } == true
                    if (!selected && !placed.isConstructing) return@forEach
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

        var hudExpanded by remember { mutableStateOf(false) }
        if (!inPlacementMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 88.dp)
                    .background(Color(0xCC0B2038))
                    .clickable { hudExpanded = !hudExpanded }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    if (hudExpanded) {
                        "容量  收起"
                    } else {
                        "床 ${state.studentCount}/${state.dormBeds.coerceAtLeast(0)}  餐 ${state.canteenSeats}  班槽 ${state.classSlots}  展开"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (hudExpanded) {
                    Text(
                        "床位 ${state.studentCount}/${state.dormBeds.coerceAtLeast(0)}",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Text(
                        "餐位 ${state.canteenSeats} · 班槽 ${state.classSlots}",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Text(
                        "上月收入 ${state.monthlyRevenue.toInt()}万 · 支出 ${state.monthlyExpenses.toInt()}万",
                        color = Color(0xFFFFD54F),
                        fontSize = 10.sp
                    )
                    Text(
                        "${state.schoolTierName}·${state.schoolOwnershipName}",
                        color = Color(0xFFB8C7D6),
                        fontSize = 10.sp
                    )
                    Text(
                        "用地 ${state.unlockedCells}/${state.totalCells} · 装扮 ${state.decorCount}",
                        color = Color(0xFFB8C7D6),
                        fontSize = 10.sp
                    )
                    if (state.festivalName.isNotBlank()) {
                        Text(
                            "${state.festivalName}·${state.festivalPhase} 布置 ${state.festivalDecoration}/3",
                            color = Color(0xFFFFD54F),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .background(if (state.showWalkers) Color(0xCC0B2038) else Color(0xCC14648C))
                    .clickable { viewModel.toggleWalkers() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    if (state.showWalkers) "隐藏小人" else "显示小人",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
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
            if (state.campusLevel < com.arktools.xiao.domain.engine.GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFD54F))
                        .clickable { viewModel.upgradeCampus() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "升级校园 Lv.${state.campusLevel}→${state.campusLevel + 1}",
                        color = Color(0xFF182635),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
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
            pendingSpec != null -> "摆放模式：拖动/点击选择位置，绿框可放、红框不可放；点「建在这里」确认"
            pendingTile != null -> "铺装模式：拖动连线批量铺设，点格后按「铺设」单格铺（${pendingTile?.costWan}万/格）"
            moveTarget != null -> "搬移模式：拖动选择新位置，点「搬到这里」确认"
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        onOpenStudentLife = { onNavigateTo(21) },
                        onOpenEmployment = { onNavigateTo(15) },
                        onOpenHiring = { onNavigateTo(2) },
                        onOpenFacilities = { onNavigateTo(7) },
                        onOpenDiscipline = { onNavigateTo(45) },
                        onOpenDistrict = { onNavigateTo(4) },
                        onOpenGraduate = { onNavigateTo(46) },
                        onOpenConference = { onNavigateTo(23) },
                        onOpenInternational = { onNavigateTo(47) },
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
                    AppointmentPickers(viewModel)
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
                        onFinishAllByAd = { viewModel.finishAllConstructionByAd() },
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

// ===== 行走学生小人 =====

/** 两点之间的格子连线（Bresenham），拖动建路时按此批量铺装。 */
private fun bresenhamLine(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
    val points = mutableListOf<Pair<Int, Int>>()
    var x = x0
    var y = y0
    val dx = kotlin.math.abs(x1 - x0)
    val dy = -kotlin.math.abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
        points.add(x to y)
        if (x == x1 && y == y1) break
        val e2 = 2 * err
        if (e2 >= dy) { err += dy; x += sx }
        if (e2 <= dx) { err += dx; y += sy }
    }
    return points
}

/** 在校园地图上散步的小人：格子级移动，永不进入建筑/装饰/水域。 */
private data class Walker(
    val role: Int,
    val fx: Float,          // 脚底 x（格坐标，含 0.5 = 格中心）
    val fy: Float,          // 脚底 y
    val tx: Int, val ty: Int,      // 目标格
    val prevKey: Long = -1L,       // 上一格 key（防回头）
    val phase: Int = 0,
    val moving: Boolean = false,
    val facingRight: Boolean = true,
    val waitTicks: Int = 0
)

private const val WALKER_TICK_MS = 100L
private const val WALKER_STEP = 0.028f
private const val WALKER_MAX = 48      // 每10人1个小人，后期封顶避免校园地图把主线程拖死
private const val WALKER_SUB = 1        // 横向多帧精灵图，一拍一切
private const val WALKER_FRAME_COLS = 20

private fun walkerSrcRect(fw: Int, fh: Int, frame: Int, cols: Int): android.graphics.Rect {
    val f = ((frame % cols) + cols) % cols
    val sx = f * fw
    return android.graphics.Rect(sx, 0, sx + fw, fh)
}

private fun walkableKey(x: Int, y: Int): Long = y.toLong() * 1000L + x

/** 可走格：解锁区内、不在建筑矩形、无装饰/水域瓦片（纯草地与道路/广场可走）。 */
private fun buildWalkableSet(state: CampusViewModel.CampusUiState): Set<Long> {
    val rect = BT.unlockedRect(state.campusLevel)
    val blocked = HashSet<Long>()
    val doorKeys = HashSet<Long>()
    state.placed.forEach { b ->
        val spec = BT.specByKey(b.key) ?: return@forEach
        val isVisit = b.key == "F_CANTEEN" || b.key == "F_CLASSROOM" || b.key == "F_DORMITORY"
        for (y in b.y until b.y + spec.h) for (x in b.x until b.x + spec.w) {
            val k = walkableKey(x, y)
            if (isVisit && y == b.y + spec.h - 1) {
                doorKeys += k
            } else {
                blocked += k
            }
        }
    }
    val set = HashSet<Long>(1024)
    for (y in rect.y0 until rect.y1) for (x in rect.x0 until rect.x1) {
        val k = walkableKey(x, y)
        if (k in blocked) continue
        val tile = state.terrain[k]
        val deco = state.decor[k]
        if (k !in doorKeys && (deco != null || (tile != null && tile != BT.TileKind.ROAD && tile != BT.TileKind.PLAZA))) continue
        set += k
    }
    return set
}

private fun walkerNeighbors(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)

private fun advanceWalkers(
    list: List<Walker>,
    walkable: Set<Long>,
    viewL: Float,
    viewR: Float,
    viewT: Float,
    viewB: Float,
    cell: Float
): List<Walker> = list.map { w ->
    if (walkable.isEmpty()) return@map w
    val wx = w.fx * cell
    val wy = w.fy * cell
    val onScreen = wx >= viewL && wx <= viewR && wy >= viewT && wy <= viewB
    if (!onScreen) {
        return@map w
    }
    if (w.waitTicks > 0) return@map w.copy(waitTicks = w.waitTicks - 1, moving = false)
    val tcx = w.tx + 0.5f
    val tcy = w.ty + 0.9f   // 目标格底部（脚底对齐）
    val dx = tcx - w.fx
    val dy = tcy - w.fy
    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (dist <= WALKER_STEP) {
        val opts = walkerNeighbors(w.tx, w.ty).filter { p ->
            val k = walkableKey(p.first, p.second)
            k in walkable && k != w.prevKey
        }
        val pool = if (opts.isNotEmpty()) opts
        else walkerNeighbors(w.tx, w.ty).filter { walkable.contains(walkableKey(it.first, it.second)) }
        val next = pool.randomOrNull()
        if (next == null) {
            w.copy(moving = false, waitTicks = 4)
        } else {
            w.copy(
                prevKey = walkableKey(w.tx, w.ty),
                tx = next.first, ty = next.second,
                moving = true,
                facingRight = if (next.first != w.tx) next.first > w.tx else w.facingRight
            )
        }
    } else {
        val nx = w.fx + dx / dist * WALKER_STEP
        val ny = w.fy + dy / dist * WALKER_STEP
        w.copy(
            fx = nx, fy = ny, moving = true, phase = w.phase + 1,
            facingRight = if (kotlin.math.abs(dx) > 0.001f) dx > 0f else w.facingRight
        )
    }
}

/** 简单的 LaunchedEffect 包装（避免额外 import 混乱） */
@Composable
private fun LaunchedEffect2(key: Any?, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { block() }
}

@Composable
private fun AppointmentPickers(viewModel: CampusViewModel) {
    val pickingAdvisor by viewModel.pickingAdvisorClass.collectAsState()
    pickingAdvisor?.let { classId ->
        val options by viewModel.advisorOptions.collectAsState()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.closePickers() },
            title = { Text("任命班主任") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "按管理、心理、教学综合排序。点名字立刻任命，已带班的会从原班挪过来。",
                        fontSize = 12.sp,
                        color = Color(0xFF617386)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (options.isEmpty()) Text("暂无在职教师", fontSize = 13.sp)
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.assignAdvisor(classId, option.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (option.avatarRes != 0) {
                                Image(
                                    painter = painterResource(id = option.avatarRes),
                                    contentDescription = option.name,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(option.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
                                    if (option.recommended) {
                                        Text("推荐", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14648C))
                                    }
                                }
                                Text(option.detail, fontSize = 11.sp, color = Color(0xFF617386))
                                Text(
                                    "教学${option.teaching} 管理${option.management} 心理${option.psychology}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF14648C)
                                )
                                option.assignedClass?.let { assigned ->
                                    Text("已在 $assigned 当班主任", fontSize = 11.sp, color = Color(0xFFB15A54))
                                }
                            }
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

    val pickingOfficer by viewModel.pickingOfficer.collectAsState()
    pickingOfficer?.let { target ->
        val options by viewModel.studentOptions.collectAsState()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.closePickers() },
            title = { Text("任命${target.role.displayName}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "本班学生都能看。推荐按这个岗位的属性排，点名字立刻任命。",
                        fontSize = 12.sp,
                        color = Color(0xFF617386)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    student.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (student.eligible) Color(0xFF182635) else Color(0xFF9AA5AF)
                                )
                                if (student.recommended) {
                                    Text("推荐", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14648C))
                                }
                            }
                            Text(
                                "智${student.intelligence} 体${student.physical} 社${student.social} 创${student.creativity} 德${student.morality} · 满意${student.satisfaction}",
                                fontSize = 11.sp,
                                color = Color(0xFF617386)
                            )
                            Text(
                                "资格分 ${student.qualificationScore} · ${if (student.eligible) "符合要求" else "未达到要求"}" +
                                    (student.currentRole?.let { " · 已任$it" } ?: ""),
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

    val pickingAdminOffice by viewModel.pickingAdminOffice.collectAsState()
    pickingAdminOffice?.let { office ->
        val options by viewModel.advisorOptions.collectAsState()
        val officeName = when (office) {
            "personnel" -> "人事处"
            "student" -> "学工处"
            else -> "后勤处"
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.closePickers() },
            title = { Text("任命$officeName") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "从在职教师里选人。点名字立刻任职，这个职位开始按你选的策略自动批。",
                        fontSize = 12.sp,
                        color = Color(0xFF617386)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (options.isEmpty()) Text("先去招聘教师", fontSize = 13.sp)
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.assignAdminOfficer(office, option.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (option.avatarRes != 0) {
                                Image(
                                    painter = painterResource(id = option.avatarRes),
                                    contentDescription = option.name,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(option.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
                                    if (option.recommended) {
                                        Text("推荐", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14648C))
                                    }
                                }
                                Text(option.detail, fontSize = 11.sp, color = Color(0xFF617386))
                                Text(
                                    "教学${option.teaching} 管理${option.management} 心理${option.psychology}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF14648C)
                                )
                            }
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

    val managingClassId by viewModel.managingOfficersClass.collectAsState()
    managingClassId?.let { classId ->
        val row = viewModel.classRows.collectAsState().value.firstOrNull { it.classId == classId }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.closeOfficerBoard() },
            title = { Text("管理班委") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "六个职位随便点。推荐按这个岗位的属性排，点任命立刻生效。",
                        fontSize = 12.sp,
                        color = Color(0xFF617386)
                    )
                    PanelButton("一键任命空缺") { viewModel.autoAppointOfficers(classId) }
                    ClassOfficerRole.entries.forEach { role ->
                        val holder = row?.officers?.get(role)
                        val effect = when (role) {
                            ClassOfficerRole.MONITOR -> "班风、凝聚力"
                            ClassOfficerRole.STUDY_COMMITTEE -> "学业分"
                            ClassOfficerRole.LIFE_COMMITTEE -> "满意度、凝聚力"
                            ClassOfficerRole.ARTS_COMMITTEE -> "班风"
                            ClassOfficerRole.SPORTS_COMMITTEE -> "满意度、凝聚力"
                            ClassOfficerRole.MENTAL_HEALTH_COMMITTEE -> "满意度、纪律"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0F4F8))
                                .padding(8.dp)
                        ) {
                            Text(role.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
                            Text(
                                if (holder == null) "空缺 · 影响$effect" else "$holder · 影响$effect",
                                fontSize = 11.sp,
                                color = Color(0xFF617386)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (holder == null) "任命" else "更换",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14648C),
                                    modifier = Modifier
                                        .clickable { viewModel.openOfficerPicker(classId, role) }
                                        .padding(vertical = 4.dp)
                                )
                                if (holder != null) {
                                    Text(
                                        "撤销",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB15A54),
                                        modifier = Modifier
                                            .clickable { viewModel.removeOfficer(classId, role) }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    "关闭",
                    modifier = Modifier.clickable { viewModel.closeOfficerBoard() }.padding(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        )
    }
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
    onOpenStudentLife: () -> Unit = {},
    onOpenEmployment: () -> Unit = {},
    onOpenHiring: () -> Unit = {},
    onOpenFacilities: () -> Unit = {},
    onOpenDiscipline: () -> Unit = {},
    onOpenDistrict: () -> Unit = {},
    onOpenGraduate: () -> Unit = {},
    onOpenConference: () -> Unit = {},
    onOpenInternational: () -> Unit = {},
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
            val activity = LocalContext.current as? android.app.Activity
            PanelButton("看广告立即竣工") {
                if (activity != null && placed != null) {
                    com.arktools.adsdk.AdHelper.showRewardAd(
                        activity = activity,
                        onRewarded = { viewModel.finishConstructionByAd(placed) }
                    )
                }
            }
        }

        when (building.kind) {
            CampusViewModel.CampusBuilding.Kind.ADMIN -> {
                StatGrid(
                    listOf(
                        "校园" to "Lv.${state.campusLevel}",
                        "在校" to "${state.studentCount}人",
                        "教师" to "${state.teacherCount}人",
                        "本月" to "${state.monthlyRevenue.toInt() - state.monthlyExpenses.toInt()}万"
                    )
                )
                OccupancyBar("用地", state.unlockedCells, state.totalCells)
                OccupancyBar("满意度", state.avgSatisfaction.toInt(), 100)
                val adminOffice by viewModel.adminOfficeConfig.collectAsState()
                Text(
                    "领导班子要在这里任命。没人任职的审批会一直弹给你；任命后按你选的策略每月自动批。",
                    fontSize = 12.sp,
                    color = Color(0xFF617386)
                )
                AdminOfficeRow(
                    title = "人事处",
                    duty = "涨薪、续约、离职",
                    holder = if (adminOffice.personnelOfficerId.isBlank()) "空缺" else viewModel.adminOfficerName("personnel"),
                    strategy = viewModel.adminOfficeStrategyLabel("personnel"),
                    onAppoint = { viewModel.openAdminOfficePicker("personnel") },
                    onClear = { viewModel.clearAdminOfficer("personnel") },
                    onCycleStrategy = { viewModel.cycleAdminOfficeStrategy("personnel") }
                )
                AdminOfficeRow(
                    title = "学工处",
                    duty = "社团、活动",
                    holder = if (adminOffice.studentAffairsOfficerId.isBlank()) "空缺" else viewModel.adminOfficerName("student"),
                    strategy = viewModel.adminOfficeStrategyLabel("student"),
                    onAppoint = { viewModel.openAdminOfficePicker("student") },
                    onClear = { viewModel.clearAdminOfficer("student") },
                    onCycleStrategy = { viewModel.cycleAdminOfficeStrategy("student") }
                )
                AdminOfficeRow(
                    title = "后勤处",
                    duty = "修楼、水管、设施维修",
                    holder = if (adminOffice.logisticsOfficerId.isBlank()) "空缺" else viewModel.adminOfficerName("logistics"),
                    strategy = viewModel.adminOfficeStrategyLabel("logistics"),
                    onAppoint = { viewModel.openAdminOfficePicker("logistics") },
                    onClear = { viewModel.clearAdminOfficer("logistics") },
                    onCycleStrategy = { viewModel.cycleAdminOfficeStrategy("logistics") }
                )
                if (state.campusLevel < com.arktools.xiao.domain.engine.GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                    PanelButton("升级校园") { onUpgradeCampus() }
                }
                PanelButton("建筑一览") { onOpenFacilities() }
                PanelButton("人事招聘") { onOpenHiring() }
                PanelButton("教学强度") { onOpenTeaching() }
                PanelButton("校友与就业") { onOpenEmployment() }
                PanelButton("学生生活与投诉") { onOpenStudentLife() }
            }
            CampusViewModel.CampusBuilding.Kind.COLLEGE -> {
                val college = building.college
                if (college != null) {
                    val enrollPct = ((college.enrollmentBonus) * 100).toInt()
                    val employPct = ((college.employmentBonus) * 100).toInt()
                    StatGrid(
                        listOf(
                            "招生" to "+$enrollPct%",
                            "就业" to "+$employPct%",
                            "月费" to "${college.monthlyCostWan}万",
                            "状态" to if (placed?.isConstructing == true) "施工" else "运转"
                        )
                    )
                    if (placed?.isConstructing != true) {
                        val ops = viewModel.buildingOps()
                        when (college) {
                            CollegeType.SCIENCE -> {
                                PanelButton(if (ops.scienceLabOpen) "关夜间实验室" else "开夜间实验室") {
                                    viewModel.toggleBuildingOp("夜间实验室", 1.2, 3.0, 40L) { it.copy(scienceLabOpen = !it.scienceLabOpen) }
                                }
                                PanelButton("课题") { onOpenResearch() }
                            }
                            CollegeType.LIBERAL_ARTS -> {
                                PanelButton(if (ops.liberalOpenDay) "停开放日" else "开放日") {
                                    viewModel.toggleBuildingOp("人文学院开放日", 0.8, 2.0, 60L) { it.copy(liberalOpenDay = !it.liberalOpenDay) }
                                }
                                PanelButton("学科建设") { onOpenDiscipline() }
                            }
                            CollegeType.ENGINEERING -> {
                                PanelButton(if (ops.engineeringWorkshop) "停工坊" else "开工坊") {
                                    viewModel.toggleBuildingOp("工学院工坊", 1.6, 4.0, 50L) { it.copy(engineeringWorkshop = !it.engineeringWorkshop) }
                                }
                                PanelButton("课题") { onOpenResearch() }
                            }
                            CollegeType.BUSINESS -> {
                                PanelButton(if (ops.businessFair) "收对接会" else "对接会") {
                                    viewModel.toggleBuildingOp("商学院对接会", 1.4, 5.0, 80L) { it.copy(businessFair = !it.businessFair) }
                                }
                                PanelButton("外联") { onOpenDistrict() }
                            }
                            CollegeType.ARTS -> {
                                PanelButton(if (ops.artsShow) "停汇演" else "汇演") {
                                    viewModel.toggleBuildingOp("艺术汇演", 1.5, 4.0, 70L) { it.copy(artsShow = !it.artsShow) }
                                }
                                PanelButton("学生生活") { onOpenStudentLife() }
                            }
                            CollegeType.MEDICINE -> {
                                PanelButton(if (ops.medicineRounds) "停见习" else "临床见习") {
                                    viewModel.toggleBuildingOp("医学院见习", 1.8, 6.0, 50L) { it.copy(medicineRounds = !it.medicineRounds) }
                                }
                                PanelButton("研究生院") { onOpenGraduate() }
                            }
                        }
                    }
                }
            }
            CampusViewModel.CampusBuilding.Kind.HOSPITAL -> {
                val ops = viewModel.buildingOps()
                OccupancyBar("声誉加成", if (ops.hospitalClinic) 90 else 40, 100)
                PanelButton(if (ops.hospitalClinic) "关门诊" else "开门诊") {
                    viewModel.toggleBuildingOp("医院门诊", 2.0, 8.0, 90L) { it.copy(hospitalClinic = !it.hospitalClinic) }
                }
            }
            CampusViewModel.CampusBuilding.Kind.FACILITY -> {
                val facility = building.facility
                if (facility != null) {
                    OccupancyBar("等级", facility.level, facility.type.maxLevel)
                    when (facility.type) {
                        FacilityType.DORMITORY -> {
                            val roster = remember(building.id, state.studentCount, state.placed) {
                                viewModel.dormRoster(building.id)
                            }
                            OccupancyBar("本楼入住", roster.occupied, roster.beds)
                            OccupancyBar("全校床位", state.studentCount, state.dormBeds)
                            Text("床位满了，9月招不进来。", fontSize = 12.sp, color = Color(0xFF617386))
                            roster.floors.forEach { floor ->
                                val preview = floor.residents.take(2).joinToString("、") { it.name }
                                val extra = (floor.residents.size - 2).coerceAtLeast(0)
                                val line = when {
                                    floor.residents.isEmpty() -> "${floor.floor}层 空置"
                                    extra > 0 -> "${floor.floor}层 ${floor.residents.size}人 · $preview 等$extra 人"
                                    else -> "${floor.floor}层 ${floor.residents.size}人 · $preview"
                                }
                                Text(line, fontSize = 12.sp, color = Color(0xFF182635))
                            }
                            PanelButton("学生生活") { onOpenStudentLife() }
                        }
                        FacilityType.CANTEEN -> {
                            val extraSeats = viewModel.buildingOps().extraWindows * 40
                            val seats = state.canteenSeats
                            val shortage = (state.studentCount - seats).coerceAtLeast(0)
                            OccupancyBar("餐位", state.studentCount, seats)
                            Text(
                                if (shortage > 0) "缺 $shortage 人的饭，月底会投诉。"
                                else "够吃。窗口+$extraSeats 餐位 · 餐标 ${state.avgMealQuality.toInt()}",
                                fontSize = 12.sp,
                                color = if (shortage > 0) Color(0xFFB0413E) else Color(0xFF617386)
                            )
                            PanelButton("加开窗口（4万）") { viewModel.addCanteenWindow() }
                            PanelButton("窗口与菜品") { onOpenStudentLife() }
                        }
                        FacilityType.SPORTS_FIELD -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("场地容量", state.studentCount, state.sportsCapacity.coerceAtLeast(1))
                            PanelButton(if (ops.sportsMeet) "停办校运会" else "举办校运会") {
                                viewModel.toggleBuildingOp("校运会", 1.0, 3.0, 50L) { it.copy(sportsMeet = !it.sportsMeet) }
                            }
                        }
                        FacilityType.EMPLOYMENT_CENTER -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("就业率", (state.employmentRate * 100).toInt(), 100)
                            PanelButton(if (ops.jobFair) "收双选会" else "双选会") {
                                viewModel.toggleBuildingOp("就业双选会", 1.2, 4.0, 60L) { it.copy(jobFair = !it.jobFair) }
                            }
                            PanelButton("就业") { onOpenEmployment() }
                        }
                        FacilityType.CONFERENCE_CENTER -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("会议档", if (ops.conferenceHost) 80 else 30, 100)
                            PanelButton(if (ops.conferenceHost) "停承办" else "承办会议") {
                                viewModel.toggleBuildingOp("承办会议", 1.8, 6.0, 100L) { it.copy(conferenceHost = !it.conferenceHost) }
                            }
                            PanelButton("会议") { onOpenConference() }
                        }
                        FacilityType.LIBRARY -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("阅览席", state.studentCount, state.librarySeats.coerceAtLeast(1))
                            OccupancyBar("科研加速", (state.researchBonus * 100).toInt(), 100)
                            PanelButton(if (ops.libraryNight) "关夜阅" else "开夜阅") {
                                viewModel.toggleBuildingOp("夜间阅览", 0.6, 1.5, 20L) { it.copy(libraryNight = !it.libraryNight) }
                            }
                            PanelButton("科研") { onOpenResearch() }
                        }
                        FacilityType.CLASSROOM -> {
                            val myClasses = viewModel.classesInBuilding(building.id)
                            val roomLevel = facility.level
                            val seats = com.arktools.xiao.domain.model.FacilityCapacity.classSlots(roomLevel) * 30
                            val seated = myClasses.sumOf { it.studentCount }
                            OccupancyBar("本楼学位", seated, seats)
                            Text("Lv.$roomLevel · 学位 $seats 人。教室满了就招不进来。", fontSize = 12.sp, color = Color(0xFF617386))
                            if (myClasses.isEmpty()) {
                                Text("9月招生后自动分班。", fontSize = 12.sp, color = Color(0xFF8AA0B4))
                            }
                            myClasses.forEach { row ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF0F4F8))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "${row.name} · ${row.studentCount}人",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF182635)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (row.advisorAvatarRes != 0) {
                                            Image(
                                                painter = painterResource(id = row.advisorAvatarRes),
                                                contentDescription = row.advisorName,
                                                modifier = Modifier.size(36.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Text(
                                            row.advisorName ?: "未安排班主任",
                                            modifier = Modifier.weight(1f),
                                            fontSize = 12.sp,
                                            color = Color(0xFF182635)
                                        )
                                        PanelButtonSmall("换班主任") { viewModel.openAdvisorPicker(row.classId) }
                                    }
                                    val appointed = ClassOfficerRole.entries.count { row.officers[it] != null }
                                    Text(
                                        buildString {
                                            append("班干部 $appointed/${ClassOfficerRole.entries.size}")
                                            val names = row.officers.entries.joinToString(" · ") {
                                                "${it.key.displayName} ${it.value}"
                                            }
                                            if (names.isNotBlank()) {
                                                append(" · ")
                                                append(names)
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = Color(0xFF617386)
                                    )
                                    PanelButtonSmall("管理班委") { viewModel.openOfficerBoard(row.classId) }
                                }
                            }
                            PanelButton("教学强度与作息") { onOpenTeaching() }
                        }
                        FacilityType.MULTIMEDIA_ROOM, FacilityType.LABORATORY, FacilityType.COMPUTER_LAB -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("实验台", state.labBenches, 40.coerceAtLeast(state.labBenches))
                            OccupancyBar("机位", state.computerSeats, 40.coerceAtLeast(state.computerSeats))
                            if (facility.type == FacilityType.MULTIMEDIA_ROOM) {
                                PanelButton(if (ops.multimediaDrill) "停演练" else "公开课") {
                                    viewModel.toggleBuildingOp("公开课演练", 0.7, 2.0, 30L) { it.copy(multimediaDrill = !it.multimediaDrill) }
                                }
                            }
                            PanelButton("课题") { onOpenResearch() }
                        }
                        FacilityType.ART_STUDIO -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("工位", state.studentCount, state.studioCapacity.coerceAtLeast(1))
                            OccupancyBar("创造力", state.avgCreativity.toInt(), 100)
                            PanelButton(if (ops.artsShow) "停汇演" else "汇演") {
                                viewModel.toggleBuildingOp("艺术汇演", 1.5, 4.0, 70L) { it.copy(artsShow = !it.artsShow) }
                            }
                        }
                        FacilityType.GARDEN -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("氛围", if (ops.gardenFestival) 80 else 40, 100)
                            PanelButton(if (ops.gardenFestival) "停花季" else "花季开放") {
                                viewModel.toggleBuildingOp("花季开放", 0.4, 1.0, 20L) { it.copy(gardenFestival = !it.gardenFestival) }
                            }
                        }
                        FacilityType.AUDITORIUM -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("活动档", if (ops.auditoriumNight) 80 else 30, 100)
                            PanelButton(if (ops.auditoriumNight) "停晚会" else "晚会") {
                                viewModel.toggleBuildingOp("全校晚会", 1.1, 3.5, 50L) { it.copy(auditoriumNight = !it.auditoriumNight) }
                            }
                            PanelButton("学生生活") { onOpenStudentLife() }
                        }
                        FacilityType.GATE -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("接待", if (ops.gateReception) 80 else 30, 100)
                            PanelButton(if (ops.gateReception) "停接待" else "接待日") {
                                viewModel.toggleBuildingOp("校门接待日", 0.5, 1.5, 40L) { it.copy(gateReception = !it.gateReception) }
                            }
                        }
                        FacilityType.INCUBATOR -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("实习", if (ops.incubatorIntern) 80 else 30, 100)
                            PanelButton(if (ops.incubatorIntern) "停实习" else "实习输送") {
                                viewModel.toggleBuildingOp("实习输送", 1.5, 5.0, 40L) { it.copy(incubatorIntern = !it.incubatorIntern) }
                            }
                            PanelButton("外联") { onOpenDistrict() }
                        }
                        FacilityType.INTERNATIONAL_CENTER -> {
                            val ops = viewModel.buildingOps()
                            OccupancyBar("交换", if (ops.intlExchange) 80 else 30, 100)
                            PanelButton(if (ops.intlExchange) "暂停交换" else "交换生") {
                                viewModel.toggleBuildingOp("交换生项目", 2.2, 8.0, 90L) { it.copy(intlExchange = !it.intlExchange) }
                            }
                            PanelButton("国际交流") { onOpenInternational() }
                        }
                        FacilityType.LOGISTICS_CENTER -> {
                            OccupancyBar("维护折扣", facility.level * 20, 100)
                        }
                        FacilityType.CLINIC -> {
                            OccupancyBar("全校接诊", state.studentCount, state.clinicSlots)
                            Text(
                                "本楼容量 ${com.arktools.xiao.domain.model.FacilityCapacity.clinicSlots(facility.level)}。几栋医务室加总，不是一人一床。",
                                fontSize = 12.sp,
                                color = Color(0xFF617386)
                            )
                            PanelButton("学生生活") { onOpenStudentLife() }
                        }
                        FacilityType.COUNSELING -> {
                            OccupancyBar("全校辅导", state.studentCount, state.counselingSlots)
                            Text(
                                "本楼容量 ${com.arktools.xiao.domain.model.FacilityCapacity.counselingSlots(facility.level)}。几栋心理站加总，不是所有人挤同一栋。",
                                fontSize = 12.sp,
                                color = Color(0xFF617386)
                            )
                            PanelButton("学生生活") { onOpenStudentLife() }
                        }
                        else -> {}
                    }
                    OccupancyBar("月维护", facility.type.baseMaintenance.toInt(), 20.coerceAtLeast(facility.type.baseMaintenance.toInt()))
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
    onPaintTile: (BT.TileKind) -> Unit,
    onFinishAllByAd: () -> Unit = {}
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
        val constructingCount = state.placed.count { it.isConstructing }
        if (constructingCount > 0) {
            val activity = LocalContext.current as? android.app.Activity
            OutlinedButton(
                onClick = {
                    if (activity != null) {
                        com.arktools.adsdk.AdHelper.showRewardAd(
                            activity = activity,
                            onRewarded = onFinishAllByAd
                        )
                    }
                },
                enabled = activity != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("看广告一键竣工（${constructingCount}处施工中）")
            }
        }

        Text("学院", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        BT.COLLEGE_SPECS.forEach { spec ->
            val college = spec.college ?: return@forEach
            val founded = state.foundedColleges.contains(college)
            val constructionDays = state.constructingColleges[college]
            val shortOfCash = !founded && constructionDays == null && state.cash < spec.costWan
            val levelLocked = !founded && constructionDays == null && state.campusLevel < spec.unlockLevel
            val tierLocked = !founded && constructionDays == null &&
                state.allowedCollegeNames.isNotEmpty() && college.name !in state.allowedCollegeNames
            val locked = shortOfCash || levelLocked || constructionDays != null || tierLocked
            val lockedText = when {
                founded -> null
                constructionDays != null -> "施工中 ${constructionDays}天"
                tierLocked -> "${state.schoolTierName}未开放"
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
                FacilityType.CLINIC -> "全校接诊 ${state.studentCount}/${state.clinicSlots} · 已建 ${owned} 栋"
                FacilityType.COUNSELING -> "全校辅导 ${state.studentCount}/${state.counselingSlots} · 已建 ${owned} 栋"
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
private fun StatGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF0F4F8))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(label, fontSize = 11.sp, color = Color(0xFF617386))
                        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OccupancyBar(label: String, used: Int, total: Int) {
    val cap = total.coerceAtLeast(1)
    val ratio = (used.toFloat() / cap).coerceIn(0f, 1f)
    val barColor = when {
        ratio >= 1f -> Color(0xFFB0413E)
        ratio >= 0.8f -> Color(0xFFD89B1A)
        else -> Color(0xFF2E9B78)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = Color(0xFF617386))
            Text("$used / $total", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFFE6EEF4))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(8.dp)
                    .background(barColor)
            )
        }
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
private fun AdminOfficeRow(
    title: String,
    duty: String,
    holder: String,
    strategy: String,
    onAppoint: () -> Unit,
    onClear: () -> Unit,
    onCycleStrategy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F4F8))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
        Text("管$duty · 现任 $holder", fontSize = 12.sp, color = Color(0xFF617386))
        Text(
            if (holder == "空缺") "空缺时这些事仍要你亲自批" else "当前策略：$strategy",
            fontSize = 12.sp,
            color = Color(0xFF14648C)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButtonSmall(if (holder == "空缺") "任命教师" else "更换") { onAppoint() }
            if (holder != "空缺") {
                PanelButtonSmall("改策略") { onCycleStrategy() }
                PanelButtonSmall("撤职") { onClear() }
            }
        }
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
