package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Locale

class LocaleHelperTest {

    @Test
    fun `absent tag (null) resolves to English`() {
        assertEquals(Locale.ENGLISH, LocaleHelper.tagToLocale(null))
    }

    @Test
    fun `absent tag (blank) resolves to English`() {
        assertEquals(Locale.ENGLISH, LocaleHelper.tagToLocale(""))
        assertEquals(Locale.ENGLISH, LocaleHelper.tagToLocale("   "))
    }

    @Test
    fun `unknown garbage tag resolves to English without crashing`() {
        assertEquals(Locale.ENGLISH, LocaleHelper.tagToLocale("garbage"))
        assertEquals(Locale.ENGLISH, LocaleHelper.tagToLocale("!!!"))
        assertEquals(Locale.ENGLISH, LocaleHelper.tagToLocale("12345"))
    }

    @Test
    fun `en tag resolves to English`() {
        assertEquals(Locale.ENGLISH, LocaleHelper.tagToLocale("en"))
    }

    @Test
    fun `zh-CN tag resolves to Locale zh CN`() {
        val locale = LocaleHelper.tagToLocale("zh-CN")
        assertEquals("zh", locale.language)
        assertEquals("CN", locale.country)
    }

    @Test
    fun `in tag resolves to Locale in (Indonesian)`() {
        val locale = LocaleHelper.tagToLocale("in")
        assertEquals(Locale("in"), locale)
    }

    @Test
    fun `fr tag resolves to Locale fr`() {
        val locale = LocaleHelper.tagToLocale("fr")
        assertEquals("fr", locale.language)
    }

    @Test
    fun `tagToLocale never returns null`() {
        assertNotNull(LocaleHelper.tagToLocale(null))
        assertNotNull(LocaleHelper.tagToLocale(""))
        assertNotNull(LocaleHelper.tagToLocale("unknown"))
        assertNotNull(LocaleHelper.tagToLocale("en"))
    }

    @Test
    fun `zh-TW tag resolves to Locale zh TW`() {
        val locale = LocaleHelper.tagToLocale("zh-TW")
        assertEquals("zh", locale.language)
        assertEquals("TW", locale.country)
    }

    @Test
    fun `ja tag resolves to Locale ja`() {
        val locale = LocaleHelper.tagToLocale("ja")
        assertEquals("ja", locale.language)
    }
}
