package com.arktools.xiao.data.repository

import androidx.room.withTransaction
import com.arktools.xiao.data.local.AppDatabase
import com.arktools.xiao.data.local.dao.SchoolDao
import com.arktools.xiao.data.local.entity.SchoolCoreEntity
import com.arktools.xiao.data.local.entity.SchoolEntity
import com.arktools.xiao.data.local.entity.SchoolManagerStateEntity
import com.arktools.xiao.data.local.entity.SchoolManagerStateChunkEntity
import com.arktools.xiao.data.local.entity.SchoolManagerStateKeys
import com.arktools.xiao.domain.engine.GameBalanceConfig
import com.arktools.xiao.domain.model.Facility
import com.arktools.xiao.domain.model.FacilityType
import com.arktools.xiao.domain.model.MarketingCampaign
import com.arktools.xiao.domain.model.School
import com.arktools.xiao.domain.model.StockInvestment
import com.arktools.xiao.domain.repository.SchoolRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.arktools.xiao.data.pref.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val schoolDao: SchoolDao,
    private val settingsDataStore: SettingsDataStore
) : SchoolRepository {

    private companion object {
        const val MANAGER_STATE_CHUNK_SIZE = 32_000
    }

    /**
     * 核心并发锁：序列化所有 School 状态的读-改-写操作。
     * 
     * 问题根因：GameEngine（Dispatchers.Default）和多个 ViewModel（viewModelScope/Main）
     * 并发执行 getSchool() → 修改 → updateSchool()，导致：
     * 1. 后写覆盖先写（lost update）——资金/声望计算错误
     * 2. MutableList 并发修改 → ConcurrentModificationException → 闪退
     * 
     * 此 Mutex 确保同一时刻只有一个协程在执行 School 的读写操作。
     */
    private val schoolMutex = Mutex()

    private suspend fun <T> withSchoolLock(block: suspend () -> T): T {
        return schoolMutex.withLock { block() }
    }

    private suspend fun <T> inWriteTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }

    override fun getSchoolFlow(): Flow<School?> = flow {
        while (true) {
            try {
                schoolDao.getSchoolCoreFlow().collect { core ->
                    emit(core?.let { loadSchool(it) })
                }
            } catch (e: Exception) {
                android.util.Log.e("SchoolRepo", "Core school flow failed, retrying: ${e.message}")
            }
            delay(1000)
        }
    }

    override suspend fun getSchool(): School? = withSchoolLock {
        schoolDao.getSchoolCore()?.let { loadSchool(it) }
    }

    override suspend fun createSchool(name: String): School = withSchoolLock {
        inWriteTransaction {
            val school = School(name = name)
            schoolDao.insertSchool(school.toEntity())
            database.schoolManagerStateChunkDao().upsertChunks(managerStateChunks(school))
            settingsDataStore.setSchoolId(school.id)
            school
        }
    }

    override suspend fun createNewSchool(
        name: String,
        principalName: String,
        tierKey: String,
        ownershipKey: String
    ): School = withSchoolLock {
        inWriteTransaction {
            val tier = com.arktools.xiao.domain.model.SchoolTier.fromKey(tierKey)
            val school = School(
                name = name,
                principalName = principalName,
                tierKey = tier.key,
                ownershipKey = com.arktools.xiao.domain.model.SchoolOwnership.fromKey(ownershipKey).key,
                cash = tier.startCash,
                reputation = GameBalanceConfig.INITIAL_REPUTATION,
                campusLevel = GameBalanceConfig.INITIAL_CAMPUS_LEVEL,
                maxTeachers = GameBalanceConfig.INITIAL_MAX_TEACHERS,
                foundedYear = GameBalanceConfig.STARTING_YEAR,
                currentYear = GameBalanceConfig.STARTING_YEAR,
                facilities = mutableListOf(
                    Facility(type = FacilityType.CLASSROOM, level = 1, condition = 100f)
                )
            )
            database.schoolManagerStateDao().deleteAll()
            database.schoolManagerStateChunkDao().deleteAll()
            schoolDao.replaceWithNewSchool(school.toEntity())
            database.schoolManagerStateChunkDao().upsertChunks(managerStateChunks(school))
            settingsDataStore.setSchoolId(school.id)
            school
        }
    }

    override suspend fun updateSchool(school: School) {
        withSchoolLock {
            inWriteTransaction {
                val currentEntity = schoolDao.getSchoolCore()
                if (currentEntity == null || currentEntity.id != school.id) {
                    android.util.Log.e("SchoolRepo", "Rejected whole-row update for missing/different school ${school.id}")
                    return@inWriteTransaction
                }
                if (school.lastSaveTime < currentEntity.lastSaveTime) {
                    android.util.Log.e(
                        "SchoolRepo",
                        "Rejected stale whole-row update for ${school.id}: " +
                            "snapshot=${school.lastSaveTime}, current=${currentEntity.lastSaveTime}"
                    )
                    return@inWriteTransaction
                }
                persistSchool(school)
            }
        }
    }

    override suspend fun advanceDay() {
        withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) } ?: return@inWriteTransaction
                school.currentDay++
                val maxDays = getDaysInMonth(school.currentMonth, school.currentYear)
                if (school.currentDay > maxDays) {
                    school.currentDay = 1
                    school.currentMonth++
                    if (school.currentMonth > 12) {
                        school.currentMonth = 1
                        school.currentYear++
                    }
                }
                persistSchool(school)
            }
        }
    }

    /** 获取某月的实际天数 */
    private fun getDaysInMonth(month: Int, year: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            else -> 30
        }
    }

    override suspend fun addCash(amount: Double) {
        withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) } ?: return@inWriteTransaction
                school.cash += amount
                persistSchool(school)
            }
        }
    }

    override suspend fun addReputation(amount: Long) {
        withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) } ?: return@inWriteTransaction
                school.reputation += amount
                persistSchool(school)
            }
        }
    }

    override suspend fun deductCash(amount: Double) {
        if (!amount.isFinite() || amount < 0.0) {
            android.util.Log.e("SchoolRepo", "Rejected invalid cash deduction: $amount")
            return
        }
        withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) } ?: return@inWriteTransaction
                school.cash = (school.cash - amount).coerceAtLeast(-100.0)
                persistSchool(school)
            }
        }
    }

    override suspend fun deductReputation(amount: Long) {
        withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) } ?: return@inWriteTransaction
                school.reputation = (school.reputation - amount).coerceAtLeast(0)
                persistSchool(school)
            }
        }
    }

    override suspend fun commitStudentWithdrawal(
        studentId: String,
        dropYear: Int,
        dropMonth: Int,
        refundAmount: Double,
        reputationPenalty: Long
    ): Boolean {
        if (
            studentId.isBlank() || dropYear <= 0 || dropMonth !in 1..12 ||
            !refundAmount.isFinite() || refundAmount < 0.0 ||
            reputationPenalty < 0L
        ) {
            return false
        }
        return withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) }
                    ?: return@inWriteTransaction false
                if (
                    school.currentYear != dropYear ||
                    school.currentMonth != dropMonth
                ) {
                    return@inWriteTransaction false
                }
                if (
                    database.studentDao().markDroppedIfActive(
                        schoolId = school.id,
                        studentId = studentId,
                        dropYear = dropYear,
                        dropMonth = dropMonth
                    ) != 1
                ) {
                    return@inWriteTransaction false
                }
                school.cash = (school.cash - refundAmount)
                    .coerceAtLeast(-100.0)
                school.reputation = (school.reputation - reputationPenalty)
                    .coerceAtLeast(0L)
                persistSchool(school)
                true
            }
        }
    }

    override suspend fun upgradeCampus() {
        withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) } ?: return@inWriteTransaction
                if (school.campusLevel >= GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                    return@inWriteTransaction
                }
                val req = GameBalanceConfig.getUpgradeRequirements(school.campusLevel + 1)
                if (school.cash < req.cashCost || school.reputation < req.minReputation) {
                    return@inWriteTransaction
                }
                val yearsAtLevel = school.currentYear - school.levelUpYear
                if (yearsAtLevel < req.minYearsAtCurrentLevel) {
                    return@inWriteTransaction
                }
                school.cash -= req.cashCost
                school.campusLevel++
                school.levelUpYear = school.currentYear
                school.maxTeachers = GameBalanceConfig.getMaxTeachersForLevel(school.campusLevel)
                val maxFacilities = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel)
                if (school.facilities.size < maxFacilities) {
                    school.facilities.add(
                        Facility(type = FacilityType.CLASSROOM, level = 1, condition = 100f)
                    )
                }
                persistSchool(school)
            }
        }
    }

    override suspend fun deleteAll() {
        withSchoolLock {
            inWriteTransaction {
                database.schoolManagerStateDao().deleteAll()
                database.schoolManagerStateChunkDao().deleteAll()
                schoolDao.clearGameTables()
            }
            settingsDataStore.clearSchoolId()
        }
    }

    override suspend fun mutateSchool(block: (School) -> Boolean): School? {
        return withSchoolLock {
            inWriteTransaction {
                val school = schoolDao.getSchoolCore()?.let { loadSchool(it) }
                    ?: return@inWriteTransaction null
                if (block(school)) {
                    persistSchool(school)
                    school
                } else {
                    null
                }
            }
        }
    }

    private suspend fun persistSchool(school: School) {
        markUpdated(school, school.lastSaveTime)
        val chunkDao = database.schoolManagerStateChunkDao()
        chunkDao.deleteBySchoolId(school.id)
        chunkDao.upsertChunks(managerStateChunks(school))
        schoolDao.updateSchool(school.toEntity())
    }

    private suspend fun loadSchool(core: SchoolCoreEntity): School {
        val states = database.schoolManagerStateChunkDao().getChunks(core.id)
            .groupBy { it.stateKey }
            .mapValues { (_, chunks) ->
                chunks.sortedBy { it.chunkIndex }.joinToString("") { it.payload }
            }
        return core.toDomain(states)
    }

    private fun managerStateChunks(school: School): List<SchoolManagerStateChunkEntity> {
        val now = school.lastSaveTime
        return managerStates(school).flatMap { state ->
            state.payload.chunked(MANAGER_STATE_CHUNK_SIZE)
                .ifEmpty { listOf("") }
                .mapIndexed { index, chunk ->
                    SchoolManagerStateChunkEntity(
                        schoolId = state.schoolId,
                        stateKey = state.stateKey,
                        chunkIndex = index,
                        payload = chunk,
                        updatedAt = now
                    )
                }
        }
    }

    private fun managerStates(school: School): List<SchoolManagerStateEntity> {
        val now = school.lastSaveTime
        return listOf(
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.FACILITIES, Json.encodeToString(ArrayList(school.facilities)), now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.STUDENT_LIFE, school.studentLifeJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.COMMISSION, school.commissionJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.MARKETING, Json.encodeToString(ArrayList(school.marketingCampaigns)), now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.STOCK_INVESTMENTS, Json.encodeToString(ArrayList(school.stockInvestments)), now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.REPUTATION, school.reputationJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.ACHIEVEMENT, school.achievementJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.MILESTONE, school.milestoneJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.TEACHER_DEV, school.teacherDevJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.CLUB, school.clubJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.SCHOLARSHIP, school.scholarshipJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.EXPANSION, school.expansionJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.GOVERNMENT, school.governmentJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.PARENT, school.parentJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.POLICY, school.policyJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.SEASONAL, school.seasonalJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.CONFERENCE, school.conferenceJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.CLUB_ACTIVITY, school.clubActivityJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.TIMETABLE, school.timetableJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.EXAM, school.examJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.TEACHING_CONFIG, school.teachingConfigJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.STATISTICS, school.statisticsJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.FINANCIAL_REPORT, school.financialReportJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.PRESSURE, school.pressureJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.COMPETITOR, school.competitorJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.CRISIS, school.crisisJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.ALUMNI, school.alumniJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.EMPLOYMENT, school.employmentJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.HEAD_TEACHER_MAP, school.headTeacherMapJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.CLASS_TIER_MAP, school.classTierMapJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.SUGGESTION_BOX, school.suggestionBoxJson, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.TIER_KEY, school.tierKey, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.OWNERSHIP_KEY, school.ownershipKey, now),
            SchoolManagerStateEntity(school.id, SchoolManagerStateKeys.PROMOTION_HISTORY, school.promotionHistoryJson, now)
        )
    }

    private fun markUpdated(school: School, previousRevision: Long) {
        school.lastSaveTime = maxOf(System.currentTimeMillis(), previousRevision + 1L)
    }

    private fun SchoolCoreEntity.toDomain(states: Map<String, String>): School {
        fun state(key: String): String = states[key].orEmpty()
        return School(
            id = id,
            name = name,
            principalName = principalName,
            tierKey = state(SchoolManagerStateKeys.TIER_KEY).ifEmpty {
                com.arktools.xiao.domain.model.SchoolTier.APPLIED.key
            },
            ownershipKey = state(SchoolManagerStateKeys.OWNERSHIP_KEY).ifEmpty {
                com.arktools.xiao.domain.model.SchoolOwnership.PRIVATE.key
            },
            promotionHistoryJson = state(SchoolManagerStateKeys.PROMOTION_HISTORY),
            cash = cash,
            marketCap = marketCap,
            reputation = reputation,
            starRating = starRating,
            foundedYear = foundedYear,
            currentYear = currentYear,
            currentMonth = currentMonth,
            currentDay = currentDay,
            campusLevel = campusLevel,
            levelUpYear = levelUpYear,
            maxTeachers = maxTeachers,
            branchSchools = branchSchools,
            hasOwnTextbook = hasOwnTextbook,
            hasOwnTech = hasOwnTech,
            totalCoursesReleased = totalCoursesReleased,
            totalRevenue = totalRevenue,
            wasNearBankrupt = wasNearBankrupt,
            principalJson = principalJson,
            facilities = try {
                Json.decodeFromString<List<Facility>>(state(SchoolManagerStateKeys.FACILITIES)).toMutableList()
            } catch (_: Exception) { mutableListOf() },
            studentLifeJson = state(SchoolManagerStateKeys.STUDENT_LIFE),
            commissionJson = state(SchoolManagerStateKeys.COMMISSION),
            marketingCampaigns = try {
                Json.decodeFromString<List<MarketingCampaign>>(state(SchoolManagerStateKeys.MARKETING)).toMutableList()
            } catch (_: Exception) { mutableListOf() },
            stockInvestments = try {
                Json.decodeFromString(state(SchoolManagerStateKeys.STOCK_INVESTMENTS))
            } catch (_: Exception) { emptyList() },
            reputationJson = state(SchoolManagerStateKeys.REPUTATION),
            achievementJson = state(SchoolManagerStateKeys.ACHIEVEMENT),
            milestoneJson = state(SchoolManagerStateKeys.MILESTONE),
            teacherDevJson = state(SchoolManagerStateKeys.TEACHER_DEV),
            clubJson = state(SchoolManagerStateKeys.CLUB),
            scholarshipJson = state(SchoolManagerStateKeys.SCHOLARSHIP),
            expansionJson = state(SchoolManagerStateKeys.EXPANSION),
            governmentJson = state(SchoolManagerStateKeys.GOVERNMENT),
            parentJson = state(SchoolManagerStateKeys.PARENT),
            policyJson = state(SchoolManagerStateKeys.POLICY),
            seasonalJson = state(SchoolManagerStateKeys.SEASONAL),
            conferenceJson = state(SchoolManagerStateKeys.CONFERENCE),
            clubActivityJson = state(SchoolManagerStateKeys.CLUB_ACTIVITY),
            timetableJson = state(SchoolManagerStateKeys.TIMETABLE),
            examJson = state(SchoolManagerStateKeys.EXAM),
            teachingConfigJson = state(SchoolManagerStateKeys.TEACHING_CONFIG),
            statisticsJson = state(SchoolManagerStateKeys.STATISTICS),
            financialReportJson = state(SchoolManagerStateKeys.FINANCIAL_REPORT),
            pressureJson = state(SchoolManagerStateKeys.PRESSURE),
            competitorJson = state(SchoolManagerStateKeys.COMPETITOR),
            crisisJson = state(SchoolManagerStateKeys.CRISIS),
            alumniJson = state(SchoolManagerStateKeys.ALUMNI),
            employmentJson = state(SchoolManagerStateKeys.EMPLOYMENT),
            headTeacherMapJson = state(SchoolManagerStateKeys.HEAD_TEACHER_MAP),
            classTierMapJson = state(SchoolManagerStateKeys.CLASS_TIER_MAP),
            suggestionBoxJson = state(SchoolManagerStateKeys.SUGGESTION_BOX),
            lastYearEndProcessingYear = lastYearEndProcessingYear,
            lastMonthlySettlementYear = lastMonthlySettlementYear,
            lastMonthlySettlementMonth = lastMonthlySettlementMonth,
            lastSaveTime = lastSaveTime
        )
    }

    private fun School.toEntity(): SchoolEntity {
        return SchoolEntity(
            id = id,
            name = name,
            principalName = principalName,
            cash = cash,
            marketCap = marketCap,
            reputation = reputation,
            starRating = starRating,
            foundedYear = foundedYear,
            currentYear = currentYear,
            currentMonth = currentMonth,
            currentDay = currentDay,
            campusLevel = campusLevel,
            levelUpYear = levelUpYear,
            maxTeachers = maxTeachers,
            branchSchools = branchSchools,
            hasOwnTextbook = hasOwnTextbook,
            hasOwnTech = hasOwnTech,
            totalCoursesReleased = totalCoursesReleased,
            totalRevenue = totalRevenue,
            wasNearBankrupt = wasNearBankrupt,
            facilitiesJson = "",
            studentLifeJson = "",
            marketingCampaignsJson = "",
            stockInvestmentsJson = "",
            reputationJson = "",
            achievementJson = "",
            milestoneJson = "",
            teacherDevJson = "",
            clubJson = "",
            scholarshipJson = "",
            expansionJson = "",
            governmentJson = "",
            parentJson = "",
            policyJson = "",
            seasonalJson = "",
            conferenceJson = "",
            clubActivityJson = "",
            timetableJson = "",
            examJson = "",
            teachingConfigJson = "",
            statisticsJson = "",
            financialReportJson = "",
            pressureJson = "",
            competitorJson = "",
            crisisJson = "",
            alumniJson = "",
            employmentJson = "",
            headTeacherMapJson = "",
            classTierMapJson = "",
            principalJson = principalJson,
            suggestionBoxJson = "",
            lastYearEndProcessingYear = lastYearEndProcessingYear,
            lastMonthlySettlementYear = lastMonthlySettlementYear,
            lastMonthlySettlementMonth = lastMonthlySettlementMonth,
            lastSaveTime = lastSaveTime
        )
    }
}
