package com.arktools.xiaozhang.ui.governance

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
import com.arktools.xiaozhang.domain.policy.AnnualGoal
import com.arktools.xiaozhang.domain.policy.BudgetLine
import com.arktools.xiaozhang.domain.policy.UniversityStrategy
import com.arktools.xiaozhang.ui.policy.PolicyViewModel
import com.arktools.xiaozhang.ui.theme.PrimaryDark

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
                    Text(line.displayName, fontSize = 13.sp, color = Color(0xFF182635), modifier = Modifier.weight(1f))
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
        Panel(title = "年度目标（6阶段考核核）") {
            AnnualGoal.entries.forEach { goal ->
                val selected = policies.collegeDevelopment.annualGoal == goal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color(0xFFDCF1FB) else Color.Transparent)
                        .clickable { viewModel.setAnnualGoal(goal) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${goal.icon} ${goal.displayName}",
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = Color(0xFF182635),
                        modifier = Modifier.weight(1f)
                    )
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
                    Text(
                        "${strategy.icon} ${strategy.displayName}",
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = Color(0xFF182635),
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) Text("✓", color = Color(0xFF1E96C8), fontWeight = FontWeight.Bold)
                }
            }
        }

        // ===== 一级管理入口 =====
        Panel(title = "管理入口") {
            data class Entry(val label: String, val icon: Int, val target: Int)
            val entries = listOf(
                Entry("教学配置（教学班容量/强度）", com.arktools.xiaozhang.R.drawable.ic_core_course, 40),
                Entry("科研研究", com.arktools.xiaozhang.R.drawable.ic_research, 41),
                Entry("学生生活（宿舍/食堂）", com.arktools.xiaozhang.R.drawable.ic_food, 21),
                Entry("学生社团", com.arktools.xiaozhang.R.drawable.ic_people, 17),
                Entry("奖助学金", com.arktools.xiaozhang.R.drawable.ic_gift, 29),
                Entry("学术会议", com.arktools.xiaozhang.R.drawable.ic_memo, 23),
                Entry("校友与就业", com.arktools.xiaozhang.R.drawable.ic_briefcase, 15),
                Entry("办学报表", com.arktools.xiaozhang.R.drawable.ic_chart, 11),
                Entry("大学政策（学费/考试等）", com.arktools.xiaozhang.R.drawable.ic_balance, 16),
                Entry("学科建设（评估定级）", com.arktools.xiaozhang.R.drawable.ic_graduate_program, 45),
                Entry("研究生院（硕博培养）", com.arktools.xiaozhang.R.drawable.ic_research_chain, 46)
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
