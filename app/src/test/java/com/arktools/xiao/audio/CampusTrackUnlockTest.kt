package com.arktools.xiao.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusTrackUnlockTest {

    @Test
    fun tracksUnlockByCampusProgress() {
        assertTrue(AudioManager.CampusTrack.MAIN.isUnlocked(1, false))
        assertFalse(AudioManager.CampusTrack.MORNING.isUnlocked(1, false))
        assertTrue(AudioManager.CampusTrack.MORNING.isUnlocked(2, false))
        assertTrue(AudioManager.CampusTrack.RELAXED.isUnlocked(3, false))
        assertFalse(AudioManager.CampusTrack.ACADEMIC.isUnlocked(4, false))
        assertTrue(AudioManager.CampusTrack.ACADEMIC.isUnlocked(5, false))
    }

    @Test
    fun graduateProgramUnlocksAcademicTrackEarly() {
        assertTrue(AudioManager.CampusTrack.ACADEMIC.isUnlocked(3, true))
    }
}
