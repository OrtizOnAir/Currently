package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FeedCategoryPreset
import com.example.data.PresetFeeds
import com.example.data.PresetRssSource
import com.example.ui.OnboardingUiState
import com.example.ui.components.CurrentsFourColorBar
import com.example.ui.components.CurrentsPrismLogoBadge
import com.example.ui.components.NewspaperUnfoldVisibility
import com.example.ui.components.skeuomorphicNewsprintSheet
import com.example.ui.theme.CurrentsActionBarDark
import com.example.ui.theme.CurrentsBlue
import com.example.ui.theme.IbmPlexSansFontFamily
import com.example.ui.theme.currentsSectionColor
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    isDarkEdition: Boolean = false,
    onToggleCategory: (String) -> Unit,
    onToggleExpandCategory: (String) -> Unit,
    onToggleSource: (PresetRssSource, String) -> Unit,
    onAddCustomUrl: (String) -> Unit,
    onRemoveCustomUrl: (String) -> Unit,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customUrlInput by rememberSaveable { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .statusBarsPadding()
            ) {
                // Google Currents (Nexus Era) Dark Holo Top Action Bar
                Surface(
                    color = CurrentsActionBarDark,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "BUILD YOUR PERSONALIZED CURRENTLY STREAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB8C0CC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        CurrentsFourColorBar(height = 3.dp)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .skeuomorphicNewsprintSheet(
                            isDarkInk = isDarkEdition,
                            showStackedSheetEdges = true,
                            showCenterFoldCrease = false
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Currents Magazine Edition Banner Header
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_masthead_ornament_1794258782349),
                                    contentDescription = "Editorial illustration",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(124.dp)
                                        .clipToBounds(),
                                    contentScale = ContentScale.Crop
                                )
                                CurrentsFourColorBar(height = 2.5.dp)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.onboarding_title),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        softWrap = true
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.onboarding_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                            }
                        }
                    }

                    // 2. Pre-made Category Editions (Technology, Politics, Global Health, etc.)
                    items(PresetFeeds.categories, key = { it.id }) { category ->
                        val isCategorySelected = category.id in state.selectedCategoryIds
                        val isExpanded = state.expandedCategoryId == category.id
                        val selectedCountInCat = category.sources.count { it.url in state.selectedSourceUrls }

                        OnboardingCategoryCard(
                            category = category,
                            isSelected = isCategorySelected,
                            isExpanded = isExpanded,
                            selectedSourceCount = selectedCountInCat,
                            selectedSourceUrls = state.selectedSourceUrls,
                            onToggleCategory = { onToggleCategory(category.id) },
                            onToggleExpand = { onToggleExpandCategory(category.id) },
                            onToggleSource = { src -> onToggleSource(src, category.id) }
                        )
                    }

                    // 3. Custom RSS Feed Input Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RssFeed,
                                        contentDescription = null,
                                        tint = CurrentsBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ADD CUSTOM RSS OR ATOM FEED",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Paste any RSS or Atom URL to include it in your personalized Currently edition.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = customUrlInput,
                                        onValueChange = { customUrlInput = it },
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.add_custom_feed_hint),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("onboarding_custom_rss_input")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (customUrlInput.isNotBlank()) {
                                                onAddCustomUrl(customUrlInput)
                                                customUrlInput = ""
                                            }
                                        },
                                        shape = RectangleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                        modifier = Modifier.testTag("onboarding_add_rss_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add RSS URL"
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add")
                                    }
                                }

                                if (state.customFeedUrls.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        state.customFeedUrls.forEach { url ->
                                            FilterChip(
                                                selected = true,
                                                onClick = { onRemoveCustomUrl(url) },
                                                label = {
                                                    Text(
                                                        text = url.removePrefix("https://").removePrefix("http://").take(26),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove custom feed",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Currents Launch Bar
                Surface(
                    color = CurrentsActionBarDark,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        CurrentsFourColorBar(height = 2.5.dp)
                        val totalFeedsCount = state.selectedSourceUrls.size + state.customFeedUrls.size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${state.selectedCategoryIds.size} EDITIONS • $totalFeedsCount FEEDS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CurrentsBlue
                                )
                                Text(
                                    text = "Includes offline Room sync & AI digests",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB8C0CC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = onFinishOnboarding,
                                enabled = totalFeedsCount > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CurrentsBlue,
                                    contentColor = Color.White
                                ),
                                shape = RectangleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("complete_onboarding_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Newspaper,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_cta),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingCategoryCard(
    category: FeedCategoryPreset,
    isSelected: Boolean,
    isExpanded: Boolean,
    selectedSourceCount: Int,
    selectedSourceUrls: Set<String>,
    onToggleCategory: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleSource: (PresetRssSource) -> Unit
) {
    val sectionColor = currentsSectionColor(category.title)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RectangleShape,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) sectionColor else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .testTag("onboarding_category_${category.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Colored Currents Category Top Stripe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(if (isSelected) sectionColor else MaterialTheme.colorScheme.outline)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCategory() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) sectionColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(sectionColor)
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = category.badgeCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        category.sources.map { it.outletName }.distinct().forEach { outlet ->
                            Text(
                                text = "• $outlet",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sectionColor
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.testTag("expand_category_${category.id}")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse sources" else "Customize sources"
                    )
                }
            }

            NewspaperUnfoldVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "INDEPENDENT SOURCES ($selectedSourceCount/${category.sources.size} SELECTED)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sectionColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    category.sources.forEach { source ->
                        val sourceSelected = source.url in selectedSourceUrls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSource(source) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = sourceSelected,
                                onClick = { onToggleSource(source) },
                                label = { Text(source.outletName, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (sourceSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = sectionColor.copy(alpha = 0.16f),
                                    selectedLabelColor = sectionColor
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = true
                                )
                                Text(
                                    text = source.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
