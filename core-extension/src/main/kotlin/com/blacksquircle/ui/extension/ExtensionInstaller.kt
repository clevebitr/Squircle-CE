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

import com.blacksquircle.ui.extension.model.InstallResult
import com.blacksquircle.ui.extension.model.InstallType
import java.io.File

/**
 * Installs, updates and removes extensions.
 *
 * All operations are fail-safe: a failed install leaves the previous version
 * untouched, and nothing is ever written outside the extensions directory.
 */
interface ExtensionInstaller {

    /**
     * @param archive zip package, already copied out of any content provider
     * @param expectedSha256 when known (index repository), the archive must match it
     */
    suspend fun install(
        archive: File,
        expectedSha256: String? = null,
        installType: InstallType = InstallType.LOCAL,
        sourceUrl: String? = null,
    ): InstallResult

    /** @return `true` when the extension was installed and is now gone. */
    suspend fun uninstall(extensionId: String): Boolean

    suspend fun setEnabled(extensionId: String, enabled: Boolean)
}