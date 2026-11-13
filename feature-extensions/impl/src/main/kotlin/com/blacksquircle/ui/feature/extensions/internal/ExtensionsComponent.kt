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

package com.blacksquircle.ui.feature.extensions.internal

import android.content.Context
import com.blacksquircle.ui.core.internal.CoreApi
import com.blacksquircle.ui.core.internal.provideCoreApi
import com.blacksquircle.ui.extension.ExtensionsApi
import com.blacksquircle.ui.extension.provideExtensionsApi
import com.blacksquircle.ui.feature.extensions.ui.extensions.ExtensionsViewModel
import com.blacksquircle.ui.navigation.api.internal.NavigationApi
import com.blacksquircle.ui.navigation.api.internal.provideNavigationApi
import dagger.Component

@ExtensionsScope
@Component(
    modules = [
        ExtensionsModule::class,
    ],
    dependencies = [
        CoreApi::class,
        NavigationApi::class,
        ExtensionsApi::class,
    ]
)
internal interface ExtensionsComponent {

    fun inject(factory: ExtensionsViewModel.Factory)

    @Component.Factory
    interface Factory {
        fun create(
            coreApi: CoreApi,
            navigationApi: NavigationApi,
            extensionsApi: ExtensionsApi,
        ): ExtensionsComponent
    }

    companion object {

        private var component: ExtensionsComponent? = null

        fun buildOrGet(context: Context): ExtensionsComponent {
            return component ?: DaggerExtensionsComponent.factory().create(
                coreApi = context.provideCoreApi(),
                navigationApi = context.provideNavigationApi(),
                extensionsApi = context.provideExtensionsApi(),
            ).also {
                component = it
            }
        }

        fun release() {
            component = null
        }
    }
}