package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ai.EditorialAiService
import com.example.data.ArticleEntity
import com.example.ui.components.CurrentsFourColorBar
import com.example.ui.components.CurrentsPrismLogoBadge
import com.example.ui.components.NewspaperSectionTurnTransition
import com.example.ui.components.NewspaperUnfoldVisibility
import com.example.ui.components.PageCornerCurlIndicator
import com.example.ui.components.skeuomorphicNewsprintSheet
import com.example.ui.theme.CurrentsActionBarDark
import com.example.ui.theme.CurrentsBlue
import com.example.ui.theme.CurrentsGreen
import com.example.ui.theme.CurrentsYellow
import com.example.ui.theme.IbmPlexSansFontFamily
import com.example.ui.theme.currentsSectionColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrontPageScreen(
    articles: List<ArticleEntity>,
    bookmarkedArticles: List<ArticleEntity>,
    allCategories: List<String>,
    selectedCategory: String,
    searchQuery: String,
    isOnline: Boolean,
    cachedArticleCount: Int,
    isSyncingFeeds: Boolean,
    summarizingIds: Set<String>,
    isDarkEdition: Boolean,
    onSelectCategory: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onSummarizeArticle: (ArticleEntity) -> Unit,
    onToggleBookmark: (ArticleEntity) -> Unit,
    onRefreshFeeds: () -> Unit,
    onToggleDarkEdition: () -> Unit,
    onOpenClippingsPage: () -> Unit,
    onOpenFeedsSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    val todayFormatted = remember {
        SimpleDateFormat("EEE, MMM d", Locale.US).format(Date()).uppercase(Locale.US)
    }

    val safeCategories = remember(allCategories) {
        allCategories.ifEmpty { listOf("Front Page") }
    }
    val initialTabIndex = remember(safeCategories) {
        safeCategories.indexOfFirst { it.equals(selectedCategory, ignoreCase = true) }.coerceAtLeast(0)
    }
    val tabPagerState = rememberPagerState(
        initialPage = initialTabIndex,
        pageCount = { safeCategories.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // When the user swipes horizontally between category tabs, update the selected category
    LaunchedEffect(tabPagerState.settledPage, safeCategories) {
        val pageCategory = safeCategories.getOrNull(tabPagerState.settledPage)
        if (pageCategory != null && !pageCategory.equals(selectedCategory, ignoreCase = true)) {
            onSelectCategory(pageCategory)
        }
    }

    // When the selected category changes (e.g., via tab tap or tab reorder), scroll pager to match
    LaunchedEffect(selectedCategory, safeCategories) {
        val targetIdx = safeCategories.indexOfFirst { it.equals(selectedCategory, ignoreCase = true) }
        if (targetIdx >= 0 && targetIdx != tabPagerState.currentPage && !tabPagerState.isScrollInProgress) {
            tabPagerState.animateScrollToPage(targetIdx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Iconic Google Currents (Nexus / Holo Era) Dark Action Bar
        Surface(
            color = CurrentsActionBarDark,
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Google Currents 4-Color Prism Badge + Edition Title + Sync Status
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CurrentsPrismLogoBadge()
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.masthead_title).uppercase(Locale.US),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = IbmPlexSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.testTag("offline_cache_status_pill")
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isOnline) CurrentsGreen else CurrentsYellow,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isOnline) {
                                        "$todayFormatted • $cachedArticleCount CACHED"
                                    } else {
                                        "OFFLINE EDITION • $cachedArticleCount CACHED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB8C0CC),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Right: Cool-Toned Action Bar Icons (Clippings, Search, Sync, Night Mode, RSS Settings Gear)
                    val coolIdleIconTint = Color(0xFFBAE6FD)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenClippingsPage,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("toggle_top_clippings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = "Open Saved Clippings page",
                                tint = if (bookmarkedArticles.isNotEmpty()) CurrentsYellow else coolIdleIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar && searchQuery.isNotBlank()) {
                                    onSearchQueryChange("")
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("toggle_offline_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Cached Edition",
                                tint = if (showSearchBar || searchQuery.isNotBlank()) CurrentsYellow else coolIdleIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleDarkEdition,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("toggle_dark_edition_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkEdition) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkEdition) "Switch to Light Edition" else "Switch to Night Edition",
                                tint = coolIdleIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenFeedsSettings,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("open_rss_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "RSS Feeds & Subscription Settings",
                                tint = coolIdleIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Signature Google Currents 4-Color Accent Stripe
                CurrentsFourColorBar(height = 3.dp)
            }
        }

        // 2. Google Currents Horizontal Swipeable Section Tab Strip (Holo Pager Tab Strip)
        CurrentsHoloSectionTabStrip(
            allCategories = safeCategories,
            activeSection = safeCategories.getOrElse(tabPagerState.currentPage) { selectedCategory },
            onSelectCategory = { clickedCategory ->
                onSelectCategory(clickedCategory)
                val targetIdx = safeCategories.indexOfFirst { it.equals(clickedCategory, ignoreCase = true) }
                if (targetIdx >= 0) {
                    coroutineScope.launch {
                        tabPagerState.animateScrollToPage(targetIdx)
                    }
                }
            }
        )

        // 3. Unfolding Search Bar (when active)
        NewspaperUnfoldVisibility(
            visible = showSearchBar || searchQuery.isNotBlank()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        shape = RectangleShape,
                        placeholder = {
                            Text(
                                text = "Search offline Room archive (headlines, articles, outlets)…",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("offline_archive_search_input")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // 4. Main Magazine Edition Canvas with Pull-to-Refresh & Swipeable Category Pager
        PullToRefreshBox(
            isRefreshing = isSyncingFeeds,
            onRefresh = onRefreshFeeds,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .skeuomorphicNewsprintSheet(
                    isDarkInk = isDarkEdition,
                    showStackedSheetEdges = true,
                    showCenterFoldCrease = false
                )
                .testTag("front_page_pull_to_refresh")
        ) {
            HorizontalPager(
                state = tabPagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .testTag("front_page_category_pager")
            ) { pageIndex ->
                val activeSection = safeCategories.getOrElse(pageIndex) { "Front Page" }
                val sectionAccent = currentsSectionColor(activeSection)
                val pageArticles = remember(articles, activeSection) {
                    if (
                        activeSection.equals("Front Page", ignoreCase = true) ||
                        activeSection.equals("All Sections", ignoreCase = true) ||
                        activeSection.equals("All", ignoreCase = true)
                    ) {
                        articles
                    } else {
                        articles.filter { it.category.equals(activeSection, ignoreCase = true) }
                    }
                }
                val secondaryPairs = remember(pageArticles) {
                    if (pageArticles.size <= 1) emptyList() else pageArticles.drop(1).chunked(2)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .testTag(if (pageIndex == tabPagerState.currentPage) "front_page_list" else "front_page_list_$pageIndex"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Currents Section Banner Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(sectionAccent.copy(alpha = 0.10f))
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(14.dp)
                                        .background(sectionAccent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (
                                        activeSection.equals("Front Page", ignoreCase = true) ||
                                        activeSection.equals("All", ignoreCase = true)
                                    ) {
                                        "FRONT PAGE EDITION"
                                    } else {
                                        "${activeSection.uppercase(Locale.US)} EDITION"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${pageArticles.size} STORIES",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sectionAccent
                            )
                        }
                    }

                    // Magazine Edition Stories (Hero + 2-Column Currents Tile Grid)
                    if (pageArticles.isEmpty()) {
                        item {
                            EmptyBroadsheetState(
                                hasActiveSearch = searchQuery.isNotBlank(),
                                onClearSearch = { onSearchQueryChange("") },
                                onOpenFeedsSettings = onOpenFeedsSettings
                            )
                        }
                    } else {
                        item {
                            val leadArticle = pageArticles.first()
                            LeadFrontPageStoryCard(
                                article = leadArticle,
                                isSummarizing = leadArticle.id in summarizingIds,
                                onOpenReader = { onOpenArticle(leadArticle.id) },
                                onSummarize = { onSummarizeArticle(leadArticle) },
                                onToggleBookmark = { onToggleBookmark(leadArticle) }
                            )
                        }

                        items(
                            items = secondaryPairs,
                            key = { pair -> "${activeSection}_${pair.joinToString("_") { it.id }}" }
                        ) { pair ->
                            if (pair.size == 2) {
                                val leftArticle = pair[0]
                                val rightArticle = pair[1]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SecondaryBroadsheetStoryCard(
                                        article = leftArticle,
                                        isSummarizing = leftArticle.id in summarizingIds,
                                        onOpenReader = { onOpenArticle(leftArticle.id) },
                                        onSummarize = { onSummarizeArticle(leftArticle) },
                                        onToggleBookmark = { onToggleBookmark(leftArticle) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                    SecondaryBroadsheetStoryCard(
                                        article = rightArticle,
                                        isSummarizing = rightArticle.id in summarizingIds,
                                        onOpenReader = { onOpenArticle(rightArticle.id) },
                                        onSummarize = { onSummarizeArticle(rightArticle) },
                                        onToggleBookmark = { onToggleBookmark(rightArticle) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            } else {
                                val singleArticle = pair[0]
                                SecondaryBroadsheetStoryCard(
                                    article = singleArticle,
                                    isSummarizing = singleArticle.id in summarizingIds,
                                    onOpenReader = { onOpenArticle(singleArticle.id) },
                                    onSummarize = { onSummarizeArticle(singleArticle) },
                                    onToggleBookmark = { onToggleBookmark(singleArticle) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Classic Google Currents (Nexus 7 / Android 4.1 Holo) Pager Tab Strip with colored section underline
 * that automatically scrolls the active category tab into view when swiping between pages.
 */
@Composable
private fun CurrentsHoloSectionTabStrip(
    allCategories: List<String>,
    activeSection: String,
    onSelectCategory: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeSection, allCategories) {
        val selectedIdx = allCategories.indexOfFirst { it.equals(activeSection, ignoreCase = true) }
        if (selectedIdx >= 0) {
            listState.animateScrollToItem(selectedIdx)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(allCategories, key = { _, cat -> cat }) { idx, category ->
                    val selected = category.equals(activeSection, ignoreCase = true)
                    val accentColor = currentsSectionColor(category)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { onSelectCategory(category) }
                                .padding(top = 8.dp)
                                .testTag("section_chip_${category.lowercase(Locale.US).replace(" ", "_")}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = category.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (selected) 3.dp else 0.dp)
                                    .background(if (selected) accentColor else Color.Transparent)
                            )
                        }

                        if (idx < allCategories.lastIndex) {
                            VerticalDivider(
                                modifier = Modifier.height(20.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun extractBaseStoryText(article: ArticleEntity): String {
    return article.content
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotBlank() &&
                !(line.startsWith("Reporting from the ") && line.contains("Across independent bureaus")) &&
                !(line.startsWith("In the full syndicated dispatch, analysts note"))
        }
        ?: article.rawSummary
}

/**
 * Hero Cover Story Tile displaying the headline and base story text from the RSS feed.
 */
@Composable
private fun LeadFrontPageStoryCard(
    article: ArticleEntity,
    isSummarizing: Boolean = false,
    onOpenReader: () -> Unit,
    onSummarize: () -> Unit = {},
    onToggleBookmark: () -> Unit
) {
    val sectionColor = currentsSectionColor(article.category)
    val baseStoryText = remember(article.content, article.rawSummary) {
        extractBaseStoryText(article)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .clipToBounds()
            .clickable { onOpenReader() }
            .testTag("lead_story_card_${article.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Bold Category Accent Bar across top of Hero Tile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(sectionColor)
            )

            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .clipToBounds(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Source Badge + Section + Bookmark Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(sectionColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = article.outletName.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${article.category.uppercase(Locale.US)} • ${article.readingTimeMinutes} MIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (article.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Clip article",
                            tint = if (article.isBookmarked) sectionColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Headline
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                if (baseStoryText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = baseStoryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenReader,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = sectionColor,
                            contentColor = Color.White
                        ),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("read_page_turn_button_${article.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "READ STORY →",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        PageCornerCurlIndicator(
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

/**
 * Compact Magazine Tile displaying the headline and base story text from the RSS feed.
 */
@Composable
private fun SecondaryBroadsheetStoryCard(
    article: ArticleEntity,
    isSummarizing: Boolean = false,
    onOpenReader: () -> Unit,
    onSummarize: () -> Unit = {},
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionColor = currentsSectionColor(article.category)
    val baseStoryText = remember(article.content, article.rawSummary) {
        extractBaseStoryText(article)
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .clipToBounds()
            .clickable { onOpenReader() }
            .testTag("story_card_${article.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Colored Category Top Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(sectionColor)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${article.outletName.uppercase(Locale.US)} • ${article.readingTimeMinutes}M",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sectionColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = if (article.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Clip article",
                                tint = if (article.isBookmarked) sectionColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (baseStoryText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = baseStoryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                HorizontalDivider(thickness = 0.8.dp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "READ →",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

        PageCornerCurlIndicator(
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun EmptyBroadsheetState(
    hasActiveSearch: Boolean,
    onClearSearch: () -> Unit,
    onOpenFeedsSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasActiveSearch) {
                    "No Cached Stories Match Your Search"
                } else {
                    "Add Sources to Get Started"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasActiveSearch) {
                    "Try a different keyword or clear the offline search filter."
                } else {
                    "Add or enable RSS and Atom feeds to populate this edition with stories."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (hasActiveSearch) {
                Button(
                    onClick = onClearSearch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CurrentsBlue,
                        contentColor = Color.White
                    ),
                    shape = RectangleShape,
                    modifier = Modifier.testTag("clear_search_button")
                ) {
                    Text("CLEAR SEARCH FILTER")
                }
            } else {
                Button(
                    onClick = onOpenFeedsSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CurrentsBlue,
                        contentColor = Color.White
                    ),
                    shape = RectangleShape,
                    modifier = Modifier.testTag("empty_open_feeds_button")
                ) {
                    Text("ADD SOURCES VIA RSS")
                }
            }
        }
    }
}
