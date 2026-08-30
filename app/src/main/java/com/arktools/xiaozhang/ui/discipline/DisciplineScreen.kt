package com.arktools.xiaozhang.ui.discipline

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.model.CollegeType
import com.arktools.xiaozhang.domain.model.DisciplineCatalog

/**
 * 学科建设：建设 → 两年评估定级 → 生源/声誉/财政反哺。
 * 深底白字胶囊风格，与外联/治院页一致。
 */
@Composable
fun DisciplineScreen(
    viewModel: DisciplineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1724))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("学科建设", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "投钱建设学科 → 每两年 6 月评估定级（D~A+）→ 评级提高生源质量、声誉并发放财政奖励",
                color = Color(0xFFB8C7D6),
                fontSize = 13.sp
            )
        }

        state.message?.let { msg ->
            item {
                Text(
                    msg,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC14648C))
                        .padding(10.dp)
                        .clickable { viewModel.consumeMessage() }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12283C))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("下次学科评估", color = Color(0xFFB8C7D6), fontSize = 12.sp)
                    Text(
                        "${state.nextEvalYear} 年 6 月 · 偶数年自动评估",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "经费 ${state.cash.toInt()} 万",
                    color = Color(0xFFFFD54F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CollegeType.entries.forEach { college ->
            val collegeRows = state.rows.filter { it.def.college == college }
            item {
                Text(
                    college.displayName,
                    color = Color(0xFF1E96C8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(collegeRows.size) { index ->
                DisciplineRow(collegeRows[index]) { viewModel.invest(it) }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DisciplineRow(
    row: DisciplineViewModel.Row,
    onInvest: (String) -> Unit
) {
    val st = row.state
    val ratingColor = when (st.lastRating) {
        "A+" -> Color(0xFFFFD54F)
        "A" -> Color(0xFF7ED8A0)
        "B" -> Color(0xFF1E96C8)
        "C" -> Color(0xFFB8C7D6)
        "D" -> Color(0xFFE08A8A)
        else -> Color(0xFF5B7186)
    }
    val locked = !row.collegeFounded || row.levelLocked
    val canBuild = !locked && !row.maxed && row.affordable

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12283C))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.def.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(row.def.desc, color = Color(0xFF8FA6BB), fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF0B2038))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    if (st.lastRating == "NONE") "未评估" else "${st.lastRating} · ${st.lastEvalYear}",
                    color = ratingColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    DisciplineCatalog.bonusLabel(st) + " · 累计投入 ${st.investWan.toInt()} 万",
                    color = Color(0xFFB8C7D6),
                    fontSize = 11.sp
                )
                when {
                    locked && !row.collegeFounded ->
                        Text("需先成立${row.def.college.displayName}", color = Color(0xFFE08A8A), fontSize = 11.sp)
                    locked && row.levelLocked ->
                        Text("校园 Lv.${row.def.college.unlockLevel} 解锁", color = Color(0xFFE08A8A), fontSize = 11.sp)
                    row.maxed ->
                        Text("建设已满级", color = Color(0xFF7ED8A0), fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .background(if (canBuild) Color(0xFF1E96C8) else Color(0xFF31465C))
                    .clickable(enabled = canBuild) { onInvest(row.def.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    when {
                        row.maxed -> "满级"
                        locked -> "未解锁"
                        !row.affordable -> "钱不够"
                        else -> "投入 ${row.nextCostWan.toInt()} 万"
                    },
                    color = if (canBuild) Color.White else Color(0xFF9DB0C2),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
