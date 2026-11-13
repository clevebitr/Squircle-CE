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

package com.blacksquircle.ui.extension

import com.blacksquircle.ui.extension.model.ExtensionContributions
import com.blacksquircle.ui.extension.model.InstalledExtension
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for what installed extensions contribute.
 *
 * Implementations keep an in-memory snapshot that is rebuilt by [refresh], so
 * consumers never touch the database or the file system synchronously.
 */
interface ExtensionRegistry {

    /** Last built snapshot. Empty until the first [refresh] finishes. */
    fun contributions(): ExtensionContributions

    fun observeContributions(): Flow<ExtensionContributions>

    fun observeInstalled(): Flow<List<InstalledExtension>>

    suspend fun installed(): List<InstalledExtension>

    /**
     * Rebuilds the snapshot from the database and the extensions directory.
     * Also removes directories that no longer have a database record.
     */
    suspend fun refresh(): ExtensionContributions
}