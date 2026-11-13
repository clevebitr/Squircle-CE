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

package com.blacksquircle.ui.feature.extensions.ui.extensions.store

import android.net.Uri
import com.blacksquircle.ui.extension.model.ExtensionFailure
import com.blacksquircle.ui.feature.extensions.domain.model.ExtensionModel
import com.blacksquircle.ui.redux.MVIAction

internal sealed interface ExtensionsAction : MVIAction {

    data object Init : ExtensionsAction
    data class Error(val error: Throwable) : ExtensionsAction

    sealed interface UiAction : ExtensionsAction {

        data object OnBackClicked : UiAction
        data class OnExtensionClicked(val extension: ExtensionModel) : UiAction
        data class OnToggleEnabledClicked(val extension: ExtensionModel, val enabled: Boolean) : UiAction
        data class OnUninstallClicked(val extension: ExtensionModel) : UiAction
        data class OnUninstallConfirmed(val extension: ExtensionModel) : UiAction
        data object OnDialogDismissed : UiAction
        data class OnInstallFile(val uri: Uri) : UiAction
    }

    sealed interface CommandAction : ExtensionsAction {
        data class ExtensionsLoaded(val extensions: List<ExtensionModel>) : CommandAction
        data class ExtensionToggled(val extension: ExtensionModel, val enabled: Boolean) : CommandAction
        data class ExtensionUninstalled(val extension: ExtensionModel) : CommandAction
        data class ExtensionInstalled(val extension: ExtensionModel) : CommandAction
        data class ExtensionRejected(val failure: ExtensionFailure) : CommandAction
    }
}