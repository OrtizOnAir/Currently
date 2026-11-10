package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rss_feeds")
data class RssFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val outletName: String,
    val url: String,
    val category: String,
    val isEnabled: Boolean = true,
    val isPreset: Boolean = false,
    val lastSyncedAt: Long? = null,
    val lastSyncStatus: String = "Cached"
)

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val feedId: Long,
    val feedTitle: String,
    val outletName: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val author: String,
    val content: String,
    val rawSummary: String,
    val aiHeadline: String? = null,
    val aiBulletsJson: String? = null,
    val aiWhyItMatters: String? = null,
    val aiDigestGeneratedAt: Long? = null,
    val link: String,
    val imageUrl: String? = null,
    val publishedAt: Long,
    val readingTimeMinutes: Int = 4,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_briefings")
data class DailyBriefingEntity(
    @PrimaryKey val editionDate: String,
    val editionHeadline: String,
    val executiveLead: String,
    val bulletsJson: String,
    val closingEditorialNote: String,
    val generatedAt: Long,
    val storyCount: Int
)

@Dao
interface NewspaperDao {
    @Query("SELECT * FROM rss_feeds ORDER BY category ASC, title ASC")
    fun observeAllFeeds(): Flow<List<RssFeedEntity>>

    @Query("SELECT * FROM rss_feeds WHERE isEnabled = 1")
    suspend fun getEnabledFeeds(): List<RssFeedEntity>

    @Query("SELECT COUNT(*) FROM rss_feeds")
    suspend fun getFeedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeds(feeds: List<RssFeedEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: RssFeedEntity): Long

    @Query("UPDATE rss_feeds SET isEnabled = :enabled WHERE id = :feedId")
    suspend fun setFeedEnabled(feedId: Long, enabled: Boolean)

    @Query("UPDATE rss_feeds SET lastSyncedAt = :syncedAt, lastSyncStatus = :status WHERE id = :feedId")
    suspend fun updateFeedSyncMetadata(feedId: Long, syncedAt: Long, status: String)

    @Query("DELETE FROM rss_feeds WHERE id = :feedId")
    suspend fun deleteFeed(feedId: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isBookmarked = 0")
    suspend fun deleteArticlesByFeed(feedId: Long)

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun observeAllArticles(): Flow<List<ArticleEntity>>

    @Query(
        """
        SELECT * FROM articles 
        WHERE title LIKE '%' || :query || '%' 
           OR subtitle LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
           OR outletName LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY publishedAt DESC
        """
    )
    fun searchCachedArticles(query: String): Flow<List<ArticleEntity>>

    @Query("SELECT COUNT(*) FROM articles")
    fun observeCachedArticleCount(): Flow<Int>

    @Query("SELECT MAX(cachedAt) FROM articles")
    fun observeLastCacheTimestamp(): Flow<Long?>

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getRecentArticles(limit: Int = 12): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :articleId LIMIT 1")
    suspend fun getArticleById(articleId: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticlesIgnoreExisting(articles: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(article: ArticleEntity)

    /**
     * Smart offline cache upsert: inserts newly fetched RSS articles or updates their text/metadata
     * while strictly preserving local user state (bookmarks, read status, and cached AI digests).
     */
    @Transaction
    suspend fun cacheFetchedArticlesPreservingUserState(articles: List<ArticleEntity>) {
        val now = System.currentTimeMillis()
        for (incoming in articles) {
            val existing = getArticleById(incoming.id)
            if (existing == null) {
                upsertArticle(incoming.copy(cachedAt = now))
            } else {
                val bestContent = if (existing.content.length > incoming.content.length) {
                    existing.content
                } else {
                    incoming.content
                }
                val bestReadingTime = if (existing.content.length > incoming.content.length) {
                    existing.readingTimeMinutes
                } else {
                    incoming.readingTimeMinutes
                }
                upsertArticle(
                    incoming.copy(
                        content = bestContent,
                        readingTimeMinutes = bestReadingTime,
                        isBookmarked = existing.isBookmarked,
                        isRead = existing.isRead,
                        aiHeadline = existing.aiHeadline ?: incoming.aiHeadline,
                        aiBulletsJson = existing.aiBulletsJson ?: incoming.aiBulletsJson,
                        aiWhyItMatters = existing.aiWhyItMatters ?: incoming.aiWhyItMatters,
                        aiDigestGeneratedAt = existing.aiDigestGeneratedAt ?: incoming.aiDigestGeneratedAt,
                        cachedAt = now
                    )
                )
            }
        }
    }

    @Query("UPDATE articles SET content = :content, readingTimeMinutes = :readingTimeMinutes WHERE id = :articleId")
    suspend fun updateArticleContent(
        articleId: String,
        content: String,
        readingTimeMinutes: Int
    )

    @Query(
        """
        UPDATE articles 
        SET aiHeadline = :aiHeadline,
            aiBulletsJson = :aiBulletsJson,
            aiWhyItMatters = :aiWhyItMatters,
            aiDigestGeneratedAt = :generatedAt
        WHERE id = :articleId
        """
    )
    suspend fun updateArticleAiDigest(
        articleId: String,
        aiHeadline: String,
        aiBulletsJson: String,
        aiWhyItMatters: String,
        generatedAt: Long
    )

    @Query("UPDATE articles SET isBookmarked = :bookmarked WHERE id = :articleId")
    suspend fun setArticleBookmarked(articleId: String, bookmarked: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE id = :articleId")
    suspend fun markArticleRead(articleId: String)

    @Query("DELETE FROM articles WHERE isBookmarked = 0 AND cachedAt < :cutoffMillis")
    suspend fun pruneUnbookmarkedCacheOlderThan(cutoffMillis: Long): Int

    @Query("SELECT * FROM daily_briefings ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatestBriefing(): Flow<DailyBriefingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDailyBriefing(briefing: DailyBriefingEntity)
}

@Database(
    entities = [
        RssFeedEntity::class,
        ArticleEntity::class,
        DailyBriefingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NewspaperDatabase : RoomDatabase() {
    abstract fun newspaperDao(): NewspaperDao

    companion object {
        @Volatile
        private var INSTANCE: NewspaperDatabase? = null

        fun getInstance(context: Context): NewspaperDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NewspaperDatabase::class.java,
                    "folio_press_newspaper.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
