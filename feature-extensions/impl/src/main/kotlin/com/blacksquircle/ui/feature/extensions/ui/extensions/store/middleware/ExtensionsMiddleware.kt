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

package com.blacksquircle.ui.feature.extensions.ui.extensions.store.middleware

import com.blacksquircle.ui.feature.extensions.domain.model.InstallOutcome
import com.blacksquircle.ui.feature.extensions.domain.repository.ExtensionsRepository
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsAction
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsState
import com.blacksquircle.ui.navigation.api.Navigator
import com.blacksquircle.ui.redux.middleware.Middleware
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExtensionsMiddleware @Inject constructor(
    private val extensionsRepository: ExtensionsRepository,
    private val navigator: Navigator,
) : Middleware<ExtensionsState, ExtensionsAction> {

    override fun bind(
        state: Flow<ExtensionsState>,
        actions: Flow<ExtensionsAction>,
    ): Flow<ExtensionsAction> {
        return merge(
            onInstalledExtensions(),
            onBackClicked(actions),
            onToggleEnabledClicked(actions),
            onUninstallConfirmed(actions),
            onInstallFile(actions),
        )
    }

    /** The database is the source of truth, so no explicit reload action is required. */
    private fun onInstalledExtensions(): Flow<ExtensionsAction> {
        return extensionsRepository.observeExtensions()
            .map { extensions ->
                ExtensionsAction.CommandAction.ExtensionsLoaded(extensions)
            }.catch<ExtensionsAction> {
                emit(ExtensionsAction.Error(it))
            }
    }

    private fun onBackClicked(actions: Flow<ExtensionsAction>): Flow<ExtensionsAction> {
        return actions.filterIsInstance<ExtensionsAction.UiAction.OnBackClicked>()
            .flatMapLatest {
                navigator.goBack()
                emptyFlow()
            }
    }

    private fun onToggleEnabledClicked(actions: Flow<ExtensionsAction>): Flow<ExtensionsAction> {
        return actions.filterIsInstance<ExtensionsAction.UiAction.OnToggleEnabledClicked>()
            .map { action ->
                extensionsRepository.setEnabled(action.extension.id, action.enabled)
                ExtensionsAction.CommandAction.ExtensionToggled(action.extension, action.enabled)
            }.catch<ExtensionsAction> {
                emit(ExtensionsAction.Error(it))
            }
    }

    private fun onUninstallConfirmed(actions: Flow<ExtensionsAction>): Flow<ExtensionsAction> {
        return actions.filterIsInstance<ExtensionsAction.UiAction.OnUninstallConfirmed>()
            .map { action ->
                extensionsRepository.uninstall(action.extension.id)
                ExtensionsAction.CommandAction.ExtensionUninstalled(action.extension)
            }.catch<ExtensionsAction> {
                emit(ExtensionsAction.Error(it))
            }
    }

    private fun onInstallFile(actions: Flow<ExtensionsAction>): Flow<ExtensionsAction> {
        return actions.filterIsInstance<ExtensionsAction.UiAction.OnInstallFile>()
            .flatMapLatest { action ->
                when (val outcome = extensionsRepository.install(action.uri)) {
                    is InstallOutcome.Installed -> {
                        flowOf(ExtensionsAction.CommandAction.ExtensionInstalled(outcome.extension))
                    }

                    is InstallOutcome.Rejected -> {
                        flowOf(ExtensionsAction.CommandAction.ExtensionRejected(outcome.failure))
                    }
                }
            }.catch<ExtensionsAction> {
                emit(ExtensionsAction.Error(it))
            }
    }
}