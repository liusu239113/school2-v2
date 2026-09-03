package com.arktools.xiao.ui.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.audio.AudioManager
import com.arktools.xiao.domain.engine.GameBalanceConfig
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.engine.SchoolDecision
import com.arktools.xiao.domain.model.ClassFacilityAssignments
import com.arktools.xiao.domain.model.ClassOfficer
import com.arktools.xiao.domain.model.ClassOfficerRole
import com.arktools.xiao.domain.model.ClassOfficers
import com.arktools.xiao.domain.model.Facility
import com.arktools.xiao.domain.model.FacilityBonusCalculator
import com.arktools.xiao.domain.model.FacilityType
import com.arktools.xiao.domain.policy.CollegeType
import com.arktools.xiao.domain.policy.SchoolPolicyManager
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.repository.StudentRepository
import com.arktools.xiao.domain.repository.TeacherRepository
import com.arktools.xiao.domain.teaching.TeachingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch
import com.arktools.xiao.ui.campus.CampusBuildTypes as BT

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
        val constructingColleges: Map<CollegeType, Int> = emptyMap(),
        val affiliatedHospital: Boolean = false,
        val facilities: List<Facility> = emptyList(),
        val placed: List<BT.PlacedBuilding> = emptyList(),
        val terrain: Map<Long, BT.TileKind> = emptyMap(),
        val tutorialDone: Boolean = false,
        val maxFacilities: Int = 5,
        val selected: CampusBuilding? = null,
        val selectedPlaced: BT.PlacedBuilding? = null,
        val selectedTile: BT.TileKind? = null,
        val selectedTilePos: Pair<Int, Int>? = null,
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
        val classSlots: Int = 0,
        val classSlotRule: Int = 3,
        val librarySeats: Int = 0,
        val labBenches: Int = 0,
        val computerSeats: Int = 0,
        val sportsCapacity: Int = 0,
        val studioCapacity: Int = 0,
        val unlockedCells: Int = 0,
        val totalCells: Int = 0,
        val currentYear: Int = 2026
    ) {
        val upgradeCampusCost: Double
            get() = GameBalanceConfig.getCampusUpgradeCost(campusLevel)
    }

    data class DormResident(
        val name: String,
        val grade: String,
        val className: String
    )

    data class DormRoster(
        val facilityId: String,
        val beds: Int,
        val occupied: Int,
        val floors: List<DormFloor>
    )

    data class DormFloor(
        val floor: Int,
        val residents: List<DormResident>
    )

    data class ClassRow(
        val classId: String,
        val name: String,
        val studentCount: Int,
        val advisorName: String?,
        val officers: Map<ClassOfficerRole, String>,
        val facilityId: String
    )

    data class StudentOption(
        val id: String,
        val name: String,
        val qualificationScore: Int,
        val eligible: Boolean
    )

    data class OfficerPickerTarget(val classId: String, val role: ClassOfficerRole)

    private val _classRows = MutableStateFlow<List<ClassRow>>(emptyList())
    val classRows: StateFlow<List<ClassRow>> = _classRows.asStateFlow()

    private val _advisorOptions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val advisorOptions: StateFlow<List<Pair<String, String>>> = _advisorOptions.asStateFlow()

    private val _studentOptions = MutableStateFlow<List<StudentOption>>(emptyList())
    val studentOptions: StateFlow<List<StudentOption>> = _studentOptions.asStateFlow()

    private val _officerMessage = MutableStateFlow<String?>(null)
    val officerMessage = _officerMessage.asStateFlow()

    private val _pickingAdvisorClass = MutableStateFlow<String?>(null)
    val pickingAdvisorClass = _pickingAdvisorClass.asStateFlow()

    private val _pickingOfficer = MutableStateFlow<OfficerPickerTarget?>(null)
    val pickingOfficer = _pickingOfficer.asStateFlow()

    private val _state = MutableStateFlow(CampusUiState())
    val state: StateFlow<CampusUiState> = _state.asStateFlow()

    private var cachedActiveStudents: List<com.arktools.xiao.domain.model.Student> = emptyList()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school == null) return@collect
                val dev = policyManager.policies.value.collegeDevelopment
                migrateIfNeeded(school, dev.placedBuildings, dev.terrainMap)
                val students = runCatching { studentRepository.getActiveStudents() }.getOrDefault(emptyList())
                cachedActiveStudents = students
                val teachers = runCatching { teacherRepository.getTeachers() }.getOrDefault(emptyList())
                val terrain = BT.decodeTerrain(dev.terrainMap)
                val decorKinds = setOf(
                    "FLOWERBED", "TREE", "BENCH", "STATUE", "LANTERN",
                    "CHERRY_TREE", "MEMORIAL", "SCHOOL_SIGN", "FOUNTAIN",
                    "GINKGO", "BAMBOO", "LAMP", "PAVILION", "PARCEL", "FITNESS"
                )
                val bonuses = FacilityBonusCalculator.calculate(school.facilities)
                _state.value = _state.value.copy(
                    cash = school.cash,
                    reputation = school.reputation,
                    campusLevel = school.campusLevel,
                    facilities = school.facilities,
                    maxFacilities = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel),
                    placed = BT.decodeBuildings(dev.placedBuildings).map { b ->
                        val f = school.facilities.firstOrNull { it.id == b.facilityId }
                        val collegeDays = collegeTypeForBuildingKey(b.key)
                            ?.let { dev.constructingColleges[it.name] }
                        when {
                            f != null -> b.copy(
                                level = f.level,
                                constructionDaysLeft = f.constructionDaysLeft
                            )
                            collegeTypeForBuildingKey(b.key) != null ->
                                b.copy(constructionDaysLeft = collegeDays ?: 0)
                            else -> b
                        }
                    },
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
                    currentYear = school.currentYear,
                    dormBeds = com.arktools.xiao.domain.model.FacilityCapacity.totalBeds(school.facilities),
                    canteenSeats = com.arktools.xiao.domain.model.FacilityCapacity.totalCanteenSeats(school.facilities),
                    classSlots = com.arktools.xiao.domain.model.FacilityCapacity.totalClassSlots(school.facilities),
                    librarySeats = com.arktools.xiao.domain.model.FacilityCapacity.totalLibrarySeats(school.facilities),
                    labBenches = com.arktools.xiao.domain.model.FacilityCapacity.totalLabBenches(school.facilities),
                    computerSeats = com.arktools.xiao.domain.model.FacilityCapacity.totalComputerSeats(school.facilities),
                    sportsCapacity = com.arktools.xiao.domain.model.FacilityCapacity.totalSportsCapacity(school.facilities),
                    studioCapacity = com.arktools.xiao.domain.model.FacilityCapacity.totalStudioCapacity(school.facilities),
                    unlockedCells = BT.unlockedRect(school.campusLevel).cells,
                    totalCells = BT.GRID_W * BT.GRID_H
                )
            }
        }
        viewModelScope.safeLaunch {
            policyManager.policies.collect { p ->
                val dev = p.collegeDevelopment
                _state.value = _state.value.copy(
                    foundedColleges = dev.founded,
                    constructingColleges = dev.constructingColleges.mapNotNull { (name, days) ->
                        runCatching { CollegeType.valueOf(name) }.getOrNull()?.let { it to days }
                    }.toMap(),
                    affiliatedHospital = dev.affiliatedHospital,
                    placed = BT.decodeBuildings(dev.placedBuildings).map { b ->
                        val f = _state.value.facilities.firstOrNull { it.id == b.facilityId }
                        val collegeType = collegeTypeForBuildingKey(b.key)
                        val collegeDays = collegeType?.let { dev.constructingColleges[it.name] }
                        when {
                            f != null -> b.copy(
                                level = f.level,
                                constructionDaysLeft = f.constructionDaysLeft
                            )
                            collegeType != null -> b.copy(constructionDaysLeft = collegeDays ?: 0)
                            else -> b
                        }
                    },
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
        viewModelScope.safeLaunch {
            gameEngine.gameDaySignal.collect { tickConstruction() }
        }
        viewModelScope.safeLaunch {
            gameEngine.classesFlow.collect { rebuildClassRows() }
        }
    }

    /** 把全校班级轮流挂到已竣工的标准教室上（展示用），并带上导师/班长信息 */
    private suspend fun rebuildClassRows() {
        val st = _state.value
        val rooms = st.placed
            .filter { it.key == "F_CLASSROOM" && !it.isConstructing }
            .sortedBy { it.facilityId }
        val officers = ClassOfficers.decode(
            policyManager.policies.value.collegeDevelopment.classOfficersJson
        )
        val dev = policyManager.policies.value.collegeDevelopment
        val existingAssignments = ClassFacilityAssignments.decode(dev.classFacilityMapJson)
        val roomCapacities = rooms.mapNotNull { room ->
            val facility = st.facilities.firstOrNull { it.id == room.facilityId } ?: return@mapNotNull null
            room.facilityId to com.arktools.xiao.domain.model.FacilityCapacity.classSlots(facility.level)
        }
        val assignments = ClassFacilityAssignments.reconcile(
            gameEngine.classes.map { it.id },
            roomCapacities,
            existingAssignments
        )
        if (assignments != existingAssignments) {
            policyManager.replaceCollegeDevelopment(
                dev.copy(classFacilityMapJson = ClassFacilityAssignments.encode(assignments))
            )
            schoolRepository.mutateSchool { school -> school.policyJson = policyManager.toJson(); true }
        }
        val teachers = runCatching { teacherRepository.getTeachers() }.getOrDefault(emptyList())
        val rows = gameEngine.classes.map { cls ->
            val roomId = assignments[cls.id].orEmpty()
            val advisor = cls.headTeacherId?.let { id -> teachers.firstOrNull { it.id == id } }
            ClassRow(
                classId = cls.id,
                name = cls.displayName,
                studentCount = cls.studentCount,
                advisorName = advisor?.name,
                officers = officers[cls.id].orEmpty().mapValues { it.value.name },
                facilityId = roomId
            )
        }
        _classRows.value = rows
        _state.value = _state.value.copy(classSlotRule =
            com.arktools.xiao.domain.model.FacilityCapacity.classSlots(st.campusLevel))
    }

    fun classesInBuilding(facilityId: String): List<ClassRow> =
        _classRows.value.filter { it.facilityId == facilityId }

    /**
     * 把在校生按宿舍楼稳定分配：同一栋楼按楼层均摊，名单可点开查看。
     * 学生实体没有 dormId，用 id 哈希保证同一存档分配稳定。
     */
    fun dormRoster(facilityId: String): DormRoster {
        val st = _state.value
        val dorms = st.placed
            .filter { it.key == "F_DORMITORY" && !it.isConstructing && it.facilityId.isNotBlank() }
            .sortedBy { it.facilityId }
        val thisDorm = dorms.firstOrNull { it.facilityId == facilityId }
        val facility = st.facilities.firstOrNull { it.id == facilityId }
        val beds = if (facility != null) {
            com.arktools.xiao.domain.model.FacilityCapacity.bedsPerDorm(facility.level)
        } else 0
        if (thisDorm == null || beds <= 0) {
            return DormRoster(facilityId, beds, 0, emptyList())
        }
        val students = cachedActiveStudents
        val assigned = students.filter { student ->
            if (dorms.isEmpty()) return@filter false
            val idx = kotlin.math.abs(student.id.hashCode()) % dorms.size
            dorms[idx].facilityId == facilityId
        }
        val floors = 4
        val perFloor = (beds / floors).coerceAtLeast(1)
        val grouped = assigned.sortedBy { it.name }.mapIndexed { index, student ->
            val floor = (index / perFloor).coerceAtMost(floors - 1) + 1
            val className = _classRows.value.firstOrNull { it.classId == student.classId }?.name ?: "未分班"
            floor to DormResident(student.name, student.gradeLevel.displayName, className)
        }.groupBy({ it.first }, { it.second })
        val floorList = (1..floors).map { n ->
            DormFloor(n, grouped[n].orEmpty())
        }
        return DormRoster(facilityId, beds, assigned.size.coerceAtMost(beds), floorList)
    }

    fun campusUpgradeHint(): String {
        val st = _state.value
        if (st.campusLevel >= GameBalanceConfig.MAX_SCHOOL_LEVEL) return "校园已满级"
        val req = GameBalanceConfig.getUpgradeRequirements(st.campusLevel + 1)
        return "升到 Lv.${st.campusLevel + 1}：经费≥${req.cashCost.toInt()}万 · 声誉≥${req.minReputation} · 教师≥${req.minTeachers} · 在校生≥${req.minStudents}。点行政楼「升级校园」。"
    }

    fun openAdvisorPicker(classId: String) {
        audioManager.playButtonClick()
        viewModelScope.safeLaunch {
            val teachers = runCatching { teacherRepository.getTeachers() }
                .getOrDefault(emptyList()).filter { it.isWorking }
            _advisorOptions.value = teachers.map { it.id to (it.name + " · " + it.level.name + "级") }
            _pickingAdvisorClass.value = classId
        }
    }

    fun assignAdvisor(classId: String, teacherId: String) {
        audioManager.playButtonClick()
        viewModelScope.safeLaunch {
            val cls = gameEngine.classes.firstOrNull { it.id == classId } ?: return@safeLaunch
            val teacher = runCatching { teacherRepository.getTeachers() }
                .getOrDefault(emptyList()).firstOrNull { it.id == teacherId } ?: return@safeLaunch
            gameEngine.classManager.assignHeadTeacher(cls, teacher, gameEngine.classes)
            gameEngine.saveHeadTeacherMap()
            gameEngine.notifyClassesChanged()
            _pickingAdvisorClass.value = null
            _officerMessage.value = "已任命 " + teacher.name + " 为 " + cls.displayName + " 学业导师"
        }
    }

    fun openOfficerPicker(classId: String, role: ClassOfficerRole) {
        audioManager.playButtonClick()
        viewModelScope.safeLaunch {
            val students = runCatching { studentRepository.getStudentsByClass(classId) }
                .getOrDefault(emptyList())
            _studentOptions.value = students.map { student ->
                val q = role.qualification(student)
                StudentOption(student.id, student.name, q.score.toInt(), q.eligible)
            }.sortedWith(compareByDescending<StudentOption> { it.eligible }.thenByDescending { it.qualificationScore })
            _pickingOfficer.value = OfficerPickerTarget(classId, role)
        }
    }

    fun appointOfficer(classId: String, role: ClassOfficerRole, studentId: String) {
        audioManager.playButtonClick()
        viewModelScope.safeLaunch {
            if (gameEngine.classes.none { it.id == classId }) return@safeLaunch
            val students = studentRepository.getStudentsByClass(classId)
            val student = students.firstOrNull { it.id == studentId } ?: return@safeLaunch
            val qualification = role.qualification(student)
            if (!qualification.eligible) {
                _officerMessage.value = "${student.name}未达到${role.displayName}任职要求"
                audioManager.playEventNegative()
                return@safeLaunch
            }
            val dev = policyManager.policies.value.collegeDevelopment
            val all = ClassOfficers.decode(dev.classOfficersJson).toMutableMap()
            val roles = all[classId].orEmpty().toMutableMap()
            roles.entries.removeAll { (_, officer) -> officer.studentId == student.id }
            roles[role] = ClassOfficer(student.id, student.name)
            all[classId] = roles
            policyManager.replaceCollegeDevelopment(
                dev.copy(classOfficersJson = ClassOfficers.encode(all))
            )
            schoolRepository.mutateSchool { school ->
                school.policyJson = policyManager.toJson()
                true
            }
            _pickingOfficer.value = null
            _officerMessage.value = "${student.name} 已任命为${role.displayName}"
            gameEngine.notifyClassesChanged()
            rebuildClassRows()
        }
    }

    fun removeOfficer(classId: String, role: ClassOfficerRole) {
        viewModelScope.safeLaunch {
            val dev = policyManager.policies.value.collegeDevelopment
            val all = ClassOfficers.decode(dev.classOfficersJson).toMutableMap()
            val roles = all[classId].orEmpty().toMutableMap()
            roles.remove(role)
            if (roles.isEmpty()) all.remove(classId) else all[classId] = roles
            policyManager.replaceCollegeDevelopment(dev.copy(classOfficersJson = ClassOfficers.encode(all)))
            schoolRepository.mutateSchool { school -> school.policyJson = policyManager.toJson(); true }
            _officerMessage.value = "已撤销${role.displayName}"
            rebuildClassRows()
        }
    }

    fun closePickers() {
        _pickingAdvisorClass.value = null
        _pickingOfficer.value = null
    }

    fun consumeOfficerMessage() {
        _officerMessage.value = null
    }

    private suspend fun tickConstruction() {
        val st = _state.value
        val constructing = st.facilities.any { it.isConstructing }
        if (!constructing) return
        var finishedName: String? = null
        var updatedFacilities: List<Facility> = st.facilities
        schoolRepository.mutateSchool { school ->
            school.facilities.forEach { f ->
                if (f.constructionDaysLeft > 0) {
                    f.constructionDaysLeft = (f.constructionDaysLeft - 1).coerceAtLeast(0)
                    if (f.constructionDaysLeft == 0) {
                        finishedName = f.type.displayName
                    }
                }
            }
            updatedFacilities = school.facilities.toList()
            true
        }
        val nextPlaced = st.placed.map { b ->
            val facility = updatedFacilities.firstOrNull { it.id == b.facilityId }
            if (facility != null) {
                b.copy(level = facility.level, constructionDaysLeft = facility.constructionDaysLeft)
            } else b
        }
        val msg = finishedName?.let { "${it}竣工，开始投入使用" }
        _state.value = st.copy(
            facilities = updatedFacilities,
            placed = nextPlaced,
            message = msg ?: st.message
        )
        if (finishedName != null) audioManager.playConstructionDone()
        updateLayoutSuspend(nextPlaced, st.terrain)
    }

    private fun collegeTypeForBuildingKey(key: String): CollegeType? = when (key) {
        "C_LIBERAL" -> CollegeType.LIBERAL_ARTS
        "C_SCIENCE" -> CollegeType.SCIENCE
        "C_ENGINEERING" -> CollegeType.ENGINEERING
        "C_BUSINESS" -> CollegeType.BUSINESS
        "C_ART" -> CollegeType.ARTS
        "C_MEDICINE" -> CollegeType.MEDICINE
        else -> null
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
            if (t != null) {
                return "格子上已有道路或装扮，请先点击同格拆除"
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

    private fun defaultRoads(campusLevel: Int): Map<Long, BT.TileKind> {
        val rect = BT.unlockedRect(campusLevel)
        val cells = mutableMapOf<Long, BT.TileKind>()
        // 只铺一条横向主干道，纵向不切地，给宿舍 3×3 留整块空地
        val roadY = (rect.y0 + 6).coerceIn(rect.y0 + 1, rect.y1 - 4)
        for (x in rect.x0 until rect.x1) cells[roadY * 1000L + x] = BT.TileKind.ROAD
        return cells
    }

    private fun relayoutOverlaps(
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>,
        campusLevel: Int
    ): List<BT.PlacedBuilding> {
        val result = mutableListOf<BT.PlacedBuilding>()
        var changed = false
        placed.forEach { b ->
            val spec = BT.specByKey(b.key) ?: return@forEach
            val err = canPlaceAt(spec, b.x, b.y, result, terrain, campusLevel, b.facilityId.ifBlank { b.key })
            if (err == null) {
                result.add(b)
            } else {
                val spot = firstFree(spec, result, terrain, campusLevel)
                if (spot != null) {
                    result.add(b.copy(x = spot.first, y = spot.second))
                    changed = true
                } else {
                    result.add(b)
                }
            }
        }
        return if (changed) result else placed
    }

    private suspend fun migrateIfNeeded(school: com.arktools.xiao.domain.model.School, placedRaw: String, terrainRaw: String) {
        if (placedRaw.isNotBlank()) {
            ensureLegacyDorm(school, placedRaw, terrainRaw)
            val existing = BT.decodeBuildings(placedRaw)
            val terrain = BT.decodeTerrain(terrainRaw)
                .associate { (it.y * 1000L + it.x) to tileKindOf(it.kind) }
            val fixed = relayoutOverlaps(existing, terrain, school.campusLevel)
            if (fixed !== existing) persistLayout(fixed, terrain)
            return
        }
        val terrainMap = defaultRoads(school.campusLevel)
        val placed = mutableListOf<BT.PlacedBuilding>()
        val adminSpot = firstFree(BT.ADMIN, placed, terrainMap, school.campusLevel) ?: (5 to 3)
        placed.add(BT.PlacedBuilding("ADMIN", adminSpot.first, adminSpot.second, school.campusLevel))
        val founded = policyManager.policies.value.collegeDevelopment.founded
        founded.forEach { college ->
            val spec = BT.collegeSpec(college) ?: return@forEach
            val spot = firstFree(spec, placed, terrainMap, school.campusLevel)
            if (spot != null) placed.add(BT.PlacedBuilding(spec.key, spot.first, spot.second))
        }
        school.facilities.forEach { f ->
            val spec = BT.facilitySpec(f.type) ?: return@forEach
            val spot = firstFree(spec, placed, terrainMap, school.campusLevel)
            if (spot != null) placed.add(BT.PlacedBuilding(spec.key, spot.first, spot.second, f.level, f.id))
        }
        if (policyManager.policies.value.collegeDevelopment.affiliatedHospital) {
            val spot = firstFree(BT.HOSPITAL, placed, terrainMap, school.campusLevel)
            if (spot != null) placed.add(BT.PlacedBuilding("HOSPITAL", spot.first, spot.second))
        }
        persistLayout(placed, terrainMap)
    }

    /**
     * 旧档自愈：早期版本新档不带宿舍，玩家可能卡在迎新门控（时间被教程暂停）。
     * 若全校没有任何宿舍（facilities 与 placed 都没有），补一栋已竣工宿舍并落位，
     * 与新档"自带宿舍"规则对齐。守卫保证只在缺宿舍时执行一次。
     */
    private suspend fun ensureLegacyDorm(school: com.arktools.xiao.domain.model.School, placedRaw: String, terrainRaw: String) {
        if (school.facilities.any { it.type == FacilityType.DORMITORY }) return
        if (BT.decodeBuildings(placedRaw).any { it.key == "F_DORMITORY" }) return
        val terrain = BT.decodeTerrain(terrainRaw)
            .associate { (it.y * 1000L + it.x) to tileKindOf(it.kind) }
        val placed = BT.decodeBuildings(placedRaw)
        val spec = BT.facilitySpec(FacilityType.DORMITORY) ?: return
        val spot = firstFree(spec, placed, terrain, school.campusLevel) ?: return
        var dormId = ""
        val added = schoolRepository.mutateSchool { s ->
            if (s.facilities.none { it.type == FacilityType.DORMITORY }) {
                val dorm = Facility(type = FacilityType.DORMITORY, level = 1, condition = 100f)
                s.facilities.add(dorm)
                dormId = dorm.id
                true
            } else {
                false
            }
        } != null
        if (!added || dormId.isBlank()) return
        val newPlaced = placed + BT.PlacedBuilding(spec.key, spot.first, spot.second, 1, dormId)
        persistLayout(newPlaced, terrain)
        _state.value = _state.value.copy(message = "学校补建了一栋宿舍楼，迎新可以继续了")
    }

    private suspend fun persistLayoutResult(
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>
    ): Boolean {
        return schoolRepository.mutateSchool { school ->
            val dev = policyManager.policies.value.collegeDevelopment
            val cellList = terrain.map { (k, kind) -> BT.TerrainCell((k % 1000L).toInt(), (k / 1000L).toInt(), kind.name) }
            val updated = dev.copy(
                placedBuildings = BT.encodeBuildings(placed),
                terrainMap = BT.encodeTerrain(cellList)
            )
            policyManager.replaceCollegeDevelopment(updated)
            school.policyJson = policyManager.toJson()
            true
        } != null
    }

    private suspend fun persistLayout(
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>
    ) {
        persistLayoutResult(placed, terrain)
    }

    private fun updateLayoutSuspend(
        placed: List<BT.PlacedBuilding>,
        terrain: Map<Long, BT.TileKind>
    ) {
        viewModelScope.safeLaunch { persistLayout(placed, terrain) }
    }

    /**
     * 广告激励：施工中的建筑（设施或学院）立即竣工。
     * 设施走 facilities 事务；学院走 constructingColleges+placed 同步。
     */
    fun finishConstructionByAd(placed: BT.PlacedBuilding) {
        viewModelScope.safeLaunch {
            var finished = false
            if (placed.facilityId.isNotBlank()) {
                val result = schoolRepository.mutateSchool { school ->
                    val idx = school.facilities.indexOfFirst {
                        it.id == placed.facilityId && it.constructionDaysLeft > 0
                    }
                    if (idx == -1) return@mutateSchool false
                    school.facilities[idx] = school.facilities[idx].copy(constructionDaysLeft = 0)
                    true
                }
                if (result == null) {
                    _state.value = _state.value.copy(message = "加速未生效，请稍后重试")
                    return@safeLaunch
                }
                finished = true
            } else {
                val ok = policyManager.finishCollegeConstruction(placed.key)
                if (!ok) {
                    _state.value = _state.value.copy(message = "该学院已竣工或不在施工中")
                    return@safeLaunch
                }
                schoolRepository.mutateSchool { latest ->
                    latest.policyJson = policyManager.toJson()
                    true
                }
                finished = true
            }
            if (finished) {
                _state.value = _state.value.copy(
                    placed = _state.value.placed.map {
                        if (it.key == placed.key && (placed.facilityId.isBlank() || it.facilityId == placed.facilityId)) {
                            it.copy(constructionDaysLeft = 0)
                        } else it
                    },
                    message = "广告加速生效，施工完成！"
                )
                audioManager.playConstructionDone()
            }
        }
    }

    private suspend fun ensureHospitalPlaced() {
        val st = _state.value
        if (!st.affiliatedHospital) return
        if (st.placed.any { it.key == "HOSPITAL" }) return
        val spot = firstFree(BT.HOSPITAL, st.placed, st.terrain, st.campusLevel) ?: return
        val newPlaced = st.placed + BT.PlacedBuilding("HOSPITAL", spot.first, spot.second)
        val snapshot = policyManager.toJson()
        if (!persistLayoutResult(newPlaced, st.terrain)) {
            policyManager.restoreFromJson(snapshot)
            return
        }
        _state.value = _state.value.copy(placed = newPlaced)
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
        audioManager.playCardOpen()
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
        audioManager.playButtonClick()
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
            val before = _state.value
            val spot = if (at != null && canPlaceAt(
                    spec, at.first, at.second, before.placed, before.terrain, before.campusLevel
                ) == null
            ) {
                at
            } else {
                firstFree(spec, before.placed, before.terrain, before.campusLevel)
            }
            if (spot == null) {
                _state.value = before.copy(message = "${spec.displayName}没有合法空位，未扣款。请先清理道路或扩建校园")
                return@safeLaunch
            }
            val result = gameEngine.foundCollege(
                type = college,
                buildDays = spec.buildDays,
                buildingKey = spec.key,
                position = spot
            )
            if (result.success) {
                audioManager.playCollegeFound()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
                val days = spec.buildDays
                val newPlaced = before.placed
                    .filterNot { it.key == spec.key }
                    .plus(BT.PlacedBuilding(spec.key, spot.first, spot.second, 1, "", days))
                val msg = if (days > 0) "${spec.displayName}开工，还需 ${days} 天竣工" else "${spec.displayName}已落成"
                _state.value = before.copy(placed = newPlaced, message = msg)
            } else {
                audioManager.playEventNegative()
                _state.value = before.copy(message = result.message)
            }
        }
    }

    fun claimHospitalConstruction(spec: BT.Spec, at: Pair<Int, Int>? = null) {
        if (spec.key != BT.HOSPITAL.key) return
        viewModelScope.safeLaunch {
            val before = _state.value
            if (before.affiliatedHospital || before.placed.any { it.key == spec.key }) {
                _state.value = before.copy(message = "附属医院已存在")
                return@safeLaunch
            }
            val spot = at?.takeIf {
                canPlaceAt(spec, it.first, it.second, before.placed, before.terrain, before.campusLevel) == null
            } ?: firstFree(spec, before.placed, before.terrain, before.campusLevel)
            if (spot == null) {
                _state.value = before.copy(message = "附属医院没有合法空位，未扣款")
                return@safeLaunch
            }
            val result = gameEngine.buildAffiliatedHospital(spec.key, spot)
            if (!result.success) {
                _state.value = before.copy(message = result.message)
                return@safeLaunch
            }
            val newPlaced = before.placed + BT.PlacedBuilding(spec.key, spot.first, spot.second)
            _state.value = before.copy(placed = newPlaced, affiliatedHospital = true, message = result.message)
            audioManager.playBuildFacility()
            gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
        }
    }


    fun buildFacility(spec: BT.Spec, at: Pair<Int, Int>? = null) {
        val type = spec.facility ?: return
        viewModelScope.safeLaunch {
            val before = _state.value
            val spot = if (at != null && canPlaceAt(
                    spec, at.first, at.second, before.placed, before.terrain, before.campusLevel
                ) == null
            ) {
                at
            } else {
                firstFree(spec, before.placed, before.terrain, before.campusLevel)
            }
            if (spot == null) {
                _state.value = before.copy(message = "${spec.displayName}没有合法空位，未扣款。请先清理道路或扩建校园")
                return@safeLaunch
            }
            var newFacility: Facility? = null
            val policySnapshot = policyManager.toJson()
            val result = schoolRepository.mutateSchool { school ->
                val max = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel)
                if (school.facilities.size >= max) {
                    _state.value = _state.value.copy(message = "建筑数量已达当前等级上限（${max}），先升级校园")
                    return@mutateSchool false
                }
                val missingCollege = spec.prerequisiteColleges.firstOrNull { required ->
                    !policyManager.policies.value.collegeDevelopment.founded.contains(required)
                }
                if (missingCollege != null) {
                    _state.value = _state.value.copy(message = "${type.displayName}需要${missingCollege.displayName}竣工")
                    return@mutateSchool false
                }
                val missingFacility = spec.prerequisiteFacilities.firstOrNull { required ->
                    school.facilities.none { it.type == required && it.isOperational }
                }
                if (missingFacility != null) {
                    _state.value = _state.value.copy(message = "${type.displayName}需要${missingFacility.displayName}投入使用")
                    return@mutateSchool false
                }
                val existingCount = school.facilities.count { it.type == type }
                if (existingCount > 0 && !type.repeatable) {
                    _state.value = _state.value.copy(message = "${type.displayName} 已建成（该类型只需一座）")
                    return@mutateSchool false
                }
                val cost = com.arktools.xiao.domain.model.FacilityCapacity.repeatCost(type, existingCount)
                if (school.cash < cost) {
                    _state.value = _state.value.copy(message = "资金不足！需要 ${cost.toInt()} 万元")
                    return@mutateSchool false
                }
                val f = Facility(type = type, level = 1, condition = 100f, constructionDaysLeft = spec.buildDays)
                if (!policyManager.addPlacedFacility(
                        key = spec.key,
                        position = spot,
                        level = f.level,
                        facilityId = f.id,
                        constructionDaysLeft = f.constructionDaysLeft
                    )
                ) {
                    _state.value = _state.value.copy(message = "地图建筑记录冲突，未扣款")
                    return@mutateSchool false
                }
                school.cash -= cost
                school.facilities.add(f)
                newFacility = f
                school.policyJson = policyManager.toJson()
                true
            }
            if (result == null) {
                policyManager.restoreFromJson(policySnapshot)
            }
            if (result != null) {
                audioManager.playBuildFacility()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
                val msg = if (spec.buildDays > 0) "${spec.displayName}开工，还需 ${spec.buildDays} 天竣工" else "${spec.displayName}已落成"
                val f = newFacility
                if (f != null) {
                    val newPlaced = before.placed + BT.PlacedBuilding(
                        spec.key, spot.first, spot.second, 1, f.id, spec.buildDays
                    )
                    _state.value = before.copy(placed = newPlaced, message = msg)
                }
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    // ===== 摆放模式 =====

    fun startPlace(spec: BT.Spec) {
        _state.value = _state.value.copy(
            showBuildMenu = false,
            message = "拖动地图找空地，再点格子放置${spec.displayName}（建造需${spec.buildDays}天）"
        )
        audioManager.playButtonClick()
        pendingSpec = spec
        pendingTile = null
        moveId = null
    }

    fun startPaint(tile: BT.TileKind) {
        _state.value = _state.value.copy(
            showBuildMenu = false,
            message = "点击空地铺设${tile.displayName}。点一次铺一块，再点取消；点已铺格子可拆除。"
        )
        audioManager.playButtonClick()
        pendingTile = tile
        pendingSpec = null
        moveId = null
    }

    fun cancelPlacement() {
        audioManager.playButtonClick()
        pendingSpec = null
        pendingTile = null
        moveId = null
        _state.value = _state.value.copy(message = null)
    }

    fun startMove(placed: BT.PlacedBuilding) {
        val spec = BT.specByKey(placed.key) ?: return
        if (spec.key == BT.HOSPITAL.key) {
            _state.value = _state.value.copy(message = "附属医院不能搬移")
            return
        }
        _state.value = _state.value.copy(
            selected = null,
            selectedPlaced = null,
            message = "拖动地图并点击绿色空位，搬移${spec.displayName}"
        )
        moveId = placed.facilityId.ifBlank { placed.key }
        pendingSpec = spec
        pendingTile = null
    }

    fun removePlaced(placed: BT.PlacedBuilding) {
        val spec = BT.specByKey(placed.key) ?: return
        if (placed.key == BT.HOSPITAL.key) {
            _state.value = _state.value.copy(message = "附属医院不可拆除")
            return
        }
        if (!spec.removable) {
            _state.value = _state.value.copy(message = "${spec.displayName}不可拆除")
            return
        }
        viewModelScope.safeLaunch {
            var refund = 0.0
            val policySnapshot = policyManager.toJson()
            val result = schoolRepository.mutateSchool { school ->
                refund = spec.costWan * 0.3
                if (spec.facility != null &&
                    !policyManager.removePlacedFacility(spec.key, placed.facilityId)
                ) {
                    _state.value = _state.value.copy(message = "地图建筑记录缺失，未拆除")
                    return@mutateSchool false
                }
                school.cash += refund
                var removed = false
                if (spec.facility != null) {
                    removed = school.facilities.removeAll { it.id == placed.facilityId }
                    if (!removed) {
                        _state.value = _state.value.copy(message = "设施记录缺失，未拆除")
                        return@mutateSchool false
                    }
                }
                school.policyJson = policyManager.toJson()
                true
            }
            if (result == null) {
                policyManager.restoreFromJson(policySnapshot)
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
                viewModelScope.safeLaunch {
                    val old = st.placed.firstOrNull { it.facilityId == moving || it.key == moving }
                    val specOld = old?.let { BT.specByKey(it.key) }
                    if (old != null && specOld != null) {
                        val others = st.placed.filter { it != old }
                        val err = canPlaceAt(spec, x, y, others, st.terrain, st.campusLevel, old.facilityId.ifBlank { old.key })
                        if (err != null) {
                            _state.value = _state.value.copy(message = err)
                            audioManager.playEventNegative()
                            return@safeLaunch
                        }
                        val moved = old.copy(x = x, y = y)
                        val newPlaced = others + moved
                        val persisted = persistLayoutResult(newPlaced, st.terrain)
                        if (!persisted) {
                            _state.value = _state.value.copy(message = "地图保存失败，搬移未生效")
                            moveId = null
                            pendingSpec = null
                            return@safeLaunch
                        }
                        _state.value = _state.value.copy(placed = newPlaced, message = "${spec.displayName}已搬移")
                    }
                    moveId = null
                    pendingSpec = null
                }
                return
            }
            // 新建：附属医院由引擎在同一事务中登记地图与功能状态。
            if (spec.key == BT.HOSPITAL.key) {
                claimHospitalConstruction(spec, x to y)
                pendingSpec = null
                return
            }
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
        // 铺瓦/装扮：点一次铺一块就退出；点已有同类格子拆除
        pendingTile?.let { tile ->
            if (!BT.inUnlockedArea(x, y, st.campusLevel)) {
                _state.value = _state.value.copy(message = "该区域尚未解锁")
                return
            }
            val key = y * 1000L + x
            val existing = st.terrain[key]
            if (existing == tile) {
                val newTerrain = st.terrain - key
                pendingTile = null
                _state.value = _state.value.copy(
                    terrain = newTerrain,
                    message = "已拆除${tile.displayName}"
                )
                updateLayoutSuspend(st.placed, newTerrain)
                return
            }
            if (st.placed.any { b ->
                val spec = BT.specByKey(b.key) ?: return@any false
                BT.occupies(b, spec, x, y)
            }) {
                _state.value = _state.value.copy(message = "格子上已有建筑")
                audioManager.playEventNegative()
                return
            }
            if (st.cash < tile.costWan) {
                _state.value = _state.value.copy(message = "资金不足！需要 ${tile.costWan} 万")
                audioManager.playEventNegative()
                return
            }
            viewModelScope.safeLaunch {
                val result = schoolRepository.mutateSchool { school ->
                    if (school.cash < tile.costWan) return@mutateSchool false
                    school.cash -= tile.costWan
                    true
                }
                if (result != null) {
                    audioManager.playBuildFacility()
                    val newTerrain = st.terrain + (key to tile)
                    pendingTile = null
                    _state.value = _state.value.copy(
                        terrain = newTerrain,
                        message = "${tile.displayName}已铺设"
                    )
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
            return
        }
        // 查看道路/装扮：展示效果并提供拆除
        st.terrain[y * 1000L + x]?.let { tile ->
            audioManager.playCardOpen()
            _state.value = st.copy(
                selectedTile = tile,
                selectedTilePos = x to y
            )
        }
    }

    fun clearTileSelection() {
        _state.value = _state.value.copy(selectedTile = null, selectedTilePos = null)
    }

    /** 拆除选中的道路/装扮，返还 50% 造价。 */
    fun removeSelectedTile() {
        val st = _state.value
        val tile = st.selectedTile
        val pos = st.selectedTilePos
        if (tile == null || pos == null) return
        viewModelScope.safeLaunch {
            val key = pos.second * 1000L + pos.first
            val newTerrain = st.terrain - key
            val refund = tile.costWan * 0.5
            val persisted = if (refund > 0.0) {
                schoolRepository.mutateSchool { school ->
                    school.cash += refund
                    true
                } != null
            } else true
            if (!persisted) {
                _state.value = st.copy(message = "拆除失败，请稍后重试")
                return@safeLaunch
            }
            _state.value = st.copy(
                terrain = newTerrain,
                selectedTile = null,
                selectedTilePos = null,
                message = "已拆除${tile.displayName}，返还 ${"%.1f".format(refund)} 万"
            )
            persistLayout(st.placed, newTerrain)
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
            val teachers = teacherRepository.getTeachers()
            val teacherCount = teachers.size
            val avgSkill = if (teachers.isNotEmpty()) teachers.map { it.averageSkill }.average() else 0.0
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
                if (req.minAverageTeacherSkill > 0 && avgSkill < req.minAverageTeacherSkill)
                    add("师资均分 ${req.minAverageTeacherSkill}")
                if (req.requiresResearch && !policyManager.researchChainManager.anyCompletedRound())
                    add("需结题科研课题")
                if (req.requiresInternational && !policyManager.internationalManager.hasAnyPartner)
                    add("需国际合作院校")
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
