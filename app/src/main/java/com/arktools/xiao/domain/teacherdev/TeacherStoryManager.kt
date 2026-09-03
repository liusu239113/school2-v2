package com.arktools.xiao.domain.teacherdev

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 教师个人故事线：每月为符合条件的教师触发两拍个人事件。
 * - 第一拍：ChoiceEvent（玩家抉择，后果走通用 teacherLoyaltyChange/cashChange）
 * - 第二拍：下个月的后续事件（bonusTeacherSkill / 声誉反馈）
 * - 状态内嵌 policyJson 持久化，不改数据库结构
 */
@Singleton
class TeacherStoryManager @Inject constructor() {

    enum class StoryId { BURNOUT, RISING_STAR }

    @Serializable
    data class ActiveStory(
        val teacherId: String,
        val teacherName: String,
        val storyId: String,
        val startAbsMonth: Int
    )

    @Serializable
    data class ManagerState(
        val active: List<ActiveStory> = emptyList(),
        val finishedCount: Int = 0
    )

    data class PendingStoryEvent(
        val title: String,
        val message: String,
        val choices: List<StoryChoice>,
        val isFollowUp: Boolean,
        val teacherName: String
    )

    data class StoryChoice(
        val label: String,
        val description: String,
        val cashChange: Double,
        val reputationChange: Long
    )

    private var state = ManagerState()
    private val random = Random(System.currentTimeMillis())

    fun snapshotState(): ManagerState = state

    fun toJson(): String = runCatching { Json.encodeToString(state) }.getOrDefault("")

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        runCatching { state = Json.decodeFromString<ManagerState>(json) }
    }

    fun reset() {
        state = ManagerState()
    }

    /**
     * 每月调用：
     * 1) 到期故事（startAbsMonth+1）收第二拍
     * 2) 25% 概率为一名符合条件的教师开新故事（每月至多一个）
     */
    fun monthlyTick(
        year: Int,
        month: Int,
        teachers: List<TeacherSnapshot>
    ): List<PendingStoryEvent> {
        val absMonth = year * 12 + month
        val events = mutableListOf<PendingStoryEvent>()

        // 1. 第二拍收尾
        val due = state.active.filter { absMonth > it.startAbsMonth }
        due.forEach { story ->
            events.add(followUpEvent(story))
        }
        if (due.isNotEmpty()) {
            state = state.copy(
                active = state.active - due.toSet(),
                finishedCount = state.finishedCount + due.size
            )
        }

        // 2. 开新故事
        if (state.active.isEmpty() && teachers.isNotEmpty() && random.nextFloat() < 0.25f) {
            val eligible = teachers.filter { teacher ->
                state.active.none { it.teacherId == teacher.id }
            }
            val burnout = eligible.filter { it.fatigue > 70 || it.loyalty < 50 }
            val rising = eligible.filter { it.averageSkill >= 65 && it.loyalty >= 70 }
            val picked = when {
                burnout.isNotEmpty() && random.nextBoolean() -> burnout.random() to StoryId.BURNOUT
                rising.isNotEmpty() -> rising.random() to StoryId.RISING_STAR
                burnout.isNotEmpty() -> burnout.random() to StoryId.BURNOUT
                else -> null
            }
            picked?.let { (teacher, storyId) ->
                state = state.copy(
                    active = state.active + ActiveStory(
                        teacherId = teacher.id,
                        teacherName = teacher.name,
                        storyId = storyId.name,
                        startAbsMonth = absMonth
                    )
                )
                events.add(openingEvent(teacher, storyId))
            }
        }
        return events
    }

    private fun openingEvent(teacher: TeacherSnapshot, storyId: StoryId): PendingStoryEvent {
        return when (storyId) {
            StoryId.BURNOUT -> PendingStoryEvent(
                title = "教师故事·燃灯者",
                message = "资深教师${teacher.name}最近连续熬夜备课，疲劳度${teacher.fatigue.toInt()}%，情绪明显低落。办公室里流传着他想歇一段的传言。",
                choices = listOf(
                    StoryChoice(
                        "安排调休与谈心",
                        "花2万调整课表并安排心理疏导，全校看到学校重视老师",
                        -2.0, +10
                    ),
                    StoryChoice(
                        "按教学制度催课",
                        "强调教学纪律，不加预算，外界观感一般",
                        0.0, -5
                    )
                ),
                isFollowUp = false,
                teacherName = teacher.name
            )
            StoryId.RISING_STAR -> PendingStoryEvent(
                title = "教师故事·青年才俊",
                message = "青年教师${teacher.name}的教学评分已达${teacher.averageSkill.toInt()}，校内口碑很好。有企业开价高薪挖人，他也在犹豫要不要读博深造。",
                choices = listOf(
                    StoryChoice(
                        "资助他读博深造",
                        "提供15万培养经费，绑定长期合约，学校惜才的名声传开",
                        -15.0, +15
                    ),
                    StoryChoice(
                        "压担子多带课",
                        "让他多承担课时，暂缓深造，先解决眼前师资紧张",
                        0.0, -5
                    )
                ),
                isFollowUp = false,
                teacherName = teacher.name
            )
        }
    }

    private fun followUpEvent(story: ActiveStory): PendingStoryEvent {
        return when (StoryId.valueOf(story.storyId)) {
            StoryId.BURNOUT -> PendingStoryEvent(
                title = "教师故事·走出低谷",
                message = "${story.teacherName}的状态回暖了，主动申请了一门公开示范课。这次经历让团队更信任学校的管理。",
                choices = emptyList(),
                isFollowUp = true,
                teacherName = story.teacherName
            )
            StoryId.RISING_STAR -> PendingStoryEvent(
                title = "教师故事·崭露头角",
                message = "${story.teacherName}把这段经历转化成了教学改革的动力，在教研室分享了一套新教法，带动了整组的教学水平。",
                choices = emptyList(),
                isFollowUp = true,
                teacherName = story.teacherName
            )
        }
    }

    /** 教师快照：由引擎从 Teacher 列表映射 */
    data class TeacherSnapshot(
        val id: String,
        val name: String,
        val fatigue: Float,
        val loyalty: Int,
        val averageSkill: Float
    )
}
