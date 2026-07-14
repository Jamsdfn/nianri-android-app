package com.nianri.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nianri.app.AppContainer
import com.nianri.app.data.UiPreferences
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
        ) {
            PlaceholderScreen("编辑重要日子")
        }
        composable(
            route = "detail/{dayId}",
            arguments = listOf(navArgument("dayId") { type = NavType.LongType }),
        ) {
            PlaceholderScreen("重要日子详情")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title)
    }
}
