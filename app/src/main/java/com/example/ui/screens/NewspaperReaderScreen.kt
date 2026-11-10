package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ArticleEntity
import com.example.ui.components.CurrentsFourColorBar
import com.example.ui.components.newspaperPageTurnEffect
import com.example.ui.components.skeuomorphicNewsprintSheet
import com.example.ui.theme.CurrentsActionBarDark
import com.example.ui.theme.CurrentsYellow
import com.example.ui.theme.currentsSectionColor

private data class StoryPageSheet(
    val article: ArticleEntity,
    val pageIndex: Int,
    val totalPages: Int,
    val paragraphs: List<String>
)

private fun isLegacySynthesizedParagraph(paragraph: String): Boolean {
    return (paragraph.startsWith("Reporting from the ") &&
        paragraph.contains("Across independent bureaus, editors are tracking")) ||
        (paragraph.startsWith("In the full syndicated dispatch, analysts note") &&
            paragraph.contains("Use the Concise AI Digest toggle above"))
}

/**
 * Leaves the story text as presented from the RSS feed (stripping any previously cached
 * synthetic boilerplate paragraphs) and keeps every story on a single vertically scrollable page.
 */
private fun paginateStoryIfNeeded(article: ArticleEntity): List<StoryPageSheet> {
    val rawBlocks = article.content
        .split(Regex("\n\\s*\n"))
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotBlank() && !isLegacySynthesizedParagraph(it) }
        .ifEmpty {
            listOf(article.rawSummary.ifBlank { article.subtitle }.trim())
                .filter { it.isNotBlank() && !isLegacySynthesizedParagraph(it) }
        }

    return listOf(
        StoryPageSheet(
            article = article,
            pageIndex = 0,
            totalPages = 1,
            paragraphs = rawBlocks
        )
    )
}

@Composable
fun NewspaperReaderScreen(
    articles: List<ArticleEntity>,
    initialArticleId: String,
    summarizingIds: Set<String> = emptySet(),
    isDarkEdition: Boolean,
    onBackToFrontPage: () -> Unit,
    onSummarizeArticle: (ArticleEntity) -> Unit = {},
    onToggleBookmark: (ArticleEntity) -> Unit,
    onMarkArticleRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackToFrontPage()
    }

    val context = LocalContext.current

    val storyPages = remember(articles) {
        articles.flatMap { article -> paginateStoryIfNeeded(article) }
    }

    val initialPageIndex = remember(initialArticleId, storyPages.size) {
        storyPages.indexOfFirst { it.article.id == initialArticleId }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { storyPages.size.coerceAtLeast(1) }
    )

    val currentSheet = storyPages.getOrNull(pagerState.currentPage)
    val currentArticle = currentSheet?.article

    LaunchedEffect(currentArticle?.id) {
        currentArticle?.let { article ->
            onMarkArticleRead(article.id)
            onSummarizeArticle(article)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("newspaper_reader_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        if (currentArticle == null || storyPages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = onBackToFrontPage, shape = RectangleShape) {
                    Text("RETURN TO EDITION")
                }
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Minimal Reader Top Action Bar (Navigation & Bookmark icons only)
            Surface(
                color = CurrentsActionBarDark,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val coolReaderIconTint = Color(0xFFBAE6FD)
                        IconButton(
                            onClick = onBackToFrontPage,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("reader_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Front Page",
                                tint = coolReaderIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onToggleBookmark(currentArticle) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("reader_bookmark_button")
                            ) {
                                Icon(
                                    imageVector = if (currentArticle.isBookmarked) {
                                        Icons.Default.Bookmark
                                    } else {
                                        Icons.Default.BookmarkBorder
                                    },
                                    contentDescription = "Clip story",
                                    tint = if (currentArticle.isBookmarked) {
                                        CurrentsYellow
                                    } else {
                                        coolReaderIconTint
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentArticle.link))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("reader_open_source_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open original source article",
                                    tint = coolReaderIconTint,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                    CurrentsFourColorBar(height = 3.dp)
                }
            }

            // Story Reader Pager — swipe from story to story in order within the current tab
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = storyPages.size > 1,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .clipToBounds()
                    .testTag("newspaper_page_turn_pager")
            ) { pageIndex ->
                val sheet = storyPages[pageIndex]
                val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction

                StoryReaderSheetView(
                    sheet = sheet,
                    isDarkEdition = isDarkEdition,
                    pageOffset = pageOffset
                )
            }
        }
    }
}

@Composable
private fun StoryReaderSheetView(
    sheet: StoryPageSheet,
    isDarkEdition: Boolean,
    pageOffset: Float
) {
    val article = sheet.article
    val sectionColor = currentsSectionColor(article.category)
    val scrollState = rememberScrollState()

    val targetProgress by remember(scrollState) {
        derivedStateOf {
            val maxScroll = scrollState.maxValue
            if (maxScroll <= 0 || maxScroll == Int.MAX_VALUE) {
                0f
            } else {
                (scrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "ArticleReadingProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .newspaperPageTurnEffect(
                pageOffset = pageOffset,
                isDarkInk = isDarkEdition
            )
            .skeuomorphicNewsprintSheet(
                isDarkInk = isDarkEdition,
                showStackedSheetEdges = false,
                showCenterFoldCrease = false
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            // Horizontal reading progress bar that fills as the user scrolls down through the content
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = sectionColor,
                trackColor = sectionColor.copy(alpha = 0.18f),
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag("article_reading_progress_bar")
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Headline
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_story_headline")
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(14.dp))

                // The story itself
                sheet.paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reader_story_paragraph")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
