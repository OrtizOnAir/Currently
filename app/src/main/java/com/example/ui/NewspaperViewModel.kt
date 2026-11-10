package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ArticleEntity
import com.example.data.NewspaperRepository
import com.example.data.PresetFeeds
import com.example.data.PresetRssSource
import com.example.data.RssFeedEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PrimaryDestination {
    FRONT_PAGE,
    SAVED_CLIPPINGS,
    NEWSROOM_FEEDS
}

sealed interface AiTaskStatus {
    data object Idle : AiTaskStatus
    data class Loading(val label: String) : AiTaskStatus
    data class Error(val message: String) : AiTaskStatus
    data class Success(val message: String) : AiTaskStatus
}

data class OnboardingUiState(
    val selectedCategoryIds: Set<String> = PresetFeeds.categories
        .filter { it.defaultSelected }
        .map { it.id }
        .toSet(),
    val selectedSourceUrls: Set<String> = PresetFeeds.categories
        .filter { it.defaultSelected }
        .flatMap { it.sources }
        .map { it.url }
        .toSet(),
    val customFeedUrls: List<String> = emptyList(),
    val expandedCategoryId: String? = "technology"
)

@OptIn(ExperimentalCoroutinesApi::class)
class NewspaperViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NewspaperRepository(application)

    private val _isOnboarded = MutableStateFlow(repository.hasCompletedOnboarding())
    val isOnboarded: StateFlow<Boolean> = _isOnboarded.asStateFlow()

    private val _isDarkEdition = MutableStateFlow(repository.isDarkEdition())
    val isDarkEdition: StateFlow<Boolean> = _isDarkEdition.asStateFlow()

    private val _onboardingState = MutableStateFlow(OnboardingUiState())
    val onboardingState: StateFlow<OnboardingUiState> = _onboardingState.asStateFlow()

    private val _currentDestination = MutableStateFlow(PrimaryDestination.FRONT_PAGE)
    val currentDestination: StateFlow<PrimaryDestination> = _currentDestination.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("Front Page")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    private val _customTabOrder = MutableStateFlow(repository.getSavedTabOrder())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Non-null when the user is reading the newspaper in full-page turn mode
    private val _activeReaderArticleId = MutableStateFlow<String?>(null)
    val activeReaderArticleId: StateFlow<String?> = _activeReaderArticleId.asStateFlow()

    private val _isSyncingFeeds = MutableStateFlow(false)
    val isSyncingFeeds: StateFlow<Boolean> = _isSyncingFeeds.asStateFlow()

    private val _summarizingArticleIds = MutableStateFlow<Set<String>>(emptySet())
    val summarizingArticleIds: StateFlow<Set<String>> = _summarizingArticleIds.asStateFlow()

    private val _aiTaskStatus = MutableStateFlow<AiTaskStatus>(AiTaskStatus.Idle)
    val aiTaskStatus: StateFlow<AiTaskStatus> = _aiTaskStatus.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = repository.observeNetworkConnectivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.isCurrentlyOnline())

    val cachedArticleCount: StateFlow<Int> = repository.cachedArticleCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastCacheTimestamp: StateFlow<Long?> = repository.lastCacheTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allFeeds: StateFlow<List<RssFeedEntity>> = repository.allFeeds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allArticles: StateFlow<List<ArticleEntity>> = repository.allArticles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orderedCategories: StateFlow<List<String>> = combine(
        allFeeds,
        allArticles,
        _customTabOrder
    ) { feeds, articles, savedOrder ->
        val dynamic = (feeds.filter { it.isEnabled }.map { it.category } +
            articles.map { it.category })
            .distinct()
        val baseTabs = (listOf("Front Page") + dynamic.ifEmpty {
            listOf("Technology", "Politics", "Global Health")
        }).distinct()

        if (savedOrder.isEmpty()) {
            baseTabs
        } else {
            val orderedExisting = savedOrder.filter { saved ->
                baseTabs.any { it.equals(saved, ignoreCase = true) }
            }.map { saved ->
                baseTabs.first { it.equals(saved, ignoreCase = true) }
            }
            val remainingNew = baseTabs.filter { tab ->
                orderedExisting.none { it.equals(tab, ignoreCase = true) }
            }
            orderedExisting + remainingNew
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        listOf("Front Page", "Technology", "Politics", "Global Health")
    )

    private val searchedArticles: StateFlow<List<ArticleEntity>> = _searchQuery
        .flatMapLatest { query ->
            repository.searchCachedArticles(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleArticles: StateFlow<List<ArticleEntity>> = combine(
        searchedArticles,
        allFeeds
    ) { articles, feeds ->
        val enabledFeedIds = feeds.filter { it.isEnabled }.map { it.id }.toSet()
        if (enabledFeedIds.isEmpty()) {
            articles
        } else {
            articles.filter { it.feedId in enabledFeedIds || it.id.startsWith("seed_") }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredArticles: StateFlow<List<ArticleEntity>> = combine(
        visibleArticles,
        _selectedCategoryFilter
    ) { articles, categoryFilter ->
        if (
            categoryFilter.equals("Front Page", ignoreCase = true) ||
            categoryFilter.equals("All Sections", ignoreCase = true)
        ) {
            articles
        } else {
            articles.filter { it.category.equals(categoryFilter, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (_isOnboarded.value) {
            refreshFeedsInBackground(silent = true)
        }
    }

    fun moveTabEarlier(category: String) {
        val currentList = orderedCategories.value.toMutableList()
        val index = currentList.indexOfFirst { it.equals(category, ignoreCase = true) }
        if (index > 0) {
            val item = currentList.removeAt(index)
            currentList.add(index - 1, item)
            _customTabOrder.value = currentList
            repository.saveTabOrder(currentList)
        }
    }

    fun moveTabLater(category: String) {
        val currentList = orderedCategories.value.toMutableList()
        val index = currentList.indexOfFirst { it.equals(category, ignoreCase = true) }
        if (index in 0 until currentList.lastIndex) {
            val item = currentList.removeAt(index)
            currentList.add(index + 1, item)
            _customTabOrder.value = currentList
            repository.saveTabOrder(currentList)
        }
    }

    fun resetTabOrder() {
        _customTabOrder.value = emptyList()
        repository.saveTabOrder(emptyList())
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleCategoryInOnboarding(categoryId: String) {
        val category = PresetFeeds.categories.find { it.id == categoryId } ?: return
        val categoryUrls = category.sources.map { it.url }.toSet()
        _onboardingState.update { state ->
            val willSelect = categoryId !in state.selectedCategoryIds
            val newCatIds = if (willSelect) {
                state.selectedCategoryIds + categoryId
            } else {
                state.selectedCategoryIds - categoryId
            }
            val newSourceUrls = if (willSelect) {
                state.selectedSourceUrls + categoryUrls
            } else {
                state.selectedSourceUrls - categoryUrls
            }
            state.copy(
                selectedCategoryIds = newCatIds,
                selectedSourceUrls = newSourceUrls,
                expandedCategoryId = if (willSelect) categoryId else state.expandedCategoryId
            )
        }
    }

    fun toggleExpandCategoryInOnboarding(categoryId: String) {
        _onboardingState.update { state ->
            state.copy(
                expandedCategoryId = if (state.expandedCategoryId == categoryId) null else categoryId
            )
        }
    }

    fun toggleSourceInOnboarding(source: PresetRssSource, categoryId: String) {
        _onboardingState.update { state ->
            val willSelect = source.url !in state.selectedSourceUrls
            val updatedUrls = if (willSelect) {
                state.selectedSourceUrls + source.url
            } else {
                state.selectedSourceUrls - source.url
            }
            val category = PresetFeeds.categories.find { it.id == categoryId }
            val anyRemainingInCategory = category?.sources?.any { it.url in updatedUrls } == true
            val updatedCatIds = if (anyRemainingInCategory) {
                state.selectedCategoryIds + categoryId
            } else {
                state.selectedCategoryIds - categoryId
            }
            state.copy(
                selectedCategoryIds = updatedCatIds,
                selectedSourceUrls = updatedUrls
            )
        }
    }

    fun addCustomUrlInOnboarding(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        _onboardingState.update { state ->
            if (trimmed in state.customFeedUrls) state
            else state.copy(customFeedUrls = state.customFeedUrls + trimmed)
        }
    }

    fun removeCustomUrlInOnboarding(url: String) {
        _onboardingState.update { state ->
            state.copy(customFeedUrls = state.customFeedUrls - url)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val state = _onboardingState.value
            val chosenSources = PresetFeeds.categories
                .flatMap { it.sources }
                .filter { it.url in state.selectedSourceUrls }

            repository.completeOnboardingWithSelections(
                selectedSources = chosenSources,
                customFeedUrls = state.customFeedUrls
            )
            _isOnboarded.value = true
            refreshFeedsInBackground(silent = true)
        }
    }

    fun reopenOnboardingEditor() {
        _activeReaderArticleId.value = null
        _isOnboarded.value = false
    }

    fun setDestination(destination: PrimaryDestination) {
        _activeReaderArticleId.value = null
        _currentDestination.value = destination
    }

    fun selectCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    private val attemptedFullWebFetchIds = mutableSetOf<String>()

    fun openArticleReader(articleId: String) {
        _activeReaderArticleId.value = articleId
        viewModelScope.launch {
            repository.markRead(articleId)
            val article = allArticles.value.firstOrNull { it.id == articleId }
            if (article != null) {
                ensureFullArticleLoaded(article)
            }
        }
    }

    fun ensureFullArticleLoaded(article: ArticleEntity) {
        if (article.id in attemptedFullWebFetchIds || article.id in _summarizingArticleIds.value) return
        attemptedFullWebFetchIds.add(article.id)
        viewModelScope.launch {
            _summarizingArticleIds.update { it + article.id }
            try {
                repository.fetchAndCacheFullArticleIfNeeded(article)
            } finally {
                _summarizingArticleIds.update { it - article.id }
            }
        }
    }

    fun closeArticleReader() {
        _activeReaderArticleId.value = null
    }

    fun toggleDarkEdition() {
        val next = !_isDarkEdition.value
        _isDarkEdition.value = next
        repository.setDarkEdition(next)
    }

    fun refreshFeedsInBackground(silent: Boolean = false) {
        if (_isSyncingFeeds.value) return
        viewModelScope.launch {
            _isSyncingFeeds.value = true
            try {
                val summary = repository.refreshEnabledFeeds()
                if (!silent) {
                    _statusBannerMessage.value = when {
                        summary.isOffline -> {
                            "Offline mode: Reading ${cachedArticleCount.value} cached stories from local Room storage."
                        }
                        summary.newArticles > 0 -> {
                            "Synced & cached ${summary.newArticles} stories across ${summary.fetchedFeeds} feeds for offline reading."
                        }
                        else -> {
                            "Offline Room cache is up to date (${cachedArticleCount.value} articles stored locally)."
                        }
                    }
                }
            } finally {
                _isSyncingFeeds.value = false
            }
        }
    }

    fun pruneOldOfflineCache() {
        viewModelScope.launch {
            val removed = repository.pruneOldCache(keepRecentDays = 7)
            _statusBannerMessage.value = if (removed > 0) {
                "Pruned $removed older unbookmarked articles from local Room cache."
            } else {
                "All cached stories are recent or bookmarked — no pruning needed."
            }
        }
    }

    fun summarizeArticle(article: ArticleEntity) {
        if (article.id in _summarizingArticleIds.value) return
        viewModelScope.launch {
            _summarizingArticleIds.update { it + article.id }
            _aiTaskStatus.value = AiTaskStatus.Loading("Gemini 3.8 Flash is distilling \"${article.title.take(36)}…\"")
            val result = repository.summarizeArticleWithAi(article)
            _summarizingArticleIds.update { it - article.id }
            result.onSuccess {
                _aiTaskStatus.value = AiTaskStatus.Success("Concise AI Digest cached locally for offline reading.")
            }.onFailure { err ->
                _aiTaskStatus.value = AiTaskStatus.Error(err.localizedMessage ?: "Failed to generate AI digest.")
            }
        }
    }

    fun addCustomRssFeed(url: String, title: String, category: String) {
        viewModelScope.launch {
            val result = repository.addCustomFeed(url, title, category)
            result.onSuccess { feed ->
                _statusBannerMessage.value = "Added \"${feed.title}\" & cached stories in local Room storage."
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Invalid RSS feed URL."
            }
        }
    }

    fun toggleFeedEnabled(feed: RssFeedEntity) {
        viewModelScope.launch {
            repository.toggleFeedEnabled(feed.id, !feed.isEnabled)
        }
    }

    fun removeFeed(feed: RssFeedEntity) {
        viewModelScope.launch {
            repository.removeFeed(feed.id)
            _statusBannerMessage.value = "Removed \"${feed.title}\" from your press."
        }
    }

    fun toggleBookmark(article: ArticleEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(article)
        }
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun dismissAiTaskStatus() {
        _aiTaskStatus.value = AiTaskStatus.Idle
    }
}
