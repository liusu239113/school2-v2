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
import androidx.compose.ui.geometry.Offset
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
 * 展示方式：整页漫画原图 + 底部台词逐条推进（每页 6 条台词）。
 * 根布局消费全部点击，台词/顶栏区域不会再把触摸渗透到下层校园 UI。
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
            Panel(R.drawable.opening_p1, p, "连续改了七版材料、盖了二十三个章，办事窗口的排号小票我都攒了一沓。办学资质总算交上去了——窗口工作人员都认识我了，见面就问：校长，今天又缺哪个章？"),
            Panel(R.drawable.opening_p1, "系统弹窗", "审核通过：准予设立高校。温馨提示：土地已划拨，其余请自主筹办。祝您办学顺利。——顺祝的顺利，约等于『没当场驳回』的那种顺利。"),
            Panel(R.drawable.opening_p1, "$p（内心）", "『其余』？教学楼、宿舍、食堂、老师、学生……还有我还没发出去的第一笔工资。行吧，创业嘛，梦想总得先有张空桌子坐着。"),
            Panel(R.drawable.opening_p1, "货车喇叭", "校长签收！折叠桌两张、扩音器一个、褪色国旗一面，附赠《高校管理速成手册》——翻开前三页就一句话：没钱，寸步难行。"),
            Panel(R.drawable.opening_p1, p, "合着别人白手起家，我这是白地起校？行，先把校牌立起来，牌子先立住，气势不能输。梦想这东西，钱越少越得喊得响。"),
            Panel(R.drawable.opening_p1, "旁白", "就这样，一个有理想、爱面子、钱包很诚实的校长，接管了一片长满野草的校园。草很高，风很吹，理想很贵。"),
            Panel(R.drawable.opening_p2, p, "我要建一所学生愿意来、老师舍不得走、家长逢人就夸的大学！口号先喊出来，预算的事……回头让财务处慢慢头疼去。"),
            Panel(R.drawable.opening_p2, "幻想泡泡", "四人寝、不断网、食堂不靠滤镜；图书馆永远有座，实验室这学期就排得上。幻想很丰满，报价单很骨感。"),
            Panel(R.drawable.opening_p2, "财务软件", "梦想预算：九位数。可用余额：五百万。系统建议：先关闭全景美颜模式，从修路开始。本提示由财务处倾情赞助。"),
            Panel(R.drawable.opening_p2, "校园热搜", "大学生生存四问：空调开不开？课抢不抢得到？DDL 能不能延期？食堂今天吃什么？——每一条，都精准踩在校长的钱包上。"),
            Panel(R.drawable.opening_p2, p, "这届学生要求也不高嘛……等等，为什么每一条后面都跟着价签？空调要电费，抢课要服务器，食堂要厨子，DDL 延期最后是我自己加班？"),
            Panel(R.drawable.opening_p2, "旁白", "第一批新生已经在路上。而你的校园目前连一条像样的路都没有——他们即将踩着草坪完成开学典礼。"),
            Panel(R.drawable.opening_p3, "学生A", "导航说已经到学校了，可我只看见草、一块崭新的校牌，还有一位笑得很心虚的大叔。大叔，请问教学楼往哪边走？"),
            Panel(R.drawable.opening_p3, "学生B", "招生宣传里说的『开放式校园』，原来是四面都还没来得及修围墙的意思？这文案我给满分，工程进度我给零分。"),
            Panel(R.drawable.opening_p3, "学生C", "先别急着退学，我已经建好新生群了，群名就叫『荒野求生 2026』。入群暗号：今天你踩泥了吗？"),
            Panel(R.drawable.opening_p3, p, "同学们冷静！楼会有的，路会有的，毕业证更会有的——前提是咱们学校别在建成之前先破产。都先散了，一人一把扫把，扫出个开学气象。"),
            Panel(R.drawable.opening_p3, "学生", "校长，宿舍有空调吗？校园网几点断？热水器是真实存在还是都市传说？我们宿舍六个人，连搓澡巾都备好了。"),
            Panel(R.drawable.opening_p3, p, "空调列入一期工程，网络坚持全天开放。至于热水……我今晚亲自找后勤聊人生。放心，今晚一定给你们一个说法。"),
            Panel(R.drawable.opening_p4, "学生们", "校长敢当面答应，先观察一个学期。要是鸽了，我们就在表白墙天天提醒，配图就用他站在草地上指方向那张。"),
            Panel(R.drawable.opening_p4, "DDL 战士", "环境先放一边，明早八点要交作业。大学第一课：今晚永远是『最后一晚』。宿舍没修好？正好，通宵还不困。"),
            Panel(R.drawable.opening_p4, "摸鱼同学", "群里说老师还没招齐，那作业是不是也没齐？这个逻辑漏洞我先记下了，以后写进我第一篇论文的致谢里。"),
            Panel(R.drawable.opening_p4, "抢课群", "选课系统开放三秒，体育课只剩太极和清晨七点长跑。服务器再次圆满完成压力测试——测试结论：不行。"),
            Panel(R.drawable.opening_p4, "$p（内心）", "一个要卷绩点，一个研究系统漏洞，还有一个已经把学校做成表情包了。这届学生不好带，但带好了绝对精彩。"),
            Panel(R.drawable.opening_p4, "旁白", "课程、宿舍、社团、心理、就业——大学不是把学生招进来就完事，而是稳稳接住他们整整四年。"),
            Panel(R.drawable.opening_p5, p, "财务处晨报：建楼要钱、养楼要钱、开课要钱，连办一场像样的迎新晚会也要钱。我问有没有不要钱的，财务说：有，欠条。"),
            Panel(R.drawable.opening_p5, "$p（内心）", "提高学费会掉满意度，压缩维护会坏设备；拉赞助可以，但校训不能改成广告词。这个度，比高数还难拿捏。"),
            Panel(R.drawable.opening_p5, "合作商", "校长，冠名合作了解一下？食堂、操场、奖学金都能安排，连湖里的鹅都能品牌联名——鹅戴小围巾那种，很出片。"),
            Panel(R.drawable.opening_p5, "$p（幻想）", "经费到账、实验室开工、教师工资准时发放，孩子们在亮堂堂的教室里上课……等等，鹅的冠名先划掉，校长的脸还要。"),
            Panel(R.drawable.opening_p5, "风险提示", "商业化过度会掉满意度和口碑；经费不足则建设停摆、教学拉胯。平衡点在哪？就在你每次签字的那支笔上。"),
            Panel(R.drawable.opening_p5, "$p（内心）", "校长不是在『要钱』和『要脸』里二选一，而是要让每一笔钱都花成学校的明天。这活儿，得精打细算，还得看得远。"),
            Panel(R.drawable.opening_p6, "卷王学生", "绩点、竞赛、科研、实习我全都要！睡眠属于可优化项，咖啡因属于基建。校长，图书馆的通宵灯能常亮吗？"),
            Panel(R.drawable.opening_p6, "摆烂学生", "我不是不努力，我是在为学校的『多元成才评价』贡献极端样本。总得有人垫底，数据才完整嘛——开玩笑的，我只是在找方向。"),
            Panel(R.drawable.opening_p6, "食堂测评社", "今日测评：红烧肉有红烧但疑似没肉。建议校长把餐位和餐标都列入重点工程，我们社团愿意持续跟踪报道。"),
            Panel(R.drawable.opening_p6, "百团大战", "摸鱼协会、熬夜研究社、流浪猫观察组火热招新！摄影社表示：校园再简陋也能拍出电影感——主要是草多。"),
            Panel(R.drawable.opening_p6, "$p（内心）", "有人在社团找到朋友，有人在操场找回状态。校园生活不是装饰品，投进去的每一分，都会变成学生身上的成长。"),
            Panel(R.drawable.opening_p6, "旁白", "每项活动都消耗经费、场地和人手，也会留下能力、人脉、荣誉，甚至多年后愿意回来看看的校友。这买卖，长远看稳赚。"),
            Panel(R.drawable.opening_p7, "学生", "校长！专业课老师辞职了！他说实验室连台能用的设备都没有，报个销还要在四栋楼之间往返盖章——跑出来的运动量比体育课还大！"),
            Panel(R.drawable.opening_p7, p, "老师可以走，问题不能装没看见。先停掉我的形象工程，把实验设备补上、报销流程简化——人才留不留得住，全看这些细节。"),
            Panel(R.drawable.opening_p7, "$p（内心）", "名师贵，青年教师要培养，骨干会被挖角。招聘不是抽卡游戏，抽完就完；把人留住、把人带强，才是真本事。"),
            Panel(R.drawable.opening_p7, "人才市场", "本年度候选名单已送达：有人论文亮眼，有人课堂封神，也有人的特长是把十分钟的会开成两小时——总之，各有千秋。"),
            Panel(R.drawable.opening_p7, p, "不只看头衔。能教学生、能做研究、愿意跟学校一起从草地干到大楼的人，才是我要的老师。级别可以慢慢升，心气不能没有。"),
            Panel(R.drawable.opening_p7, "旁白", "学院、课程、教师、实验台、班级，一环扣一环。缺掉任何一环，再漂亮的大楼也只是昂贵的背景板。"),
            Panel(R.drawable.opening_p8, "旁白", "深夜，整个工地只剩校长室亮着灯。窗外是刚打好地基的楼，桌上是比地基还难清空的待办清单。"),
            Panel(R.drawable.opening_p8, "待办清单", "教学排课、科研预算、宿舍维修、学生申诉、教师晋升、就业合作……最后一行小字：查一查是谁批准湖里养鹅的。"),
            Panel(R.drawable.opening_p8, p, "别人上大学熬四年，我当校长怕是要熬到退休。关键是，我连『申请延毕』的入口都找不到——这系统对校长真不友好。"),
            Panel(R.drawable.opening_p8, "$p（回忆）", "我说过要办好大学。不是把排名数字做大，而是让一个普通家庭的孩子，也能在这里找到自己的出路。这话我还记得。"),
            Panel(R.drawable.opening_p8, "窗外学生", "校长室还亮着。看来空调的事他真没忘……走，去食堂给他留份夜宵，就当投他一票信任。"),
            Panel(R.drawable.opening_p8, p, "嘴上嫌我抠，夜宵倒记得给我留。行吧，就冲这份夜宵，这所学校也值得我再多扛两张报表。"),
            Panel(R.drawable.opening_p9, "旁白", "清晨，第一条水泥路铺进校园。它不产生任何排名加分，却让学生终于不用踩着泥、蹦着砖去上第一节课。"),
            Panel(R.drawable.opening_p9, "学生", "条件确实一般，但问题有人管、承诺有进度。只要学校肯一天天长起来，我们也愿意跟着它一起长。"),
            Panel(R.drawable.opening_p9, "躺平学生", "我也成长了：以前能睡到十点，现在因为宿舍施工，九点五十九就自然醒了。这算不算学校对我的重塑？"),
            Panel(R.drawable.opening_p9, "$p（内心）", "有人冲奖学金，有人还在摸索方向，有人只是努力不掉队。大学不该只有一种成功模板，得给每个人留条路。"),
            Panel(R.drawable.opening_p9, "办学提示", "你的每次投入都会形成依赖链：建筑承载课程，课程培养学生，学生成就带来声誉、合作与愿意回头的校友。"),
            Panel(R.drawable.opening_p9, "旁白", "严格或宽松、应用或研究、公办或民办，都没有标准答案。唯一的评分标准，是你的投入能不能变成学生身上的本事。"),
            Panel(R.drawable.opening_p10, p, "从今天起，这片草地有名字了——$s。小是小了点，牌子新了点，但目标不会跟着缩水。"),
            Panel(R.drawable.opening_p10, "旁白", "学生带着期待报到，教师带着理想入职，工人把图纸一格一格变成真实的楼。所有人的日历，都翻到了同一天。"),
            Panel(R.drawable.opening_p10, "校牌", "$s。牌子崭新，围墙尚短；专业目录只有薄薄几页，但往后的故事，足够写满很多很多年。"),
            Panel(R.drawable.opening_p10, "旁白", "你将面对的不只是一个又一个按钮，还有预算的取舍、人才的来去、学科的成长、四年的青春和社会的评分。"),
            Panel(R.drawable.opening_p10, p, "先建教室，再保宿舍食堂；站稳脚跟就开学院、做科研，把每一届毕业生都送到体面的去处。一步一步来，步步都算数。"),
            Panel(R.drawable.opening_p10, p, "欢迎来到 $s。别急着当名校——先从今天开始，不让任何一项待办烂尾。下课，开工！")
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val panel = panels[index]
    val pageIndex = index / 6 + 1
    val lineIndex = index % 6
    val pageImage = remember(panel.res) {
        BitmapFactory.decodeResource(context.resources, panel.res).asImageBitmap()
    }

    // 根布局消费全部点击：点画面/台词区都推进剧情，不会再渗透到下层建造 UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F1A))
            .clickable {
                if (index < panels.lastIndex) index++ else onDone()
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
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
                    "页面 $pageIndex/10 · 台词 ${lineIndex + 1}/6",
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

            // 整页漫画：等比完整显示，不再裁切单格
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                pageImage?.let { image ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = minOf(
                            size.width / image.width,
                            size.height / image.height
                        )
                        val dstW = (image.width * scale).toInt().coerceAtLeast(1)
                        val dstH = (image.height * scale).toInt().coerceAtLeast(1)
                        val dx = ((size.width - dstW) / 2f).toInt()
                        val dy = ((size.height - dstH) / 2f).toInt()
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(dx, dy),
                            dstSize = IntSize(dstW, dstH),
                            filterQuality = FilterQuality.None
                        )
                    }
                }
            }

            // 底部台词区（点击落到根布局推进剧情；上一幕按钮自行消费点击）
            Column(
                modifier = Modifier
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
}
