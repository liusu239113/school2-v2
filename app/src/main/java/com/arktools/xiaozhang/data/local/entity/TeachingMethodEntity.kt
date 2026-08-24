package com.arktools.xiaozhang.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teaching_methods")
data class TeachingMethodEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val unlockYear: Int,
    val cost: Double,
    val researchDays: Int,
    val bonusType: String,
    val bonusValue: Float,
    val prerequisiteIdsJson: String,
    val isUnlocked: Boolean,
    val isResearching: Boolean = false,
    val remainingResearchDays: Int = 0,
    val schoolId: String
)

val defaultTeachingMethods = mapOf(
    "多媒体教学" to "投影仪、幻灯片、视频等多媒体手段辅助教学",
    "互动式教学" to "师生互动、小组讨论，提升课堂参与度",
    "项目式学习" to "以项目为驱动，学生在实践中学习",
    "翻转课堂" to "课前自学、课堂讨论，颠覆传统模式",
    "游戏化教学" to "将游戏元素融入教学，提升学习兴趣",
    "分层教学" to "根据学生水平分层授课，因材施教",
    "微课教学" to "5-10分钟短视频，聚焦单一知识点",
    "探究式学习" to "提出问题，引导学生自主探索答案",
    "合作学习" to "小组协作完成任务，培养团队能力",
    "情境教学" to "创设真实情境，激发学习动机",
    "思维导图教学" to "用思维导图梳理知识结构",
    "案例教学法" to "通过实际案例分析，培养应用能力",
    "AI辅助教学" to "利用人工智能个性化辅导学生",
    "VR沉浸式教学" to "虚拟现实技术，身临其境的学习体验",
    "跨学科融合" to "打破学科壁垒，综合培养",
    "导师制教学" to "一对一导师指导，个性化发展",
    "实验教学法" to "动手实验，在实践中验证理论",
    "辩论式教学" to "通过辩论培养批判性思维",
    "体验式学习" to "亲身参与体验，从做中学",
    "任务驱动教学" to "以任务为导向，在完成任务中学习",
    "脚手架教学" to "提供学习支架，逐步撤除引导",
    "差异化教学" to "针对不同学生设计不同学习路径",
    "同伴教学法" to "学生互相教学，共同进步",
    "反思性教学" to "引导反思学习过程，总结经验",
    "故事化教学" to "用故事包装知识点，增强记忆",
    "竞赛式教学" to "通过竞赛激发学习积极性",
    "混合式教学" to "线上线下相结合，灵活学习",
    "社会化学习" to "利用社交媒体促进学习交流",
    "自适应学习" to "根据学习数据自动调整难度",
    "户外实践教学" to "走出教室，在真实环境中学习",
    "戏剧教学法" to "通过角色扮演和戏剧表演学习",
    "问题导向学习" to "以真实问题为起点，驱动学习",
    "模拟仿真教学" to "计算机模拟真实场景进行训练",
    "正念教学法" to "融入正念练习，提升专注力",
    "创业教育法" to "培养创新思维和创业能力"
)
