package com.mochyy.mochymusic.core.changelog

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.core.platform.platformVersionName
import com.mochyy.mochymusic.core.updater.extractUrls
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.utils.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    versionTag: String = "",
) {
    val platformContext = LocalPlatformContext.current
    val uriHandler = LocalUriHandler.current
    val openingVersion = remember(versionTag, platformContext) {
        versionTag.ifBlank { "v" + platformVersionName(platformContext) }
    }
    val currentLabel = stringResource(Res.string.current)
    var changelogSections by remember { mutableStateOf<List<ChangelogSection>>(emptyList()) }
    var updateImage by remember { mutableStateOf<String?>(null) }
    var updateDescription by remember { mutableStateOf<String?>(null) }
    var updateWarning by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var showingCached by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var currentVersionTag by remember { mutableStateOf(openingVersion) }
    var availableReleases by remember { mutableStateOf<List<ReleaseMetadata>>(
        listOf(ReleaseMetadata(openingVersion, openingVersion, currentLabel, null))
    ) }
    var isFetchingOldReleases by remember { mutableStateOf(false) }

    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = isLoading || isFetchingOldReleases

    val scaleFraction = {
        if (isRefreshing) 1f
        else LinearOutSlowInEasing.transform(pullToRefreshState.distanceFraction).coerceIn(0f, 1f)
    }

    fun fetchChangelog(tag: String) {
        isLoading = true
        hasError = false
        coroutineScope.launch(ioDispatcher) {
            val read = ChangelogRepository.load(platformContext, tag)
            withContext(Dispatchers.Main) {
                read.fold(
                    onSuccess = { data ->
                        changelogSections = data.payload.sections
                        updateImage = data.payload.image?.takeIf { it.isNotBlank() }
                        updateDescription = data.payload.description?.takeIf { it.isNotBlank() }
                        updateWarning = data.payload.warning?.takeIf { it.isNotBlank() }
                        isLoading = false
                        showingCached = data.cached
                    },
                    onFailure = {
                        isLoading = false
                        hasError = true
                    },
                )
            }
        }
    }

    fun fetchOldReleases() {
        if (isFetchingOldReleases) return
        isFetchingOldReleases = true
        coroutineScope.launch(ioDispatcher) {
            val list = ChangelogRepository.releases().getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (list.isNotEmpty()) {
                    val currentVersion = ReleaseMetadata(openingVersion, openingVersion, currentLabel, null)
                    availableReleases = (listOf(currentVersion) + list).distinctBy { it.tagName }
                }
                isFetchingOldReleases = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchOldReleases()
    }

    LaunchedEffect(currentVersionTag) {
        withContext(ioDispatcher) { ChangelogRepository.cleanup(platformContext, currentVersionTag) }
        fetchChangelog(currentVersionTag)
    }

    Scaffold(
        modifier = Modifier.pullToRefresh(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = { fetchChangelog(currentVersionTag) }
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.changelog_title)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(Res.drawable.arrow_back), null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                if (availableReleases.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            availableReleases.forEachIndexed { index, release ->
                                ToggleButton(
                                    checked = currentVersionTag == release.tagName,
                                    onCheckedChange = {
                                        if (currentVersionTag != release.tagName) {
                                            currentVersionTag = release.tagName
                                        }
                                    },
                                    shapes = when {
                                        availableReleases.size == 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        index == availableReleases.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    modifier = Modifier.semantics { role = Role.RadioButton }
                                ) {
                                    Text(
                                        text = release.tagName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                        if (isFetchingOldReleases) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (hasError && !isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(Res.drawable.error), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(Res.string.error_loading_changelog), color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isLoading && availableReleases.isEmpty()) {

                        } else {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentVersionTag,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (showingCached) {
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                            Text(stringResource(Res.string.cached), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }

                                updateImage?.let { imageUrl ->
                                    Spacer(modifier = Modifier.height(16.dp))
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.FillWidth
                                    )
                                }

                                updateDescription?.let { desc ->
                                    Spacer(Modifier.height(16.dp))
                                    Text(desc, style = MaterialTheme.typography.bodyLarge)
                                }

                                if (changelogSections.isNotEmpty()) {
                                    changelogSections.forEach { section ->
                                        if (section.title.isNotBlank()) {
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                text = section.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(8.dp))
                                        } else {
                                            Spacer(Modifier.height(16.dp))
                                        }

                                        section.items.forEach { item ->
                                            val urls = item.extractUrls()
                                            val annotatedText = buildAnnotatedString {
                                                append(item.trim())
                                                urls.forEach { (range, url) ->
                                                    addStringAnnotation("URL", url, range.first, range.last + 1)
                                                    addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline), range.first, range.last + 1)
                                                }
                                            }
                                            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.padding(top = 8.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                                ClickableText(
                                                    text = annotatedText,
                                                    onClick = { offset ->
                                                        annotatedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                                            uriHandler.openUri(it.item)
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                                                )
                                            }
                                        }
                                    }
                                }

                                updateWarning?.let { warning ->
                                    Spacer(Modifier.height(24.dp))
                                    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(painterResource(Res.drawable.error), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        scaleX = scaleFraction()
                        scaleY = scaleFraction()
                    }
            ) {
                PullToRefreshDefaults.LoadingIndicator(state = pullToRefreshState, isRefreshing = isRefreshing)
            }
        }
    }
}
