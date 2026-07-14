package com.nianri.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nianri.app.AppContainer
import com.nianri.app.data.UiPreferences
import com.nianri.app.ui.detail.DetailScreen
import com.nianri.app.ui.detail.DetailViewModel
import com.nianri.app.ui.edit.EditDayScreen
import com.nianri.app.ui.edit.EditDayViewModel
import com.nianri.app.ui.home.HomeScreen
import com.nianri.app.ui.home.HomeViewModel

@Composable
fun NianriNavHost(
    container: AppContainer,
    uiPreferences: UiPreferences,
    importantDayId: Long? = null,
    editImportantDayId: Long? = null,
    openNewDay: Boolean = false,
) {
    val navController = rememberNavController()
    val startDestination = remember(importantDayId, editImportantDayId, openNewDay) {
        when {
            editImportantDayId != null -> "edit?dayId=$editImportantDayId"
            importantDayId != null -> "detail/$importantDayId"
            openNewDay -> "edit?dayId=0"
            else -> "home"
        }
    }
    fun navigateHomeClearingStack() {
        navController.navigate("home") {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(container, uiPreferences),
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onAdd = { navController.navigate("edit?dayId=0") },
                onOpen = { id -> navController.navigate("detail/$id") },
                onToggleDisplay = homeViewModel::toggleDisplay,
                onDismissCalendarExplanation = homeViewModel::dismissCalendarExplanation,
                onDisplayErrorShown = homeViewModel::clearDisplayError,
            )
        }
        composable(
            route = "edit?dayId={dayId}",
            arguments = listOf(
                navArgument("dayId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val context = LocalContext.current
            val dayId = backStackEntry.arguments?.getLong("dayId") ?: 0L
            val editViewModel: EditDayViewModel = viewModel(
                factory = EditDayViewModel.Factory(dayId, container),
            )
            val state by editViewModel.uiState.collectAsStateWithLifecycle()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { editViewModel.refreshPermissionState() }
            val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { editViewModel.refreshPermissionState() }
            LifecycleResumeEffect(editViewModel) {
                editViewModel.refreshPermissionState()
                onPauseOrDispose { }
            }
            EditDayScreen(
                state = state,
                onBack = {
                    if (!navController.popBackStack()) {
                        navigateHomeClearingStack()
                    }
                },
                onNameChange = editViewModel::setName,
                onBasisChange = editViewModel::setBasis,
                onMonthChange = editViewModel::setMonth,
                onDayChange = editViewModel::setDay,
                onDisplayChange = editViewModel::setDisplay,
                onToggleReminder = editViewModel::toggleReminder,
                onPinnedChange = editViewModel::setPinned,
                onSave = editViewModel::save,
                onDelete = editViewModel::delete,
                onSaved = { savedId ->
                    if (dayId == 0L) {
                        navController.navigate("detail/$savedId") {
                            popUpTo("home")
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onDeleted = {
                    navigateHomeClearingStack()
                },
                onRequestNotificationPermission = {
                    editViewModel.notificationPermissionRequestStarted()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onRequestExactAlarmPermission = {
                    editViewModel.exactAlarmPermissionRequestStarted()
                    exactAlarmPermissionLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}")),
                    )
                },
                onOpenReminderSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}")),
                    )
                },
            )
        }
        composable(
            route = "detail/{dayId}",
            arguments = listOf(navArgument("dayId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val dayId = requireNotNull(backStackEntry.arguments?.getLong("dayId"))
            val detailViewModel: DetailViewModel = viewModel(
                factory = DetailViewModel.Factory(dayId, container),
            )
            val state by detailViewModel.uiState.collectAsStateWithLifecycle()
            DetailScreen(
                state = state,
                onBack = {
                    if (!navController.popBackStack()) {
                        navigateHomeClearingStack()
                    }
                },
                onEdit = { id -> navController.navigate("edit?dayId=$id") },
                onDelete = detailViewModel::delete,
                onDeleted = {
                    navigateHomeClearingStack()
                },
            )
        }
    }
}
