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

import com.blacksquircle.ui.extension.model.ExtensionFailure

/**
 * Validates the entry list of an archive before anything is extracted, so a
 * malicious or broken package never touches the file system.
 */
internal object ZipEntriesValidator {

    data class Entry(
        val name: String,
        val sizeBytes: Long,
        val compressedSizeBytes: Long,
    ) {
        val isDirectory: Boolean
            get() = name.endsWith("/")
    }

    fun validate(entries: List<Entry>): ExtensionFailure? {
        if (entries.isEmpty()) {
            return ExtensionFailure.InvalidPackage("Package is empty")
        }
        if (entries.size > ExtensionConstants.MAX_ENTRY_COUNT) {
            return ExtensionFailure.InvalidPackage(
                "Too many entries: ${entries.size} > ${ExtensionConstants.MAX_ENTRY_COUNT}"
            )
        }

        var totalBytes = 0L
        var hasManifest = false

        for (entry in entries) {
            val name = entry.name
            if (!PathSanitizer.isSafeRelativePath(name)) {
                return ExtensionFailure.InvalidPackage("Unsafe path: $name")
            }

            val normalized = name.trimEnd('/')
            if (entry.isDirectory) {
                if (normalized == ExtensionConstants.MANIFEST_FILE) {
                    return ExtensionFailure.InvalidPackage("Manifest cannot be a directory")
                }
                if (!isAllowedPath(normalized)) {
                    return ExtensionFailure.InvalidPackage("Unexpected directory: $name")
                }
                continue
            }

            if (entry.sizeBytes < 0 || entry.sizeBytes > ExtensionConstants.MAX_ENTRY_BYTES) {
                return ExtensionFailure.InvalidPackage("Entry too large: $name")
            }
            totalBytes += entry.sizeBytes
            if (totalBytes > ExtensionConstants.MAX_TOTAL_BYTES) {
                return ExtensionFailure.InvalidPackage("Package too large after unpacking")
            }
            if (entry.compressedSizeBytes > 0) {
                val ratio = entry.sizeBytes.toDouble() / entry.compressedSizeBytes.toDouble()
                if (ratio > ExtensionConstants.MAX_COMPRESSION_RATIO) {
                    return ExtensionFailure.InvalidPackage("Suspicious compression ratio: $name")
                }
            }

            if (normalized == ExtensionConstants.MANIFEST_FILE) {
                hasManifest = true
                continue
            }
            if (!isAllowedPath(normalized)) {
                if (!isAllowedRootFile(normalized)) {
                    return ExtensionFailure.InvalidPackage("Unexpected file: $name")
                }
                continue
            }
            val fileExtension = normalized.substringAfterLast('.', "").lowercase()
            if (fileExtension in ExtensionConstants.FORBIDDEN_FILE_EXTENSIONS) {
                return ExtensionFailure.InvalidPackage("Executable content is not allowed: $name")
            }
        }

        if (!hasManifest) {
            return ExtensionFailure.InvalidPackage("Missing ${ExtensionConstants.MANIFEST_FILE}")
        }
        return null
    }

    private fun isAllowedPath(path: String): Boolean {
        return ExtensionConstants.ALLOWED_DIRECTORIES.any { PathSanitizer.isInside(path, it) }
    }

    /** Root-level licence/readme files, but never anything nested or executable. */
    private fun isAllowedRootFile(path: String): Boolean {
        if (path.contains('/')) {
            return false
        }
        return path in ExtensionConstants.ALLOWED_ROOT_FILES
    }
}