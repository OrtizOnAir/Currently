package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.AiTaskStatus
import com.example.ui.NewspaperViewModel
import com.example.ui.PrimaryDestination
import com.example.ui.components.NewspaperSectionTurnTransition
import com.example.ui.components.NewspaperUnfoldVisibility
import com.example.ui.screens.FrontPageScreen
import com.example.ui.screens.NewspaperReaderScreen
import com.example.ui.screens.NewsroomFeedsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SavedClippingsScreen
import com.example.ui.theme.FolioPressTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // STEP 2: Initialize Compose UI
        setContent {
            val viewModel: NewspaperViewModel = viewModel()
            val isDarkEdition by viewModel.isDarkEdition.collectAsStateWithLifecycle()

            FolioPressTheme(darkTheme = isDarkEdition) {
                FolioPressApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FolioPressApp(viewModel: NewspaperViewModel) {
    val isOnboarded by viewModel.isOnboarded.collectAsStateWithLifecycle()
    val onboardingState by viewModel.onboardingState.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val cachedArticleCount by viewModel.cachedArticleCount.collectAsStateWithLifecycle()
    val lastCacheTimestamp by viewModel.lastCacheTimestamp.collectAsStateWithLifecycle()
    val activeReaderArticleId by viewModel.activeReaderArticleId.collectAsStateWithLifecycle()
    val allFeeds by viewModel.allFeeds.collectAsStateWithLifecycle()
    val allArticles by viewModel.allArticles.collectAsStateWithLifecycle()
    val visibleArticles by viewModel.visibleArticles.collectAsStateWithLifecycle()
    val filteredArticles by viewModel.filteredArticles.collectAsStateWithLifecycle()
    val isSyncingFeeds by viewModel.isSyncingFeeds.collectAsStateWithLifecycle()
    val summarizingIds by viewModel.summarizingArticleIds.collectAsStateWithLifecycle()
    val aiTaskStatus by viewModel.aiTaskStatus.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isDarkEdition by viewModel.isDarkEdition.collectAsStateWithLifecycle()

    val availableCategories by viewModel.orderedCategories.collectAsStateWithLifecycle()

    val cachedDigestCount = remember(allArticles) {
        allArticles.count { !it.aiBulletsJson.isNullOrBlank() }
    }

    val bookmarkedArticles = remember(allArticles) {
        allArticles.filter { it.isBookmarked }
    }

    val topLevelPageKey = remember(isOnboarded, activeReaderArticleId) {
        when {
            !isOnboarded -> "ONBOARDING_CHARTER"
            activeReaderArticleId != null -> "READER_$activeReaderArticleId"
            else -> "EDITION_MAIN"
        }
    }

    NewspaperSectionTurnTransition(
        targetState = topLevelPageKey,
        isDarkInk = isDarkEdition,
        modifier = Modifier.fillMaxSize()
    ) { pageKey ->
        when {
            pageKey == "ONBOARDING_CHARTER" -> {
                OnboardingScreen(
                    state = onboardingState,
                    isDarkEdition = isDarkEdition,
                    onToggleCategory = viewModel::toggleCategoryInOnboarding,
                    onToggleExpandCategory = viewModel::toggleExpandCategoryInOnboarding,
                    onToggleSource = viewModel::toggleSourceInOnboarding,
                    onAddCustomUrl = viewModel::addCustomUrlInOnboarding,
                    onRemoveCustomUrl = viewModel::removeCustomUrlInOnboarding,
                    onFinishOnboarding = viewModel::completeOnboarding
                )
            }

            pageKey.startsWith("READER_") -> {
                val readerArticleId = pageKey.removePrefix("READER_")
                val readerPool = if (currentDestination == PrimaryDestination.SAVED_CLIPPINGS) {
                    bookmarkedArticles.ifEmpty { filteredArticles.ifEmpty { allArticles } }
                } else {
                    filteredArticles.ifEmpty { allArticles }
                }
                NewspaperReaderScreen(
                    articles = readerPool,
                    initialArticleId = readerArticleId,
                    summarizingIds = summarizingIds,
                    isDarkEdition = isDarkEdition,
                    onBackToFrontPage = viewModel::closeArticleReader,
                    onSummarizeArticle = viewModel::ensureFullArticleLoaded,
                    onToggleBookmark = viewModel::toggleBookmark,
                    onMarkArticleRead = {}
                )
            }

            else -> {
                if (currentDestination != PrimaryDestination.FRONT_PAGE) {
                    BackHandler {
                        viewModel.setDestination(PrimaryDestination.FRONT_PAGE)
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            // Status / AI Task Banner
                            EditorialStatusBanner(
                                aiTaskStatus = aiTaskStatus,
                                statusMessage = statusBannerMessage,
                                onDismissAiStatus = viewModel::dismissAiTaskStatus,
                                onDismissMessage = viewModel::clearStatusBanner
                            )

                            // Slide Transition between Front Page and Settings
                            NewspaperSectionTurnTransition(
                                targetState = currentDestination,
                                isDarkInk = isDarkEdition,
                                modifier = Modifier.weight(1f)
                            ) { destination ->
                                when (destination) {
                                    PrimaryDestination.FRONT_PAGE -> {
                                        FrontPageScreen(
                                            articles = visibleArticles,
                                            bookmarkedArticles = bookmarkedArticles,
                                            allCategories = availableCategories,
                                            selectedCategory = selectedCategory,
                                            searchQuery = searchQuery,
                                            isOnline = isOnline,
                                            cachedArticleCount = cachedArticleCount,
                                            isSyncingFeeds = isSyncingFeeds,
                                            summarizingIds = summarizingIds,
                                            isDarkEdition = isDarkEdition,
                                            onSelectCategory = viewModel::selectCategoryFilter,
                                            onSearchQueryChange = viewModel::updateSearchQuery,
                                            onOpenArticle = viewModel::openArticleReader,
                                            onSummarizeArticle = viewModel::summarizeArticle,
                                            onToggleBookmark = viewModel::toggleBookmark,
                                            onRefreshFeeds = { viewModel.refreshFeedsInBackground(silent = false) },
                                            onToggleDarkEdition = viewModel::toggleDarkEdition,
                                            onOpenClippingsPage = {
                                                viewModel.setDestination(PrimaryDestination.SAVED_CLIPPINGS)
                                            },
                                            onOpenFeedsSettings = {
                                                viewModel.setDestination(PrimaryDestination.NEWSROOM_FEEDS)
                                            }
                                        )
                                    }

                                    PrimaryDestination.SAVED_CLIPPINGS -> {
                                        SavedClippingsScreen(
                                            bookmarkedArticles = bookmarkedArticles,
                                            isDarkEdition = isDarkEdition,
                                            onBackToFrontPage = {
                                                viewModel.setDestination(PrimaryDestination.FRONT_PAGE)
                                            },
                                            onOpenArticle = viewModel::openArticleReader,
                                            onRemoveBookmark = viewModel::toggleBookmark
                                        )
                                    }

                                    PrimaryDestination.NEWSROOM_FEEDS -> {
                                        NewsroomFeedsScreen(
                                            feeds = allFeeds,
                                            orderedCategories = availableCategories,
                                            cachedArticleCount = cachedArticleCount,
                                            cachedDigestCount = cachedDigestCount,
                                            lastCacheTimestamp = lastCacheTimestamp,
                                            isOnline = isOnline,
                                            isSyncingFeeds = isSyncingFeeds,
                                            isDarkEdition = isDarkEdition,
                                            onBackToFrontPage = {
                                                viewModel.setDestination(PrimaryDestination.FRONT_PAGE)
                                            },
                                            onMoveTabEarlier = viewModel::moveTabEarlier,
                                            onMoveTabLater = viewModel::moveTabLater,
                                            onResetTabOrder = viewModel::resetTabOrder,
                                            onSyncAndCacheNow = { viewModel.refreshFeedsInBackground(silent = false) },
                                            onPruneOldCache = viewModel::pruneOldOfflineCache,
                                            onAddCustomFeed = viewModel::addCustomRssFeed,
                                            onToggleFeedEnabled = viewModel::toggleFeedEnabled,
                                            onRemoveFeed = viewModel::removeFeed,
                                            onReopenOnboardingCategories = viewModel::reopenOnboardingEditor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialStatusBanner(
    aiTaskStatus: AiTaskStatus,
    statusMessage: String?,
    onDismissAiStatus: () -> Unit,
    onDismissMessage: () -> Unit
) {
    val activeText: String? = when (aiTaskStatus) {
        is AiTaskStatus.Loading -> aiTaskStatus.label
        is AiTaskStatus.Success -> aiTaskStatus.message
        is AiTaskStatus.Error -> aiTaskStatus.message
        AiTaskStatus.Idle -> statusMessage
    }

    NewspaperUnfoldVisibility(visible = activeText != null) {
        val isError = aiTaskStatus is AiTaskStatus.Error
        val isLoading = aiTaskStatus is AiTaskStatus.Loading
        Surface(
            color = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("editorial_status_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = activeText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
                IconButton(
                    onClick = {
                        if (aiTaskStatus != AiTaskStatus.Idle) onDismissAiStatus()
                        if (statusMessage != null) onDismissMessage()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss notice",
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
