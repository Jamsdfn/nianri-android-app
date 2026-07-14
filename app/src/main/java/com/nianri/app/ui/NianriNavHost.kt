package com.nianri.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
) {
    val navController = rememberNavController()
    val startDestination = remember(importantDayId) {
        importantDayId?.let { "detail/$it" } ?: "home"
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
            val dayId = backStackEntry.arguments?.getLong("dayId") ?: 0L
            val editViewModel: EditDayViewModel = viewModel(
                factory = EditDayViewModel.Factory(dayId, container),
            )
            val state by editViewModel.uiState.collectAsStateWithLifecycle()
            EditDayScreen(
                state = state,
                onBack = navController::popBackStack,
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
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
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
                onBack = navController::popBackStack,
                onEdit = { id -> navController.navigate("edit?dayId=$id") },
                onDelete = detailViewModel::delete,
                onDeleted = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
            )
        }
    }
}
