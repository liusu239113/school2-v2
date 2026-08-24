package com.arktools.xiaozhang.data.repository

import androidx.room.withTransaction
import com.arktools.xiaozhang.data.local.AppDatabase
import com.arktools.xiaozhang.data.local.dao.TeachingMethodDao
import com.arktools.xiaozhang.data.local.entity.TeachingMethodEntity
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import com.arktools.xiaozhang.domain.model.BonusType
import com.arktools.xiaozhang.domain.model.MethodCategory
import com.arktools.xiaozhang.domain.model.TeachingMethod
import com.arktools.xiaozhang.domain.repository.ResearchRepository
import com.arktools.xiaozhang.domain.repository.TeachingMethodUnlockResult
import com.arktools.xiaozhang.domain.repository.TeachingMethodUnlockStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ResearchRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val teachingMethodDao: TeachingMethodDao,
    private val settingsDataStore: SettingsDataStore
) : ResearchRepository {

    override fun getMethodsFlow(): Flow<List<TeachingMethod>> {
        return settingsDataStore.schoolId.map { it ?: "" }
            .flatMapLatest { schoolId ->
                teachingMethodDao.getMethodsBySchoolFlow(schoolId).map { list ->
                    list.map { it.toDomain() }
                }
            }
    }

    override suspend fun getMethods(): List<TeachingMethod> {
        val schoolId = settingsDataStore.schoolId.first() ?: return emptyList()
        return teachingMethodDao.getMethodsBySchool(schoolId).map { it.toDomain() }
    }

    override suspend fun getUnlockedMethods(): List<TeachingMethod> {
        val schoolId = settingsDataStore.schoolId.first() ?: return emptyList()
        return teachingMethodDao.getUnlockedMethods(schoolId).map { it.toDomain() }
    }

    override suspend fun getMethodById(methodId: String): TeachingMethod? {
        val schoolId = settingsDataStore.schoolId.first() ?: return null
        return teachingMethodDao.getMethodById(schoolId, methodId)?.toDomain()
    }

    override suspend fun unlockMethod(
        methodId: String
    ): TeachingMethodUnlockResult {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank()) {
            return TeachingMethodUnlockResult(
                TeachingMethodUnlockStatus.UNAVAILABLE
            )
        }

        return database.withTransaction {
            val schoolDao = database.schoolDao()
            val school = schoolDao.getSchoolCore()
            if (school == null || school.id != schoolId) {
                return@withTransaction TeachingMethodUnlockResult(
                    TeachingMethodUnlockStatus.UNAVAILABLE
                )
            }

            val entity = teachingMethodDao.getMethodById(
                schoolId,
                methodId
            ) ?: return@withTransaction TeachingMethodUnlockResult(
                TeachingMethodUnlockStatus.UNAVAILABLE
            )
            val method = entity.toDomain()
            if (entity.isUnlocked) {
                return@withTransaction TeachingMethodUnlockResult(
                    TeachingMethodUnlockStatus.ALREADY_UNLOCKED,
                    method = method
                )
            }
            if (!entity.cost.isFinite() || entity.cost < 0.0) {
                return@withTransaction TeachingMethodUnlockResult(
                    TeachingMethodUnlockStatus.UNAVAILABLE,
                    method = method
                )
            }

            val unlockedMethods =
                teachingMethodDao.getUnlockedMethods(schoolId)
            val unlockedCount = unlockedMethods.size
            val requiredUnlocks = when {
                entity.cost <= 10.0 -> 0
                entity.cost <= 25.0 -> 3
                entity.cost <= 50.0 -> 6
                entity.cost <= 100.0 -> 10
                else -> 15
            }
            if (unlockedCount < requiredUnlocks) {
                return@withTransaction TeachingMethodUnlockResult(
                    status = TeachingMethodUnlockStatus.TIER_LOCKED,
                    method = method,
                    requiredUnlocks = requiredUnlocks,
                    unlockedCount = unlockedCount
                )
            }

            val prerequisiteIds = try {
                Json.decodeFromString<List<String>>(
                    entity.prerequisiteIdsJson
                )
            } catch (_: Exception) {
                return@withTransaction TeachingMethodUnlockResult(
                    TeachingMethodUnlockStatus.UNAVAILABLE,
                    method = method
                )
            }
            val unlockedIds = unlockedMethods.mapTo(HashSet()) { it.id }
            if (prerequisiteIds.any { it !in unlockedIds }) {
                return@withTransaction TeachingMethodUnlockResult(
                    TeachingMethodUnlockStatus.PREREQUISITE_LOCKED,
                    method = method
                )
            }
            if (school.cash < entity.cost) {
                return@withTransaction TeachingMethodUnlockResult(
                    status = TeachingMethodUnlockStatus.INSUFFICIENT_FUNDS,
                    method = method,
                    availableCash = school.cash
                )
            }

            check(
                teachingMethodDao.startResearch(
                    schoolId,
                    methodId
                ) == 1
            ) { "Teaching method research state changed concurrently" }

            val now = System.currentTimeMillis()
            val schoolRows = if (entity.cost > 0.0) {
                schoolDao.deductCashIfEnough(
                    schoolId,
                    entity.cost,
                    now
                )
            } else {
                schoolDao.touchRevision(schoolId, now)
            }
            check(schoolRows == 1) {
                "Teaching method payment update failed"
            }

            TeachingMethodUnlockResult(
                status = TeachingMethodUnlockStatus.SUCCESS,
                method = method.copy(
                    isResearching = true,
                    remainingResearchDays = method.researchDays
                )
            )
        }
    }

    override suspend fun advanceResearchDay(): List<TeachingMethod> {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank()) return emptyList()
        return database.withTransaction {
            teachingMethodDao.advanceResearchDay(schoolId)
            val completed = teachingMethodDao.getMethodsBySchool(schoolId)
                .filter { it.isResearching && it.remainingResearchDays <= 0 }
            teachingMethodDao.completeReadyResearch(schoolId)
            completed.map { it.toDomain().copy(
                isUnlocked = true,
                isResearching = false,
                remainingResearchDays = 0
            ) }
        }
    }

    override suspend fun initializeDefaultMethods(schoolId: String) {
        val defaultMethods = listOf(
            TeachingMethod(
                name = "传统讲授法",
                description = "最基础的教学方法，所有学校默认掌握",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1988,
                cost = 0.0,
                researchDays = 0,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.05f,
                isUnlocked = true
            ),
            TeachingMethod(
                name = "情境教学法",
                description = "通过创设情境提高学生理解力",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1990,
                cost = 5.0,
                researchDays = 30,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.10f
            ),
            TeachingMethod(
                name = "项目式学习",
                description = "以项目为导向的探究式学习",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1995,
                cost = 15.0,
                researchDays = 60,
                bonusType = BonusType.RESEARCH_SPEED,
                bonusValue = 0.15f
            ),
            TeachingMethod(
                name = "翻转课堂",
                description = "课前自学，课上讨论的新型模式",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2005,
                cost = 30.0,
                researchDays = 90,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.20f
            ),
            TeachingMethod(
                name = "STEAM教育",
                description = "跨学科融合的综合教育模式",
                category = MethodCategory.CURRICULUM,
                unlockYear = 2015,
                cost = 50.0,
                researchDays = 120,
                bonusType = BonusType.REVENUE,
                bonusValue = 0.25f
            ),
            TeachingMethod(
                name = "AI辅助教学",
                description = "利用人工智能个性化教学",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 2020,
                cost = 100.0,
                researchDays = 180,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.30f
            ),
            TeachingMethod(
                name = "正向激励理论",
                description = "提高教师工作积极性",
                category = MethodCategory.PSYCHOLOGY,
                unlockYear = 1992,
                cost = 8.0,
                researchDays = 45,
                bonusType = BonusType.TEACHER_LOYALTY,
                bonusValue = 0.10f
            ),
            TeachingMethod(
                name = "精细化管理",
                description = "优化学校运营效率",
                category = MethodCategory.MANAGEMENT,
                unlockYear = 2000,
                cost = 20.0,
                researchDays = 60,
                bonusType = BonusType.COST_REDUCTION,
                bonusValue = 0.15f
            ),
            TeachingMethod(
                name = "多媒体教学",
                description = "投影仪、幻灯片、视频等多媒体手段辅助教学",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 1993,
                cost = 10.0,
                researchDays = 40,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.08f
            ),
            TeachingMethod(
                name = "互动式教学",
                description = "师生互动、小组讨论，提升课堂参与度",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1991,
                cost = 6.0,
                researchDays = 35,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.07f
            ),
            TeachingMethod(
                name = "游戏化教学",
                description = "将游戏元素融入教学，提升学习兴趣",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2010,
                cost = 25.0,
                researchDays = 70,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.18f
            ),
            TeachingMethod(
                name = "分层教学",
                description = "根据学生水平分层授课，因材施教",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1996,
                cost = 12.0,
                researchDays = 50,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.12f
            ),
            TeachingMethod(
                name = "微课教学",
                description = "5-10分钟短视频，聚焦单一知识点",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 2012,
                cost = 20.0,
                researchDays = 55,
                bonusType = BonusType.RESEARCH_SPEED,
                bonusValue = 0.10f
            ),
            TeachingMethod(
                name = "探究式学习",
                description = "提出问题，引导学生自主探索答案",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1994,
                cost = 8.0,
                researchDays = 45,
                bonusType = BonusType.RESEARCH_SPEED,
                bonusValue = 0.09f
            ),
            TeachingMethod(
                name = "合作学习",
                description = "小组协作完成任务，培养团队能力",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1993,
                cost = 7.0,
                researchDays = 40,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.08f
            ),
            TeachingMethod(
                name = "思维导图教学",
                description = "用思维导图梳理知识结构",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2003,
                cost = 15.0,
                researchDays = 55,
                bonusType = BonusType.RESEARCH_SPEED,
                bonusValue = 0.12f
            ),
            TeachingMethod(
                name = "案例教学法",
                description = "通过实际案例分析培养应用能力",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1997,
                cost = 14.0,
                researchDays = 50,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.13f
            ),
            TeachingMethod(
                name = "VR沉浸式教学",
                description = "虚拟现实技术，身临其境的学习体验",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 2022,
                cost = 120.0,
                researchDays = 200,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.35f
            ),
            TeachingMethod(
                name = "跨学科融合",
                description = "打破学科壁垒，综合培养",
                category = MethodCategory.CURRICULUM,
                unlockYear = 2016,
                cost = 40.0,
                researchDays = 100,
                bonusType = BonusType.REVENUE,
                bonusValue = 0.22f
            ),
            TeachingMethod(
                name = "导师制教学",
                description = "一对一导师指导，个性化发展",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1998,
                cost = 18.0,
                researchDays = 65,
                bonusType = BonusType.TEACHER_LOYALTY,
                bonusValue = 0.15f
            ),
            TeachingMethod(
                name = "实验教学法",
                description = "动手实验，在实践中验证理论",
                category = MethodCategory.CURRICULUM,
                unlockYear = 1992,
                cost = 10.0,
                researchDays = 40,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.10f
            ),
            TeachingMethod(
                name = "辩论式教学",
                description = "通过辩论培养批判性思维",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2001,
                cost = 12.0,
                researchDays = 45,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.11f
            ),
            TeachingMethod(
                name = "体验式学习",
                description = "亲身参与体验，从做中学",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1999,
                cost = 16.0,
                researchDays = 55,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.14f
            ),
            TeachingMethod(
                name = "任务驱动教学",
                description = "以任务为导向，在完成任务中学习",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2002,
                cost = 14.0,
                researchDays = 50,
                bonusType = BonusType.RESEARCH_SPEED,
                bonusValue = 0.12f
            ),
            TeachingMethod(
                name = "脚手架教学",
                description = "提供学习支架，逐步撤除引导",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2004,
                cost = 16.0,
                researchDays = 55,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.13f
            ),
            TeachingMethod(
                name = "差异化教学",
                description = "针对不同学生设计不同学习路径",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2008,
                cost = 22.0,
                researchDays = 70,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.17f
            ),
            TeachingMethod(
                name = "同伴教学法",
                description = "学生互相教学，共同进步",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2006,
                cost = 18.0,
                researchDays = 60,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.15f
            ),
            TeachingMethod(
                name = "反思性教学",
                description = "引导反思学习过程，总结经验",
                category = MethodCategory.PSYCHOLOGY,
                unlockYear = 2003,
                cost = 15.0,
                researchDays = 50,
                bonusType = BonusType.TEACHER_LOYALTY,
                bonusValue = 0.12f
            ),
            TeachingMethod(
                name = "故事化教学",
                description = "用故事包装知识点，增强记忆",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2007,
                cost = 20.0,
                researchDays = 55,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.16f
            ),
            TeachingMethod(
                name = "竞赛式教学",
                description = "通过竞赛激发学习积极性",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2009,
                cost = 22.0,
                researchDays = 65,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.18f
            ),
            TeachingMethod(
                name = "混合式教学",
                description = "线上线下相结合，灵活学习",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 2018,
                cost = 60.0,
                researchDays = 90,
                bonusType = BonusType.REVENUE,
                bonusValue = 0.28f
            ),
            TeachingMethod(
                name = "社会化学习",
                description = "利用社交媒体促进学习交流",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 2014,
                cost = 35.0,
                researchDays = 80,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.22f
            ),
            TeachingMethod(
                name = "自适应学习",
                description = "根据学习数据自动调整难度",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 2021,
                cost = 90.0,
                researchDays = 150,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.32f
            ),
            TeachingMethod(
                name = "户外实践教学",
                description = "走出教室，在真实环境中学习",
                category = MethodCategory.CURRICULUM,
                unlockYear = 1995,
                cost = 12.0,
                researchDays = 45,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.11f
            ),
            TeachingMethod(
                name = "戏剧教学法",
                description = "通过角色扮演和戏剧表演学习",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 2000,
                cost = 18.0,
                researchDays = 60,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.15f
            ),
            TeachingMethod(
                name = "问题导向学习",
                description = "以真实问题为起点，驱动学习",
                category = MethodCategory.PEDAGOGY,
                unlockYear = 1998,
                cost = 15.0,
                researchDays = 55,
                bonusType = BonusType.RESEARCH_SPEED,
                bonusValue = 0.14f
            ),
            TeachingMethod(
                name = "模拟仿真教学",
                description = "计算机模拟真实场景进行训练",
                category = MethodCategory.TECHNOLOGY,
                unlockYear = 2017,
                cost = 55.0,
                researchDays = 100,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.25f
            ),
            TeachingMethod(
                name = "正念教学法",
                description = "融入正念练习，提升专注力",
                category = MethodCategory.PSYCHOLOGY,
                unlockYear = 2019,
                cost = 45.0,
                researchDays = 80,
                bonusType = BonusType.TEACHER_LOYALTY,
                bonusValue = 0.20f
            ),
            TeachingMethod(
                name = "创业教育法",
                description = "培养创新思维和创业能力",
                category = MethodCategory.CURRICULUM,
                unlockYear = 2015,
                cost = 50.0,
                researchDays = 110,
                bonusType = BonusType.REVENUE,
                bonusValue = 0.30f
            ),
            TeachingMethod(
                name = "大单元教学",
                description = "以大概念统领单元教学设计",
                category = MethodCategory.CURRICULUM,
                unlockYear = 2020,
                cost = 70.0,
                researchDays = 130,
                bonusType = BonusType.TEACHING_QUALITY,
                bonusValue = 0.28f
            ),
            TeachingMethod(
                name = "核心素养导向",
                description = "以核心素养为目标的教学设计",
                category = MethodCategory.CURRICULUM,
                unlockYear = 2018,
                cost = 65.0,
                researchDays = 120,
                bonusType = BonusType.REVENUE,
                bonusValue = 0.26f
            ),
            TeachingMethod(
                name = "元认知训练",
                description = "训练学生对自己学习过程的认知和调控",
                category = MethodCategory.PSYCHOLOGY,
                unlockYear = 2010,
                cost = 30.0,
                researchDays = 75,
                bonusType = BonusType.RESEARCH_SPEED,
                bonusValue = 0.20f
            ),
            TeachingMethod(
                name = "家校协同管理",
                description = "家校联动，共同促进学生学习",
                category = MethodCategory.MANAGEMENT,
                unlockYear = 2005,
                cost = 25.0,
                researchDays = 70,
                bonusType = BonusType.ENROLLMENT,
                bonusValue = 0.18f
            )
        )

        teachingMethodDao.insertMethods(defaultMethods.map { it.toEntity(schoolId) })
    }

    override suspend fun getUnlockedMethodBonus(methodIds: List<String>): Float {
        val methods = getUnlockedMethods()
        return methods.filter { it.id in methodIds }.sumOf { it.bonusValue.toDouble() }.toFloat()
    }

    override suspend fun getUnlockedBonusByType(bonusType: BonusType): Float {
        val methods = getUnlockedMethods()
        return methods.filter { it.bonusType == bonusType }.sumOf { it.bonusValue.toDouble() }.toFloat()
    }

    override suspend fun deleteAll() {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        teachingMethodDao.deleteMethodsBySchool(schoolId)
    }

    private fun TeachingMethodEntity.toDomain(): TeachingMethod {
        return TeachingMethod(
            id = id,
            name = name,
            description = description,
            category = try { MethodCategory.valueOf(category) } catch (_: Exception) { MethodCategory.entries.first() },
            unlockYear = unlockYear,
            cost = cost,
            researchDays = researchDays,
            bonusType = try { BonusType.valueOf(bonusType) } catch (_: Exception) { BonusType.entries.first() },
            bonusValue = bonusValue,
            prerequisiteIds = try { Json.decodeFromString(prerequisiteIdsJson) } catch (_: Exception) { emptyList() },
            isUnlocked = isUnlocked,
            isResearching = isResearching,
            remainingResearchDays = remainingResearchDays
        )
    }

    private fun TeachingMethod.toEntity(schoolId: String): TeachingMethodEntity {
        return TeachingMethodEntity(
            id = id,
            name = name,
            description = description,
            category = category.name,
            unlockYear = unlockYear,
            cost = cost,
            researchDays = researchDays,
            bonusType = bonusType.name,
            bonusValue = bonusValue,
            prerequisiteIdsJson = Json.encodeToString(prerequisiteIds),
            isUnlocked = isUnlocked,
            isResearching = isResearching,
            remainingResearchDays = remainingResearchDays,
            schoolId = schoolId
        )
    }
}
