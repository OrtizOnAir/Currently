package com.example.ai

import android.util.Log
import com.example.data.ArticleEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "EditorialAiService"

data class ArticleDigestResult(
    val printHeadline: String,
    val bullets: List<String>,
    val whyItMatters: String
)

data class EditionBriefingResult(
    val editionHeadline: String,
    val executiveLead: String,
    val bullets: List<String>,
    val closingEditorialNote: String
)

class EditorialAiService {

    private val articleDigestSchema = Schema.obj(
        properties = mapOf(
            "printHeadline" to Schema.string(
                description = "A crisp, authoritative broadsheet sub-headline (under 16 words) capturing the core finding."
            ),
            "keyTakeaways" to Schema.array(
                items = Schema.string(description = "One concise, factual takeaway sentence."),
                description = "Exactly 3 concise executive bullet points summarizing the article."
            ),
            "whyItMatters" to Schema.string(
                description = "A 1-2 sentence editorial synthesis explaining why this story matters in the broader civic, scientific, or global context."
            )
        )
    )

    private val editionBriefingSchema = Schema.obj(
        properties = mapOf(
            "editionHeadline" to Schema.string(
                description = "A front-page banner headline synthesizing today's edition across the active RSS feeds."
            ),
            "executiveLead" to Schema.string(
                description = "A cohesive 2-paragraph editorial synthesis weaving together the top stories from today's independent feeds."
            ),
            "crossCuttingBullets" to Schema.array(
                items = Schema.string(description = "A concise thematic digest bullet citing the source outlet."),
                description = "3 to 4 concise digest bullets covering the distinct beats in today's newspaper."
            ),
            "closingEditorialNote" to Schema.string(
                description = "A single reflective closing sentence on what to watch next."
            )
        )
    )

    // Lazy initialization of Firebase AI Logic generative models per skill guidelines
    private val articleDigestModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = articleDigestSchema
            }
        )
    }

    private val editionBriefingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = editionBriefingSchema
            }
        )
    }

    suspend fun summarizeArticle(article: ArticleEntity): Result<ArticleDigestResult> {
        return try {
            val prompt = buildString {
                appendLine("You are the Senior Digest Editor at Currently, an independent news and magazine edition reader.")
                appendLine("Summarize the following long-form article into a concise, high-signal daily digest for easier reading.")
                appendLine("Source Outlet: ${article.outletName} (${article.category})")
                appendLine("Title: ${article.title}")
                appendLine("Subtitle: ${article.subtitle}")
                appendLine("Article Text:")
                appendLine(article.content.take(6000))
            }

            val response = withTimeout(60_000L) {
                articleDigestModel.generateContent(prompt)
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty digest response."))
            }

            val json = JSONObject(rawJson)
            val printHeadline = json.optString("printHeadline", article.subtitle).ifBlank { article.subtitle }
            val takeawaysArray = json.optJSONArray("keyTakeaways") ?: JSONArray()
            val bullets = (0 until takeawaysArray.length())
                .mapNotNull { takeawaysArray.optString(it)?.trim() }
                .filter { it.isNotEmpty() }
            val whyItMatters = json.optString("whyItMatters", "").trim()

            if (bullets.isEmpty()) {
                return Result.failure(IllegalStateException("Digest contained no takeaway bullets."))
            }

            Result.success(
                ArticleDigestResult(
                    printHeadline = printHeadline,
                    bullets = bullets,
                    whyItMatters = whyItMatters
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Article digest generation timed out", e)
            Result.failure(IllegalStateException("AI digest timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Article digest generation failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to summarize article with Gemini."))
        }
    }

    suspend fun generateDailyEditionBriefing(articles: List<ArticleEntity>): Result<EditionBriefingResult> {
        if (articles.isEmpty()) {
            return Result.failure(IllegalArgumentException("No stories available to synthesize."))
        }
        return try {
            val topStories = articles.take(8)
            val prompt = buildString {
                appendLine("You are the Editor-in-Chief of Currently.")
                appendLine("Synthesize the following top stories from the reader's custom RSS newspaper into a cohesive Daily Digest Briefing:")
                topStories.forEachIndexed { idx, story ->
                    appendLine("${idx + 1}. [${story.outletName} — ${story.category}] ${story.title}")
                    appendLine("   Summary: ${story.rawSummary.ifBlank { story.subtitle }}")
                }
            }

            val response = withTimeout(60_000L) {
                editionBriefingModel.generateContent(prompt)
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty daily briefing."))
            }

            val json = JSONObject(rawJson)
            val headline = json.optString("editionHeadline", "Today's Independent Wire Synthesis")
            val lead = json.optString("executiveLead", "")
            val bulletsArr = json.optJSONArray("crossCuttingBullets") ?: JSONArray()
            val bullets = (0 until bulletsArr.length())
                .mapNotNull { bulletsArr.optString(it)?.trim() }
                .filter { it.isNotEmpty() }
            val closing = json.optString("closingEditorialNote", "")

            Result.success(
                EditionBriefingResult(
                    editionHeadline = headline,
                    executiveLead = lead,
                    bullets = bullets,
                    closingEditorialNote = closing
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Daily edition briefing timed out", e)
            Result.failure(IllegalStateException("Daily briefing timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily edition briefing failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to generate daily edition digest."))
        }
    }

    companion object {
        fun encodeBullets(bullets: List<String>): String {
            val arr = JSONArray()
            bullets.forEach { arr.put(it) }
            return arr.toString()
        }

        fun decodeBullets(jsonString: String?): List<String> {
            if (jsonString.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(jsonString)
                (0 until arr.length()).mapNotNull { arr.optString(it)?.takeIf { s -> s.isNotBlank() } }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
