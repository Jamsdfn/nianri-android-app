package com.nianri.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
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
import com.nianri.app.ui.transfer.TransferViewModel
import java.io.IOException

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
            val context = LocalContext.current
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(container, uiPreferences),
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            val transferViewModel: TransferViewModel = viewModel(
                factory = TransferViewModel.Factory(container),
            )
            val transferState by transferViewModel.uiState.collectAsStateWithLifecycle()
            val clipboard = remember(context) {
                context.getSystemService(ClipboardManager::class.java)
            }
            val createDocumentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri != null) {
                    transferViewModel.exportTo { text ->
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IOException("Unable to open output document")
                        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(text)
                        }
                    }
                }
            }
            val openDocumentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    transferViewModel.importFrom {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("Unable to open input document")
                        stream.bufferedReader(Charsets.UTF_8).use { reader ->
                            reader.readText()
                        }
                    }
                }
            }
            HomeScreen(
                state = state,
                onAdd = { navController.navigate("edit?dayId=0") },
                onOpen = { id -> navController.navigate("detail/$id") },
                onToggleDisplay = homeViewModel::toggleDisplay,
                onDismissCalendarExplanation = homeViewModel::dismissCalendarExplanation,
                onDisplayErrorShown = homeViewModel::clearDisplayError,
                transferState = transferState,
                onSelectTransferTab = transferViewModel::selectTab,
                onRequestExport = {
                    createDocumentLauncher.launch(transferViewModel.defaultExportFileName())
                },
                onCopyExport = {
                    transferViewModel.copyToClipboard { text ->
                        clipboard.setPrimaryClip(ClipData.newPlainText("念日配置", text))
                    }
                },
                onRequestImport = {
                    openDocumentLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                onImportTextChange = transferViewModel::setImportText,
                onPasteFromClipboard = {
                    transferViewModel.pasteFromClipboard {
                        clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                    }
                },
                onImportPastedText = transferViewModel::importPastedText,
                onTransferMessageShown = transferViewModel::clearMessage,
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
                onPickReminderTime = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> editViewModel.setReminderTime(hour, minute) },
                        state.reminderTimeMinutes / 60,
                        state.reminderTimeMinutes % 60,
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
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
