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

package com.blacksquircle.ui.feature.extensions.domain.repository

import android.net.Uri
import com.blacksquircle.ui.feature.extensions.domain.model.ExtensionModel
import com.blacksquircle.ui.feature.extensions.domain.model.InstallOutcome
import kotlinx.coroutines.flow.Flow

internal interface ExtensionsRepository {

    /** Installed extensions, sorted by install time. Emission on every database change. */
    fun observeExtensions(): Flow<List<ExtensionModel>>

    suspend fun install(uri: Uri): InstallOutcome

    suspend fun setEnabled(extensionId: String, enabled: Boolean)

    suspend fun uninstall(extensionId: String)
}