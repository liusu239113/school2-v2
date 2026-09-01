package com.arktools.xiaozhang.ui.story

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    val context = LocalContext.current
    val panels = remember(p, s) {
        listOf(
            Panel(R.drawable.opening_p1, p, "连续改了七版材料、盖了二十三个章……办学资质，总算交上去了。"),
            Panel(R.drawable.opening_p1, "系统弹窗", "审核通过：准予设立高校。温馨提示——土地已划拨，其余请自主筹办。"),
            Panel(R.drawable.opening_p1, "$p（内心）", "“其余”是指教学楼、宿舍、老师、学生，以及我尚未准备好的后半生吗？"),
            Panel(R.drawable.opening_p1, "货车喇叭", "校长签收！折叠桌两张、扩音器一个，还有《高校管理速成手册》。"),
            Panel(R.drawable.opening_p1, p, "合着别人白手起家，我是白地起校？行，先把校牌立起来，气势不能输。"),
            Panel(R.drawable.opening_p1, "旁白", "就这样，一个有理想、爱面子、钱包很诚实的校长，接管了一片草地。"),
            Panel(R.drawable.opening_p2, p, "我要建一所让学生愿意来、老师舍不得走、家长逢人就夸的大学！"),
            Panel(R.drawable.opening_p2, "幻想泡泡", "四人寝、不断网、食堂不靠滤镜；图书馆有座，实验室不排到下学期。"),
            Panel(R.drawable.opening_p2, "财务软件", "梦想预算：九位数。可用余额：五百万。建议先关闭全景效果。"),
            Panel(R.drawable.opening_p2, "校园热搜", "大学生生存四问：空调开不开？课抢不抢得到？DDL 能不能延期？食堂今天吃什么？"),
            Panel(R.drawable.opening_p2, p, "这届学生要求也不高嘛……等等，为什么每一条都要花钱？"),
            Panel(R.drawable.opening_p2, "旁白", "第一批新生已在路上，而你的校园目前连一条像样的路都没有。"),
            Panel(R.drawable.opening_p3, "学生A", "导航说已经到学校了，可我只看见草、校牌，还有一位笑得很心虚的大叔。"),
            Panel(R.drawable.opening_p3, "学生B", "招生宣传里的“开放式校园”，原来是四面都还没来得及修围墙？"),
            Panel(R.drawable.opening_p3, "学生C", "先别急着退学，我已经建好新生群了，群名叫“荒野求生 2026”。"),
            Panel(R.drawable.opening_p3, p, "同学们冷静！楼会有的，路会有的，毕业证更会有的——前提是学校别先破产。"),
            Panel(R.drawable.opening_p3, "学生", "校长，宿舍有空调吗？校园网几点断？热水器是不是靠缘分？"),
            Panel(R.drawable.opening_p3, p, "空调列入一期工程，网络坚持全天开放。至于热水……我今晚亲自找后勤。"),
            Panel(R.drawable.opening_p4, "学生们", "校长敢当面答应，先观察一个学期。要是鸽了，我们就在表白墙天天提醒。"),
            Panel(R.drawable.opening_p4, "DDL 战士", "环境先放一边，明早八点交作业。大学第一课：今晚就是“最后一晚”。"),
            Panel(R.drawable.opening_p4, "摸鱼同学", "群里说老师还没招齐，那作业是不是也没齐？这逻辑应该可以写进论文。"),
            Panel(R.drawable.opening_p4, "抢课群", "选课系统开放三秒，体育课只剩太极和清晨七点长跑。服务器再次完成压力测试。"),
            Panel(R.drawable.opening_p4, "$p（内心）", "一个要卷绩点，一个研究系统漏洞，还有一个已经准备给学校做表情包。"),
            Panel(R.drawable.opening_p4, "旁白", "课程、宿舍、社团、心理、就业——大学不是把学生招进来，而是接住他们四年。"),
            Panel(R.drawable.opening_p5, p, "财务处报告：建楼要钱、养楼要钱、开课要钱，连举行一次像样的迎新晚会也要钱。"),
            Panel(R.drawable.opening_p5, "$p（内心）", "提高学费会掉满意度，压缩维护会坏设备；拉赞助可以，但不能把校训改成广告词。"),
            Panel(R.drawable.opening_p5, "合作商", "校长，冠名合作了解一下？食堂、操场、奖学金，连湖里的鹅都能安排品牌联名。"),
            Panel(R.drawable.opening_p5, "$p（幻想）", "经费到账、实验室开工、教师工资准时发放……等等，鹅的冠名先划掉。"),
            Panel(R.drawable.opening_p5, "风险提示", "商业化过度会降低学生满意度与学校口碑；经费不足则可能让建设和教学一起停摆。"),
            Panel(R.drawable.opening_p5, "$p（内心）", "校长不是选“要钱还是要脸”，而是想办法让每一笔钱都能变成学校的明天。"),
            Panel(R.drawable.opening_p6, "卷王学生", "绩点、竞赛、科研、实习我全都要！睡眠属于可优化的非核心指标。"),
            Panel(R.drawable.opening_p6, "摆烂学生", "我不是不努力，我是在为学校的“多元成才评价”贡献一个极端样本。"),
            Panel(R.drawable.opening_p6, "食堂测评社", "今天的红烧肉有红烧但疑似没肉。建议校长把餐位和餐标都列入重点工程。"),
            Panel(R.drawable.opening_p6, "百团大战", "摸鱼协会、熬夜研究社、流浪猫观察组招新！摄影社负责把简陋校园拍成电影感。"),
            Panel(R.drawable.opening_p6, "$p（内心）", "有人在社团找到朋友，有人在操场找回状态。校园生活不是装饰，也得认真投资。"),
            Panel(R.drawable.opening_p6, "旁白", "每项活动都消耗经费、场地和人手，也会留下能力、关系、荣誉，甚至多年后的校友资源。"),
            Panel(R.drawable.opening_p7, "学生", "$p！专业课老师辞职了，他说实验室没设备，报销单却需要在四栋楼之间往返盖章！"),
            Panel(R.drawable.opening_p7, p, "老师可以走，问题不能装没看见。先停掉我的形象工程，把实验设备和行政流程补上。"),
            Panel(R.drawable.opening_p7, "$p（内心）", "名师贵、青年教师要培养、骨干会被挖；招聘不是抽卡，留下人才更不是。"),
            Panel(R.drawable.opening_p7, "人才市场", "本年度候选名单已送达：有人论文亮眼，有人课堂厉害，也有人的特长是把会议开得特别长。"),
            Panel(R.drawable.opening_p7, p, "不只看头衔。能教学生、能做研究、愿意和学校一起成长的人，才是我要的老师。"),
            Panel(R.drawable.opening_p7, "旁白", "学院、课程、教师、实验台和班级必须彼此匹配；缺掉一环，再漂亮的大楼也只是背景。"),
            Panel(R.drawable.opening_p8, "旁白", "深夜，行政楼只剩校长室亮着。窗外是工地，桌上是比工地更难清空的待办。"),
            Panel(R.drawable.opening_p8, "待办清单", "教学排课、科研预算、宿舍维修、学生申诉、教师晋升、就业合作……以及“谁批准湖里养鹅”。"),
            Panel(R.drawable.opening_p8, p, "别人上大学熬四年，我当校长看来要熬到退休。关键是，我连毕业答辩都不能申请。"),
            Panel(R.drawable.opening_p8, "$p（回忆）", "我说过要办好大学，不是把排名数字做大，而是让普通学生也能在这里找到出路。"),
            Panel(R.drawable.opening_p8, "窗外学生", "校长室还亮着。看来空调的事他真没忘……走，给他留份食堂夜宵。"),
            Panel(R.drawable.opening_p8, p, "嘴上嫌我抠，夜宵倒记得给我留。行吧，这所学校值得再多扛一张报表。"),
            Panel(R.drawable.opening_p9, "旁白", "清晨，第一条路铺进校园。它不增加排名，却让学生终于不用踩着泥去上第一节课。"),
            Panel(R.drawable.opening_p9, "学生", "条件确实一般，但问题有人管、承诺有进度。只要学校肯长，我们也愿意一起长。"),
            Panel(R.drawable.opening_p9, "躺平学生", "我也成长了：以前能睡到十点，现在因为宿舍施工，九点五十九就醒。"),
            Panel(R.drawable.opening_p9, "$p（内心）", "有人冲奖学金，有人摸索方向，有人只是努力不掉队——大学不该只有一种成功模板。"),
            Panel(R.drawable.opening_p9, "办学提示", "你的每次选择都会形成依赖链：建筑承载课程，课程培养学生，学生成就带来声誉与校友。"),
            Panel(R.drawable.opening_p9, "旁白", "严格或宽松、应用或研究、公办或民办都没有万能答案；只有能否让投入真正产生长期价值。"),
            Panel(R.drawable.opening_p10, p, "从今天起，这片草地有了名字——$s。小是小了点，目标可不能跟着缩水。"),
            Panel(R.drawable.opening_p10, "旁白", "学生带着期待报到，教师带着理想入职，工人把蓝图一格一格变成真正的校园。"),
            Panel(R.drawable.opening_p10, "校牌", "$s。牌子崭新，围墙尚短；专业目录只有几页，但未来的故事足够写很多年。"),
            Panel(R.drawable.opening_p10, "旁白", "你将面对的不只是建设按钮，还有预算取舍、人才更替、学科成长、学生人生与社会评价。"),
            Panel(R.drawable.opening_p10, p, "先建教室，再保宿舍和食堂；等站稳脚跟，我们开学院、做科研、把毕业生送到好去处。"),
            Panel(R.drawable.opening_p10, p, "欢迎来到 $s。别急着当名校——先从今天不让任何一项待办烂尾开始。")
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val panel = panels[index]
    val pageIndex = index / 6 + 1
    val cellIndex = index % 6
    val pageImage = remember(panel.res) {
        BitmapFactory.decodeResource(context.resources, panel.res).asImageBitmap()
    }
    val sourceRect = when (cellIndex) {
        0 -> IntOffset(16, 16) to IntSize(348, 318)
        1 -> IntOffset(372, 16) to IntSize(332, 318)
        2 -> IntOffset(16, 340) to IntSize(688, 312)
        3 -> IntOffset(16, 664) to IntSize(348, 292)
        4 -> IntOffset(372, 664) to IntSize(332, 292)
        else -> IntOffset(16, 974) to IntSize(688, 292)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F1A))) {
        pageImage?.let { image ->
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 150.dp)
                    .clickable {
                        if (index < panels.lastIndex) index++ else onDone()
                    }
            ) {
                val srcOffset = sourceRect.first
                val srcSize = sourceRect.second
                drawImage(
                    image = image,
                    srcOffset = srcOffset,
                    srcSize = srcSize,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.None
                )
            }
        }

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
            Text(
                "页面 $pageIndex/10 · 第${index % 6 + 1}/6格",
                color = Color(0xFFB8C7D6),
                fontSize = 13.sp
            )
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
