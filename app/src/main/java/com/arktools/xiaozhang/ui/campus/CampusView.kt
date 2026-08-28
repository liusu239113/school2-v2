package com.arktools.xiaozhang.ui.campus

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

/**
 * M1 建筑式校园主视图：
 * - 草地/道路瓦片平铺（FilterQuality.None 保持像素）
 * - 已建建筑摆放，点击弹出实底建筑面板
 * - 右下角建造菜单（学院楼 / 功能建筑）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusView(
    onNavigateTo: (Int) -> Unit = {},
    viewModel: CampusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val grassTile = remember(R.drawable.tile_grass) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_grass).asImageBitmap()
    }
    val pathTile = remember(R.drawable.tile_path) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_path).asImageBitmap()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val w = maxWidth
        val h = maxHeight

        // 瓦片平铺：草地打底 + 中央纵向大道 + 横向环路
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ts = 64
            var x = 0
            while (x < wPx) {
                var y = 0
                while (y < hPx) {
                    drawImage(
                        image = grassTile,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(grassTile.width, grassTile.height),
                        dstOffset = IntOffset(x, y),
                        dstSize = IntSize(ts, ts),
                        filterQuality = FilterQuality.None
                    )
                    y += ts
                }
                x += ts
            }
            val roadW = (wPx * 0.10f).toInt().coerceAtLeast(40)
            val roadX = (wPx * 0.45f).toInt()
            var ry = 0
            while (ry < hPx) {
                drawImage(
                    image = pathTile,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(pathTile.width, pathTile.height),
                    dstOffset = IntOffset(roadX, ry),
                    dstSize = IntSize(roadW, roadW),
                    filterQuality = FilterQuality.None
                )
                ry += roadW
            }
            val hRoadY = (hPx * 0.55f).toInt()
            var rx = 0
            while (rx < wPx) {
                drawImage(
                    image = pathTile,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(pathTile.width, pathTile.height),
                    dstOffset = IntOffset(rx, hRoadY),
                    dstSize = IntSize(roadW, roadW),
                    filterQuality = FilterQuality.None
                )
                rx += roadW
            }
        }

        // 顶部资源条：深底白字
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryDark)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("经费 ${state.cash.toInt()}万", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("声誉 ${state.reputation}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("校园 Lv.${state.campusLevel}", color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        // 行政楼：大道尽头居中（常驻）
        BuildingSprite(
            resId = R.drawable.bld_admin,
            label = "行政楼",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * 0.05f)
                .width(w * 0.34f),
            onClick = {
                viewModel.selectBuilding(
                    CampusViewModel.CampusBuilding(
                        id = "admin",
                        displayName = "行政楼",
                        drawableRes = R.drawable.bld_admin,
                        kind = CampusViewModel.CampusBuilding.Kind.ADMIN
                    )
                )
            }
        )

        // 学院楼：横路上方一排（已成立才显示）
        val collegeRow = listOf(
            CollegeType.LIBERAL_ARTS,
            CollegeType.SCIENCE,
            CollegeType.ENGINEERING,
            CollegeType.BUSINESS
        )
        val foundedRow = collegeRow.filter { state.foundedColleges.contains(it) }
        foundedRow.forEachIndexed { index, college ->
            val slotX = when (foundedRow.size) {
                1 -> 0.5f
                2 -> 0.30f + index * 0.40f
                3 -> 0.22f + index * 0.28f
                else -> 0.13f + index * 0.245f
            }
            val spriteW = w * 0.19f
            BuildingSprite(
                resId = CampusViewModel.collegeDrawable(college),
                label = college.displayName,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = w * slotX - spriteW / 2,
                        y = h * 0.28f
                    )
                    .width(spriteW),
                onClick = {
                    viewModel.selectBuilding(
                        CampusViewModel.CampusBuilding(
                            id = "college_${college.name}",
                            displayName = college.displayName,
                            drawableRes = CampusViewModel.collegeDrawable(college),
                            kind = CampusViewModel.CampusBuilding.Kind.COLLEGE,
                            college = college
                        )
                    )
                }
            )
        }

        // 图书馆 / 宿舍：横路下方（已建才显示）
        val libraryFac = state.facilities.firstOrNull { it.type == FacilityType.LIBRARY }
        if (libraryFac != null) {
            val spriteW = w * 0.20f
            BuildingSprite(
                resId = R.drawable.bld_library,
                label = "图书馆 Lv.${libraryFac.level}",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = w * 0.18f - spriteW / 2, y = h * 0.64f)
                    .width(spriteW),
                onClick = {
                    viewModel.selectBuilding(
                        CampusViewModel.CampusBuilding(
                            id = libraryFac.id,
                            displayName = libraryFac.type.displayName,
                            drawableRes = R.drawable.bld_library,
                            kind = CampusViewModel.CampusBuilding.Kind.FACILITY,
                            facility = libraryFac
                        )
                    )
                }
            )
        }
        val dormFac = state.facilities.firstOrNull { it.type == FacilityType.DORMITORY }
        if (dormFac != null) {
            val spriteW = w * 0.20f
            BuildingSprite(
                resId = R.drawable.bld_dorm,
                label = "宿舍楼 Lv.${dormFac.level}",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = w * 0.62f - spriteW / 2, y = h * 0.64f)
                    .width(spriteW),
                onClick = {
                    viewModel.selectBuilding(
                        CampusViewModel.CampusBuilding(
                            id = dormFac.id,
                            displayName = dormFac.type.displayName,
                            drawableRes = R.drawable.bld_dorm,
                            kind = CampusViewModel.CampusBuilding.Kind.FACILITY,
                            facility = dormFac
                        )
                    )
                }
            )
        }

        // 建造 FAB（右下）
        FloatingActionButton(
            onClick = { viewModel.openBuildMenu() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text("建造", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // 建筑面板
        state.selected?.let { building ->
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelection() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                BuildingPanelContent(
                    building = building,
                    state = state,
                    onUpgradeFacility = { viewModel.upgradeFacility(building.facility?.id ?: "") },
                    onUpgradeCampus = { viewModel.upgradeCampus() },
                    onOpenTeaching = { onNavigateTo(40) }
                )
            }
        }

        // 建造菜单
        if (state.showBuildMenu) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeBuildMenu() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                BuildMenuContent(
                    state = state,
                    onFoundCollege = { viewModel.foundCollege(it) },
                    onBuyFacility = { viewModel.buyFacility(it) }
                )
            }
        }

        // 全局消息条（深底白字，点击消失）
        state.message?.let { msg ->
            Text(
                text = msg,
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xCC0B2038), RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable { viewModel.consumeMessage() }
            )
        }
    }
}

/** 单栋建筑精灵 + 底部名字胶囊（深底白字） */
@Composable
private fun BuildingSprite(
    resId: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = label,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillBounds
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color(0xCC0B2038), RoundedCornerShape(0.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** 建筑面板内容（实底白卡） */
@Composable
private fun BuildingPanelContent(
    building: CampusViewModel.CampusBuilding,
    state: CampusViewModel.CampusUiState,
    onUpgradeFacility: () -> Unit,
    onUpgradeCampus: () -> Unit,
    onOpenTeaching: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            building.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF182635)
        )

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
                    Text(
                        "本学院学生的专业与师资覆盖情况，可在「人事」页查看",
                        fontSize = 12.sp,
                        color = Color(0xFF617386)
                    )
                    PanelButton("教学与招生管理") { onOpenTeaching() }
                }
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
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 建造菜单内容 */
@Composable
private fun BuildMenuContent(
    state: CampusViewModel.CampusUiState,
    onFoundCollege: (CollegeType) -> Unit,
    onBuyFacility: (FacilityType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("建造", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF182635))
        Text(
            "当前经费 ${state.cash.toInt()}万 · 建筑 ${state.facilities.size}/${state.maxFacilities}",
            fontSize = 13.sp,
            color = Color(0xFF617386)
        )

        Text("学院", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        CollegeType.entries.forEach { college ->
            val founded = state.foundedColleges.contains(college)
            BuildRow(
                title = college.displayName,
                subtitle = college.description,
                rightText = "${college.foundingCostWan.toInt()}万",
                locked = !founded && state.cash < college.foundingCostWan,
                done = founded,
                onClick = { if (!founded) onFoundCollege(college) }
            )
        }

        Text("功能建筑", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E96C8))
        listOf(
            FacilityType.LIBRARY,
            FacilityType.DORMITORY,
            FacilityType.CANTEEN,
            FacilityType.SPORTS_FIELD
        ).forEach { type ->
            val built = state.facilities.any { it.type == type }
            BuildRow(
                title = type.displayName,
                subtitle = type.description,
                rightText = "${type.baseCost.toInt()}万",
                locked = !built && state.cash < type.baseCost,
                done = built,
                onClick = { if (!built) onBuyFacility(type) }
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
