package com.arktools.xiaozhang.ui.campus

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.policy.CollegeType
import com.arktools.xiaozhang.ui.theme.PrimaryDark
import com.arktools.xiaozhang.ui.campus.CampusBuildTypes as BT

/**
 * 瓦片自由建造校园：
 * - 22×14 网格，等级解锁区域
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

    val cell = 48.dp.value * density
    val worldW = BT.GRID_W * cell
    val worldH = BT.GRID_H * cell
    var camera by remember { mutableStateOf(Offset(0f, 0f)) }

    var pendingSpec by remember { mutableStateOf<BT.Spec?>(null) }
    var pendingTile by remember { mutableStateOf<BT.TileKind?>(null) }
    var moveTarget by remember { mutableStateOf<BT.PlacedBuilding?>(null) }

    val bitmaps = remember {
        mapOf(
            R.drawable.bld_admin to R.drawable.bld_admin,
            R.drawable.bld_liberal to R.drawable.bld_liberal,
            R.drawable.bld_generic to R.drawable.bld_generic,
            R.drawable.bld_library to R.drawable.bld_library,
            R.drawable.bld_dorm to R.drawable.bld_dorm,
            R.drawable.bld_art to R.drawable.bld_art,
            R.drawable.bld_medicine to R.drawable.bld_medicine,
            R.drawable.bld_hospital to R.drawable.bld_hospital
        ).mapValues { (_, res) ->
            BitmapFactory.decodeResource(context.resources, res).asImageBitmap()
        }
    }
    val grassTile = remember(R.drawable.tile_grass) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_grass).asImageBitmap()
    }
    val pathTile = remember(R.drawable.tile_path) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_path).asImageBitmap()
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

        // 初始镜头：对准解锁区中心
        LaunchedEffect2(Unit) {
            if (camera == Offset(0f, 0f)) {
                camera = Offset(
                    ((screenW - worldW) / 2f).coerceAtMost(0f) + 0f,
                    ((screenH - worldH) / 2f).coerceAtMost(0f)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        camera = Offset(camera.x - drag.x, camera.y - drag.y)
                        clampCamera()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val world = tap + camera
                        val cx = (world.x / cell).toInt().coerceIn(0, BT.GRID_W - 1)
                        val cy = (world.y / cell).toInt().coerceIn(0, BT.GRID_H - 1)
                        viewModel.onCellTapped(cx, cy)
                    }
                }
        ) {
            // 草地：屏幕空间平铺，随镜头位移产生移动感
            val ox = ((camera.x % cell) + cell) % cell
            val oy = ((camera.y % cell) + cell) % cell
            var sx = -ox
            while (sx < size.width) {
                var sy = -oy
                while (sy < size.height) {
                    drawImage(
                        image = grassTile,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(grassTile.width, grassTile.height),
                        dstOffset = IntOffset(sx.toInt(), sy.toInt()),
                        dstSize = IntSize(cell.toInt(), cell.toInt()),
                        filterQuality = FilterQuality.None
                    )
                    sy += cell
                }
                sx += cell
            }

            translate(left = camera.x, top = camera.y) {
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
                        BT.TileKind.PLAZA -> {
                            drawRect(Color(0xFFD9DDE3), Offset(tx, ty), Size(cell, cell))
                            drawRect(Color(0xFFB9BFC7), Offset(tx, ty), Size(cell, cell), style = Stroke(2f))
                        }
                        BT.TileKind.WATER -> {
                            drawRect(Color(0xFF3D9BD1), Offset(tx, ty), Size(cell, cell))
                            drawRect(Color(0xFF6FC0E8), Offset(tx + 6, ty + 14), Size(cell - 24, 4f))
                        }
                        BT.TileKind.FLOWERBED -> {
                            drawRect(Color(0xFF7A5230), Offset(tx + 4, ty + 4), Size(cell - 8, cell - 8))
                            drawCircle(Color(0xFFE1597B), 5f, Offset(tx + cell / 2, ty + cell / 2))
                        }
                        BT.TileKind.TREE -> {
                            drawRect(Color(0xFF6B4A2B), Offset(tx + cell / 2 - 3, ty + cell * 0.6f), Size(6f, cell * 0.3f))
                            drawCircle(Color(0xFF2E7D46), cell * 0.28f, Offset(tx + cell / 2, ty + cell * 0.4f))
                        }
                        BT.TileKind.LANTERN -> {
                            drawRect(Color(0xFFB0413E), Offset(tx + cell / 2 - 4, ty + cell * 0.35f), Size(8f, cell * 0.45f))
                            drawRect(Color(0xFFFFD54F), Offset(tx + cell / 2 - 6, ty + cell * 0.18f), Size(12f, 10f))
                        }
                        BT.TileKind.BENCH -> {
                            drawRect(Color(0xFF8A5A33), Offset(tx + 6, ty + cell * 0.5f), Size(cell - 12, 8f))
                            drawRect(Color(0xFF6B4A2B), Offset(tx + 8, ty + cell * 0.62f), Size(4f, 8f))
                            drawRect(Color(0xFF6B4A2B), Offset(tx + cell - 12, ty + cell * 0.62f), Size(4f, 8f))
                        }
                        BT.TileKind.STATUE -> {
                            drawRect(Color(0xFFB9BFC7), Offset(tx + cell / 2 - 6, ty + cell * 0.3f), Size(12f, cell * 0.5f))
                            drawCircle(Color(0xFFD9DDE3), 7f, Offset(tx + cell / 2, ty + cell * 0.22f))
                        }
                        else -> {}
                    }
                }

                // 锁定区域遮罩
                val ring = (state.campusLevel - 1).coerceAtMost(4)
                val ux0 = (BT.INIT_X - ring) * cell
                val uy0 = (BT.INIT_Y - ring) * cell
                val ux1 = ux0 + (BT.INIT_W + ring * 2) * cell
                val uy1 = uy0 + (BT.INIT_H + ring * 2) * cell
                drawRect(Color(0x66000000), Offset(0f, 0f), Size(worldW, uy0))
                drawRect(Color(0x66000000), Offset(0f, uy1), Size(worldW, worldH - uy1))
                drawRect(Color(0x66000000), Offset(0f, uy0), Size(ux0, uy1 - uy0))
                drawRect(Color(0x66000000), Offset(ux1, uy0), Size(worldW - ux1, uy1 - uy0))
                drawRect(Color(0x33FFFFFF), Offset(ux0, uy0), Size(ux1 - ux0, uy1 - uy0), style = Stroke(3f))

                // 建筑
                state.placed.forEach { placed ->
                    val spec = BT.specByKey(placed.key) ?: return@forEach
                    val bmp = bitmaps[spec.drawableRes] ?: return@forEach
                    val dstW = spec.w * cell
                    val dstH = spec.h * cell
                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(
                            (placed.x * cell).toInt(),
                            (placed.y * cell + dstH * 0.10f).toInt()
                        ),
                        dstSize = IntSize(dstW.toInt(), (dstH * 0.9f).toInt()),
                        filterQuality = FilterQuality.None
                    )
                }
            }
        }

        // 楼名标签（楼图下缘外侧，紧贴不遮挡）
        state.placed.forEach { placed ->
            val spec = BT.specByKey(placed.key) ?: return@forEach
            Box(
                modifier = Modifier
                    .offset(
                        x = with(LocalDensity.current) { (placed.x * cell - camera.x).toDp() },
                        y = with(LocalDensity.current) { (placed.y * cell + spec.h * cell * 0.92f - camera.y).toDp() }
                    )
                    .clickable {
                        val hit = viewModel.buildingAt(placed.x, placed.y)
                        if (hit != null) viewModel.selectPlaced(hit.first, hit.second)
                    }
            ) {
                Text(
                    text = spec.displayName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xCC0B2038))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // 建造 FAB
        FloatingActionButton(
            onClick = { viewModel.openBuildMenu() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text("建造", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // 模式提示（触发容器内顶部：资源条之下）
        val modeHint = when {
            pendingSpec != null -> "摆放模式：点击绿色解锁区的空地放置「${pendingSpec?.displayName}」，非法位置会提示原因"
            pendingTile != null -> "铺装模式：点击地图铺设「${pendingTile?.displayName}」（${pendingTile?.costWan}万/格）"
            moveTarget != null -> "搬移模式：点击空位移动「${moveTarget?.let { BT.specByKey(it.key)?.displayName }}」"
            else -> null
        }
        modeHint?.let { hint ->
            Text(
                text = hint,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp, start = 12.dp, end = 12.dp)
                    .background(Color(0xCC0B2038))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable {
                        pendingSpec = null
                        pendingTile = null
                        moveTarget = null
                        viewModel.consumeMessage()
                    },
                textAlign = TextAlign.Center
            )
        }

        // 面板内消息（建筑面板顶部显示）
        state.selected?.let { building ->
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelection() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                        building = building,
                        state = state,
                        placed = state.selectedPlaced,
                        onUpgradeFacility = { viewModel.upgradeFacility(building.facility?.id ?: "") },
                        onUpgradeCampus = { viewModel.upgradeCampus() },
                        onOpenTeaching = { onNavigateTo(40) },
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

        // 建造抽屉
        if (state.showBuildMenu) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeBuildMenu() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                            pendingSpec = spec
                        },
                        onBuyFacility = { spec ->
                            viewModel.closeBuildMenu()
                            pendingSpec = spec
                        },
                        onPaintTile = { tile ->
                            viewModel.closeBuildMenu()
                            pendingTile = tile
                        }
                    )
                }
            }
        }

        // 新手引导（四步，随存档记忆）
        if (!state.tutorialDone) {
            CampusTutorialOverlay(onDone = { viewModel.markTutorialDone() })
        }
    }
}

/** 简单的 LaunchedEffect 包装（避免额外 import 混乱） */
@Composable
private fun LaunchedEffect2(key: Any?, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { block() }
}

/** 新手引导遮罩（四步，文案即说明） */
@Composable
private fun CampusTutorialOverlay(onDone: () -> Unit) {
    var step by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val steps = listOf(
        "欢迎来到你的大学！\n\n点击校园里的建筑（如行政楼）可以查看与管理。",
        "点右下角「建造」按钮：\n\n可以建造学院楼、图书馆、宿舍，也可以铺设道路、摆放树木长椅，装扮你的校园。",
        "底部「人事」：发布招聘后，从三名候选人中挑选一位入职。",
        "底部「治院」：把 10 点预算分给教学、科研、校园生活或社会合作，6 月按年度目标考核。"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(enabled = false, onClick = {})
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .background(Color(0xFF0B2038))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "新手引导 ${step + 1}/4",
                color = Color(0xFFFFD54F),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                steps[step],
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "跳过",
                    color = Color(0xFFB8C7D6),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable(onClick = onDone)
                        .padding(6.dp)
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E96C8))
                        .clickable {
                            if (step >= steps.size - 1) onDone() else step += 1
                        }
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (step >= steps.size - 1) "开始经营" else "知道了",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** 建筑面板内容（实底白卡 + 搬移/拆除） */
@Composable
private fun BuildingPanelContent(
    building: CampusViewModel.CampusBuilding,
    state: CampusViewModel.CampusUiState,
    placed: BT.PlacedBuilding?,
    onUpgradeFacility: () -> Unit,
    onUpgradeCampus: () -> Unit,
    onOpenTeaching: () -> Unit,
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

        when (building.kind) {
            CampusViewModel.CampusBuilding.Kind.ADMIN -> {
                Text("校园等级 Lv.${state.campusLevel}", fontSize = 14.sp, color = Color(0xFF182635))
                if (state.campusLevel < com.arktools.xiaozhang.domain.engine.GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                    Text(
                        "升级到 Lv.${state.campusLevel + 1} 需要 ${state.upgradeCampusCost.toInt()} 万（还需满足声誉/师生等条件）",
                        fontSize = 13.sp,
                        color = Color(0xFF617386)
                    )
                    PanelButton("升级校园") { onUpgradeCampus() }
                } else {
                    Text("已达最高等级", fontSize = 13.sp, color = Color(0xFF2E9B78))
                }
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
                    PanelButton("教学与招生管理") { onOpenTeaching() }
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
                Text("附属医院每 6 月学年评估时提供诊疗收入与声誉加成。", fontSize = 13.sp, color = Color(0xFF182635))
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
                    Text(
                        "月维护 ${facility.type.baseMaintenance}万",
                        fontSize = 13.sp,
                        color = Color(0xFF617386)
                    )
                    if (facility.level < facility.type.maxLevel) {
                        PanelButton("升级") { onUpgradeFacility() }
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
            "当前经费 ${state.cash.toInt()}万 · 校园 Lv.${state.campusLevel}",
            fontSize = 13.sp,
            color = Color(0xFF617386)
        )

        Text("学院", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        BT.COLLEGE_SPECS.forEach { spec ->
            val college = spec.college ?: return@forEach
            val founded = state.foundedColleges.contains(college)
            BuildRow(
                title = spec.displayName,
                subtitle = college.description,
                rightText = "${spec.costWan.toInt()}万",
                locked = !founded && state.cash < spec.costWan,
                done = founded,
                onClick = { if (!founded) onFoundCollege(spec) }
            )
        }

        Text("功能建筑", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        BT.FACILITY_SPECS.forEach { spec ->
            val type = spec.facility ?: return@forEach
            val built = state.facilities.any { it.type == type }
            BuildRow(
                title = spec.displayName,
                subtitle = type.description,
                rightText = "${spec.costWan.toInt()}万",
                locked = !built && state.cash < spec.costWan,
                done = built,
                onClick = { if (!built) onBuyFacility(spec) }
            )
        }

        Text("地面与装扮", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        BT.TileKind.entries.forEach { tile ->
            val locked = state.campusLevel < tile.unlockLevel || state.cash < tile.costWan
            BuildRow(
                title = tile.displayName,
                subtitle = if (state.campusLevel < tile.unlockLevel) "校园 Lv.${tile.unlockLevel} 解锁" else "点击后到地图上点格铺设",
                rightText = "${tile.costWan}万",
                locked = locked,
                done = false,
                onClick = { if (!locked) onPaintTile(tile) }
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF4F7FA))
            .clickable(enabled = !locked && !done, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF617386), maxLines = 2)
        }
        Text(
            text = if (done) "已建成" else rightText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                done -> Color(0xFF2E9B78)
                locked -> Color(0xFF9AA8B5)
                else -> Color(0xFF1E96C8)
            }
        )
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
