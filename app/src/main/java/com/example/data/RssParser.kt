package com.example.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class RssParser(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun fetchAndParseFeed(feed: RssFeedEntity): List<ArticleEntity> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feed.url.trim())
            .header("User-Agent", "Currently/3.0 (Android RSS Edition Reader)")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            .build()

        val parsedArticles = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} from ${feed.outletName}")
            }
            val xmlBody = response.body?.string().orEmpty()
            if (xmlBody.isBlank()) {
                throw IllegalStateException("Empty RSS payload from ${feed.outletName}")
            }
            parseXml(xmlBody, feed)
        }

        // Concurrently pull the full article text from each RSS entry's source webpage when the RSS feed
        // only provides a short summary/excerpt.
        coroutineScope {
            parsedArticles.mapIndexed { index, article ->
                async {
                    if (index < 10 && article.content.length < 900 && isFetchableWebArticleUrl(article.link, feed.url)) {
                        val extractedFullText = fetchFullArticleFromWeb(article.link)
                        if (!extractedFullText.isNullOrBlank() && extractedFullText.length > article.content.length) {
                            val wordCount = extractedFullText.split(Regex("\\s+")).count { it.isNotBlank() }
                            val readingMinutes = max(1, (wordCount + 160) / 180)
                            article.copy(
                                content = extractedFullText,
                                readingTimeMinutes = readingMinutes
                            )
                        } else {
                            article
                        }
                    } else {
                        article
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Fetches the source webpage referenced by an RSS item and extracts the full article body
     * using JSON-LD articleBody metadata and semantic HTML (<article>, <main>, <p>) extraction.
     */
    suspend fun fetchFullArticleFromWeb(articleUrl: String): String? = withContext(Dispatchers.IO) {
        val trimmedUrl = articleUrl.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
            !trimmedUrl.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext null
        }

        runCatching {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.isNotBlank() &&
                    !contentType.contains("html", ignoreCase = true) &&
                    !contentType.contains("xml", ignoreCase = true)
                ) {
                    return@use null
                }
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) return@use null
                extractArticleBodyFromHtml(html)
            }
        }.getOrNull()
    }

    fun extractArticleBodyFromHtml(html: String): String? {
        if (html.isBlank()) return null

        // 1. Try JSON-LD "articleBody" if present and substantial
        val jsonLdBody = extractJsonLdArticleBody(html)

        // 2. Extract semantic <p> paragraphs from <article>, <main>, or document body
        val htmlParagraphsBody = extractSemanticParagraphsFromHtml(html)

        val bestCandidate = when {
            jsonLdBody != null && htmlParagraphsBody != null -> {
                if (htmlParagraphsBody.length >= (jsonLdBody.length * 0.75).toInt()) {
                    htmlParagraphsBody
                } else {
                    jsonLdBody
                }
            }
            htmlParagraphsBody != null -> htmlParagraphsBody
            jsonLdBody != null -> jsonLdBody
            else -> null
        }

        return bestCandidate?.takeIf { it.length >= 200 }
    }

    private fun extractJsonLdArticleBody(html: String): String? {
        val scriptRegex = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE
        )
        val bodyFieldRegex = Regex(""""articleBody"\s*:\s*"((?:\\.|[^"\\])*)"""")

        var longestBody: String? = null
        for (match in scriptRegex.findAll(html)) {
            val jsonContent = match.groupValues.getOrNull(1).orEmpty()
            for (bodyMatch in bodyFieldRegex.findAll(jsonContent)) {
                val rawEscaped = bodyMatch.groupValues.getOrNull(1).orEmpty()
                val unescaped = unescapeJsonString(rawEscaped)
                val cleanedParagraphs = cleanHtmlToParagraphs(unescaped)
                if (cleanedParagraphs.length > (longestBody?.length ?: 0)) {
                    longestBody = cleanedParagraphs
                }
            }
        }
        return longestBody?.takeIf { it.length >= 260 }
    }

    private fun extractSemanticParagraphsFromHtml(html: String): String? {
        // Strip non-editorial containers first
        val strippedChrome = html
            .replace(Regex("<!--[\\s\\S]*?-->"), " ")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<noscript[^>]*>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<svg[^>]*>[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<nav[^>]*>[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<footer[^>]*>[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<aside[^>]*>[\\s\\S]*?</aside>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<form[^>]*>[\\s\\S]*?</form>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<figcaption[^>]*>[\\s\\S]*?</figcaption>", RegexOption.IGNORE_CASE), " ")

        // Prefer <article> content, then <main> content, then full stripped HTML
        val articleMatches = Regex("<article\\b[^>]*>([\\s\\S]*?)</article>", RegexOption.IGNORE_CASE)
            .findAll(strippedChrome)
            .map { it.groupValues[1] }
            .toList()

        val mainMatches = Regex("<main\\b[^>]*>([\\s\\S]*?)</main>", RegexOption.IGNORE_CASE)
            .findAll(strippedChrome)
            .map { it.groupValues[1] }
            .toList()

        val candidateRegions = buildList {
            if (articleMatches.isNotEmpty()) {
                add(articleMatches.maxByOrNull { it.length }.orEmpty())
                if (articleMatches.size > 1) {
                    add(articleMatches.joinToString("\n"))
                }
            }
            if (mainMatches.isNotEmpty()) {
                add(mainMatches.maxByOrNull { it.length }.orEmpty())
            }
            add(strippedChrome)
        }

        val pTagRegex = Regex("<p\\b[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE)

        for (region in candidateRegions) {
            val extractedParagraphs = pTagRegex.findAll(region)
                .map { match ->
                    cleanHtmlToPlainText(match.groupValues[1])
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
                .filter { para -> isLikelyArticleParagraph(para) }
                .distinct()
                .toList()

            val joined = extractedParagraphs.joinToString("\n\n")
            if (extractedParagraphs.size >= 2 && joined.length >= 260) {
                return joined
            }
        }

        return null
    }

    private fun isLikelyArticleParagraph(text: String): Boolean {
        if (text.length < 35) return false
        val lower = text.lowercase(Locale.US)
        val boilerplatePatterns = listOf(
            "all rights reserved",
            "terms of service",
            "privacy policy",
            "cookie policy",
            "sign up for our newsletter",
            "subscribe to our newsletter",
            "enable javascript",
            "manage your preferences"
        )
        if (boilerplatePatterns.any { lower.contains(it) } && text.length < 180) {
            return false
        }
        return true
    }

    private fun unescapeJsonString(escaped: String): String {
        val sb = StringBuilder(escaped.length)
        var i = 0
        while (i < escaped.length) {
            val c = escaped[i]
            if (c == '\\' && i + 1 < escaped.length) {
                when (val next = escaped[i + 1]) {
                    'n' -> {
                        sb.append("\n\n")
                        i += 2
                    }
                    'r' -> {
                        i += 2
                    }
                    't' -> {
                        sb.append(' ')
                        i += 2
                    }
                    '"', '\\', '/' -> {
                        sb.append(next)
                        i += 2
                    }
                    'u' -> {
                        if (i + 5 < escaped.length) {
                            val hex = escaped.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                sb.append(c)
                                i++
                            }
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun isFetchableWebArticleUrl(articleLink: String, feedUrl: String): Boolean {
        val trimmed = articleLink.trim()
        if (trimmed.isBlank() || trimmed.equals(feedUrl.trim(), ignoreCase = true)) return false
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) return false
        val path = runCatching { java.net.URI(trimmed).path.orEmpty() }.getOrDefault("")
        // Skip root homepage links (e.g., https://www.bbc.co.uk/news/technology) that are not individual articles
        return path.length > 1 && !trimmed.endsWith(".xml", ignoreCase = true) && !trimmed.endsWith(".rss", ignoreCase = true)
    }

    fun parseXml(xml: String, feed: RssFeedEntity): List<ArticleEntity> {
        val articles = mutableListOf<ArticleEntity>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml.trim()))

        var eventType = parser.eventType
        var insideItem = false
        var currentTitle = ""
        var currentDescription = ""
        var currentContentEncoded = ""
        var currentLink = ""
        var currentAuthor = ""
        var currentPubDate = ""
        var currentImageUrl: String? = null
        var currentGuid = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase(Locale.US).orEmpty()
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        insideItem = true
                        currentTitle = ""
                        currentDescription = ""
                        currentContentEncoded = ""
                        currentLink = ""
                        currentAuthor = ""
                        currentPubDate = ""
                        currentImageUrl = null
                        currentGuid = ""
                    } else if (insideItem) {
                        when (tagName) {
                            "title" -> currentTitle = safeNextText(parser)
                            "description", "summary" -> {
                                val text = safeNextText(parser)
                                if (currentDescription.isBlank()) currentDescription = text
                            }
                            "content:encoded", "content" -> {
                                val text = safeNextText(parser)
                                if (text.length > currentContentEncoded.length) {
                                    currentContentEncoded = text
                                }
                            }
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                val rel = parser.getAttributeValue(null, "rel")
                                val text = safeNextText(parser)
                                if (!href.isNullOrBlank() && (rel == null || rel == "alternate")) {
                                    currentLink = href
                                } else if (text.isNotBlank() && currentLink.isBlank()) {
                                    currentLink = text
                                }
                            }
                            "guid", "id" -> currentGuid = safeNextText(parser)
                            "dc:creator", "author", "name" -> {
                                val authorText = safeNextText(parser)
                                if (authorText.isNotBlank() && currentAuthor.isBlank()) {
                                    currentAuthor = authorText
                                }
                            }
                            "pubdate", "published", "updated", "dc:date" -> {
                                val dateText = safeNextText(parser)
                                if (dateText.isNotBlank() && currentPubDate.isBlank()) {
                                    currentPubDate = dateText
                                }
                            }
                            "media:thumbnail", "media:content", "enclosure" -> {
                                val urlAttr = parser.getAttributeValue(null, "url")
                                val typeAttr = parser.getAttributeValue(null, "type").orEmpty()
                                if (!urlAttr.isNullOrBlank() && currentImageUrl == null) {
                                    if (tagName != "enclosure" || typeAttr.startsWith("image/") ||
                                        urlAttr.endsWith(".jpg", true) || urlAttr.endsWith(".png", true) || urlAttr.endsWith(".webp", true)
                                    ) {
                                        currentImageUrl = urlAttr
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if ((tagName == "item" || tagName == "entry") && insideItem) {
                        insideItem = false
                        val cleanTitle = cleanHtmlToPlainText(currentTitle).replace("\n", " ").trim()
                        if (cleanTitle.isNotBlank()) {
                            val rawHtml = if (currentContentEncoded.length > currentDescription.length) {
                                currentContentEncoded
                            } else {
                                currentDescription
                            }
                            val extractedImg = currentImageUrl ?: extractFirstImgSrc(rawHtml)
                            val paragraphs = cleanHtmlToParagraphs(rawHtml)
                            val fullDescriptionText = cleanHtmlToPlainText(currentDescription)
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            val summaryText = fullDescriptionText.take(280)

                            val fullBody = paragraphs.ifBlank { fullDescriptionText }
                            val subtitle = if (summaryText.isNotBlank() && summaryText != cleanTitle) {
                                summaryText.take(160) + if (summaryText.length > 160) "…" else ""
                            } else {
                                ""
                            }

                            val wordCount = fullBody.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                            val readingMinutes = max(1, (wordCount + 160) / 180)
                            val idSource = (currentGuid.ifBlank { currentLink.ifBlank { cleanTitle } } + "|" + feed.url)

                            articles.add(
                                ArticleEntity(
                                    id = sha256Id(idSource),
                                    feedId = feed.id,
                                    feedTitle = feed.title,
                                    outletName = feed.outletName,
                                    category = feed.category,
                                    title = cleanTitle,
                                    subtitle = subtitle,
                                    author = currentAuthor.trim().ifBlank { "${feed.outletName} Editorial Desk" },
                                    content = fullBody,
                                    rawSummary = fullDescriptionText,
                                    link = currentLink.ifBlank { feed.url },
                                    imageUrl = extractedImg,
                                    publishedAt = parseDateMillis(currentPubDate),
                                    readingTimeMinutes = readingMinutes
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return articles.take(15)
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractFirstImgSrc(html: String): String? {
        val regex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val url = regex.find(html)?.groupValues?.getOrNull(1)
        return url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun cleanHtmlToParagraphs(html: String): String {
        if (html.isBlank()) return ""
        val withBreaks = html
            .replace(Regex("(?i)</p>|</div>|</li>|<br\\s*/?>|</h2>|</h3>"), "\n\n")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
        return decodeHtmlEntities(withBreaks)
            .lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun cleanHtmlToPlainText(html: String): String {
        if (html.isBlank()) return ""
        val stripped = html.replace(Regex("<[^>]+>"), " ")
        return decodeHtmlEntities(stripped)
    }

    private fun decodeHtmlEntities(input: String): String {
        val namedReplaced = input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#8217;", "’")
            .replace("&#8216;", "‘")
            .replace("&#8220;", "“")
            .replace("&#8221;", "”")
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&lsquo;", "‘")
            .replace("&rsquo;", "’")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&hellip;", "…")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        // Decode numeric decimal entities (e.g., &#8217; or &#160;) and hex entities (e.g., &#x2019;)
        val decimalReplaced = Regex("&#(\\d+);").replace(namedReplaced) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 32..0x10FFFF) code.toChar().toString() else match.value
        }
        return Regex("&#x([0-9a-fA-F]+);").replace(decimalReplaced) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..0x10FFFF) code.toChar().toString() else match.value
        }
    }

    private fun parseDateMillis(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(raw.trim())
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    private fun sha256Id(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
