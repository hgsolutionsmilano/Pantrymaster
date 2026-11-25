package com.example.ui.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.info.HouseInfoDetailScreen
import com.example.ui.screens.info.HouseInfoEditorScreen
import com.example.ui.screens.info.HouseInfoScreen
import com.example.ui.screens.pantry.MovementHistoryScreen
import com.example.ui.screens.pantry.PantryScreen
import com.example.ui.screens.pantry.ProductDetailScreen
import com.example.ui.screens.pantry.ProductEditorScreen
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.settings.AppUpdateAvailableDialog
import com.example.ui.screens.settings.AppUpdateViewModel
import com.example.ui.screens.settings.CategoryManagerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.shopping.ShoppingScreen
import com.example.ui.screens.stats.StatsScreen

private data class TopNavItem(
    val route: PantryDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topNavItems = listOf(
    TopNavItem(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopNavItem(PantryRoute, "Dispensa", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "nav_pantry"),
    TopNavItem(ShoppingRoute, "Spesa", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart, "nav_shopping"),
    TopNavItem(InfoRoute, "Info", Icons.Filled.Info, Icons.Outlined.Info, "nav_info"),
    TopNavItem(StatsRoute, "Statistiche", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_stats")
)

@Composable
fun PantryMasterNavHost(
    widthSizeClass: WindowWidthSizeClass,
    updateViewModel: AppUpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val startupDialogState by updateViewModel.startupDialogState.collectAsStateWithLifecycle()
    val backupActionMessage by updateViewModel.backupActionMessage.collectAsStateWithLifecycle()
    val isExportingUpdate by updateViewModel.isExporting.collectAsStateWithLifecycle()
    val notificationsEnabled by updateViewModel.notificationsEnabled.collectAsStateWithLifecycle()
    var promptedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val startupNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            updateViewModel.onNotificationPermissionGranted()
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (updateViewModel.canRequestPackageInstalls()) {
            updateViewModel.installReadyUpdate()
        }
    }

    val updateBackupZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(updateViewModel::exportBackupZip) }

    val updateSaveApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri -> uri?.let(updateViewModel::exportPendingApk) }

    LaunchedEffect(Unit) {
        updateViewModel.checkOnStartup()
    }

    LaunchedEffect(updateViewModel) {
        updateViewModel.installPromptIntents.collect { confirmIntent ->
            runCatching {
                context.startActivity(confirmIntent)
            }.onFailure {
                runCatching {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
        }
    }

    LaunchedEffect(notificationsEnabled) {
        if (!promptedNotificationPermission &&
            notificationsEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            promptedNotificationPermission = true
            startupNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    startupDialogState?.let { dialog ->
        val canInstall = updateViewModel.canRequestPackageInstalls()
        AppUpdateAvailableDialog(
            updateInfo = dialog.updateInfo,
            downloadState = dialog.downloadState,
            canInstallPackages = canInstall,
            onInstallClick = {
                if (updateViewModel.canRequestPackageInstalls()) {
                    updateViewModel.installReadyUpdate()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                    unknownSourcesLauncher.launch(settingsIntent)
                }
            },
            onContinueInBackground = updateViewModel::continueInBackground,
            onRetryClick = updateViewModel::retryDownload,
            onRemindLater = updateViewModel::remindLater,
            onIgnoreVersion = { updateViewModel.ignoreVersion(dialog.updateInfo.tagName) },
            onExportBackupClick = { updateBackupZipLauncher.launch("pantrymaster_backup.zip") },
            onSaveApkClick = {
                val fileName = dialog.updateInfo.apkFileName
                    ?: "PantryMaster-${dialog.updateInfo.tagName}.apk"
                updateSaveApkLauncher.launch(fileName)
            },
            actionFeedbackMessage = backupActionMessage,
            isExporting = isExportingUpdate
        )
    }

    val isExpanded = widthSizeClass != WindowWidthSizeClass.Compact
    val showTopLevelChrome = currentDestination?.let { dest ->
        topNavItems.any { item -> dest.hasRoute(item.route::class) }
    } ?: true

    fun navigateTopLevel(route: PantryDestination) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isExpanded && showTopLevelChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    topNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.route::class)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label, maxLines = 1) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExpanded && showTopLevelChrome) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    topNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.route::class)
                        } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            isExpanded = isExpanded,
                            onOpenScanner = { navController.navigate(ScannerRoute) },
                            onAddProduct = { navController.navigate(ProductEditorRoute()) },
                            onOpenProduct = { id -> navController.navigate(ProductDetailRoute(id)) },
                            onOpenPantry = { navigateTopLevel(PantryRoute) },
                            onOpenShopping = { navigateTopLevel(ShoppingRoute) },
                            onOpenInfo = { navigateTopLevel(InfoRoute) },
                            onOpenInfoDetail = { id -> navController.navigate(InfoDetailRoute(id)) },
                            onOpenHistory = { navController.navigate(MovementHistoryRoute) },
                            onOpenSettings = { navController.navigate(SettingsRoute) }
                        )
                    }

                    composable<PantryRoute> {
                        PantryScreen(
                            isExpanded = isExpanded,
                            onOpenProduct = { id -> navController.navigate(ProductDetailRoute(id)) },
                            onAddProduct = { navController.navigate(ProductEditorRoute()) },
                            onOpenScanner = { navController.navigate(ScannerRoute) }
                        )
                    }

                    composable<ShoppingRoute> {
                        ShoppingScreen()
                    }

                    composable<InfoRoute> {
                        HouseInfoScreen(
                            isExpanded = isExpanded,
                            onOpenDetail = { id -> navController.navigate(InfoDetailRoute(id)) },
                            onCreateEntry = { cat ->
                                navController.navigate(InfoEditorRoute(initialCategoryName = cat?.name))
                            }
                        )
                    }

                    composable<StatsRoute> {
                        StatsScreen(isExpanded = isExpanded)
                    }

                    composable<ProductDetailRoute> { entry ->
                        val route = entry.toRoute<ProductDetailRoute>()
                        ProductDetailScreen(
                            productId = route.productId,
                            onBack = { navController.popBackStack() },
                            onEditProduct = { id ->
                                navController.navigate(ProductEditorRoute(productId = id))
                            }
                        )
                    }

                    composable<ProductEditorRoute> { entry ->
                        val route = entry.toRoute<ProductEditorRoute>()
                        ProductEditorScreen(
                            productId = route.productId,
                            prefillBarcode = route.prefillBarcode,
                            prefillName = route.prefillName,
                            prefillBrand = route.prefillBrand,
                            prefillCategoryId = route.prefillCategoryId,
                            prefillDescription = route.prefillDescription,
                            prefillUnitName = route.prefillUnitName,
                            prefillLocalImagePath = route.prefillLocalImagePath,
                            prefillExpiryDateMillis = route.prefillExpiryDateMillis,
                            prefillExpiryTypeName = route.prefillExpiryTypeName,
                            onBack = { navController.popBackStack() },
                            onSaved = { savedId ->
                                if (navController.currentBackStackEntry?.destination?.hasRoute<ProductEditorRoute>() == true) {
                                    navController.popBackStack()
                                    if (route.productId == null) {
                                        navController.navigate(ProductDetailRoute(savedId))
                                    }
                                }
                            }
                        )
                    }

                    composable<ScannerRoute> {
                        ScannerScreen(
                            onBack = { navController.popBackStack() },
                            onManualCreate = { code, expiryMillis, expiryType ->
                                navController.popBackStack()
                                navController.navigate(
                                    ProductEditorRoute(
                                        prefillBarcode = code,
                                        prefillExpiryDateMillis = expiryMillis,
                                        prefillExpiryTypeName = expiryType?.name
                                    )
                                )
                            },
                            onOpenRemotePrefill = { remote, expiryMillis, expiryType ->
                                navController.popBackStack()
                                navController.navigate(
                                    ProductEditorRoute(
                                        prefillBarcode = remote.barcode,
                                        prefillName = remote.name,
                                        prefillBrand = remote.brand,
                                        prefillCategoryId = remote.suggestedCategoryId,
                                        prefillDescription = remote.description,
                                        prefillUnitName = remote.suggestedUnit.name,
                                        prefillLocalImagePath = remote.localImagePath,
                                        prefillExpiryDateMillis = expiryMillis,
                                        prefillExpiryTypeName = expiryType?.name
                                    )
                                )
                            },
                            onOpenProductDetail = { id ->
                                navController.popBackStack()
                                navController.navigate(ProductDetailRoute(id))
                            }
                        )
                    }

                    composable<InfoDetailRoute> { entry ->
                        val route = entry.toRoute<InfoDetailRoute>()
                        HouseInfoDetailScreen(
                            entryId = route.entryId,
                            onBack = { navController.popBackStack() },
                            onEdit = { id ->
                                navController.navigate(InfoEditorRoute(entryId = id))
                            }
                        )
                    }

                    composable<InfoEditorRoute> { entry ->
                        val route = entry.toRoute<InfoEditorRoute>()
                        HouseInfoEditorScreen(
                            entryId = route.entryId,
                            initialCategoryName = route.initialCategoryName,
                            onBack = { navController.popBackStack() },
                            onSaved = { savedId ->
                                navController.popBackStack()
                                if (route.entryId == null) {
                                    navController.navigate(InfoDetailRoute(savedId))
                                }
                            }
                        )
                    }

                    composable<MovementHistoryRoute> {
                        MovementHistoryScreen(onBack = { navController.popBackStack() })
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenCategories = { navController.navigate(CategoryManagerRoute) }
                        )
                    }

                    composable<CategoryManagerRoute> {
                        CategoryManagerScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
