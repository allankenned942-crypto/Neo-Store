package com.machiav3lli.fdroid.ui.pages

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.machiav3lli.fdroid.NeoActivity
import com.machiav3lli.fdroid.NeoApp
import com.machiav3lli.fdroid.R
import com.machiav3lli.fdroid.data.content.Preferences
import com.machiav3lli.fdroid.data.entity.DialogKey
import com.machiav3lli.fdroid.data.entity.Source
import com.machiav3lli.fdroid.ui.components.DelayedLinearProgressBar
import com.machiav3lli.fdroid.ui.components.ProductsListItem
import com.machiav3lli.fdroid.ui.components.RoundButton
import com.machiav3lli.fdroid.ui.components.SelectChip
import com.machiav3lli.fdroid.ui.components.SortFilterButton
import com.machiav3lli.fdroid.ui.components.SuggestionsPopup
import com.machiav3lli.fdroid.ui.components.TopBar
import com.machiav3lli.fdroid.ui.components.WideSearchField
import com.machiav3lli.fdroid.ui.compose.icons.Phosphor
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.ArrowSquareOut
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.CircleWavyWarning
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.CirclesFour
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.HeartStraight
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.X
import com.machiav3lli.fdroid.ui.dialog.BaseDialog
import com.machiav3lli.fdroid.ui.dialog.KeyDialogUI
import com.machiav3lli.fdroid.ui.navigation.NavItem
import com.machiav3lli.fdroid.utils.extension.koinNeoViewModel
import com.machiav3lli.fdroid.utils.onLaunchClick
import com.machiav3lli.fdroid.viewmodels.MainVM
import com.machiav3lli.fdroid.viewmodels.SearchVM
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import com.machiav3lli.fdroid.data.index.AuroraSearch
import com.machiav3lli.fdroid.data.index.GithubSource
import coil3.compose.AsyncImage
import com.machiav3lli.fdroid.ui.components.SourceBadge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.machiav3lli.fdroid.data.index.ForgeSearch
import com.machiav3lli.fdroid.viewmodels.PrefsVM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchPage(
    query: String = "",
    viewModel: SearchVM = koinNeoViewModel(),
    mainVM: MainVM = koinNeoViewModel(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val neoActivity = LocalActivity.current as NeoActivity
    val openDialog = remember { mutableStateOf(false) }
    val dialogKey: MutableState<DialogKey?> = remember { mutableStateOf(null) }

    val listState = rememberLazyListState()
    val pageState by viewModel.pageState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val dataState by mainVM.dataState.collectAsStateWithLifecycle()
    val prefsVM: PrefsVM = koinNeoViewModel()
    val playScope = rememberCoroutineScope()
    var playResults by remember { mutableStateOf<List<AuroraSearch.Hit>>(emptyList()) }
    var playLoading by remember { mutableStateOf(false) }
    LaunchedEffect(pageState.query) {
        val q = pageState.query.trim()
        if (q.length < 3) {
            playResults = emptyList()
            return@LaunchedEffect
        }
        delay(800)
        playLoading = true
        try {
            playResults = AuroraSearch.search(context, q)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            playResults = emptyList()
        } finally {
            playLoading = false
        }
    }

    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val queryState = rememberTextFieldState(pageState.query)
    var lastProcessedQuery by rememberSaveable { mutableStateOf("") }
    val suggestions = if (pageState.query.isBlank()) {
        searchHistory.take(5)
    } else {
        searchHistory.filter {
            it.contains(pageState.query, true)
        }
    }
    var showSuggestions by remember { mutableStateOf(true) }

    val currentTab by remember {
        derivedStateOf {
            listOf(
                Source.SEARCH,
                Source.SEARCH_INSTALLED,
                Source.SEARCH_NEW,
                Source.SEARCH_FAVORITES
            )
                .indexOf(pageState.source)
        }
    }

    val modifiedSortFilter by remember(pageState.sortFilter) {
        derivedStateOf {
            Preferences[Preferences.Key.SortOrderSearch] != Preferences.Key.SortOrderSearch.default.value ||
                    Preferences[Preferences.Key.SortOrderAscendingSearch] != Preferences.Key.SortOrderAscendingSearch.default.value ||
                    Preferences[Preferences.Key.ReposFilterSearch] != Preferences.Key.ReposFilterSearch.default.value ||
                    Preferences[Preferences.Key.CategoriesFilterSearch] != Preferences.Key.CategoriesFilterSearch.default.value ||
                    Preferences[Preferences.Key.LicensesFilterSearch] != Preferences.Key.LicensesFilterSearch.default.value ||
                    Preferences[Preferences.Key.AntifeaturesFilterSearch] != Preferences.Key.AntifeaturesFilterSearch.default.value ||
                    Preferences[Preferences.Key.MinTargetSDKSearch] != Preferences.Key.MinTargetSDKSearch.default.value ||
                    Preferences[Preferences.Key.MaxTargetSDKSearch] != Preferences.Key.MaxTargetSDKSearch.default.value ||
                    Preferences[Preferences.Key.MinMinSDKSearch] != Preferences.Key.MinMinSDKSearch.default.value ||
                    Preferences[Preferences.Key.MaxMinSDKSearch] != Preferences.Key.MaxMinSDKSearch.default.value
        }
    }

    BackHandler(enabled = pageState.query.isNotBlank()) {
        // TODO find another solution as this blocks the predictive back gesture
        viewModel.setSearchQuery("")
        onDismiss()
    }

    var srcFilter by rememberSaveable { mutableStateOf("") }
    var forgeResults by remember { mutableStateOf<List<ForgeSearch.Hit>>(emptyList()) }
    LaunchedEffect(pageState.query) {
        val q = pageState.query.trim()
        if (q.length < 3) {
            forgeResults = emptyList()
            return@LaunchedEffect
        }
        delay(1200)
        forgeResults = try {
            ForgeSearch.search(q)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(query) {
        if (query != lastProcessedQuery) {
            viewModel.setSearchQuery(query)
            queryState.setTextAndPlaceCursorAtEnd(query)
            showSuggestions = true
            lastProcessedQuery = query
        }
    }

    LaunchedEffect(Unit) {
        Preferences.addPreferencesChangeListener {
            when (it) {
                Preferences.Key.ReposFilterSearch,
                Preferences.Key.CategoriesFilterSearch,
                Preferences.Key.AntifeaturesFilterSearch,
                Preferences.Key.LicensesFilterSearch,
                Preferences.Key.SortOrderSearch,
                Preferences.Key.SortOrderAscendingSearch,
                Preferences.Key.MinTargetSDKSearch,
                Preferences.Key.MaxTargetSDKSearch,
                Preferences.Key.MinMinSDKSearch,
                Preferences.Key.MaxMinSDKSearch,
                    -> viewModel.setSortFilter(
                    listOf(
                        Preferences[Preferences.Key.ReposFilterSearch],
                        Preferences[Preferences.Key.CategoriesFilterSearch],
                        Preferences[Preferences.Key.AntifeaturesFilterSearch],
                        Preferences[Preferences.Key.LicensesFilterSearch],
                        Preferences[Preferences.Key.SortOrderSearch],
                        Preferences[Preferences.Key.SortOrderAscendingSearch],
                        Preferences[Preferences.Key.MinTargetSDKSearch],
                        Preferences[Preferences.Key.MaxTargetSDKSearch],
                        Preferences[Preferences.Key.MinMinSDKSearch],
                        Preferences[Preferences.Key.MaxMinSDKSearch],
                    ).toString()
                )

                else -> {}
            }
        }
    }

    val searchBar: @Composable (() -> Unit) = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(
                withTopBarInsets = !Preferences[Preferences.Key.BottomSearchBar],
            ) {
                WideSearchField(
                    queryState = queryState,
                    modifier = Modifier.weight(1f),
                    onQueryChanged = { newQuery ->
                        viewModel.setSearchQuery(newQuery)
                        showSuggestions = true
                    },
                    onCleanQuery = {
                        viewModel.setSearchQuery("")
                    },
                    onDone = {
                        viewModel.submitSearchQuery(queryState.text.toString())
                        showSuggestions = false
                    },
                )
                SortFilterButton(isModified = modifiedSortFilter) {
                    neoActivity.navigateSortFilterSheet(NavItem.Search)
                }
                RoundButton(
                    icon = Phosphor.X,
                    description = stringResource(id = R.string.cancel),
                ) {
                    onDismiss()
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                item {
                    SelectChip(
                        text = stringResource(id = R.string.all),
                        icon = Phosphor.CirclesFour,
                        checked = currentTab == 0 && srcFilter == "",
                        alwaysShowIcon = true,
                    ) {
                        srcFilter = ""
                        viewModel.setSearchSource(Source.SEARCH)
                    }
                }
                item {
                    SelectChip(
                        text = "Aurora",
                        checked = srcFilter == "aurora",
                        alwaysShowIcon = false,
                    ) {
                        srcFilter = if (srcFilter == "aurora") "" else "aurora"
                        viewModel.setSearchSource(Source.SEARCH)
                    }
                }
                item {
                    SelectChip(
                        text = "Komi",
                        checked = srcFilter == "komi",
                        alwaysShowIcon = false,
                    ) {
                        srcFilter = if (srcFilter == "komi") "" else "komi"
                        viewModel.setSearchSource(Source.SEARCH)
                    }
                }
                item {
                    SelectChip(
                        text = stringResource(id = R.string.installed),
                        icon = Phosphor.ArrowSquareOut,
                        checked = currentTab == 1,
                        alwaysShowIcon = true,
                    ) {
                        srcFilter = ""
                        viewModel.setSearchSource(Source.SEARCH_INSTALLED)
                    }
                }
                item {
                    SelectChip(
                        text = stringResource(id = R.string.new_applications),
                        icon = Phosphor.CircleWavyWarning,
                        checked = currentTab == 2,
                        alwaysShowIcon = true,
                    ) {
                        srcFilter = ""
                        viewModel.setSearchSource(Source.SEARCH_NEW)
                    }
                }
                item {
                    SelectChip(
                        text = stringResource(id = R.string.favorite_applications),
                        icon = Phosphor.HeartStraight,
                        checked = currentTab == 3,
                        alwaysShowIcon = true,
                    ) {
                        srcFilter = ""
                        viewModel.setSearchSource(Source.SEARCH_FAVORITES)
                    }
                }
            }
            DropdownMenuPopup(
                expanded = showSuggestions && suggestions.isNotEmpty(),
                onDismissRequest = {
                    showSuggestions = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                properties = PopupProperties(focusable = false),
                popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
                    if (!Preferences[Preferences.Key.BottomSearchBar])
                        MenuAnchorPosition.Above else MenuAnchorPosition.Below
                )
            ) {
                SuggestionsPopup(
                    suggestions = suggestions,
                    onSuggestion = { suggestion ->
                        viewModel.setSearchQuery(suggestion)
                        viewModel.submitSearchQuery(suggestion)
                        showSuggestions = false
                    },
                    onClearHistory = {
                        viewModel.clearHistory()
                        showSuggestions = false
                    }
                )
            }
        }
    }

    val shownProducts = when (srcFilter) {
        "aurora" -> pageState.filteredProducts.filter {
            dataState.reposMap[it.repositoryId]?.address?.startsWith("aurora:") == true
        }
        "komi" -> pageState.filteredProducts.filter {
            dataState.reposMap[it.repositoryId]?.address
                ?.let { a -> GithubSource.isGithub(a) } == true
        }
        else -> pageState.filteredProducts
    }

    val productsList: @Composable (() -> Unit) = {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            state = listState,
        ) {
            items(
                items = shownProducts,
                key = { it.packageName },
            ) { item ->
                ProductsListItem(
                    item = item,
                    repo = dataState.reposMap[item.repositoryId],
                    isFavorite = dataState.favorites.contains(item.packageName),
                    onUserClick = {
                        neoActivity.navigateProduct(it.packageName)
                    },
                    onFavouriteClick = {
                        mainVM.setFavorite(
                            it.packageName,
                            !dataState.favorites.contains(it.packageName)
                        )
                    },
                    installed = pageState.installedMap[item.packageName],
                    onActionClick = {
                        val installed = pageState.installedMap[it.packageName]
                        val action = {
                            NeoApp.wm.install(
                                Pair(it.packageName, it.repositoryId)
                            )
                        }
                        if (installed != null && installed.launcherActivities.isNotEmpty())
                            context.onLaunchClick(
                                installed,
                                neoActivity.supportFragmentManager
                            )
                        else if (Preferences[Preferences.Key.DownloadShowDialog]) {
                            dialogKey.value = DialogKey.Download(it.name, action)
                            openDialog.value = true
                        } else action()
                    }
                )
            }
            if (forgeResults.isNotEmpty() && (srcFilter == "" || srcFilter == "komi")) {
                item(key = "forge_header") {
                    Text(
                        text = "GitHub / Codeberg",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(items = forgeResults, key = { "forge_" + it.url }) { r ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playScope.launch {
                                    val id = prefsVM.addForgeRepository(r.url, r.name)
                                    if (id == null) {
                                        android.widget.Toast.makeText(
                                            context, "Ja adicionado: ${r.name}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    android.widget.Toast.makeText(
                                        context, "Sincronizando ${r.name} (baixa o APK)...",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    val (pkg, err) = prefsVM.waitForRepoPackage(id)
                                    if (pkg != null) neoActivity.navigateProduct(pkg)
                                    else android.widget.Toast.makeText(
                                        context, err, android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r.icon.isNotBlank()) AsyncImage(
                                model = r.icon, contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                            ) else Box(Modifier.size(44.dp))
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${r.host} - ${r.stars} estrelas" +
                                            (if (r.desc.isNotBlank()) " - ${r.desc.take(80)}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                            SourceBadge("KOMI", 2)
                        }
                    }
                }
            }
            val localPkgs = pageState.filteredProducts.map { it.packageName }.toSet()
            val hits = if (srcFilter == "" || srcFilter == "aurora") playResults.filter { it.pkg !in localPkgs } else emptyList()
            if (hits.isNotEmpty()) {
                item(key = "play_header") {
                    Text(
                        text = "Google Play",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(items = hits, key = { "play_" + it.pkg }) { r ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playScope.launch {
                                    val id = prefsVM.addPlayRepository(r.pkg, r.name)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (id == null) "Abrindo: ${r.name}"
                                        else "Sincronizando: ${r.name}...",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    var found = false
                                    for (i in 0 until 40) {
                                        found = kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.IO
                                        ) { mainVM.productExist(r.pkg) }
                                        if (found) break
                                        delay(1000)
                                    }
                                    if (found) neoActivity.navigateProduct(r.pkg)
                                    else android.widget.Toast.makeText(
                                        context,
                                        "Nao sincronizou (pode ser erro 429). Tente de novo",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r.icon.isNotBlank()) AsyncImage(
                                model = r.icon, contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                            ) else Box(Modifier.size(44.dp))
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge)
                                Text(r.pkg, style = MaterialTheme.typography.bodySmall)
                            }
                            SourceBadge("AURORA", 1)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (!Preferences[Preferences.Key.BottomSearchBar]) {
                Column {
                    searchBar()
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        },
        bottomBar = {
            if (Preferences[Preferences.Key.BottomSearchBar]) {
                Column(
                    modifier = Modifier.windowInsetsPadding(BottomAppBarDefaults.windowInsets)
                ) {
                    HorizontalDivider(thickness = 0.5.dp)
                    searchBar()
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            DelayedLinearProgressBar(
                visible = isLoading,
                modifier = Modifier.padding(8.dp)
            )
            if (!pageState.isInitialized)
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            else if (pageState.filteredProducts.isEmpty() && pageState.query.isNotBlank() && playResults.isEmpty() && !playLoading)
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.application_not_found)
                    )
                }
            else productsList()
        }
    }

    if (openDialog.value) {
        BaseDialog(openDialogCustom = openDialog) {
            when (dialogKey.value) {
                is DialogKey.Download -> KeyDialogUI(
                    key = dialogKey.value,
                    openDialog = openDialog,
                    primaryAction = {
                        if (Preferences[Preferences.Key.ActionLockDialog] != Preferences.ActionLock.None)
                            neoActivity.launchLockPrompt {
                                (dialogKey.value as DialogKey.Download).action()
                                openDialog.value = false
                            }
                        else {
                            (dialogKey.value as DialogKey.Download).action()
                            openDialog.value = false
                        }
                    },
                    onDismiss = {
                        dialogKey.value = null
                        openDialog.value = false
                    }
                )

                else                  -> {}
            }
        }
    }
}
