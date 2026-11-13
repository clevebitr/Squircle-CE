/*
 * Copyright Squircle CE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blacksquircle.ui.feature.extensions.ui.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksquircle.ui.core.contract.ContractResult
import com.blacksquircle.ui.core.contract.MimeType
import com.blacksquircle.ui.core.contract.rememberOpenFileContract
import com.blacksquircle.ui.core.effect.CleanupEffect
import com.blacksquircle.ui.core.extensions.daggerViewModel
import com.blacksquircle.ui.core.extensions.showToast
import com.blacksquircle.ui.ds.PreviewBackground
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.button.FloatingButton
import com.blacksquircle.ui.ds.dialog.AlertDialog
import com.blacksquircle.ui.ds.emptyview.EmptyView
import com.blacksquircle.ui.ds.preference.PreferenceGroup
import com.blacksquircle.ui.ds.progress.CircularProgress
import com.blacksquircle.ui.ds.scaffold.ScaffoldSuite
import com.blacksquircle.ui.ds.toolbar.Toolbar
import com.blacksquircle.ui.extension.model.InstallType
import com.blacksquircle.ui.feature.extensions.R
import com.blacksquircle.ui.feature.extensions.domain.model.ExtensionModel
import com.blacksquircle.ui.feature.extensions.internal.ExtensionsComponent
import com.blacksquircle.ui.feature.extensions.ui.extensions.compose.ExtensionItem
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsAction
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsEvent
import com.blacksquircle.ui.ds.R as UiR

@Composable
internal fun ExtensionsScreen(
    viewModel: ExtensionsViewModel = daggerViewModel { context ->
        val component = ExtensionsComponent.buildOrGet(context)
        ExtensionsViewModel.Factory().also(component::inject)
    }
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val openFileContract = rememberOpenFileContract { result ->
        when (result) {
            is ContractResult.Success -> {
                viewModel.dispatch(ExtensionsAction.UiAction.OnInstallFile(result.uri))
            }

            is ContractResult.Canceled -> Unit
        }
    }

    ExtensionsScreen(
        viewState = viewState,
        onBackClicked = {
            viewModel.dispatch(ExtensionsAction.UiAction.OnBackClicked)
        },
        onExtensionClicked = { extension ->
            viewModel.dispatch(ExtensionsAction.UiAction.OnExtensionClicked(extension))
        },
        onToggleEnabledClicked = { extension, enabled ->
            viewModel.dispatch(ExtensionsAction.UiAction.OnToggleEnabledClicked(extension, enabled))
        },
        onUninstallClicked = { extension ->
            viewModel.dispatch(ExtensionsAction.UiAction.OnUninstallClicked(extension))
        },
        onUninstallConfirmed = { extension ->
            viewModel.dispatch(ExtensionsAction.UiAction.OnUninstallConfirmed(extension))
        },
        onDialogDismissed = {
            viewModel.dispatch(ExtensionsAction.UiAction.OnDialogDismissed)
        },
        onImportClicked = {
            openFileContract.launch(arrayOf(MimeType.ANY))
        },
    )

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExtensionsEvent.Toast -> {
                    context.showToast(text = event.message)
                }
            }
        }
    }

    CleanupEffect {
        ExtensionsComponent.release()
    }
}

@Composable
private fun ExtensionsScreen(
    viewState: ExtensionsViewState,
    onBackClicked: () -> Unit = {},
    onExtensionClicked: (ExtensionModel) -> Unit = {},
    onToggleEnabledClicked: (ExtensionModel, Boolean) -> Unit = { _, _ -> },
    onUninstallClicked: (ExtensionModel) -> Unit = {},
    onUninstallConfirmed: (ExtensionModel) -> Unit = {},
    onDialogDismissed: () -> Unit = {},
    onImportClicked: () -> Unit = {},
) {
    ScaffoldSuite(
        topBar = {
            Toolbar(
                title = stringResource(R.string.extensions_toolbar_title),
                navigationIcon = UiR.drawable.ic_back,
                onNavigationClicked = onBackClicked,
            )
        },
        floatingActionButton = {
            FloatingButton(
                iconResId = UiR.drawable.ic_plus,
                onClick = onImportClicked,
                modifier = Modifier.padding(8.dp),
            )
        },
    ) { contentPadding ->
        when {
            viewState.isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgress()
                }
            }

            viewState.isEmpty -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    EmptyView(
                        iconResId = UiR.drawable.ic_file_find,
                        title = stringResource(R.string.extensions_empty_title),
                        subtitle = stringResource(R.string.extensions_empty_subtitle),
                        action = stringResource(R.string.extensions_empty_action),
                        onClick = onImportClicked,
                    )
                }
            }

            else -> {
                ExtensionsList(
                    viewState = viewState,
                    contentPadding = contentPadding,
                    onExtensionClicked = onExtensionClicked,
                    onToggleEnabledClicked = onToggleEnabledClicked,
                )
            }
        }
    }

    val details = viewState.details
    if (details != null) {
        ExtensionDetailsDialog(
            extension = details,
            onRemoveClicked = { onUninstallClicked(details) },
            onDismissClicked = onDialogDismissed,
        )
    }

    val pendingUninstall = viewState.pendingUninstall
    if (pendingUninstall != null) {
        UninstallDialog(
            extension = pendingUninstall,
            onConfirmClicked = { onUninstallConfirmed(pendingUninstall) },
            onDismissClicked = onDialogDismissed,
        )
    }
}

@Composable
private fun ExtensionsList(
    viewState: ExtensionsViewState,
    contentPadding: PaddingValues,
    onExtensionClicked: (ExtensionModel) -> Unit,
    onToggleEnabledClicked: (ExtensionModel, Boolean) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            PreferenceGroup(title = stringResource(R.string.extensions_group_installed))
        }
        items(
            items = viewState.extensions,
            key = ExtensionModel::id,
        ) { extension ->
            ExtensionItem(
                extension = extension,
                onToggleClicked = { enabled -> onToggleEnabledClicked(extension, enabled) },
                onClick = { onExtensionClicked(extension) },
            )
        }
    }
}

@Composable
private fun ExtensionDetailsDialog(
    extension: ExtensionModel,
    onRemoveClicked: () -> Unit,
    onDismissClicked: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.extensions_dialog_details_title),
        content = {
            Column {
                Text(
                    text = extension.id,
                    color = SquircleTheme.colors.colorTextAndIconSecondary,
                    style = SquircleTheme.typography.text14Regular,
                )
                if (extension.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = extension.description,
                        color = SquircleTheme.colors.colorTextAndIconSecondary,
                        style = SquircleTheme.typography.text16Regular,
                    )
                }
            }
        },
        onDismiss = onDismissClicked,
        properties = DialogProperties(),
        confirmButton = stringResource(R.string.extensions_dialog_details_remove),
        dismissButton = stringResource(R.string.extensions_dialog_close),
        onConfirmClicked = onRemoveClicked,
        onDismissClicked = onDismissClicked,
    )
}

@Composable
private fun UninstallDialog(
    extension: ExtensionModel,
    onConfirmClicked: () -> Unit,
    onDismissClicked: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.extensions_dialog_uninstall_title),
        content = {
            Text(
                text = stringResource(
                    R.string.extensions_dialog_uninstall_message,
                    extension.displayName,
                ),
                color = SquircleTheme.colors.colorTextAndIconSecondary,
                style = SquircleTheme.typography.text16Regular,
            )
        },
        onDismiss = onDismissClicked,
        properties = DialogProperties(),
        confirmButton = stringResource(UiR.string.common_delete),
        dismissButton = stringResource(UiR.string.common_no),
        onConfirmClicked = onConfirmClicked,
        onDismissClicked = onDismissClicked,
    )
}

@PreviewLightDark
@Composable
private fun ExtensionsScreenPreview() {
    PreviewBackground {
        ExtensionsScreen(
            viewState = ExtensionsViewState(
                extensions = listOf(
                    ExtensionModel(
                        id = "dev.squircle.diff",
                        displayName = "Diff / Patch",
                        version = "1.0.0",
                        publisher = "dev.squircle",
                        description = "Syntax highlighting for diff and patch files",
                        enabled = true,
                        installType = InstallType.LOCAL,
                    ),
                    ExtensionModel(
                        id = "dev.squircle.web",
                        displayName = "Web Modern",
                        version = "1.2.0",
                        publisher = "dev.squircle",
                        description = "GraphQL, Protobuf and Terraform",
                        enabled = false,
                        installType = InstallType.INDEX,
                    ),
                ),
                isLoading = false,
            ),
        )
    }
}