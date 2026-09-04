package com.arktools.xiao.ui.governance

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.policy.AnnualGoal
import com.arktools.xiao.domain.policy.BudgetLine
import com.arktools.xiao.domain.policy.UniversityStrategy
import com.arktools.xiao.ui.policy.PolicyViewModel
import com.arktools.xiao.ui.theme.PrimaryDark

/**
 * 治院：学期预算（教学/科研/校园生活/社会合作 10 点）、年度目标、办学方针，
 * 以及一级管理入口（跳转对应管理页）。全部实底深浅二色，杜绝文字压图。
 */
@Composable
fun GovernanceScreen(
    onNavigateTo: (Int) -> Unit,
    viewModel: PolicyViewModel = hiltViewModel()
) {
    val policies by viewModel.policies.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val campusLevel by viewModel.campusLevel.collectAsState()
    val goalSnapshot by viewModel.goalSnapshot.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1724))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("治院", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "预算与方针决定这所大学往哪走；6月学年评估按此考核",
            color = Color(0xFFB8C7D6),
            fontSize = 13.sp
        )

        operationMessage?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC14648C))
                    .padding(10.dp)
                    .clickable { viewModel.consumeOperationMessage() }
            )
        }

        // ===== 学期专项预算 =====
        Panel(title = "学期专项预算（10 点）") {
            val plan = policies.budgetAllocation
            Text(
                "已分配 ${plan.teachingWeight + plan.researchWeight + plan.campusLifeWeight + plan.societyWeight}/10 · 每点每月 0.8 万",
                fontSize = 12.sp,
                color = Color(0xFF617386)
            )
            Spacer(Modifier.height(6.dp))
            BudgetLine.entries.forEach { line ->
                val value = when (line) {
                    BudgetLine.TEACHING -> plan.teachingWeight
                    BudgetLine.RESEARCH -> plan.researchWeight
                    BudgetLine.CAMPUS_LIFE -> plan.campusLifeWeight
                    BudgetLine.SOCIETY -> plan.societyWeight
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.displayName, fontSize = 13.sp, color = Color(0xFF182635))
                        Text(line.description, fontSize = 11.sp, color = Color(0xFF617386))
                    }
                    StepButton("−", enabled = value > 0) { viewModel.adjustBudget(line, -1) }
                    Text(
                        "$value",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF182635),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    StepButton("+", enabled = true) { viewModel.adjustBudget(line, 1) }
                }
            }
        }

        // ===== 年度目标 =====
        Panel(title = "年度目标（6月真考核，达标给钱和声誉）") {
            Text(
                "现在：在校 ${goalSnapshot.students} 人 · 科研 ${goalSnapshot.research} 项 · 声誉 ${goalSnapshot.reputation} · 满意度 ${goalSnapshot.satisfaction.toInt()}% · 就业 ${(goalSnapshot.employmentRate * 100).toInt()}%",
                fontSize = 12.sp,
                color = Color(0xFF14648C)
            )
            AnnualGoal.entries.forEach { goal ->
                val selected = policies.collegeDevelopment.annualGoal == goal
                val preview = goal.evaluate(
                    campusLevel = campusLevel,
                    students = goalSnapshot.students,
                    research = goalSnapshot.research,
                    reputation = goalSnapshot.reputation,
                    satisfaction = goalSnapshot.satisfaction,
                    employmentRate = goalSnapshot.employmentRate,
                    previousReputation = policies.collegeDevelopment.lastReviewReputation,
                    previousResearch = policies.collegeDevelopment.lastReviewResearch,
                    previousStudents = policies.collegeDevelopment.lastReviewStudents,
                    previousSatisfaction = policies.collegeDevelopment.lastReviewSatisfaction
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color(0xFFDCF1FB) else Color.Transparent)
                        .clickable { viewModel.setAnnualGoal(goal) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${goal.icon} ${goal.displayName}",
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = Color(0xFF182635)
                        )
                        Text(goal.requirementSummary(campusLevel), fontSize = 11.sp, color = Color(0xFF617386))
                        Text(goal.rewardSummary(campusLevel), fontSize = 11.sp, color = Color(0xFF14648C))
                        if (selected) {
                            Text(
                                if (preview.success) "按当前数据能达标" else "按当前数据还差：${preview.detail}",
                                fontSize = 11.sp,
                                color = if (preview.success) Color(0xFF2E9B78) else Color(0xFFB0413E)
                            )
                        }
                    }
                    if (selected) Text("✓", color = Color(0xFF1E96C8), fontWeight = FontWeight.Bold)
                }
            }
        }

        // ===== 办学方针 =====
        Panel(title = "办学方针") {
            UniversityStrategy.entries.forEach { strategy ->
                val selected = policies.universityStrategy == strategy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color(0xFFDCF1FB) else Color.Transparent)
                        .clickable { viewModel.setUniversityStrategy(strategy) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${strategy.icon} ${strategy.displayName}",
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = Color(0xFF182635)
                        )
                        Text(
                            strategy.effectSummary,
                            fontSize = 11.sp,
                            color = Color(0xFF617386)
                        )
                    }
                    if (selected) Text("✓", color = Color(0xFF1E96C8), fontWeight = FontWeight.Bold)
                }
            }
        }

        // ===== 一级管理入口（只保留治院真正用得上、且不与建筑点开重复的） =====
        Panel(title = "管理入口") {
            data class Entry(val label: String, val icon: Int, val target: Int)
            val entries = listOf(
                Entry("教学配置（点加号开班，决定招多少人）", com.arktools.xiao.R.drawable.ic_core_course, 40),
                Entry("科研课题", com.arktools.xiao.R.drawable.ic_research, 41),
                Entry("大学政策（学费/考试）", com.arktools.xiao.R.drawable.ic_balance, 16),
                Entry("奖助学金", com.arktools.xiao.R.drawable.ic_gift, 29),
                Entry("学期课表", com.arktools.xiao.R.drawable.ic_core_course, 31),
                Entry("考试管理", com.arktools.xiao.R.drawable.ic_memo, 32),
                Entry("办学报表", com.arktools.xiao.R.drawable.ic_chart, 11),
                Entry("成就墙（解锁给经费和声誉）", com.arktools.xiao.R.drawable.ic_trophy, 10)
            )
            entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.playUiClick()
                            onNavigateTo(entry.target)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = entry.icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(entry.label, fontSize = 14.sp, color = Color(0xFF182635), modifier = Modifier.weight(1f))
                    Text("→", color = Color(0xFF1E96C8), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14648C))
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (enabled) PrimaryDark else Color(0xFF9AA8B5))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
