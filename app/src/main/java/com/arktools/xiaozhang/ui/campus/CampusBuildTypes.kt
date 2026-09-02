package com.arktools.xiaozhang.ui.campus

import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.policy.CollegeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 瓦片自由建造数据层：
 * - 48×32 网格（旧档建筑仍停在原坐标，新地向外长）
 * - 初始解锁中央 14×8，校园升级分阶段开地，满级铺满
 * - 建筑占格（行政 3×2 / 学院 2×2 / 宿舍 2×3 / 医院 3×3 ...）
 * - 地形与装扮：道路、广场砖、水系、花坛、松树、石灯笼、长椅、雕像
 * - 状态经 policyJson 持久化（CollegeDevelopment.placedBuildings / terrainMap）
 */
object CampusBuildTypes {

    const val GRID_W = 48
    const val GRID_H = 32

    /** 初始解锁矩形（左上 4,3 起，14×8）——与旧 22×14 地图重叠，旧档不位移 */
    const val INIT_X = 4
    const val INIT_Y = 3
    const val INIT_W = 16
    const val INIT_H = 12

    data class UnlockRect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w: Int get() = x1 - x0
        val h: Int get() = y1 - y0
        val cells: Int get() = w * h
    }

    fun unlockRing(campusLevel: Int): Int = when (campusLevel.coerceAtLeast(1)) {
        1 -> 0
        2 -> 4
        3 -> 8
        4 -> 14
        5 -> 20
        else -> 99
    }

    fun unlockedRect(campusLevel: Int): UnlockRect {
        val ring = unlockRing(campusLevel)
        val x0 = (INIT_X - ring).coerceAtLeast(0)
        val y0 = (INIT_Y - ring).coerceAtLeast(0)
        val x1 = (INIT_X + INIT_W + ring).coerceAtMost(GRID_W)
        val y1 = (INIT_Y + INIT_H + ring).coerceAtMost(GRID_H)
        return UnlockRect(x0, y0, x1, y1)
    }

    /** 非草地地形/装扮（1×1） */
    enum class TileKind(
        val costWan: Double,
        val unlockLevel: Int,
        val displayName: String,
        val drawableRes: Int = 0,
        val monthlyMaintenanceWan: Double = 0.0,
        val satisfactionBonus: Float = 0f,
        val reputationBonus: Long = 0L
    ) {
        ROAD(0.5, 1, "水泥路", R.drawable.tile_path),
        PLAZA(1.0, 2, "广场砖", R.drawable.tile_plaza),
        WATER(2.0, 3, "水系", R.drawable.deco_water),
        FLOWERBED(0.3, 1, "花坛", R.drawable.deco_flowerbed, 0.02, 0.02f),
        TREE(0.2, 1, "松树", R.drawable.deco_tree, 0.01, 0.01f),
        LANTERN(0.8, 1, "石灯笼", R.drawable.deco_lantern, 0.03, 0.02f, 1L),
        BENCH(0.4, 1, "长椅", R.drawable.deco_bench, 0.02, 0.03f),
        STATUE(1.0, 1, "雕像", R.drawable.deco_statue),
        CHERRY_TREE(0.6, 2, "樱花树", R.drawable.deco_cherry, 0.03, 0.05f, 1L),
        GINKGO(0.8, 2, "银杏树", R.drawable.deco_ginkgo, 0.03, 0.05f, 1L),
        BAMBOO(0.4, 1, "竹丛", R.drawable.deco_bamboo, 0.01, 0.03f),
        LAMP(0.5, 1, "路灯", R.drawable.deco_lamp, 0.02, 0.02f, 1L),
        MEMORIAL(1.8, 2, "纪念碑", R.drawable.deco_memorial, 0.04, 0.05f, 3L),
        SCHOOL_SIGN(2.5, 2, "校名牌", R.drawable.deco_school_sign, 0.02, 0.03f, 3L),
        PAVILION(3.0, 3, "凉亭", R.drawable.deco_pavilion, 0.06, 0.08f, 2L),
        PARCEL(1.5, 2, "快递驿站", R.drawable.deco_parcel, 0.03, 0.06f),
        FITNESS(1.2, 2, "健身角", R.drawable.deco_fitness, 0.04, 0.06f),
        FOUNTAIN(2.0, 3, "喷泉", R.drawable.deco_fountain, 0.10, 0.08f, 3L)
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
        val removable: Boolean = true,
        val buildDays: Int = 3,
        val prerequisiteColleges: List<CollegeType> = emptyList(),
        val prerequisiteFacilities: List<FacilityType> = emptyList(),
        val downstream: String = ""
    )

    val ADMIN = Spec(
        "ADMIN", "行政楼", 4, 3, 0.0, 1,
        R.drawable.bld_admin, movable = true, removable = false, buildDays = 0
    )
    val COLLEGE_SPECS = listOf(
        Spec("C_LIBERAL", "人文学院", 3, 2, 18.0, 1, R.drawable.bld_liberal, college = CollegeType.LIBERAL_ARTS, removable = false, buildDays = 3),
        Spec("C_SCIENCE", "理学院", 3, 3, 36.0, 2, R.drawable.bld_generic, college = CollegeType.SCIENCE, removable = false, buildDays = 4),
        Spec("C_ENGINEERING", "工学院", 4, 3, 58.0, 3, R.drawable.bld_generic, college = CollegeType.ENGINEERING, removable = false, buildDays = 5),
        Spec("C_BUSINESS", "商学院", 3, 3, 72.0, 4, R.drawable.bld_generic, college = CollegeType.BUSINESS, removable = false, buildDays = 4),
        Spec("C_ART", "艺术学院", 3, 2, 65.0, 3, R.drawable.bld_art, college = CollegeType.ARTS, removable = false, buildDays = 4),
        Spec("C_MEDICINE", "医学院", 4, 3, 110.0, 4, R.drawable.bld_medicine, college = CollegeType.MEDICINE, removable = false, buildDays = 6)
    )
    val FACILITY_SPECS = listOf(
        Spec("F_CLASSROOM", "标准教室", 2, 2, 18.0, 1, R.drawable.bld_classroom, facility = FacilityType.CLASSROOM, buildDays = 2),
        Spec("F_LIBRARY", "图书馆", 3, 3, 30.0, 1, R.drawable.bld_library, facility = FacilityType.LIBRARY, buildDays = 4),
        Spec("F_DORMITORY", "宿舍楼", 3, 3, 95.0, 1, R.drawable.bld_dorm, facility = FacilityType.DORMITORY, buildDays = 4),
        Spec("F_CANTEEN", "食堂", 3, 2, 28.0, 1, R.drawable.bld_canteen, facility = FacilityType.CANTEEN, buildDays = 3),
        Spec("F_MULTIMEDIA", "多媒体教室", 2, 2, 35.0, 1, R.drawable.bld_multimedia, facility = FacilityType.MULTIMEDIA_ROOM, buildDays = 3),
        Spec("F_GARDEN", "校园花园", 2, 2, 12.0, 1, R.drawable.bld_garden, facility = FacilityType.GARDEN, buildDays = 2),
        Spec("F_GATE", "校门", 4, 3, 8.0, 1, R.drawable.bld_gate, facility = FacilityType.GATE, buildDays = 2),
        Spec("F_SPORTS_FIELD", "体育馆", 4, 3, 45.0, 2, R.drawable.bld_sports, facility = FacilityType.SPORTS_FIELD, buildDays = 5),
        Spec("F_LABORATORY", "实验室", 3, 2, 50.0, 2, R.drawable.bld_lab, facility = FacilityType.LABORATORY, buildDays = 4, prerequisiteColleges = listOf(CollegeType.SCIENCE), downstream = "解锁理科实验课与应用科研"),
        Spec("F_COMPUTER_LAB", "计算机房", 3, 2, 40.0, 2, R.drawable.bld_computer, facility = FacilityType.COMPUTER_LAB, buildDays = 3, prerequisiteColleges = listOf(CollegeType.ENGINEERING), downstream = "解锁编程课与数字化项目"),
        Spec("F_ART_STUDIO", "艺术工作室", 2, 2, 25.0, 2, R.drawable.bld_studio, facility = FacilityType.ART_STUDIO, buildDays = 3, prerequisiteColleges = listOf(CollegeType.ARTS), downstream = "解锁艺术实践与作品产出"),
        Spec("F_EMPLOYMENT", "就业指导中心", 3, 2, 45.0, 2, R.drawable.bld_employment, facility = FacilityType.EMPLOYMENT_CENTER, buildDays = 3, downstream = "提高毕业去向质量与校企合作"),
        Spec("F_INCUBATOR", "校企合作中心", 3, 2, 80.0, 3, R.drawable.bld_incubator, facility = FacilityType.INCUBATOR, buildDays = 4, prerequisiteFacilities = listOf(FacilityType.EMPLOYMENT_CENTER), downstream = "创业与深造概率再提升，衔接校企实习资源"),
        Spec("F_INTERNATIONAL", "国际交流中心", 3, 3, 90.0, 4, R.drawable.bld_intl, facility = FacilityType.INTERNATIONAL_CENTER, buildDays = 4, prerequisiteColleges = listOf(CollegeType.BUSINESS), downstream = "国际生学费收入与年度合作声誉提升"),
        Spec("F_LOGISTICS", "后勤保障中心", 3, 2, 70.0, 2, R.drawable.bld_logistics, facility = FacilityType.LOGISTICS_CENTER, buildDays = 4, prerequisiteFacilities = listOf(FacilityType.CANTEEN), downstream = "降低全校设施维护成本"),
        Spec("F_CONFERENCE", "会议中心", 3, 3, 60.0, 3, R.drawable.bld_conference, facility = FacilityType.CONFERENCE_CENTER, buildDays = 4, prerequisiteFacilities = listOf(FacilityType.LIBRARY), downstream = "解锁学术会议与合作声誉"),
        Spec("F_AUDITORIUM", "大礼堂", 4, 3, 100.0, 3, R.drawable.bld_auditorium, facility = FacilityType.AUDITORIUM, buildDays = 5, prerequisiteFacilities = listOf(FacilityType.SPORTS_FIELD), downstream = "解锁大型文化节与校友活动")
    )
    val HOSPITAL = Spec(
        "HOSPITAL", "附属医院", 3, 3, 300.0, 4,
        R.drawable.bld_hospital, movable = false, removable = false, buildDays = 0,
        prerequisiteColleges = listOf(CollegeType.MEDICINE),
        downstream = "解锁临床实习、诊疗收入与医学声誉"
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
        val facilityId: String = "",
        val constructionDaysLeft: Int = 0
    ) {
        val isConstructing: Boolean get() = constructionDaysLeft > 0
    }

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
        val rect = unlockedRect(campusLevel)
        return x >= rect.x0 && x < rect.x1 && y >= rect.y0 && y < rect.y1
    }
}
