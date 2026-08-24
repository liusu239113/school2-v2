package com.arktools.xiaozhang.data.save

import android.util.Log
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程级持久化协调器。
 *
 * Activity/ViewModel 生命周期结束不能取消已经开始的存档；所有调用统一串行，
 * 并在数据库恢复期间拒绝旧进程写回。
 */
@Singleton
class PersistenceCoordinator @Inject constructor(
    private val gameEngine: GameEngine,
    private val schoolRepository: SchoolRepository,
    private val saveManager: SaveManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()

    fun requestAutoSave(reason: String): Deferred<Boolean> = scope.async {
        persistenceMutex.withLock {
            persistLocked(reason)
        }
    }

    suspend fun restoreLatestBackup(): Boolean =
        persistenceMutex.withLock {
            saveManager.loadLatestBackup()
        }

    /**
     * 在与自动保存相同的互斥区内执行清档/重建。
     * 当前有进度时，保护快照必须成功；否则拒绝执行破坏性操作。
     */
    suspend fun <T> runExclusiveDestructiveOperation(
        reason: String,
        operation: suspend () -> T
    ): T = persistenceMutex.withLock {
        val hasExistingGame = schoolRepository.getSchool() != null
        if (hasExistingGame && !persistLocked("before-$reason")) {
            throw IllegalStateException(
                "Protection snapshot failed; destructive operation '$reason' was blocked"
            )
        }
        operation()
    }

    private suspend fun persistLocked(reason: String): Boolean {
        if (saveManager.isRestoreInProgress()) {
            Log.w("Persistence", "Skipped $reason save during database restore")
            return false
        }

        gameEngine.setSaving(true)
        return try {
            gameEngine.flushAllManagerStates()
            if (saveManager.isRestoreInProgress()) {
                Log.w("Persistence", "Aborted $reason save after restore started")
                return false
            }

            val school = schoolRepository.getSchool() ?: return false
            val saved = saveManager.saveAutoSave(
                schoolName = school.name,
                currentYear = school.currentYear,
                currentMonth = school.currentMonth,
                cash = school.cash,
                reputation = school.reputation,
                schoolId = school.id
            )
            if (!saved) {
                Log.e("Persistence", "$reason auto-save failed; previous snapshot preserved")
            }
            saved
        } catch (e: Exception) {
            Log.e("Persistence", "$reason auto-save failed", e)
            false
        } finally {
            gameEngine.setSaving(false)
        }
    }
}
