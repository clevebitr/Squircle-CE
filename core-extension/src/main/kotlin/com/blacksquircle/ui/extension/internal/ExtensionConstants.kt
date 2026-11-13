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

package com.blacksquircle.ui.extension.internal

internal object ExtensionConstants {

    const val MANIFEST_FILE = "extension.json"

    const val STAGING_DIR = ".staging"

    const val SCHEME_PREFIX = "extension://"

    const val SCHEMA_VERSION = 1

    const val MAX_ARCHIVE_BYTES = 20L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 5L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    const val MAX_ENTRY_COUNT = 500
    const val MAX_COMPRESSION_RATIO = 100.0

    /** Directories an archive is allowed to contain. Root only holds the manifest. */
    val ALLOWED_DIRECTORIES = listOf(
        "themes/",
        "syntaxes/",
        "language-configuration/",
        "fonts/",
        "icons/",
        "completions/",
        "snippets/",
        "LICENSES/",
    )

    /**
     * Documentation that third-party grammars and themes are usually shipped with.
     * Licences are mandatory upstream (most TextMate assets are MIT), so rejecting
     * them would make compliant packages uninstallable.
     */
    val ALLOWED_ROOT_FILES = setOf(
        "README.md",
        "CHANGELOG.md",
        "NOTICE",
        "NOTICE.txt",
        "LICENSE",
        "LICENSE.txt",
        "LICENSE.md",
    )

    /** Extensions are data only, so anything executable is rejected outright. */
    val FORBIDDEN_FILE_EXTENSIONS = setOf(
        "dex", "so", "jar", "apk", "aar", "class", "sh", "bin", "exe",
    )

    fun contributionUri(extensionId: String, relativePath: String): String {
        return SCHEME_PREFIX + extensionId + "/" + relativePath
    }
}