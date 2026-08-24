package com.arktools.xiaozhang.util

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 全局协程异常处理器：捕获 ViewModel 中未处理的异常，防止 App 闪退。
 *
 * 使用方式：在 ViewModel 中用 `viewModelScope.safeLaunch { ... }` 替代 `viewModelScope.launch { ... }`
 *
 * 原理：CoroutineExceptionHandler 只在根协程（非 child）上生效。
 * viewModelScope.launch 创建的就是根协程，所以 handler 会捕获其中所有未处理异常。
 */
val ViewModelExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e("ViewModel", "Coroutine exception caught (app not crashed): ${throwable.message}", throwable)
}

/**
 * 安全的协程启动器：自动附加异常处理，防止未捕获异常导致 App 崩溃。
 * 异常会被记录日志但不会传播到 UncaughtExceptionHandler。
 */
fun CoroutineScope.safeLaunch(block: suspend CoroutineScope.() -> Unit): Job {
    return launch(ViewModelExceptionHandler) {
        block()
    }
}
