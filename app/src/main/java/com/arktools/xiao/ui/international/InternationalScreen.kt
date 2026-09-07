package com.arktools.xiao.ui.international

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
import androidx.compose.foundation.layout.width
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
import com.arktools.xiao.domain.international.InternationalProgramManager

/**
 * 国际交流（Lv5 解锁）：海外合作院校、留学生培养、交换外派。
 * 是冲击校园 Lv6 的必要条件。
 */
@Composable
fun InternationalScreen(
    viewModel: InternationalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val unlocked = true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1724))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("国际交流", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "C 档邻校现在就能签。签完每年 9 月招留学生，学费进账；外派交换生一年后归国加声誉。B/A 档随校园等级开放。",
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stat("合作院校", "${state.signedIds.size} 所")
                Stat("在读留学生", "${state.intlCount} 人")
                Stat("月学费收入", String.format(java.util.Locale.CHINA, "%.1f 万", state.monthlyIncome))
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12283C))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stat("年度声誉", "+${state.annualRep}")
                Stat("外派交换生", "${state.outgoingCount} 人")
                Stat("留学生毕业", "${state.intlGraduated} 人")
            }
        }

        if (unlocked && state.signedIds.isNotEmpty()) {
            item {
                Text("合作院校", color = Color(0xFF1E96C8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            items(state.signedIds.size) { i ->
                InternationalProgramManager.byId(state.signedIds[i])?.let { def ->
                    PartnerCard(def, signed = true, canSign = false) { }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E3A5C))
                        .clickable { viewModel.dispatchOutgoing() }
                        .padding(12.dp)
                ) {
                    Text(
                        "外派交换生（2 人 / 1 年归国，每人声誉 +25）",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (unlocked) {
            item {
                Text("可签约院校", color = Color(0xFF1E96C8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            val catalog = viewModel.catalog().filter { !viewModel.signed(it.id) }
            items(catalog.size) { i ->
                val def = catalog[i]
                val minLevel = when (def.tier) {
                    "C" -> 1
                    "B" -> 2
                    else -> 4
                }
                val canSign = state.campusLevel >= minLevel &&
                    state.cash >= def.feeWan &&
                    state.reputation >= def.repRequired
                PartnerCard(
                    def = def,
                    signed = false,
                    canSign = canSign,
                    lockHint = if (state.campusLevel < minLevel) "校园 Lv.$minLevel 开放" else null
                ) { viewModel.sign(def) }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF8FA6BB), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PartnerCard(
    def: com.arktools.xiao.domain.international.PartnerDef,
    signed: Boolean,
    canSign: Boolean,
    lockHint: String? = null,
    onSign: () -> Unit
) {
    val tierColor = when (def.tier) {
        "A" -> Color(0xFFFFD54F)
        "B" -> Color(0xFF7ED8A0)
        else -> Color(0xFFB8C7D6)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12283C))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF0B2038))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(def.tier, color = tierColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(def.country, color = Color(0xFF8FA6BB), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "名额 +${def.intlQuota}/年 · 年度声誉 +${def.annualReputation} · 需声誉 ${def.repRequired}",
                color = Color(0xFFB8C7D6),
                fontSize = 11.sp
            )
            if (signed) {
                Text("已合作", color = Color(0xFF7ED8A0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Box(
                    modifier = Modifier
                        .background(if (canSign) Color(0xFF1E96C8) else Color(0xFF31465C))
                        .clickable(enabled = canSign) { onSign() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        lockHint ?: "签约 ${def.feeWan.toInt()} 万",
                        color = if (canSign) Color.White else Color(0xFF9DB0C2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
