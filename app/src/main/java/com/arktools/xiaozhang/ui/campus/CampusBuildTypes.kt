package com.arktools.xiaozhang.ui.campus

import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.policy.CollegeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 瓦片自由建造数据层：
 * - 22×14 网格，初始解锁中央 14×8，校园每升 1 级外扩一圈
 * - 建筑占格（行政 3×2 / 学院 2×2 / 宿舍 2×3 / 医院 3×3 ...）
 * - 地形与装扮：道路、广场砖、水系、花坛、松树、石灯笼、长椅、雕像
 * - 状态经 policyJson 持久化（CollegeDevelopment.placedBuildings / terrainMap）
 */
object CampusBuildTypes {

    const val GRID_W = 22
    const val GRID_H = 14

    /** 初始解锁矩形（左上 4,3 起，14×8） */
    const val INIT_X = 4
    const val INIT_Y = 3
    const val INIT_W = 14
    const val INIT_H = 8

    /** 非草地地形/装扮（1×1） */
    enum class TileKind(val costWan: Double, val unlockLevel: Int, val displayName: String) {
        ROAD(0.5, 1, "水泥路"),
        PLAZA(1.0, 2, "广场砖"),
        WATER(2.0, 3, "水系"),
        FLOWERBED(0.3, 1, "花坛"),
        TREE(0.2, 1, "松树"),
        LANTERN(0.8, 1, "石灯笼"),
        BENCH(0.4, 1, "长椅"),
        STATUE(1.0, 1, "雕像")
    }

    /** 建筑规格 */
    data class Spec(
        val key: String,
        val displayName: String,
        val w: Int,
        val h: Int,
        val costWan: Double,
        val unlockLevel: Int,
        val drawableRes: Int,
        val college: CollegeType? = null,
        val facility: FacilityType? = null,
        val movable: Boolean = true,
        val removable: Boolean = true
    )

    val ADMIN = Spec(
        "ADMIN", "行政楼", 3, 2, 0.0, 1,
        R.drawable.bld_admin, movable = false, removable = false
    )
    val COLLEGE_SPECS = listOf(
        Spec("C_LIBERAL", "人文学院", 2, 2, 18.0, 1, R.drawable.bld_liberal, college = CollegeType.LIBERAL_ARTS, removable = false),
        Spec("C_SCIENCE", "理学院", 2, 2, 36.0, 2, R.drawable.bld_generic, college = CollegeType.SCIENCE, removable = false),
        Spec("C_ENGINEERING", "工学院", 2, 2, 58.0, 3, R.drawable.bld_generic, college = CollegeType.ENGINEERING, removable = false),
        Spec("C_BUSINESS", "商学院", 2, 2, 72.0, 4, R.drawable.bld_generic, college = CollegeType.BUSINESS, removable = false),
        Spec("C_ART", "艺术学院", 2, 2, 65.0, 3, R.drawable.bld_art, college = CollegeType.ARTS, removable = false),
        Spec("C_MEDICINE", "医学院", 2, 2, 110.0, 4, R.drawable.bld_medicine, college = CollegeType.MEDICINE, removable = false)
    )
    val FACILITY_SPECS = listOf(
        Spec("F_CLASSROOM", "标准教室", 2, 2, 18.0, 1, R.drawable.facility_classroom, facility = FacilityType.CLASSROOM),
        Spec("F_LIBRARY", "图书馆", 2, 2, 30.0, 1, R.drawable.bld_library, facility = FacilityType.LIBRARY),
        Spec("F_DORMITORY", "宿舍楼", 2, 3, 95.0, 1, R.drawable.bld_dorm, facility = FacilityType.DORMITORY),
        Spec("F_CANTEEN", "食堂", 2, 2, 28.0, 1, R.drawable.facility_canteen, facility = FacilityType.CANTEEN),
        Spec("F_MULTIMEDIA", "多媒体教室", 2, 2, 35.0, 1, R.drawable.facility_multimedia_room, facility = FacilityType.MULTIMEDIA_ROOM),
        Spec("F_GARDEN", "校园花园", 2, 2, 12.0, 1, R.drawable.facility_garden, facility = FacilityType.GARDEN),
        Spec("F_GATE", "校门", 2, 1, 8.0, 1, R.drawable.facility_gate, facility = FacilityType.GATE),
        Spec("F_SPORTS_FIELD", "体育馆", 3, 2, 45.0, 2, R.drawable.facility_sports_field, facility = FacilityType.SPORTS_FIELD),
        Spec("F_LABORATORY", "实验室", 2, 2, 50.0, 2, R.drawable.facility_laboratory, facility = FacilityType.LABORATORY),
        Spec("F_COMPUTER_LAB", "计算机房", 2, 2, 40.0, 2, R.drawable.facility_computer_lab, facility = FacilityType.COMPUTER_LAB),
        Spec("F_ART_STUDIO", "艺术工作室", 2, 2, 25.0, 2, R.drawable.facility_art_studio, facility = FacilityType.ART_STUDIO),
        Spec("F_EMPLOYMENT", "就业指导中心", 2, 2, 45.0, 2, R.drawable.bld_employment, facility = FacilityType.EMPLOYMENT_CENTER),
        Spec("F_CONFERENCE", "会议中心", 2, 2, 60.0, 3, R.drawable.bld_conference, facility = FacilityType.CONFERENCE_CENTER),
        Spec("F_AUDITORIUM", "大礼堂", 3, 2, 100.0, 3, R.drawable.facility_auditorium, facility = FacilityType.AUDITORIUM)
    )
    val HOSPITAL = Spec(
        "HOSPITAL", "附属医院", 3, 3, 0.0, 4,
        R.drawable.bld_hospital, movable = true, removable = false
    )

    fun allBuildingSpecs(): List<Spec> = listOf(ADMIN) + COLLEGE_SPECS + FACILITY_SPECS + listOf(HOSPITAL)

    fun specByKey(key: String): Spec? = allBuildingSpecs().firstOrNull { it.key == key }

    fun collegeSpec(college: CollegeType): Spec? = COLLEGE_SPECS.firstOrNull { it.college == college }

    fun facilitySpec(type: FacilityType): Spec? = FACILITY_SPECS.firstOrNull { it.facility == type }

    // ===== 持久化模型 =====

    @Serializable
    data class PlacedBuilding(
        val key: String,
        val x: Int,
        val y: Int,
        val level: Int = 1,
        val facilityId: String = ""
    )

    @Serializable
    data class TerrainCell(val x: Int, val y: Int, val kind: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeBuildings(list: List<PlacedBuilding>): String =
        runCatching { json.encodeToString(list) }.getOrDefault("")

    fun decodeBuildings(raw: String): List<PlacedBuilding> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<PlacedBuilding>>(raw) }.getOrDefault(emptyList())

    fun encodeTerrain(list: List<TerrainCell>): String =
        runCatching { json.encodeToString(list) }.getOrDefault("")

    fun decodeTerrain(raw: String): List<TerrainCell> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<TerrainCell>>(raw) }.getOrDefault(emptyList())

    /** 占格判定：格子是否被某建筑占据 */
    fun occupies(b: PlacedBuilding, spec: Spec, x: Int, y: Int): Boolean =
        x >= b.x && x < b.x + spec.w && y >= b.y && y < b.y + spec.h

    /** 解锁区域检查 */
    fun inUnlockedArea(x: Int, y: Int, campusLevel: Int): Boolean {
        val ring = (campusLevel - 1).coerceAtMost(4)
        val x0 = INIT_X - ring
        val y0 = INIT_Y - ring
        val w = INIT_W + ring * 2
        val h = INIT_H + ring * 2
        return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h
    }
}
