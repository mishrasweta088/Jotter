/*
 * Copyright (c) 2026 Open Apps Labs
 *
 * This file is part of Jotter
 *
 * Jotter is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Jotter is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Jotter.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.openappslabs.jotter.ui.screens.notedetailscreen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openappslabs.jotter.ui.components.CategoryItems
import com.openappslabs.jotter.ui.components.CategorySheet
import com.openappslabs.jotter.ui.components.DeleteNoteDialog
import com.openappslabs.jotter.ui.components.DiscardChangesDialog
import com.openappslabs.jotter.ui.components.EditViewButton
import com.openappslabs.jotter.ui.components.NoteActionDialog
import com.openappslabs.jotter.ui.components.PinLockBar
import com.openappslabs.jotter.ui.components.RestoreNoteDialog
import com.openappslabs.jotter.ui.theme.rememberJotterHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onManageCategoryClick: () -> Unit = {},
    onNavigateToArchive: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel()
) {
    val haptics = rememberJotterHaptics()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showRestoreNoteDialog by remember { mutableStateOf(false) }
    var pendingDiscard by remember { mutableStateOf(false) }
    var showNoteActionDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val clipboardManager = LocalClipboardManager.current
    var showToneMenu by remember { mutableStateOf(false) }

    var isViewMode by remember(uiState.isNotePersisted, userPrefs.defaultOpenInEdit) {
        val initialViewMode = if (uiState.isNotePersisted) {
            !userPrefs.defaultOpenInEdit
        } else {
            false
        }
        mutableStateOf(initialViewMode)
    }

    val isSaveEnabled = !isViewMode && uiState.isModified && (uiState.title.isNotBlank() || uiState.content.isNotBlank())

    val locale = Locale.getDefault()
    val dateString = remember(uiState.createdTime, userPrefs.is24HourFormat, userPrefs.dateFormat, locale) {
        val timePattern = if (userPrefs.is24HourFormat) "HH:mm" else "hh:mm a"
        val datePattern = userPrefs.dateFormat
        val pattern = "$datePattern, $timePattern"
        SimpleDateFormat(pattern, locale).format(Date(uiState.createdTime))
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    val titleStyle = remember(onSurfaceColor) {
        TextStyle(
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor
        )
    }

    val contentStyle = remember(onSurfaceColor) {
        TextStyle(
            fontSize = 18.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Normal,
            color = onSurfaceColor.copy(alpha = 0.85f)
        )
    }

    val cursorBrush = remember(primaryColor) {
        SolidColor(primaryColor)
    }

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && pendingDiscard) {
            pendingDiscard = false
            showDiscardDialog = true
        }
    }

    fun handleBack() {
        if (!isViewMode && uiState.isModified) {
            if (isImeVisible) {
                pendingDiscard = true
                keyboardController?.hide()
            } else {
                showDiscardDialog = true
            }
        } else {
            if (!isViewMode && uiState.isNotePersisted) {
                isViewMode = true
            } else {
                onBackClick()
            }
        }
    }

    val shouldInterceptBack = !isViewMode && (uiState.isModified || uiState.isNotePersisted)
    BackHandler(enabled = shouldInterceptBack) {
        handleBack()
    }

    val isArchivedOrTrashed = uiState.isArchived || uiState.isTrashed

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (uiState.isNotePersisted && !isArchivedOrTrashed) {
                        EditViewButton(
                            isEditing = !isViewMode,
                            onToggle = {
                                haptics.tick()
                                isViewMode = !isViewMode
                            }
                        )
                    } else {
                        Text(
                            text = "",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = {
                            haptics.click()
                            handleBack()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val showCloseIcon = !isViewMode && uiState.isModified
                            Icon(
                                imageVector = if (showCloseIcon) Icons.Default.Close else Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = if (showCloseIcon) "Close" else "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (isArchivedOrTrashed) {
                        Surface(
                            onClick = {
                                haptics.click()
                                showRestoreNoteDialog = true
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            enabled = true,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = "Restore/Unarchive",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        if (uiState.content.isNotBlank()) {
                            Box {
                                Surface(
                                    onClick = {
                                        haptics.click()
                                        showToneMenu = true
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (uiState.isRewriting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Magic Tone Changer",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                DropdownMenu(
                                    expanded = showToneMenu,
                                    onDismissRequest = { showToneMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Professional") },
                                        onClick = {
                                            showToneMenu = false
                                            viewModel.rewriteNote("Professional")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Academic") },
                                        onClick = {
                                            showToneMenu = false
                                            viewModel.rewriteNote("Academic")
                                        }
                                    )
                                }
                            }

                            if (uiState.content.length > 50) {
                                Surface(
                                    onClick = {
                                        haptics.click()
                                        viewModel.summarizeNote()
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (uiState.isSummarizing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Stars,
                                                contentDescription = "Summarize",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isViewMode) {
                            Surface(
                                onClick = {
                                    haptics.click()
                                    showNoteActionDialog = true
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Actions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = {
                                    haptics.success()
                                    viewModel.saveNote()
                                    isViewMode = true
                                    keyboardController?.hide()
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                enabled = isSaveEnabled,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Done,
                                        contentDescription = "Save",
                                        tint = if (isSaveEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (uiState.category.isBlank()) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                }
                            )
                            .clickable {
                                if (!isViewMode && !isArchivedOrTrashed) {
                                    if (isImeVisible) {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        scope.launch {
                                            delay(200)
                                            showCategorySheet = true
                                        }
                                    } else {
                                        haptics.click()
                                        showCategorySheet = true
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.category.isBlank()) "UNCATEGORIZED" else uiState.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.category.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.isArchived) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Archived",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (uiState.isTrashed) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Trashed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (uiState.isNotePersisted) {
                            Text(
                                text = "Created at $dateString",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                BasicTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    readOnly = isViewMode || isArchivedOrTrashed,
                    textStyle = titleStyle,
                    cursorBrush = cursorBrush,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box {
                            if (uiState.title.isEmpty() && !isViewMode && !isArchivedOrTrashed) {
                                Text(
                                    text = "Untitled",
                                    style = titleStyle.copy(color = onSurfaceColor.copy(alpha = 0.3f))
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                BasicTextField(
                    value = uiState.content,
                    onValueChange = { viewModel.updateContent(it) },
                    readOnly = isViewMode || isArchivedOrTrashed,
                    textStyle = contentStyle,
                    cursorBrush = cursorBrush,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box {
                            if (uiState.content.isEmpty() && !isViewMode && !isArchivedOrTrashed) {
                                Text(
                                    text = "Start typing...",
                                    style = contentStyle.copy(color = onSurfaceColor.copy(alpha = 0.3f))
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (!isViewMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isViewMode && uiState.isNotePersisted && !isArchivedOrTrashed) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            val showBottomBar = isViewMode && uiState.isNotePersisted && !isArchivedOrTrashed && (scrollState.value == 0 || scrollState.maxValue == 0)
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                PinLockBar(
                    isPinned = uiState.isPinned,
                    isLocked = uiState.isLocked,
                    onTogglePin = { viewModel.togglePin() },
                    onToggleLock = {
                        if (userPrefs.isBiometricEnabled) {
                            viewModel.toggleLock()
                        } else {
                            scope.launch {
                                if (snackbarHostState.currentSnackbarData == null) {
                                    snackbarHostState.showSnackbar(
                                        message = "Enable Note Lock in Settings to use this feature",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCategorySheet) {
        CategorySheet(
            categories = CategoryItems(availableCategories),
            selectedCategory = uiState.category,
            onCategorySelect = { newCategory ->
                haptics.tick()
                viewModel.updateCategory(newCategory)
                isViewMode = false
            },
            onManageCategoriesClick = {
                haptics.click()
                onManageCategoryClick()
            },
            onDismiss = { showCategorySheet = false }
        )
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDismiss = {
                haptics.click()
                showDiscardDialog = false
            },
            onConfirm = {
                haptics.tick()
                showDiscardDialog = false
                if (uiState.isNotePersisted) {
                    viewModel.undoChanges()
                    isViewMode = true
                } else {
                    onBackClick()
                }
            }
        )
    }

    if (showDeleteDialog) {
        DeleteNoteDialog(
            onDismiss = {
                haptics.click()
                showDeleteDialog = false
            },
            onConfirm = {
                haptics.heavy()
                showDiscardDialog = false
                viewModel.deleteNote()
                onBackClick()
            }
        )
    }

    if (showNoteActionDialog) {
        NoteActionDialog(
            onDismiss = {
                haptics.click()
                showNoteActionDialog = false
            },
            onArchiveConfirm = {
                haptics.tick()
                showNoteActionDialog = false
                viewModel.archiveNote()
                onBackClick()
            },
            onDeleteConfirm = {
                haptics.heavy()
                showNoteActionDialog = false
                viewModel.deleteNote()
                onBackClick()
            }
        )
    }

    if (showRestoreNoteDialog) {
        RestoreNoteDialog(
            onDismiss = {
                haptics.click()
                showRestoreNoteDialog = false
            },
            onConfirm = {
                haptics.success()
                showRestoreNoteDialog = false
                viewModel.restoreNote()
                onBackClick()
            }
        )
    }

    uiState.summaryResult?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSummary() },
            title = {
                Column {
                    Text("AI Summary (Beta)")
                    uiState.lastBenchmark?.let {
                        Text(
                            text = "Latency: ${it.latencyMs}ms | ${String.format(Locale.getDefault(), "%.1f", it.tokensPerSec)} t/s | Heap: ${it.heapBeforeMb}→${it.heapAfterMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = { Text(summary) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSummary() }) {
                    Text("Got it")
                }
            }
        )
    }

    uiState.rewriteResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRewrite() },
            title = {
                Column {
                    Text("Magic Rewrite")
                    uiState.lastBenchmark?.let {
                        Text(
                            text = "Latency: ${it.latencyMs}ms | ${String.format(Locale.getDefault(), "%.1f", it.tokensPerSec)} t/s | Heap: ${it.heapBeforeMb}→${it.heapAfterMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.success()
                    viewModel.applyRewrite()
                    isViewMode = false
                }) {
                    Text("Replace Note")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        haptics.click()
                        clipboardManager.setText(AnnotatedString(result))
                        scope.launch {
                            snackbarHostState.showSnackbar("Copied to clipboard")
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    TextButton(onClick = { viewModel.dismissRewrite() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    val aiError = uiState.summaryError ?: uiState.rewriteError
    aiError?.let { error ->
        AlertDialog(
            onDismissRequest = { 
                viewModel.dismissSummary()
                viewModel.dismissRewrite()
            },
            title = { Text("AI Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.dismissSummary()
                    viewModel.dismissRewrite()
                }) {
                    Text("OK")
                }
            }
        )
    }
}
