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

/**
 * Rejects paths that could escape the extension directory.
 *
 * Extension archives and manifests are untrusted input, so a path is only
 * accepted when it is relative, free of traversal segments and inside one of
 * the allow-listed directories.
 */
internal object PathSanitizer {

    private const val SEPARATOR = '/'
    private const val WINDOWS_SEPARATOR = '\\'
    private const val TRAVERSAL = ".."

    fun isSafeRelativePath(path: String): Boolean {
        val normalized = path.trimEnd(SEPARATOR)
        if (normalized.isEmpty()) {
            return false
        }
        if (normalized.first() == SEPARATOR || normalized.first() == WINDOWS_SEPARATOR) {
            return false
        }
        if (normalized.contains(WINDOWS_SEPARATOR)) {
            return false
        }
        if (normalized.contains('\u0000') || normalized.contains(':')) {
            return false
        }
        return normalized.split(SEPARATOR).none { it == TRAVERSAL || it.isEmpty() }
    }

    /**
     * @param directory e.g. `syntaxes/`
     *
     * Accepts both the directory entry itself (`syntaxes`, as it appears in zip
     * archives) and anything below it (`syntaxes/diff.tmLanguage.json`).
     */
    fun isInside(path: String, directory: String): Boolean {
        val normalizedPath = path.trimEnd(SEPARATOR)
        val normalizedDirectory = directory.trimEnd(SEPARATOR)
        if (normalizedPath.isEmpty() || normalizedDirectory.isEmpty()) {
            return false
        }
        return normalizedPath == normalizedDirectory ||
            normalizedPath.startsWith(normalizedDirectory + SEPARATOR)
    }
}