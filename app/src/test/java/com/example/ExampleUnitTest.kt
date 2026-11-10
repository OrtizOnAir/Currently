package com.example

import com.example.ai.EditorialAiService
import com.example.data.PresetFeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun presetCategories_includeIndependentSources() {
        val categoryTitles = PresetFeeds.categories.map { it.title }
        assertTrue(categoryTitles.contains("Technology"))
        assertTrue(categoryTitles.contains("Politics"))
        assertTrue(categoryTitles.contains("Global Health"))

        val outlets = PresetFeeds.categories.flatMap { it.sources }.map { it.outletName }.toSet()
        assertTrue(outlets.containsAll(listOf("BBC", "NPR", "PBS NewsHour", "The Guardian")))
    }

    @Test
    fun seedArticles_populateOfflineRoomCache() {
        val articles = PresetFeeds.buildSeedArticles(
            selectedCategories = setOf("Technology", "Politics", "Global Health"),
            nowMillis = 1700000000000L
        )
        assertTrue(articles.size >= 3)
        val firstWithDigest = articles.first { !it.aiBulletsJson.isNullOrBlank() }
        val bullets = EditorialAiService.decodeBullets(firstWithDigest.aiBulletsJson)
        assertEquals(3, bullets.size)
    }
}
