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

package com.blacksquircle.ui.feature.extensions.ui.extensions.store.mapper

import com.blacksquircle.ui.feature.extensions.ui.extensions.ExtensionsViewState
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsState
import com.blacksquircle.ui.redux.mapper.ViewStateMapper
import javax.inject.Inject

internal class ExtensionsViewStateMapper @Inject constructor() :
    ViewStateMapper<ExtensionsState, ExtensionsViewState> {

    override fun map(state: ExtensionsState): ExtensionsViewState {
        return ExtensionsViewState(
            extensions = state.extensions,
            isLoading = state.isLoading,
            isEmpty = !state.isLoading && state.extensions.isEmpty(),
            details = state.details,
            pendingUninstall = state.pendingUninstall,
        )
    }
}