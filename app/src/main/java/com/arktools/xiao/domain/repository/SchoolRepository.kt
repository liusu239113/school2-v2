package com.arktools.xiao.domain.repository

import com.arktools.xiao.domain.model.School
import kotlinx.coroutines.flow.Flow

interface SchoolRepository {
    fun getSchoolFlow(): Flow<School?>
    suspend fun getSchool(): School?
    suspend fun createSchool(name: String): School
    suspend fun createNewSchool(
        name: String,
        principalName: String = "张校长",
        tierKey: String = "APPLIED",
        ownershipKey: String = "PRIVATE"
    ): School
    suspend fun updateSchool(school: School)
    suspend fun advanceDay()
    suspend fun addCash(amount: Double)
    suspend fun addReputation(amount: Long)
    suspend fun deductCash(amount: Double)
    suspend fun deductReputation(amount: Long)
    suspend fun commitStudentWithdrawal(
        studentId: String,
        dropYear: Int,
        dropMonth: Int,
        refundAmount: Double,
        reputationPenalty: Long
    ): Boolean
    suspend fun upgradeCampus()
    suspend fun deleteAll()

    /**
     * 原子性地读取最新 School 状态、执行修改、写回数据库。
     * 在 Mutex 保护下执行，避免 read-modify-write 竞态条件。
     * 
     * @param block 接收最新 School 对象，可以直接修改它。返回 true 表示要写回，false 表示放弃。
     * @return 修改后的 School，如果 School 不存在或 block 返回 false 则返回 null
     */
    suspend fun mutateSchool(block: (School) -> Boolean): School?
}
