package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ArticleEntity
import com.example.data.NewspaperDatabase
import com.example.data.PresetFeeds
import com.example.data.RssFeedEntity
import com.example.data.RssParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and verify independent onboarding presets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Folio Press", appName)

        val categoryTitles = PresetFeeds.categories.map { it.title }
        assertTrue(categoryTitles.contains("Technology"))
        assertTrue(categoryTitles.contains("Politics"))
        assertTrue(categoryTitles.contains("Global Health"))

        val outlets = PresetFeeds.categories.flatMap { it.sources }.map { it.outletName }.toSet()
        assertTrue(outlets.containsAll(listOf("BBC", "NPR", "PBS NewsHour", "The Guardian")))
    }

    @Test
    fun `parse rss xml feed and persist offline in room database preserving bookmarks`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NewspaperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.newspaperDao()

        val parser = RssParser()
        val feed = RssFeedEntity(
            id = 99,
            title = "BBC News — Technology",
            outletName = "BBC",
            url = "https://feeds.bbci.co.uk/news/technology/rss.xml",
            category = "Technology"
        )
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>BBC Technology</title>
                    <item>
                        <title>Open-Source Civic Compute Networks Expand</title>
                        <description>Public libraries across Europe are linking local AI servers.</description>
                        <link>https://www.bbc.co.uk/news/technology-100</link>
                        <pubDate>Mon, 09 Nov 2026 12:00:00 GMT</pubDate>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val articles = parser.parseXml(sampleXml, feed)
        assertEquals(1, articles.size)

        // 1. Cache into Room for offline reading
        dao.cacheFetchedArticlesPreservingUserState(articles)
        val cached = dao.observeAllArticles().first()
        assertEquals(1, cached.size)
        val articleId = cached.first().id

        // 2. Bookmark and save AI digest locally
        dao.setArticleBookmarked(articleId, true)
        dao.updateArticleAiDigest(
            articleId = articleId,
            aiHeadline = "European Public Libraries Link Local AI Servers",
            aiBulletsJson = "[\"Point 1\",\"Point 2\"]",
            aiWhyItMatters = "Demonstrates civic AI ownership.",
            generatedAt = 1000L
        )

        // 3. Re-syncing feed from network must preserve local bookmark and cached AI digest
        dao.cacheFetchedArticlesPreservingUserState(articles)
        val afterResync: ArticleEntity? = dao.getArticleById(articleId)
        assertTrue(afterResync?.isBookmarked == true)
        assertEquals("European Public Libraries Link Local AI Servers", afterResync?.aiHeadline)

        // 4. Offline search query works without network
        val searchResults = dao.searchCachedArticles("Civic Compute").first()
        assertEquals(1, searchResults.size)

        db.close()
    }
}
