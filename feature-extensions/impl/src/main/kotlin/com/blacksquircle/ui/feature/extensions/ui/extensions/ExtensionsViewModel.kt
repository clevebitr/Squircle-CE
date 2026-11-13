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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsAction
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsEvent
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsState
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.ExtensionsStore
import com.blacksquircle.ui.feature.extensions.ui.extensions.store.mapper.ExtensionsViewStateMapper
import com.blacksquircle.ui.redux.lifecycle.StoreViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Provider

internal class ExtensionsViewModel @Inject constructor(
    store: ExtensionsStore,
    viewStateMapper: ExtensionsViewStateMapper,
) : StoreViewModel<ExtensionsState, ExtensionsAction, ExtensionsEvent>(store) {

    val viewState: StateFlow<ExtensionsViewState> = state
        .map(viewStateMapper::map)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExtensionsViewState())

    class Factory : ViewModelProvider.Factory {

        @Inject
        lateinit var viewModelProvider: Provider<ExtensionsViewModel>

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return viewModelProvider.get() as T
        }
    }
}