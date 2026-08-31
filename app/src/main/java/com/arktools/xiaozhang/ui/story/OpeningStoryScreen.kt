package com.arktools.xiaozhang.ui.story

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
 * 开场剧情漫画（仅新存档首次进入时展示，看过/跳过后写入进度不再出现）。
 * 分格漫画 + 底部旁白框，参考像素 AVG 章节演出。
 */
@Composable
fun OpeningStoryScreen(
    onDone: () -> Unit
) {
    data class Panel(val res: Int, val speaker: String, val text: String)
    val panels = remember {
        listOf(
            Panel(R.drawable.story_p1, "旁白", "一纸红头任命，把你推向了没人看好的位置——一所新大学的筹建校长。"),
            Panel(R.drawable.story_p2, "教育局长", "地批给你了，钱也只够打地基。名头、师资、生源，都得你自己挣。"),
            Panel(R.drawable.story_p3, "旁白", "打桩声昼夜不停。校舍一栋栋立起来，可没有教师和学生的校园，还只是空壳。"),
            Panel(R.drawable.story_p4, "旁白", "9月1日，行政楼前人头攒动。开学典礼的横幅挂起——你的大学时代，开始了。")
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val panel = panels[index]

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

        // 顶栏：章节标题 + 页码 + 跳过
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "序章 · 临危受命",
                color = Color(0xFFFFD54F),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${index + 1}/${panels.size}",
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

        // 底部旁白框
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
