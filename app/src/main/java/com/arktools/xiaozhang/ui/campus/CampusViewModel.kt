package com.arktools.xiaozhang.ui.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.engine.SchoolDecision
import com.arktools.xiaozhang.domain.model.Facility
import com.arktools.xiaozhang.domain.model.FacilityBonusCalculator
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.policy.CollegeType
import com.arktools.xiaozhang.domain.policy.SchoolPolicyManager
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.domain.teaching.TeachingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch
import com.arktools.xiaozhang.ui.campus.CampusBuildTypes as BT

/**
 * 瓦片自由建造 ViewModel：
 * - 建筑占格放置/搬移/拆除，地形与装扮铺设
 * - 全部变更原子写回 policyJson（placedBuildings / terrainMap / tutorialDone）
 * - 学院与设施继续复用既有 foundCollege / 设施扣费逻辑，数值零改动
 */
@HiltViewModel
class CampusViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val policyManager: SchoolPolicyManager,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val teachingManager: TeachingManager
) : ViewModel() {

    data class CampusBuilding(
        val id: String,
        val displayName: String,
        val drawableRes: Int,
        val kind: Kind,
        val facility: Facility? = null,
        val college: CollegeType? = null,
        val spec: BT.Spec? = null
    ) {
        enum class Kind { ADMIN, COLLEGE, FACILITY, HOSPITAL }
    }

    data class CampusUiState(
        val cash: Double = 0.0,
        val reputation: Long = 0,
        val campusLevel: Int = 1,
        val foundedColleges: List<CollegeType> = emptyList(),
        val affiliatedHospital: Boolean = false,
        val facilities: List<Facility> = emptyList(),
        val placed: List<BT.PlacedBuilding> = emptyList(),
        val terrain: Map<Long, BT.TileKind> = emptyMap(),
        val tutorialDone: Boolean = false,
        val maxFacilities: Int = 5,
        val selected: CampusBuilding? = null,
        val selectedPlaced: BT.PlacedBuilding? = null,
        val showBuildMenu: Boolean = false,
        val message: String? = null,
        val studentCount: Int = 0,
        val teacherCount: Int = 0,
        val avgDormSatisfaction: Float = 0f,
        val avgMealQuality: Float = 0f,
        val avgSatisfaction: Float = 0f,
        val employmentRate: Float = 0f,
        val clubCount: Int = 0,
        val scholarshipRecipientCount: Int = 0,
        val decorCount: Int = 0,
        val avgIntelligence: Float = 0f,
        val avgPhysical: Float = 0f,
        val avgSocial: Float = 0f,
        val avgCreativity: Float = 0f,
        val teachingQualityBonus: Float = 0f,
        val researchBonus: Float = 0f,
        val enrollmentBonus: Float = 0f,
        val currentMonth: Int = 8,
        val currentDay: Int = 1,
        val dormBeds: Int = 0,
        val canteenSeats: Int = 0,
        val classSlots: Int = 0
    ) {
        val upgradeCampusCost: Double
            get() = GameBalanceConfig.getCampusUpgradeCost(campusLevel)
    }

    private val _state = MutableStateFlow(CampusUiState())
    val state: StateFlow<CampusUiState> = _state.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school == null) return@collect
                val dev = policyManager.policies.value.collegeDevelopment
                migrateIfNeeded(school, dev.placedBuildings, dev.terrainMap)
                val students = runCatching { studentRepository.getActiveStudents() }.getOrDefault(emptyList())
                val teachers = runCatching { teacherRepository.getTeachers() }.getOrDefault(emptyList())
                val terrain = BT.decodeTerrain(dev.terrainMap)
                val decorKinds = setOf("FLOWERBED", "TREE", "BENCH", "STATUE", "LANTERN")
                val bonuses = FacilityBonusCalculator.calculate(school.facilities)
                _state.value = _state.value.copy(
                    cash = school.cash,
                    reputation = school.reputation,
                    campusLevel = school.campusLevel,
                    facilities = school.facilities,
                    maxFacilities = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel),
                    placed = BT.decodeBuildings(dev.placedBuildings),
                    terrain = terrain.associate { (it.y * 1000L + it.x) to tileKindOf(it.kind) },
                    tutorialDone = dev.tutorialDone,
                    studentCount = students.size,
                    teacherCount = teachers.count { it.isWorking },
                    avgDormSatisfaction = if (students.isNotEmpty()) {
                        students.map { it.dormSatisfaction }.average().toFloat()
                    } else 0f,
                    avgMealQuality = if (students.isNotEmpty()) {
                        students.map { it.mealQuality }.average().toFloat()
                    } else 0f,
                    avgSatisfaction = if (students.isNotEmpty()) {
                        students.map { it.satisfaction.toFloat() }.average().toFloat()
                    } else 0f,
                    employmentRate = gameEngine.employmentMarket.state.value.stats.employmentRate,
                    clubCount = gameEngine.clubManager.clubs.value.size,
                    scholarshipRecipientCount = gameEngine.scholarshipManager.state.value.recipients.size,
                    decorCount = terrain.count { it.kind in decorKinds },
                    avgIntelligence = if (students.isNotEmpty()) {
                        students.map { it.attributes.intelligence }.average().toFloat()
                    } else 0f,
                    avgPhysical = if (students.isNotEmpty()) {
                        students.map { it.attributes.physical }.average().toFloat()
                    } else 0f,
                    avgSocial = if (students.isNotEmpty()) {
                        students.map { it.attributes.social }.average().toFloat()
                    } else 0f,
                    avgCreativity = if (students.isNotEmpty()) {
                        students.map { it.attributes.creativity }.average().toFloat()
                    } else 0f,
                    teachingQualityBonus = bonuses.teachingQualityBonus,
                    researchBonus = bonuses.researchBonus,
                    enrollmentBonus = bonuses.enrollmentBonus,
                    currentMonth = school.currentMonth,
                    currentDay = school.currentDay,
                    dormBeds = com.arktools.xiaozhang.domain.model.FacilityCapacity.totalBeds(school.facilities),
                    canteenSeats = com.arktools.xiaozhang.domain.model.FacilityCapacity.totalCanteenSeats(school.facilities),
                    classSlots = com.arktools.xiaozhang.domain.model.FacilityCapacity.totalClassSlots(school.facilities)
                )
            }
        }
        viewModelScope.safeLaunch {
            policyManager.policies.collect { p ->
                val dev = p.collegeDevelopment
                _state.value = _state.value.copy(
                    foundedColleges = dev.founded,
                    affiliatedHospital = dev.affiliatedHospital,
                    placed = BT.decodeBuildings(dev.placedBuildings),
                    terrain = BT.decodeTerrain(dev.terrainMap)
                        .associate { (it.y * 1000L + it.x) to tileKindOf(it.kind) },
                    tutorialDone = dev.tutorialDone
                )
            }
        }
        viewModelScope.safeLaunch {
            kotlinx.coroutines.delay(1500)
            ensureHospitalPlaced()
        }
    }

    private fun tileKindOf(name: String): BT.TileKind =
        BT.TileKind.entries.firstOrNull { it.name == name } ?: BT.TileKind.ROAD

    // ===== 占格与迁移 =====

    private fun occupiedCells(placed: List<BT.PlacedBuilding>): MutableSet<Long> {
        val set = mutableSetOf<Long>()
        placed.forEach { b ->
            val spec = BT.specByKey(b.key) ?: return@forEach
            for (dy in 0 until spec.h) for (dx in 0 until spec.w) {
                set.add((b.y + dy) * 1000L + (b.x + dx))
            }
        }
        return set
    }

    private fun canPlaceAt(
        spec: BT.Spec, x: Int, y: Int,
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>,
        campusLevel: Int, ignoreId: String? = null
    ): String? {
        val others = placed.filter { it.key != "ADMIN" || spec.key != "ADMIN" }
        for (dy in 0 until spec.h) for (dx in 0 until spec.w) {
            val cx = x + dx
            val cy = y + dy
            if (cx < 0 || cy < 0 || cx >= BT.GRID_W || cy >= BT.GRID_H) return "超出校园边界"
            if (!BT.inUnlockedArea(cx, cy, campusLevel)) return "该区域尚未解锁（升级校园扩大范围）"
            val key = cy * 1000L + cx
            val t = terrain[key]
            if (t == BT.TileKind.ROAD || t == BT.TileKind.PLAZA || t == BT.TileKind.WATER) {
                return "不能建在道路/广场/水系上"
            }
            val occupiedBy = others.firstOrNull { b ->
                if (b.facilityId == ignoreId) return@firstOrNull false
                val sp = BT.specByKey(b.key) ?: return@firstOrNull false
                BT.occupies(b, sp, cx, cy)
            }
            if (occupiedBy != null) return "与${BT.specByKey(occupiedBy.key)?.displayName ?: "其他建筑"}位置重叠"
        }
        return null
    }

    private fun firstFree(
        spec: BT.Spec,
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>,
        campusLevel: Int
    ): Pair<Int, Int>? {
        for (y in 0 until BT.GRID_H) for (x in 0 until BT.GRID_W) {
            if (canPlaceAt(spec, x, y, placed, terrain, campusLevel) == null) return x to y
        }
        return null
    }

    private fun migrateIfNeeded(school: com.arktools.xiaozhang.domain.model.School, placedRaw: String, terrainRaw: String) {
        if (placedRaw.isNotBlank()) return
        val founded = policyManager.policies.value.collegeDevelopment.founded
        val placed = mutableListOf(BT.PlacedBuilding("ADMIN", 9, 3, school.campusLevel))
        founded.forEach { college ->
            val spec = BT.collegeSpec(college) ?: return@forEach
            val spot = firstFree(spec, placed, emptyMap(), school.campusLevel)
            if (spot != null) placed.add(BT.PlacedBuilding(spec.key, spot.first, spot.second))
        }
        school.facilities.forEach { f ->
            val spec = BT.facilitySpec(f.type) ?: return@forEach
            val spot = firstFree(spec, placed, emptyMap(), school.campusLevel)
            if (spot != null) placed.add(BT.PlacedBuilding(spec.key, spot.first, spot.second, f.level, f.id))
        }
        if (policyManager.policies.value.collegeDevelopment.affiliatedHospital) {
            val spot = firstFree(BT.HOSPITAL, placed, emptyMap(), school.campusLevel)
            if (spot != null) placed.add(BT.PlacedBuilding("HOSPITAL", spot.first, spot.second))
        }
        // 默认道路网：横 y=9 全宽 + 纵 x=2
        val terrainCells = mutableListOf<BT.TerrainCell>()
        for (x in 0 until BT.GRID_W) terrainCells.add(BT.TerrainCell(x, 9, "ROAD"))
        for (y in 3 until 10) terrainCells.add(BT.TerrainCell(2, y, "ROAD"))
        val terrainMap = terrainCells.associate { (it.y * 1000L + it.x) to BT.TileKind.ROAD }
        persistLayout(placed, terrainMap)
    }

    private fun persistLayout(
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>
    ) {
        viewModelScope.safeLaunch {
            schoolRepository.mutateSchool { school ->
                val dev = policyManager.policies.value.collegeDevelopment
                var target = school
                val cellList = terrain.map { (k, kind) -> BT.TerrainCell((k % 1000L).toInt(), (k / 1000L).toInt(), kind.name) }
                // 写入 CollegeDevelopment 两字段并同步 policyJson
                target.policyJson = run {
                    val updated = dev.copy(
                        placedBuildings = BT.encodeBuildings(placed),
                        terrainMap = BT.encodeTerrain(cellList)
                    )
                    policyManager.replaceCollegeDevelopment(updated)
                    policyManager.toJson()
                }
                true
            }
        }
    }

    private fun updateLayoutSuspend(
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>
    ) {
        viewModelScope.safeLaunch { persistLayout(placed, terrain) }
    }

    private suspend fun ensureHospitalPlaced() {
        val st = _state.value
        if (!st.affiliatedHospital) return
        if (st.placed.any { it.key == "HOSPITAL" }) return
        val spot = firstFree(BT.HOSPITAL, st.placed, st.terrain, st.campusLevel) ?: return
        val newPlaced = st.placed + BT.PlacedBuilding("HOSPITAL", spot.first, spot.second)
        _state.value = _state.value.copy(placed = newPlaced)
        updateLayoutSuspend(newPlaced, st.terrain)
    }

    // ===== 查询 =====

    fun buildingAt(cellX: Int, cellY: Int): Pair<BT.PlacedBuilding, BT.Spec>? {
        val st = _state.value
        st.placed.forEach { b ->
            val spec = BT.specByKey(b.key) ?: return@forEach
            if (BT.occupies(b, spec, cellX, cellY)) return b to spec
        }
        return null
    }

    // ===== 面板选择 =====

    fun selectBuilding(b: CampusBuilding, placed: BT.PlacedBuilding?) {
        _state.value = _state.value.copy(selected = b, selectedPlaced = placed)
    }

    fun selectPlaced(placed: BT.PlacedBuilding, spec: BT.Spec) {
        val st = _state.value
        val facility = st.facilities.firstOrNull { it.id == placed.facilityId }
        val kind = when {
            placed.key == "ADMIN" -> CampusBuilding.Kind.ADMIN
            placed.key == "HOSPITAL" -> CampusBuilding.Kind.HOSPITAL
            spec.college != null -> CampusBuilding.Kind.COLLEGE
            else -> CampusBuilding.Kind.FACILITY
        }
        _state.value = st.copy(
            selected = CampusBuilding(
                id = placed.facilityId.ifBlank { placed.key },
                displayName = spec.displayName,
                drawableRes = spec.drawableRes,
                kind = kind,
                facility = facility,
                college = spec.college,
                spec = spec
            ),
            selectedPlaced = placed
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = null, selectedPlaced = null)
    }

    fun openBuildMenu() {
        _state.value = _state.value.copy(showBuildMenu = true)
    }

    fun closeBuildMenu() {
        _state.value = _state.value.copy(showBuildMenu = false)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun libraryChainSummary(): String =
        policyManager.researchChainManager.progressSummary()

    // ===== 建造：学院 =====

    fun foundCollege(spec: BT.Spec, at: Pair<Int, Int>? = null) {
        val college = spec.college ?: return
        viewModelScope.safeLaunch {
            val result = gameEngine.foundCollege(college)
            if (result.success) {
                audioManager.playCollegeFound()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
                val st = _state.value
                // 优先放在玩家选定的格子；未指定时才自动找空位
                val spot = if (at != null && canPlaceAt(spec, at.first, at.second, st.placed, st.terrain, st.campusLevel) == null) {
                    at
                } else {
                    firstFree(spec, st.placed, st.terrain, st.campusLevel)
                }
                if (spot != null) {
                    val newPlaced = st.placed + BT.PlacedBuilding(spec.key, spot.first, spot.second)
                    _state.value = _state.value.copy(placed = newPlaced, message = "${spec.displayName}已落成")
                    updateLayoutSuspend(newPlaced, st.terrain)
                } else {
                    _state.value = _state.value.copy(message = "${spec.displayName}已成立，但校园没有空位，请清理后再摆放")
                }
            } else {
                audioManager.playEventNegative()
                _state.value = _state.value.copy(message = result.message)
            }
        }
    }

    // ===== 建造：设施 =====

    fun buildFacility(spec: BT.Spec, at: Pair<Int, Int>? = null) {
        val type = spec.facility ?: return
        viewModelScope.safeLaunch {
            var newFacility: Facility? = null
            val result = schoolRepository.mutateSchool { school ->
                val max = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel)
                if (school.facilities.size >= max) {
                    _state.value = _state.value.copy(message = "建筑数量已达当前等级上限（${max}），先升级校园")
                    return@mutateSchool false
                }
                val existingCount = school.facilities.count { it.type == type }
                if (existingCount > 0 && !type.repeatable) {
                    _state.value = _state.value.copy(message = "${type.displayName} 已建成（该类型只需一座）")
                    return@mutateSchool false
                }
                val cost = com.arktools.xiaozhang.domain.model.FacilityCapacity.repeatCost(type, existingCount)
                if (school.cash < cost) {
                    _state.value = _state.value.copy(message = "资金不足！需要 ${cost.toInt()} 万元")
                    return@mutateSchool false
                }
                school.cash -= cost
                val f = Facility(type = type, level = 1, condition = 100f)
                school.facilities.add(f)
                newFacility = f
                true
            }
            if (result != null) {
                audioManager.playBuildFacility()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
                val f = newFacility
                val st = _state.value
                if (f != null) {
                    // 优先放在玩家选定的格子；未指定时才自动找空位
                    val spot = if (at != null && canPlaceAt(spec, at.first, at.second, st.placed, st.terrain, st.campusLevel) == null) {
                        at
                    } else {
                        firstFree(spec, st.placed, st.terrain, st.campusLevel)
                    }
                    if (spot != null) {
                        val newPlaced = st.placed + BT.PlacedBuilding(spec.key, spot.first, spot.second, 1, f.id)
                        _state.value = _state.value.copy(placed = newPlaced, message = "${spec.displayName}已落成")
                        updateLayoutSuspend(newPlaced, st.terrain)
                    }
                }
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    // ===== 摆放模式 =====

    fun startPlace(spec: BT.Spec) {
        _state.value = _state.value.copy(showBuildMenu = false, message = "点击地图上的绿色空地放置${spec.displayName}")
        pendingSpec = spec
        pendingTile = null
        moveId = null
    }

    fun startPaint(tile: BT.TileKind) {
        _state.value = _state.value.copy(showBuildMenu = false, message = "点击地图铺设${tile.displayName}")
        pendingTile = tile
        pendingSpec = null
        moveId = null
    }

    fun startMove(placed: BT.PlacedBuilding) {
        val spec = BT.specByKey(placed.key) ?: return
        _state.value = _state.value.copy(
            selected = null, selectedPlaced = null,
            message = "拖动地图并点击绿色空位，搬移${spec.displayName}"
        )
        moveId = placed.facilityId.ifBlank { placed.key }
        pendingSpec = spec
        pendingTile = null
    }

    fun removePlaced(placed: BT.PlacedBuilding) {
        val spec = BT.specByKey(placed.key) ?: return
        if (!spec.removable) {
            _state.value = _state.value.copy(message = "${spec.displayName}不可拆除")
            return
        }
        viewModelScope.safeLaunch {
            var refund = 0.0
            var facilityRemoved = false
            val result = schoolRepository.mutateSchool { school ->
                refund = spec.costWan * 0.3
                school.cash += refund
                if (spec.facility != null) {
                    facilityRemoved = school.facilities.removeAll { it.id == placed.facilityId }
                }
                true
            }
            if (result != null) {
                audioManager.playCashEarn()
                val st = _state.value
                val newPlaced = st.placed.filter { it.facilityId != placed.facilityId || it.key != placed.key }
                _state.value = _state.value.copy(
                    placed = newPlaced,
                    selected = null, selectedPlaced = null,
                    message = "${spec.displayName}已拆除，返还 ${"%.1f".format(refund)} 万"
                )
                updateLayoutSuspend(newPlaced, st.terrain)
            }
        }
    }

    // ===== 地图点击分发（由 View 调用） =====

    private var pendingSpec: BT.Spec? = null
    private var pendingTile: BT.TileKind? = null
    private var moveId: String? = null

    fun onCellTapped(x: Int, y: Int) {
        val st = _state.value
        // 摆放建筑
        pendingSpec?.let { spec ->
            val moving = moveId
            if (moving != null) {
                val old = st.placed.firstOrNull { it.facilityId == moving || it.key == moving }
                val specOld = old?.let { BT.specByKey(it.key) }
                if (old != null && specOld != null) {
                    val others = st.placed.filter { it != old }
                    val err = canPlaceAt(spec, x, y, others, st.terrain, st.campusLevel, old.facilityId.ifBlank { old.key })
                    if (err != null) {
                        _state.value = _state.value.copy(message = err)
                        audioManager.playEventNegative()
                        return
                    }
                    val moved = old.copy(x = x, y = y)
                    val newPlaced = others + moved
                    _state.value = _state.value.copy(placed = newPlaced, message = "${spec.displayName}已搬移")
                    updateLayoutSuspend(newPlaced, st.terrain)
                }
                moveId = null
                pendingSpec = null
                return
            }
            // 新建：学院/设施走各自扣费流程，放置成功由流程内 firstFree 决定
            val err = canPlaceAt(spec, x, y, st.placed, st.terrain, st.campusLevel)
            if (err != null) {
                _state.value = _state.value.copy(message = err)
                audioManager.playEventNegative()
                return
            }
            if (spec.college != null) {
                foundCollege(spec, x to y)
            } else if (spec.facility != null) {
                buildFacility(spec, x to y)
            }
            pendingSpec = null
            return
        }
        // 铺瓦/装扮
        pendingTile?.let { tile ->
            if (!BT.inUnlockedArea(x, y, st.campusLevel)) {
                _state.value = _state.value.copy(message = "该区域尚未解锁")
                return
            }
            if (st.terrain[x + y * 1000L] == tile) return
            if (st.cash < tile.costWan) {
                _state.value = _state.value.copy(message = "资金不足！需要 ${tile.costWan} 万")
                audioManager.playEventNegative()
                return
            }
            viewModelScope.safeLaunch {
                val result = schoolRepository.mutateSchool { school ->
                    if (school.cash < tile.costWan) return@mutateSchool false
                    school.cash -= tile.costWan
                    school.policyJson = policyManager.toJson()
                    true
                }
                if (result != null) {
                    audioManager.playBuildFacility()
                    val newTerrain = st.terrain + ((y * 1000L + x) to tile)
                    _state.value = _state.value.copy(terrain = newTerrain)
                    updateLayoutSuspend(st.placed, newTerrain)
                }
            }
            return
        }
        // 查看建筑
        val hit = buildingAt(x, y)
        if (hit != null) {
            val (b, spec) = hit
            val facility = st.facilities.firstOrNull { it.id == b.facilityId }
            val displayKind = when {
                b.key == "ADMIN" -> CampusBuilding.Kind.ADMIN
                b.key == "HOSPITAL" -> CampusBuilding.Kind.HOSPITAL
                spec.college != null -> CampusBuilding.Kind.COLLEGE
                else -> CampusBuilding.Kind.FACILITY
            }
            viewModelSelect(b, spec, facility, displayKind)
        }
    }

    private fun viewModelSelect(
        b: BT.PlacedBuilding,
        spec: BT.Spec,
        facility: Facility?,
        kind: CampusBuilding.Kind
    ) {
        _state.value = _state.value.copy(
            selected = CampusBuilding(
                id = b.facilityId.ifBlank { b.key },
                displayName = spec.displayName,
                drawableRes = spec.drawableRes,
                kind = kind,
                facility = facility,
                college = spec.college,
                spec = spec
            ),
            selectedPlaced = b
        )
    }

    fun markTutorialDone() {
        viewModelScope.safeLaunch {
            policyManager.markCampusTutorialDone()
            schoolRepository.mutateSchool { school ->
                school.policyJson = policyManager.toJson()
                true
            }
            _state.value = _state.value.copy(tutorialDone = true)
        }
    }

    fun replayCampusTutorial() {
        viewModelScope.safeLaunch {
            val current = policyManager.policies.value.collegeDevelopment
            policyManager.replaceCollegeDevelopment(current.copy(tutorialDone = false))
            schoolRepository.mutateSchool { school ->
                school.policyJson = policyManager.toJson()
                true
            }
            _state.value = _state.value.copy(tutorialDone = false)
        }
    }

    // ===== 升级（行政楼/设施） =====

    fun upgradeCampus() {
        viewModelScope.safeLaunch {
            val school = schoolRepository.getSchool() ?: return@safeLaunch
            if (school.campusLevel >= GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                _state.value = _state.value.copy(message = "校园已达最高等级！")
                return@safeLaunch
            }
            val req = GameBalanceConfig.getUpgradeRequirements(school.campusLevel + 1)
            val teacherCount = teacherRepository.getTeachers().size
            val classCount = teachingManager.config.totalClasses
            val studentCount = studentRepository.getActiveStudentCount()
            val yearsAtLevel = school.currentYear - school.levelUpYear
            val failures = buildList {
                if (school.cash < req.cashCost) add("资金 ${req.cashCost.toInt()}万")
                if (school.reputation < req.minReputation) add("声誉 ${req.minReputation}")
                if (teacherCount < req.minTeachers) add("教师 ${req.minTeachers}人")
                if (classCount < req.minClasses) add("班级 ${req.minClasses}个")
                if (studentCount < req.minStudents) add("学生 ${req.minStudents}人")
                if (yearsAtLevel < req.minYearsAtCurrentLevel) add("运营满 ${req.minYearsAtCurrentLevel}年")
            }
            if (failures.isNotEmpty()) {
                _state.value = _state.value.copy(message = "升级条件不足：${failures.joinToString("、")}")
                audioManager.playEventNegative()
                return@safeLaunch
            }
            schoolRepository.upgradeCampus()
            gameEngine.notifyFactionDecision(SchoolDecision.EXPAND_CAMPUS)
            audioManager.playLevelUp()
            val newLevel = schoolRepository.getSchool()?.campusLevel ?: school.campusLevel
            val unlocked = GameBalanceConfig.getNewlyUnlockedModules(school.campusLevel, newLevel)
            val unlockText = if (unlocked.isNotEmpty()) {
                " 新开放：${unlocked.joinToString("、") { it.displayName }}"
            } else ""
            _state.value = _state.value.copy(
                message = "校园升级成功！当前 Lv.$newLevel（赠送1间教室）$unlockText"
            )
        }
    }

    fun upgradeFacility(facilityId: String) {
        viewModelScope.safeLaunch {
            var name = ""
            var lv = 0
            val result = schoolRepository.mutateSchool { school ->
                val idx = school.facilities.indexOfFirst { it.id == facilityId }
                if (idx == -1) return@mutateSchool false
                val facility = school.facilities[idx]
                val type = facility.type
                if (facility.level >= type.maxLevel) {
                    _state.value = _state.value.copy(message = "${type.displayName} 已达最大等级")
                    return@mutateSchool false
                }
                val cost = FacilityBonusCalculator.getUpgradeCost(facility)
                if (school.cash < cost) {
                    _state.value = _state.value.copy(
                        message = "资金不足！升级需要 ${String.format("%.1f", cost)} 万元"
                    )
                    return@mutateSchool false
                }
                school.cash -= cost
                val upgraded = facility.copy(level = facility.level + 1)
                school.facilities[idx] = upgraded
                name = type.displayName
                lv = upgraded.level
                true
            }
            if (result != null) {
                audioManager.playLevelUp()
                _state.value = _state.value.copy(message = "$name 升级到 Lv.$lv！")
            } else {
                audioManager.playEventNegative()
            }
        }
    }
}
