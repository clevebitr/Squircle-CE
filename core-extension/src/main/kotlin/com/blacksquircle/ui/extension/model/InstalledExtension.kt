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

package com.blacksquircle.ui.extension.model

import java.io.File

data class InstalledExtension(
    val id: String,
    val version: String,
    val displayName: String,
    val publisher: String,
    val description: String,
    val enabled: Boolean,
    val installType: InstallType,
    val sourceUrl: String?,
    val installedAt: Long,
    val directory: File,
)

enum class InstallType(val value: String) {
    /** Downloaded from the index repository. */
    INDEX("index"),

    /** Imported by the user from a local archive. */
    LOCAL("local");

    companion object {

        fun of(value: String): InstallType {
            return entries.find { it.value == value } ?: LOCAL
        }
    }
}

sealed interface InstallResult {

    data class Success(val extension: InstalledExtension) : InstallResult

    data class Failure(val failure: ExtensionFailure) : InstallResult
}