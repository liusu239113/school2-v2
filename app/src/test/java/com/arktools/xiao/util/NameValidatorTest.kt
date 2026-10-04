package com.arktools.xiao.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameValidatorTest {

    @Test
    fun normalNamesAreAccepted() {
        assertTrue(NameValidator.validate("星海大学", "大学名称").ok)
        assertTrue(NameValidator.validate("张明", "校长姓名").ok)
        assertTrue(NameValidator.validate("明德学院", "大学名称").ok)
        assertTrue(NameValidator.validate("Northwind University", "大学名称").ok)
    }

    @Test
    fun realUniversitiesAreRejected() {
        assertFalse(NameValidator.validate("清华大学", "大学名称").ok)
        assertFalse(NameValidator.validate("北京大学", "大学名称").ok)
        assertFalse(NameValidator.validate("复旦大学", "大学名称").ok)
        assertFalse(NameValidator.validate("哈佛大学", "大学名称").ok)
    }

    @Test
    fun politicalNamesAreRejected() {
        assertFalse(NameValidator.validate("习近平", "校长姓名").ok)
        assertFalse(NameValidator.validate("毛泽东", "校长姓名").ok)
        assertFalse(NameValidator.validate("蒋介石", "校长姓名").ok)
        assertFalse(NameValidator.validate("普京", "校长姓名").ok)
    }

    @Test
    fun separatorsCannotBypassTheFilter() {
        // 空格 / 分隔符 / 全角 / 重复字 都不能绕过
        assertFalse(NameValidator.validate("习 近 平", "校长姓名").ok)
        assertFalse(NameValidator.validate("习-近-平", "校长姓名").ok)
        assertFalse(NameValidator.validate("习　近　平", "校长姓名").ok)
        assertFalse(NameValidator.validate("习习近近平平", "校长姓名").ok)
    }

    @Test
    fun contactInfoIsRejected() {
        assertFalse(NameValidator.validate("招生13800138000", "大学名称").ok)
        assertFalse(NameValidator.validate("加微信123456", "大学名称").ok)
        assertFalse(NameValidator.validate("abc@qq.com", "大学名称").ok)
        assertFalse(NameValidator.validate("www.example.com", "大学名称").ok)
    }

    @Test
    fun profanityIsRejected() {
        assertFalse(NameValidator.validate("垃圾学校", "大学名称").ok)
        assertFalse(NameValidator.validate("fuck", "大学名称").ok)
        assertFalse(NameValidator.validate("傻逼", "校长姓名").ok)
    }

    @Test
    fun tooShortOrEmptyIsRejected() {
        assertFalse(NameValidator.validate("", "大学名称").ok)
        assertFalse(NameValidator.validate("   ", "大学名称").ok)
        assertFalse(NameValidator.validate("甲", "校长姓名").ok)
    }

    @Test
    fun symbolsOnlyIsRejected() {
        assertFalse(NameValidator.validate("!!!", "大学名称").ok)
        assertFalse(NameValidator.validate("。。。", "大学名称").ok)
    }
}
