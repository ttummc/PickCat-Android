package dao3.pickcat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dao3.pickcat.core.network.UserRef
import dao3.pickcat.feature.composer.ComposerScreen
import dao3.pickcat.feature.forum.CategoryScreen
import dao3.pickcat.feature.forum.ForumScreen
import dao3.pickcat.feature.home.HomeScreen
import dao3.pickcat.feature.library.LibraryScreen
import dao3.pickcat.feature.media.FileStateTab
import dao3.pickcat.feature.media.MediaScreen
import dao3.pickcat.feature.notifications.LikesScreen
import dao3.pickcat.feature.notifications.NotificationsScreen
import dao3.pickcat.feature.profile.CollectionScreen
import dao3.pickcat.feature.profile.ProfileScreen
import dao3.pickcat.feature.search.SearchScreen
import dao3.pickcat.feature.settings.EditProfileScreen
import dao3.pickcat.feature.settings.SettingsScreen
import dao3.pickcat.feature.topic.TopicDetailScreen
import dao3.pickcat.feature.work.WorkDetailScreen
import dao3.pickcat.feature.work.WorksScreen
import dao3.pickcat.core.ui.EmptyState
import dao3.pickcat.core.ui.OfflineBanner

private object Routes {
    const val HOME = "home"
    const val FORUM = "forum"
    const val CATEGORY = "category/{slug}"
    const val TOPIC = "topic/{topicId}?postNumber={postNumber}"
    const val COMPOSER = "composer"
    const val WORKS = "works"
    const val WORK = "work/{workId}"
    const val PROFILE = "profile/{userId}"
    const val COLLECTION = "collection/{collectionId}"
    const val ME = "me"
    const val NOTIFICATIONS = "notifications"
    const val LIKES = "likes"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit-profile"
    const val MEDIA = "media"
    const val GALLERY = "gallery"
    const val LIBRARY = "library"

    fun category(slug: String) = "category/$slug"
    fun topic(id: String, postNumber: Int? = null) =
        "topic/$id" + (postNumber?.let { "?postNumber=$it" } ?: "")
    fun work(id: String) = "work/$id"
    fun profile(id: String) = "profile/$id"
    fun collection(id: String) = "collection/$id"
}

private data class MainDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String,
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun PickCatMainShell(
    appViewModel: AppViewModel,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    val navController = rememberNavController()
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val isOnline by appViewModel.isOnline.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        if (activity?.intent?.getBooleanExtra(NotificationSyncWorker.EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            activity.intent.removeExtra(NotificationSyncWorker.EXTRA_OPEN_NOTIFICATIONS)
            navController.navigate(Routes.NOTIFICATIONS)
        }
    }
    val widthSizeClass = runCatching {
        calculateWindowSizeClass(context as android.app.Activity).widthSizeClass
    }.getOrDefault(WindowWidthSizeClass.Compact)
    val expanded = widthSizeClass != WindowWidthSizeClass.Compact
    val currentUser = session.user
    val reselectEvents = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val scope = rememberCoroutineScope()

    val destinations = listOf(
        MainDestination(
            label = "首页",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            route = Routes.HOME,
        ),
        MainDestination(
            label = "论坛",
            selectedIcon = Icons.Filled.Forum,
            unselectedIcon = Icons.Outlined.Forum,
            route = Routes.FORUM,
        ),
        MainDestination(
            label = "作品",
            selectedIcon = Icons.Filled.VideoLibrary,
            unselectedIcon = Icons.Outlined.VideoLibrary,
            route = Routes.WORKS,
        ),
        MainDestination(
            label = "我的",
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            route = Routes.ME,
        ),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute == Routes.HOME ||
        currentRoute == Routes.FORUM ||
        currentRoute == Routes.WORKS ||
        currentRoute == Routes.ME
    val selectedRoute = currentRoute

    fun navigateTopLevel(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    var lastHomeBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = currentRoute == Routes.HOME) {
        val now = System.currentTimeMillis()
        if (now - lastHomeBackAt < 2000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastHomeBackAt = now
            scope.launch { snackbarHostState.showSnackbar("再按一次返回键退出应用") }
        }
    }

    val content: @Composable () -> Unit = {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialScale = 0.97f,
                    )
            },
            exitTransition = {
                fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        targetScale = 1.015f,
                    )
            },
            popEnterTransition = {
                fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialScale = 0.985f,
                    )
            },
            popExitTransition = {
                fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        targetScale = 1.02f,
                    )
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    appViewModel = appViewModel,
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                    onOpenCategory = { tag -> navController.navigate(Routes.category(tag.slug)) },
                    onCompose = { navController.navigate(Routes.COMPOSER) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onNotifications = { navigateTopLevel(Routes.NOTIFICATIONS) },
                )
            }
            composable(Routes.FORUM) {
                if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                    ForumTwoPane(
                        onOpenCategory = { tag -> navController.navigate(Routes.category(tag.slug)) },
                        onCompose = { navController.navigate(Routes.COMPOSER) },
                        onSearch = { navController.navigate(Routes.SEARCH) },
                    )
                } else {
                    ForumScreen(
                        onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                        onOpenCategory = { tag -> navController.navigate(Routes.category(tag.slug)) },
                        onCompose = { navController.navigate(Routes.COMPOSER) },
                        onSearch = { navController.navigate(Routes.SEARCH) },
                    )
                }
            }
            composable(
                route = Routes.CATEGORY,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                CategoryScreen(
                    tagSlug = entry.arguments?.getString("slug").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                    onCompose = { navController.navigate(Routes.COMPOSER) },
                )
            }
            composable(
                route = Routes.TOPIC,
                arguments = listOf(
                    navArgument("topicId") { type = NavType.StringType },
                    navArgument("postNumber") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                TopicDetailScreen(
                    topicId = entry.arguments?.getString("topicId").orEmpty(),
                    postNumber = entry.arguments?.getString("postNumber")?.toIntOrNull(),
                    onBack = { navController.popBackStack() },
                    onOpenCategory = { tag -> navController.navigate(Routes.category(tag.slug)) },
                )
            }
            composable(Routes.COMPOSER) {
                ComposerScreen(
                    onBack = { navController.popBackStack() },
                    onPublished = {
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.WORKS) {
                if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                    WorksTwoPane(
                        onSearch = { navController.navigate(Routes.SEARCH) },
                        onOpenAuthor = { author -> navController.navigate(Routes.profile(author.id)) },
                    )
                } else {
                    WorksScreen(
                        onOpenWork = { work -> navController.navigate(Routes.work(work.id)) },
                        onSearch = { navController.navigate(Routes.SEARCH) },
                    )
                }
            }
            composable(
                route = Routes.WORK,
                arguments = listOf(navArgument("workId") { type = NavType.StringType }),
            ) { entry ->
                WorkDetailScreen(
                    workId = entry.arguments?.getString("workId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenAuthor = { author ->
                        if (author.id.isNotBlank()) {
                            navController.navigate(Routes.profile(author.id))
                        }
                    },
                    onOpenWork = { work -> navController.navigate(Routes.work(work.id)) },
                )
            }
            composable(
                route = Routes.PROFILE,
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
            ) { entry ->
                val userId = entry.arguments?.getString("userId").orEmpty()
                ProfileScreen(
                    userId = userId,
                    isSelf = currentUser?.id == userId,
                    onBack = { navController.popBackStack() },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                    onOpenWork = { work -> navController.navigate(Routes.work(work.id)) },
                    onOpenUser = { user -> navController.navigate(Routes.profile(user.id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenMedia = { navController.navigate(Routes.MEDIA) },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenCollection = { collection ->
                        navController.navigate(Routes.collection(collection.id))
                    },
                )
            }
            composable(
                route = Routes.COLLECTION,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
            ) { entry ->
                CollectionScreen(
                    collectionId = entry.arguments?.getString("collectionId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                )
            }
            composable(Routes.ME) {
                val userId = currentUser?.id.orEmpty()
                ProfileScreen(
                    userId = userId,
                    isSelf = true,
                    showBack = false,
                    onBack = { navController.popBackStack() },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                    onOpenWork = { work -> navController.navigate(Routes.work(work.id)) },
                    onOpenUser = { user -> navController.navigate(Routes.profile(user.id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenMedia = { navController.navigate(Routes.MEDIA) },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenCollection = { collection ->
                        navController.navigate(Routes.collection(collection.id))
                    },
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    appViewModel = appViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTopic = { topicId, postNumber ->
                        navController.navigate(Routes.topic(topicId, postNumber))
                    },
                    onOpenWork = { workId -> navController.navigate(Routes.work(workId)) },
                    onOpenLikes = { navController.navigate(Routes.LIKES) },
                )
            }
            composable(Routes.LIKES) {
                LikesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    appViewModel = appViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                    onOpenUser = { user -> navController.navigate(Routes.profile(user.id)) },
                    onOpenWork = { work -> navController.navigate(Routes.work(work.id)) },
                    onOpenTag = { tag -> navController.navigate(Routes.category(tag.slug)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    appViewModel = appViewModel,
                    onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onOpenMedia = { navController.navigate(Routes.MEDIA) },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenGallery = { navController.navigate(Routes.GALLERY) },
                )
            }
            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(
                    userId = currentUser?.id.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MEDIA) {
                MediaScreen(
                    title = "个人图盘",
                    initialTab = FileStateTab.READY,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.GALLERY) {
                MediaScreen(
                    title = "媒体库",
                    initialTab = FileStateTab.READY,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWork = { work -> navController.navigate(Routes.work(work.id)) },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic.id)) },
                )
            }
        }
    }

    if (expanded) {
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        ) { outerPadding ->
            Row(Modifier.fillMaxSize().padding(outerPadding)) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    destinations.forEach { destination ->
                        val selected = selectedRoute == destination.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (selected) reselectEvents.tryEmit(Unit)
                                else navigateTopLevel(destination.route)
                            },
                            icon = {
                                DestinationIcon(
                                    destination = destination,
                                    selected = selected,
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalTabReselect provides reselectEvents) {
                        content()
                    }
                    OfflineBanner(
                        visible = !isOnline,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    )
                }
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isTopLevel) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        destinations.forEach { destination ->
                            val selected = selectedRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (selected) reselectEvents.tryEmit(Unit)
                                    else navigateTopLevel(destination.route)
                                },
                                icon = {
                                    DestinationIcon(
                                        destination = destination,
                                        selected = selected,
                                    )
                                },
                                label = { Text(destination.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { outerPadding ->
            Box(Modifier.fillMaxSize().padding(outerPadding)) {
                CompositionLocalProvider(LocalTabReselect provides reselectEvents) {
                    content()
                }
                OfflineBanner(
                    visible = !isOnline,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 84.dp),
                )
            }
        }
    }
}

@Composable
private fun ForumTwoPane(
    onOpenCategory: (dao3.pickcat.core.network.Tag) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
) {
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedTopicId != null) { selectedTopicId = null }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = if (maxWidth >= 1000.dp) 380.dp else 320.dp
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight()) {
                ForumScreen(
                    onOpenTopic = { topic -> selectedTopicId = topic.id },
                    onOpenCategory = onOpenCategory,
                    onCompose = onCompose,
                    onSearch = onSearch,
                )
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (selectedTopicId == null) {
                    EmptyState(
                        title = "选择一个帖子",
                        description = "左侧列表和右侧讨论同时显示。",
                        icon = Icons.Default.ChatBubbleOutline,
                        modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    )
                } else {
                    TopicDetailScreen(
                        topicId = selectedTopicId.orEmpty(),
                        postNumber = null,
                        onBack = { selectedTopicId = null },
                        onOpenCategory = onOpenCategory,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorksTwoPane(
    onSearch: () -> Unit,
    onOpenAuthor: (UserRef) -> Unit,
) {
    var selectedWorkId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedWorkId != null) { selectedWorkId = null }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = if (maxWidth >= 1000.dp) 380.dp else 320.dp
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight()) {
                WorksScreen(
                    onOpenWork = { work -> selectedWorkId = work.id },
                    onSearch = onSearch,
                )
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (selectedWorkId == null) {
                    EmptyState(
                        title = "选择一个作品",
                        description = "左侧列表和右侧详情同时显示。",
                        icon = Icons.Default.VideoLibrary,
                        modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    )
                } else {
                    WorkDetailScreen(
                        workId = selectedWorkId.orEmpty(),
                        onBack = { selectedWorkId = null },
                        onOpenAuthor = onOpenAuthor,
                        onOpenWork = { work -> selectedWorkId = work.id },
                        embedded = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: MainDestination, selected: Boolean) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
        },
        label = "bottom-navigation-icon",
    ) { active ->
        Icon(
            imageVector = if (active) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.label,
        )
    }
}
