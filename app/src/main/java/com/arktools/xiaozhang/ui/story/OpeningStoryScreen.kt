package com.arktools.xiaozhang.ui.story

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arktools.xiaozhang.R

/**
 * 开场剧情漫画：仅新存档首次进入展示，看完/跳过写入进度后不再出现。
 * 10 页 × 6 格像素漫画版式，台词使用玩家创建的校长名与校名。
 */
@Composable
fun OpeningStoryScreen(
    principalName: String,
    schoolName: String,
    onDone: () -> Unit
) {
    data class Panel(val res: Int, val speaker: String, val text: String)
    val p = principalName.ifBlank { "校长" }
    val s = schoolName.ifBlank { "新大学" }
    val panels = remember(p, s) {
        listOf(
            Panel(R.drawable.opening_p1, p, "终于把办学资质提交上去了！"),
            Panel(R.drawable.opening_p1, "系统弹窗", "恭喜，办学申请审核通过，准予办学！"),
            Panel(R.drawable.opening_p1, "$p（内心）", "……说好的教学楼呢？"),
            Panel(R.drawable.opening_p1, "货车喇叭", "物资已送达，请校长自行开荒！"),
            Panel(R.drawable.opening_p1, p, "合着就给我一块地？"),
            Panel(R.drawable.opening_p1, "旁白", "你的大学创业噩梦，就此启程。"),
            Panel(R.drawable.opening_p2, p, "我要打造梦中的神仙大学！"),
            Panel(R.drawable.opening_p2, "幻想泡泡", "食堂菜品琳琅满目，宿舍全是四人间！"),
            Panel(R.drawable.opening_p2, "钱包", "余额：接近于无。"),
            Panel(R.drawable.opening_p2, "新闻推送", "当代大学生，脆皮等级持续刷新。"),
            Panel(R.drawable.opening_p2, p, "现在的学生这么难伺候？"),
            Panel(R.drawable.opening_p2, "旁白", "第一批新生，已经在路上了。"),
            Panel(R.drawable.opening_p3, "学生A", "咱们大学就这？"),
            Panel(R.drawable.opening_p3, "学生B", "宣传图的高楼大厦呢？诈骗是吧！"),
            Panel(R.drawable.opening_p3, "学生C", "我要去论坛挂学校！"),
            Panel(R.drawable.opening_p3, p, "诸位同学先别发帖！万事好商量！"),
            Panel(R.drawable.opening_p3, "学生", "校长，宿舍有空调吗？"),
            Panel(R.drawable.opening_p3, p, "空调……未来规划图纸上有！"),
            Panel(R.drawable.opening_p4, "学生们", "行吧，既来之则安之。"),
            Panel(R.drawable.opening_p4, "学生OS", "不管环境咋样，先赶完 DDL 再说。"),
            Panel(R.drawable.opening_p4, "学生", "哈哈，这视频太好笑了。"),
            Panel(R.drawable.opening_p4, "学生", "又到抢课季了，希望别再抢不上！"),
            Panel(R.drawable.opening_p4, "$p（内心）", "好家伙，一堆“神仙”学生。"),
            Panel(R.drawable.opening_p4, "旁白", "教学任务、经费压力、学生诉求，全压在你肩上。"),
            Panel(R.drawable.opening_p5, p, "办学经费严重告急！"),
            Panel(R.drawable.opening_p5, "$p（内心）", "拉赞助？搞培训？还是……卖广告位？"),
            Panel(R.drawable.opening_p5, "商人", "校长！校园广告合作了解一下！"),
            Panel(R.drawable.opening_p5, "$p（幻想）", "经费瞬间暴涨！"),
            Panel(R.drawable.opening_p5, "警告", "⚠ 广告泛滥，学生满意度将大幅下降！"),
            Panel(R.drawable.opening_p5, "$p（内心）", "钱和口碑，我到底选哪边？"),
            Panel(R.drawable.opening_p6, "卷王学生", "卷死他们！"),
            Panel(R.drawable.opening_p6, "摆烂学生", "摆烂才是真谛。"),
            Panel(R.drawable.opening_p6, "学生", "食堂饭菜能不能提升一点！"),
            Panel(R.drawable.opening_p6, "招新摊位", "摸鱼协会、熬夜研究社，火热招新中~"),
            Panel(R.drawable.opening_p6, "$p（内心）", "大学生活还真是多姿多彩。"),
            Panel(R.drawable.opening_p6, "旁白", "各类随机事件，会不停找上门。"),
            Panel(R.drawable.opening_p7, "学生", "$p！我们专业课老师跑路了！"),
            Panel(R.drawable.opening_p7, p, "啊？老师人没了？"),
            Panel(R.drawable.opening_p7, "$p（内心）", "我去哪招靠谱老师啊！"),
            Panel(R.drawable.opening_p7, "旁白", "求职名单鱼龙混杂：摸鱼讲师与实力大牛并存。"),
            Panel(R.drawable.opening_p7, p, "招人也是一门大学问！"),
            Panel(R.drawable.opening_p7, "旁白", "学校建设，远没有想象中简单。"),
            Panel(R.drawable.opening_p8, "旁白", "深夜，整片校园只剩你一盏灯还亮着。"),
            Panel(R.drawable.opening_p8, "旁白", "报表、课程方案、基建清单，堆满了桌子。"),
            Panel(R.drawable.opening_p8, p, "当校长怎么比上大学还累……"),
            Panel(R.drawable.opening_p8, "$p（回忆）", "当初我立志，打造顶尖学府！"),
            Panel(R.drawable.opening_p8, "旁白", "窗外，还有学生在夜灯下打闹聊天。"),
            Panel(R.drawable.opening_p8, p, "算了，硬着头皮继续干吧。"),
            Panel(R.drawable.opening_p9, "旁白", "第二天清晨，阳光洒向校园。"),
            Panel(R.drawable.opening_p9, "学生", "条件是差了点，但期待学校变好！"),
            Panel(R.drawable.opening_p9, "躺平学生", "阳光正好，适合睡觉。"),
            Panel(R.drawable.opening_p9, "$p（内心）", "有人奋进，有人躺平，这就是真实的大学。"),
            Panel(R.drawable.opening_p9, "提示", "你可以自由决定学校的发展路线。"),
            Panel(R.drawable.opening_p9, "旁白", "严格治学，还是快乐放养——由你抉择。"),
            Panel(R.drawable.opening_p10, p, "从今天起，这里就是 $s。"),
            Panel(R.drawable.opening_p10, "旁白", "学生、老师、工人，从四面八方汇聚而来。"),
            Panel(R.drawable.opening_p10, "旁白", "风吹过崭新的校牌：$s。"),
            Panel(R.drawable.opening_p10, "旁白", "属于你的高校故事，正式开启。"),
            Panel(R.drawable.opening_p10, "旁白", "建设校舍、开设课程、管理万千学子——"),
            Panel(R.drawable.opening_p10, p, "欢迎来到 $s。点击开始办学！")
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val panel = panels[index]
    val pageIndex = index / 6 + 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F1A))
            .clickable {
                if (index < panels.lastIndex) index++ else onDone()
            }
    ) {
        Image(
            painter = painterResource(id = panel.res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "序章 · 白手起校",
                color = Color(0xFFFFD54F),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text("页面 $pageIndex/10", color = Color(0xFFB8C7D6), fontSize = 13.sp)
            Spacer(Modifier.width(16.dp))
            Text(
                "跳过序章",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x661E96C8))
                    .clickable { onDone() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (index > 0) {
                Text(
                    "上一幕",
                    color = Color(0xFFB8C7D6),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(Color(0x66000000))
                        .clickable { index-- }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6101826))
                    .padding(16.dp)
            ) {
                Text(
                    panel.speaker,
                    color = Color(0xFF1E96C8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    panel.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (index < panels.lastIndex) "点击画面继续" else "点击开始办学",
                    color = Color(0xFF8FA6BB),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
