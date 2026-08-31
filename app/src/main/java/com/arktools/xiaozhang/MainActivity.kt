package com.arktools.xiaozhang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.data.save.PersistenceCoordinator
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.ui.main.MainScreen
import com.arktools.xiaozhang.ui.theme.SchoolTycoonTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var audioManager: AudioManager

    @Inject
    lateinit var gameEngine: GameEngine

    @Inject
    lateinit var persistenceCoordinator: PersistenceCoordinator

    /** 记录退后台前引擎是否已经是暂停状态，避免回前台时误恢复 */
    private var wasEnginePausedBeforeBackground = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 游戏全程沉浸式：隐藏系统状态栏，下滑临时呼出
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            SchoolTycoonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 记录退后台前的引擎状态
        wasEnginePausedBeforeBackground = gameEngine.isPausedFlow.value
        // 应用进入后台时暂停 BGM 和游戏引擎
        audioManager.pauseBgm()
        gameEngine.pause()
        // 进程级协调器持有保存任务；Activity 销毁不会取消已经排队或执行中的自动存档。
        persistenceCoordinator.requestAutoSave("activity-onPause")
    }

    override fun onResume() {
        super.onResume()
        // 应用回前台时恢复 BGM
        audioManager.resumeBgm()
        // 仅当退后台前引擎是运行状态时才恢复
        if (!wasEnginePausedBeforeBackground) {
            gameEngine.resume()
        }
    }
}
