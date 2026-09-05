package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.Facility
import com.arktools.xiao.domain.model.FacilityType
import com.arktools.xiao.domain.policy.SchoolPolicyManager
import com.arktools.xiao.domain.repository.SchoolRepository

/**
 * 应急校园操作：从 GameEngine 拆出，事件选项直接改餐位/落食堂。
 */
internal object CampusEmergencyOps {

    suspend fun applyExtraWindows(
        schoolRepository: SchoolRepository,
        policyManager: SchoolPolicyManager,
        delta: Int
    ) {
        if (delta == 0) return
        schoolRepository.mutateSchool { latest ->
            val current = policyManager.policies.value.collegeDevelopment
            val ops = current.buildingOps
            val next = (ops.extraWindows + delta).coerceIn(0, 6)
            if (next == ops.extraWindows) return@mutateSchool true
            policyManager.replaceCollegeDevelopment(current.copy(buildingOps = ops.copy(extraWindows = next)))
            latest.policyJson = policyManager.toJson()
            true
        }
    }

    suspend fun applyBuildCanteenNow(
        schoolRepository: SchoolRepository,
        policyManager: SchoolPolicyManager
    ) {
        schoolRepository.mutateSchool { latest ->
            if (latest.facilities.any { it.type == FacilityType.CANTEEN }) return@mutateSchool true
            val bt = com.arktools.xiao.ui.campus.CampusBuildTypes
            val spec = bt.facilitySpec(FacilityType.CANTEEN) ?: return@mutateSchool true
            val current = policyManager.policies.value.collegeDevelopment
            val placed = bt.decodeBuildings(current.placedBuildings)
            val terrain = bt.decodeTerrain(current.terrainMap).associate { (it.y * 1000L + it.x) to true }
            val rect = bt.unlockedRect(latest.campusLevel)
            var spot: Pair<Int, Int>? = null
            outer@ for (y in rect.y0..(rect.y1 - spec.h)) {
                for (x in rect.x0..(rect.x1 - spec.w)) {
                    var ok = true
                    cell@ for (dy in 0 until spec.h) for (dx in 0 until spec.w) {
                        val cx = x + dx
                        val cy = y + dy
                        if (terrain.containsKey(cy * 1000L + cx)) {
                            ok = false
                            break@cell
                        }
                        val occupied = placed.any { b ->
                            val sp = bt.specByKey(b.key) ?: return@any false
                            bt.occupies(b, sp, cx, cy)
                        }
                        if (occupied) {
                            ok = false
                            break@cell
                        }
                    }
                    if (ok) {
                        spot = x to y
                        break@outer
                    }
                }
            }
            val f = Facility(type = FacilityType.CANTEEN, level = 1, condition = 100f, constructionDaysLeft = 0)
            if (spot != null) {
                policyManager.addPlacedFacility(spec.key, spot, 1, f.id, 0)
            }
            latest.facilities.add(f)
            latest.policyJson = policyManager.toJson()
            true
        }
    }
}
