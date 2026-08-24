package com.arktools.xiaozhang.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.competitor.CompetitorEngine
import com.arktools.xiaozhang.domain.competitor.CompetitorPersonality
import com.arktools.xiaozhang.domain.competitor.CompetitorStrategy
import com.arktools.xiaozhang.domain.competitor.RankingEntry
import com.arktools.xiaozhang.domain.model.BonusType
import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.repository.ResearchRepository
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val studentRepository: StudentRepository,
    private val competitorEngine: CompetitorEngine,
    private val researchRepository: ResearchRepository
) : ViewModel() {

    private val _currentSchool = MutableStateFlow<School?>(null)
    val currentSchool: StateFlow<School?> = _currentSchool.asStateFlow()

    private val _rankings = MutableStateFlow<List<RankingItem>>(emptyList())
    val rankings: StateFlow<List<RankingItem>> = _rankings.asStateFlow()

    private val _playerRank = MutableStateFlow(1)
    val playerRank: StateFlow<Int> = _playerRank.asStateFlow()

    private val _stockPrice = MutableStateFlow(100.0)
    val stockPrice: StateFlow<Double> = _stockPrice.asStateFlow()

    private val _peRatio = MutableStateFlow(0.0)
    val peRatio: StateFlow<Double> = _peRatio.asStateFlow()

    private val _priceChange = MutableStateFlow(0.0)
    val priceChange: StateFlow<Double> = _priceChange.asStateFlow()

    // 竞争力分析：展示教研加成对排名的贡献
    private val _competitiveEdge = MutableStateFlow(CompetitiveEdge())
    val competitiveEdge: StateFlow<CompetitiveEdge> = _competitiveEdge.asStateFlow()

    private var lastStockPrice = 100.0

    init {
        loadSchool()
        loadCompetitiveEdge()
    }

    private fun loadCompetitiveEdge() {
        viewModelScope.safeLaunch {
            val teachingBonus = researchRepository.getUnlockedBonusByType(BonusType.TEACHING_QUALITY)
            val enrollmentBonus = researchRepository.getUnlockedBonusByType(BonusType.ENROLLMENT)
            val revenueBonus = researchRepository.getUnlockedBonusByType(BonusType.REVENUE)
            val costBonus = researchRepository.getUnlockedBonusByType(BonusType.COST_REDUCTION)
            val unlockedCount = researchRepository.getUnlockedMethods().size
            val totalCount = researchRepository.getMethods().size

            _competitiveEdge.value = CompetitiveEdge(
                teachingQualityBonus = teachingBonus,
                enrollmentBonus = enrollmentBonus,
                revenueBonus = revenueBonus,
                costReductionBonus = costBonus,
                researchProgress = if (totalCount > 0) unlockedCount.toFloat() / totalCount else 0f,
                unlockedCount = unlockedCount,
                totalCount = totalCount
            )
        }
    }

    private fun loadSchool() {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect {
                _currentSchool.value = it
                it?.let { school ->
                    val newPrice = calculateStockPrice(school)
                    _priceChange.value = if (lastStockPrice > 0) {
                        ((newPrice - lastStockPrice) / lastStockPrice) * 100.0
                    } else 0.0
                    lastStockPrice = _stockPrice.value
                    _stockPrice.value = newPrice
                    _peRatio.value = calculatePERatio(school, newPrice)
                    generateRankings(school)
                }
            }
        }
    }

    private fun calculateStockPrice(school: School): Double {
        val basePrice = 10.0
        val reputationFactor = school.reputation / 100.0
        val revenueFactor = school.totalRevenue / 50.0
        val campusFactor = school.campusLevel * 5.0
        return (basePrice + reputationFactor + revenueFactor + campusFactor)
            .coerceAtLeast(1.0)
    }

    private fun calculatePERatio(school: School, stockPrice: Double): Double {
        val monthlyRevenue = school.totalRevenue / ((school.currentYear - school.foundedYear).coerceAtLeast(1) * 12.0)
        return if (monthlyRevenue > 0) {
            stockPrice / monthlyRevenue
        } else {
            0.0
        }
    }

    /**
     * 使用 CompetitorEngine 动态生成排行榜
     */
    private suspend fun generateRankings(school: School) {
        val activeStudentCount = studentRepository.getActiveStudentCount()
        val entries = competitorEngine.getRankings(school)

        val items = entries.mapIndexed { index, entry ->
            RankingItem(
                rank = index + 1,
                name = entry.name,
                reputation = entry.reputation,
                studentCount = if (entry.isPlayer) activeStudentCount else entry.studentCount,
                starRating = entry.starRating,
                isPlayer = entry.isPlayer,
                strategy = entry.strategy
            )
        }

        _rankings.value = items
        _playerRank.value = items.indexOfFirst { it.isPlayer } + 1
    }

    private val _selectedCompetitor = MutableStateFlow<CompetitorDetail?>(null)
    val selectedCompetitor: StateFlow<CompetitorDetail?> = _selectedCompetitor.asStateFlow()

    fun selectCompetitor(item: RankingItem) {
        if (item.isPlayer) return
        // Find matching competitor to get full details
        val competitor = competitorEngine.competitorState.value.find { it.name == item.name }
        if (competitor != null) {
            _selectedCompetitor.value = CompetitorDetail(
                name = competitor.name,
                motto = competitor.motto,
                strategy = competitor.strategy,
                personality = competitor.personality,
                reputation = competitor.reputation,
                studentCount = competitor.studentCount,
                courseCount = competitor.courseCount,
                teacherCount = competitor.teacherCount,
                campusLevel = competitor.campusLevel,
                starRating = competitor.starRating,
                morale = competitor.morale,
                rank = item.rank
            )
        }
    }

    fun dismissCompetitorDetail() {
        _selectedCompetitor.value = null
    }

    data class RankingItem(
        val rank: Int,
        val name: String,
        val reputation: Long,
        val studentCount: Int = 0,
        val starRating: Float = 0f,
        val isPlayer: Boolean = false,
        val strategy: CompetitorStrategy? = null
    )

    data class CompetitorDetail(
        val name: String,
        val motto: String,
        val strategy: CompetitorStrategy,
        val personality: CompetitorPersonality,
        val reputation: Long,
        val studentCount: Int,
        val courseCount: Int,
        val teacherCount: Int,
        val campusLevel: Int,
        val starRating: Float,
        val morale: Float,
        val rank: Int
    )

    data class CompetitiveEdge(
        val teachingQualityBonus: Float = 0f,
        val enrollmentBonus: Float = 0f,
        val revenueBonus: Float = 0f,
        val costReductionBonus: Float = 0f,
        val researchProgress: Float = 0f,
        val unlockedCount: Int = 0,
        val totalCount: Int = 0
    )
}
