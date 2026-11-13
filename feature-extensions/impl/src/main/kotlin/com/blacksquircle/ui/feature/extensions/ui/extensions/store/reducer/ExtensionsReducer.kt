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

package com.blacksquircle.ui.feature.extensions.ui.extensions.store.reducer

import com.blacksquircle.ui.core.provider.resources.StringProvider
import com.blacksquircle.ui.feature.extensions.R
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsAction
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsEvent
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsState
import com.blacksquircle.ui.redux.reducer.Reducer
import javax.inject.Inject
import com.blacksquircle.ui.ds.R as UiR

internal class ExtensionsReducer @Inject constructor(
    private val stringProvider: StringProvider,
) : Reducer<ExtensionsState, ExtensionsAction, ExtensionsEvent>() {

    override fun reduce(action: ExtensionsAction) {
        when (action) {
            is ExtensionsAction.CommandAction.ExtensionsLoaded -> {
                state {
                    copy(
                        extensions = action.extensions,
                        isLoading = false,
                    )
                }
            }

            is ExtensionsAction.UiAction.OnExtensionClicked -> {
                state {
                    copy(details = action.extension)
                }
            }

            is ExtensionsAction.UiAction.OnUninstallClicked -> {
                state {
                    copy(
                        details = null,
                        pendingUninstall = action.extension,
                    )
                }
            }

            is ExtensionsAction.UiAction.OnUninstallConfirmed -> {
                state {
                    copy(pendingUninstall = null)
                }
            }

            is ExtensionsAction.UiAction.OnDialogDismissed -> {
                state {
                    copy(
                        details = null,
                        pendingUninstall = null,
                    )
                }
            }

            is ExtensionsAction.UiAction.OnToggleEnabledClicked -> {
                state {
                    copy(
                        extensions = extensions.map { extension ->
                            if (extension.id == action.extension.id) {
                                extension.copy(enabled = action.enabled)
                            } else {
                                extension
                            }
                        }
                    )
                }
            }

            is ExtensionsAction.CommandAction.ExtensionToggled -> {
                val messageResId = if (action.enabled) {
                    R.string.extensions_toast_enabled
                } else {
                    R.string.extensions_toast_disabled
                }
                event(
                    ExtensionsEvent.Toast(
                        stringProvider.getString(messageResId, action.extension.displayName)
                    )
                )
            }

            is ExtensionsAction.CommandAction.ExtensionUninstalled -> {
                state {
                    copy(
                        extensions = extensions.filterNot { it.id == action.extension.id },
                        details = null,
                        pendingUninstall = null,
                    )
                }
                event(
                    ExtensionsEvent.Toast(
                        stringProvider.getString(
                            R.string.extensions_toast_removed,
                            action.extension.displayName,
                        )
                    )
                )
            }

            is ExtensionsAction.CommandAction.ExtensionInstalled -> {
                event(
                    ExtensionsEvent.Toast(
                        stringProvider.getString(
                            R.string.extensions_toast_installed,
                            action.extension.displayName,
                        )
                    )
                )
            }

            is ExtensionsAction.CommandAction.ExtensionRejected -> {
                state {
                    copy(isLoading = false)
                }
                event(
                    ExtensionsEvent.Toast(
                        stringProvider.getString(
                            R.string.extensions_toast_rejected,
                            action.failure.message,
                        )
                    )
                )
            }

            is ExtensionsAction.Error -> {
                state {
                    copy(isLoading = false)
                }
                event(
                    ExtensionsEvent.Toast(stringProvider.getString(UiR.string.common_error_occurred))
                )
            }

            is ExtensionsAction.Init,
            is ExtensionsAction.UiAction.OnBackClicked,
            is ExtensionsAction.UiAction.OnInstallFile -> Unit
        }
    }
}