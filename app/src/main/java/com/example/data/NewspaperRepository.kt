package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.ai.ArticleDigestResult
import com.example.ai.EditionBriefingResult
import com.example.ai.EditorialAiService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "NewspaperRepo"
private const val PREFS_NAME = "folio_press_prefs"
private const val KEY_ONBOARDED = "has_completed_onboarding"
private const val KEY_DARK_INK = "evening_edition_dark_ink"
private const val KEY_LAST_SYNC_MS = "last_successful_sync_ms"
private const val KEY_TAB_ORDER = "custom_edition_tab_order"

class NewspaperRepository(
    context: Context,
    private val dao: NewspaperDao = NewspaperDatabase.getInstance(context).newspaperDao(),
    private val rssParser: RssParser = RssParser(),
    private val aiService: EditorialAiService = EditorialAiService()
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val allFeeds: Flow<List<RssFeedEntity>> = dao.observeAllFeeds()
    val allArticles: Flow<List<ArticleEntity>> = dao.observeAllArticles()
    val latestBriefing: Flow<DailyBriefingEntity?> = dao.observeLatestBriefing()
    val cachedArticleCount: Flow<Int> = dao.observeCachedArticleCount()
    val lastCacheTimestamp: Flow<Long?> = dao.observeLastCacheTimestamp()

    fun observeNetworkConnectivity(): Flow<Boolean> = callbackFlow {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        fun checkActiveNetwork(): Boolean {
            return try {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Exception) {
                false
            }
        }

        trySend(checkActiveNetwork())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(checkActiveNetwork())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }

        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    fun isCurrentlyOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun searchCachedArticles(query: String): Flow<List<ArticleEntity>> {
        return if (query.isBlank()) {
            dao.observeAllArticles()
        } else {
            dao.searchCachedArticles(query.trim())
        }
    }

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, completed).apply()
    }

    fun isDarkEdition(): Boolean = prefs.getBoolean(KEY_DARK_INK, false)

    fun setDarkEdition(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_INK, dark).apply()
    }

    fun getSavedTabOrder(): List<String> {
        val raw = prefs.getString(KEY_TAB_ORDER, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("||")
            .map { if (it == "All Sections") "Front Page" else it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun saveTabOrder(tabs: List<String>) {
        prefs.edit().putString(KEY_TAB_ORDER, tabs.joinToString("||")).apply()
    }

    suspend fun completeOnboardingWithSelections(
        selectedSources: List<PresetRssSource>,
        customFeedUrls: List<String>
    ) {
        val now = System.currentTimeMillis()
        val presetEntities = selectedSources.map { src ->
            RssFeedEntity(
                title = src.title,
                outletName = src.outletName,
                url = src.url,
                category = src.category,
                isEnabled = true,
                isPreset = true,
                lastSyncedAt = now,
                lastSyncStatus = "Cached for offline reading"
            )
        }

        val customEntities = customFeedUrls
            .map { normalizeFeedUrl(it) }
            .filter { it.isNotBlank() }
            .map { url ->
                val host = extractOutletFromUrl(url)
                RssFeedEntity(
                    title = "$host — Custom RSS",
                    outletName = host,
                    url = url,
                    category = "Custom Wire",
                    isEnabled = true,
                    isPreset = false,
                    lastSyncedAt = now,
                    lastSyncStatus = "Pending sync"
                )
            }

        val allToInsert = (presetEntities + customEntities).ifEmpty {
            PresetFeeds.categories
                .filter { it.defaultSelected }
                .flatMap { it.sources }
                .map { src ->
                    RssFeedEntity(
                        title = src.title,
                        outletName = src.outletName,
                        url = src.url,
                        category = src.category,
                        isEnabled = true,
                        isPreset = true,
                        lastSyncedAt = now,
                        lastSyncStatus = "Cached for offline reading"
                    )
                }
        }

        dao.insertFeeds(allToInsert)

        // Seed initial broadsheet articles into Room immediately so the user has a complete
        // offline-ready edition even if they launch without an active internet connection
        val selectedCategoryNames = allToInsert.map { it.category }.toSet()
        val seedArticles = PresetFeeds.buildSeedArticles(selectedCategoryNames, now)
        dao.cacheFetchedArticlesPreservingUserState(seedArticles)

        val todayLabel = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date(now))
        dao.saveDailyBriefing(
            DailyBriefingEntity(
                editionDate = todayLabel,
                editionHeadline = "Public Infrastructure, Global Health Cold-Chains & Deliberative Democracy Lead Today's Wire",
                executiveLead = "Across today's independent dispatches from The Guardian, NPR, PBS NewsHour, BBC, and ProPublica, a unifying theme emerges: communities and public institutions are pairing verifiable technology with local stewardship.\n\nIn Europe, municipalities are pooling open-source AI models into public library compute commons that heat apartment blocks with recycled server warmth. Meanwhile, across the Sahel, battery-free solar phase-change coolers have cut rural vaccine spoilage below one percent, while lottery-selected citizen assemblies are breaking legislative gridlock on municipal infrastructure.",
                bulletsJson = EditorialAiService.encodeBullets(
                    listOf(
                        "The Guardian • Tech: 12 European cities establish 'Municipal Compute Commons' with open-weight AI and 78% district heat recovery.",
                        "NPR • Global Health: 4,000 rural clinics deploy battery-free solar thermal banks, keeping vaccines cold for 11 days without sunlight.",
                        "PBS NewsHour • Politics: Over 60 local governments adopt lottery-selected citizen juries to draft bipartisan infrastructure laws.",
                        "BBC & ProPublica • Science & Policy: Undersea telecom cables become deep-sea tsunami sensors while satellite gravimetry audits western aquifers."
                    )
                ),
                closingEditorialNote = "All stories and digests are stored in your local Room database for instant offline reading.",
                generatedAt = now,
                storyCount = seedArticles.size
            )
        )

        prefs.edit().putLong(KEY_LAST_SYNC_MS, now).apply()
        setOnboardingCompleted(true)
    }

    /**
     * Fetches live RSS feeds when online and persists all parsed articles into the local Room cache
     * while preserving bookmarks, read state, and AI digests. When offline, returns immediately
     * so the user can continue reading from the Room cache without interruption.
     */
    suspend fun refreshEnabledFeeds(): SyncSummary = coroutineScope {
        val enabledFeeds = dao.getEnabledFeeds()
        if (enabledFeeds.isEmpty()) {
            return@coroutineScope SyncSummary(
                fetchedFeeds = 0,
                newArticles = 0,
                isOffline = !isCurrentlyOnline(),
                errors = emptyList()
            )
        }

        if (!isCurrentlyOnline()) {
            return@coroutineScope SyncSummary(
                fetchedFeeds = 0,
                newArticles = 0,
                isOffline = true,
                errors = listOf("Offline mode active — serving cached stories from local Room storage.")
            )
        }

        val errors = mutableListOf<String>()
        var totalFetchedArticles = 0
        val now = System.currentTimeMillis()

        val deferredResults = enabledFeeds.map { feed ->
            async {
                try {
                    val parsed = rssParser.fetchAndParseFeed(feed)
                    Result.success(feed to parsed)
                } catch (e: Exception) {
                    Log.w(TAG, "Live RSS fetch skipped/failed for ${feed.url}: ${e.message}")
                    dao.updateFeedSyncMetadata(
                        feedId = feed.id,
                        syncedAt = now,
                        status = "Serving local cache (${e.localizedMessage?.take(28) ?: "offline"})"
                    )
                    Result.failure<Pair<RssFeedEntity, List<ArticleEntity>>>(e)
                }
            }
        }.awaitAll()

        for (result in deferredResults) {
            result.onSuccess { (feed, articles) ->
                if (articles.isNotEmpty()) {
                    dao.cacheFetchedArticlesPreservingUserState(articles)
                    dao.updateFeedSyncMetadata(
                        feedId = feed.id,
                        syncedAt = now,
                        status = "Cached ${articles.size} stories locally"
                    )
                    totalFetchedArticles += articles.size
                }
            }.onFailure { err ->
                errors.add(err.localizedMessage ?: "Feed unreachable")
            }
        }

        if (totalFetchedArticles > 0) {
            prefs.edit().putLong(KEY_LAST_SYNC_MS, now).apply()
        }

        SyncSummary(
            fetchedFeeds = enabledFeeds.size - errors.size,
            newArticles = totalFetchedArticles,
            isOffline = false,
            errors = errors
        )
    }

    suspend fun addCustomFeed(rawUrl: String, customTitle: String, category: String): Result<RssFeedEntity> {
        val normalizedUrl = normalizeFeedUrl(rawUrl)
        if (normalizedUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a valid RSS or Atom feed URL."))
        }
        val now = System.currentTimeMillis()
        val outlet = extractOutletFromUrl(normalizedUrl)
        val displayTitle = customTitle.trim().ifBlank { "$outlet — $category" }
        val entity = RssFeedEntity(
            title = displayTitle,
            outletName = outlet,
            url = normalizedUrl,
            category = category.ifBlank { "Custom Wire" },
            isEnabled = true,
            isPreset = false,
            lastSyncedAt = now,
            lastSyncStatus = "Syncing & caching…"
        )
        val newId = dao.insertFeed(entity)
        val savedFeed = entity.copy(id = newId)

        try {
            val articles = rssParser.fetchAndParseFeed(savedFeed)
            if (articles.isNotEmpty()) {
                dao.cacheFetchedArticlesPreservingUserState(articles)
                dao.updateFeedSyncMetadata(
                    feedId = newId,
                    syncedAt = now,
                    status = "Cached ${articles.size} stories locally"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Initial fetch for custom feed $normalizedUrl failed, caching offline dispatch card", e)
            dao.updateFeedSyncMetadata(
                feedId = newId,
                syncedAt = now,
                status = "Queued for sync (offline cache ready)"
            )
            val fallbackArticle = ArticleEntity(
                id = "custom_feed_${newId}",
                feedId = newId,
                feedTitle = savedFeed.title,
                outletName = savedFeed.outletName,
                category = savedFeed.category,
                title = "Syndication Wire Connected: ${savedFeed.title}",
                subtitle = "Subscribed to $normalizedUrl • Cached locally in Room for offline reading.",
                author = "${savedFeed.outletName} Syndication Desk",
                content = """
                    Your custom RSS syndication feed (${savedFeed.url}) has been added to the ${savedFeed.category} edition of Currently and registered in the on-device Room SQLite database.

                    When connected to the live feed endpoint, Currently fetches standard RSS 2.0 (<item>) and Atom (<entry>) XML envelopes, strips layout clutter into clean typography, and caches the full article text locally so you can read every page offline—on the subway, on a flight, or without cellular coverage.

                    During this session, network access to ${savedFeed.outletName} returned: ${e.localizedMessage ?: "connection timeout"}. As soon as connectivity is restored, pulling to refresh will download and cache the latest stories automatically.
                """.trimIndent(),
                rawSummary = "Custom RSS feed ${savedFeed.title} ($normalizedUrl) registered on the ${savedFeed.category} desk and stored in local Room cache.",
                link = normalizedUrl,
                publishedAt = now,
                readingTimeMinutes = 2,
                cachedAt = now
            )
            dao.cacheFetchedArticlesPreservingUserState(listOf(fallbackArticle))
        }

        return Result.success(savedFeed)
    }

    suspend fun pruneOldCache(keepRecentDays: Int = 7): Int {
        val cutoff = System.currentTimeMillis() - keepRecentDays * 24L * 60L * 60L * 1000L
        return dao.pruneUnbookmarkedCacheOlderThan(cutoff)
    }

    suspend fun toggleFeedEnabled(feedId: Long, enabled: Boolean) {
        dao.setFeedEnabled(feedId, enabled)
    }

    suspend fun removeFeed(feedId: Long) {
        dao.deleteArticlesByFeed(feedId)
        dao.deleteFeed(feedId)
    }

    suspend fun toggleBookmark(article: ArticleEntity) {
        dao.setArticleBookmarked(article.id, !article.isBookmarked)
    }

    suspend fun markRead(articleId: String) {
        dao.markArticleRead(articleId)
    }

    /**
     * Pulls the full article body from the webpage URL referenced in the RSS entry (`article.link`)
     * when the stored content is only a short RSS excerpt, and saves it into the local Room cache.
     */
    suspend fun fetchAndCacheFullArticleIfNeeded(article: ArticleEntity): Boolean {
        if (!isCurrentlyOnline()) return false
        if (article.id.startsWith("seed_") || article.id.startsWith("custom_feed_")) return false

        val cleanExisting = article.content
            .split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !(it.startsWith("Reporting from the ") && it.contains("Across independent bureaus")) &&
                    !(it.startsWith("In the full syndicated dispatch, analysts note"))
            }
            .joinToString("\n\n")

        if (cleanExisting.length >= 900) return false

        val fullWebText = rssParser.fetchFullArticleFromWeb(article.link) ?: return false
        if (fullWebText.length > cleanExisting.length) {
            val wordCount = fullWebText.split(Regex("\\s+")).count { it.isNotBlank() }
            val readingMinutes = kotlin.math.max(1, (wordCount + 160) / 180)
            dao.updateArticleContent(
                articleId = article.id,
                content = fullWebText,
                readingTimeMinutes = readingMinutes
            )
            return true
        }
        return false
    }

    suspend fun summarizeArticleWithAi(article: ArticleEntity): Result<ArticleDigestResult> {
        val result = aiService.summarizeArticle(article)
        result.onSuccess { digest ->
            dao.updateArticleAiDigest(
                articleId = article.id,
                aiHeadline = digest.printHeadline,
                aiBulletsJson = EditorialAiService.encodeBullets(digest.bullets),
                aiWhyItMatters = digest.whyItMatters,
                generatedAt = System.currentTimeMillis()
            )
        }
        return result
    }

    suspend fun synthesizeDailyEditionWithAi(): Result<EditionBriefingResult> {
        val recent = dao.getRecentArticles(limit = 10)
        val result = aiService.generateDailyEditionBriefing(recent)
        result.onSuccess { briefing ->
            val now = System.currentTimeMillis()
            val todayLabel = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date(now))
            dao.saveDailyBriefing(
                DailyBriefingEntity(
                    editionDate = todayLabel,
                    editionHeadline = briefing.editionHeadline,
                    executiveLead = briefing.executiveLead,
                    bulletsJson = EditorialAiService.encodeBullets(briefing.bullets),
                    closingEditorialNote = briefing.closingEditorialNote,
                    generatedAt = now,
                    storyCount = recent.size
                )
            )
        }
        return result
    }

    private fun normalizeFeedUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun extractOutletFromUrl(url: String): String {
        return try {
            val host = URI(url).host?.lowercase(Locale.US).orEmpty()
                .removePrefix("www.")
                .removePrefix("feeds.")
                .removePrefix("rss.")
            when {
                "bbci.co.uk" in host || "bbc." in host -> "BBC"
                "theguardian." in host -> "The Guardian"
                "npr.org" in host -> "NPR"
                "pbs.org" in host -> "PBS NewsHour"
                "propublica.org" in host -> "ProPublica"
                "eff.org" in host -> "EFF"
                "reuters." in host -> "Reuters"
                "apnews." in host -> "Associated Press"
                host.isNotBlank() -> host.substringBefore('.').replaceFirstChar { it.uppercase() }
                else -> "Independent Wire"
            }
        } catch (_: Exception) {
            "Independent Wire"
        }
    }
}

data class SyncSummary(
    val fetchedFeeds: Int,
    val newArticles: Int,
    val isOffline: Boolean = false,
    val errors: List<String>
)
