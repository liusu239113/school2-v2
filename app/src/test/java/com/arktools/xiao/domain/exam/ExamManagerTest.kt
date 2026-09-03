package com.arktools.xiao.domain.exam

import com.arktools.xiao.domain.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamManagerTest {

    @Test
    fun subjectScoreCapsAndConversionsUseTheConfiguredScale() {
        assertEquals(150f, Subject.CHINESE.maxScore, 0.001f)
        assertEquals(150f, Subject.MATH.maxScore, 0.001f)
        assertEquals(150f, Subject.ENGLISH.maxScore, 0.001f)
        assertEquals(100f, Subject.PHYSICS.maxScore, 0.001f)
        assertEquals(100f, Subject.HISTORY.maxScore, 0.001f)

        assertEquals(120f, Subject.CHINESE.rawScoreFromNormalized(80f), 0.001f)
        assertEquals(80f, Subject.CHINESE.normalizeScore(120f), 0.001f)
        assertEquals(80f, Subject.PHYSICS.normalizeScore(80f), 0.001f)
    }

    @Test
    fun gradesAreCalculatedFromNormalizedPercentage() {
        val currentChineseScore = score(subject = Subject.CHINESE, score = 135f, maxScore = 150f)
        val legacyChineseScore = score(subject = Subject.CHINESE, score = 95f, maxScore = 100f)

        assertEquals(90f, currentChineseScore.normalizedScore, 0.001f)
        assertEquals("A", currentChineseScore.grade)
        assertEquals(95f, legacyChineseScore.normalizedScore, 0.001f)
        assertEquals("A", legacyChineseScore.grade)
    }

    @Test
    fun oldJsonWithoutScoreSchemeKeepsChineseScoresOnTheLegacy100PointScale() {
        val manager = ExamManager()
        manager.fromJson(
            """
            {
              "records": [],
              "scores": {
                "student-1": [
                  {
                    "studentId": "student-1",
                    "studentName": "测试学生",
                    "classId": "class-1",
                    "examId": "legacy-exam",
                    "subjectName": "CHINESE",
                    "score": 95.0,
                    "rank": 1
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val restored = manager.getStudentScores("student-1").single()
        assertEquals(100f, restored.maxScore, 0.001f)
        assertEquals(95f, restored.normalizedScore, 0.001f)
        assertEquals("A", restored.grade)
    }

    @Test
    fun currentJsonRoundTripPreservesSubjectSpecificMaximum() {
        val original = score(subject = Subject.CHINESE, score = 120f, maxScore = 150f)
        val json = ExamData(
            scoreSchemeVersion = 2,
            scores = mapOf(
                original.studentId to listOf(
                    SerializableScore(
                        studentId = original.studentId,
                        studentName = original.studentName,
                        classId = original.classId,
                        examId = original.examId,
                        subjectName = original.subject.name,
                        score = original.score,
                        rank = original.rank,
                        maxScore = original.maxScore
                    )
                )
            )
        )

        val manager = ExamManager()
        manager.fromJson(kotlinx.serialization.json.Json.encodeToString(ExamData.serializer(), json))

        val restored = manager.getStudentScores(original.studentId).single()
        assertEquals(150f, restored.maxScore, 0.001f)
        assertEquals(80f, restored.normalizedScore, 0.001f)
    }

    private fun score(subject: Subject, score: Float, maxScore: Float) = StudentScore(
        studentId = "student-1",
        studentName = "测试学生",
        classId = "class-1",
        examId = "exam-1",
        subject = subject,
        score = score,
        rank = 1,
        maxScore = maxScore
    )
}
