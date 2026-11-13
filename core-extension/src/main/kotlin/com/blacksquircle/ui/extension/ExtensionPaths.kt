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

import android.content.Context
import com.blacksquircle.ui.core.files.Directories
import com.blacksquircle.ui.extension.internal.ExtensionConstants
import com.blacksquircle.ui.extension.internal.PathSanitizer
import java.io.File

/**
 * Location of installed extensions on disk.
 *
 * Layout: `files/extensions/<extensionId>/…`, so contributed paths can be
 * resolved from the extension id alone — no absolute path is ever persisted.
 */
class ExtensionPaths(private val context: Context) {

    val root: File
        get() = Directories.extensionsDir(context)

    val staging: File
        get() = File(root, ExtensionConstants.STAGING_DIR).also { it.mkdirs() }

    fun directoryOf(extensionId: String): File {
        return File(root, extensionId)
    }

    fun manifestOf(extensionId: String): File {
        return File(directoryOf(extensionId), ExtensionConstants.MANIFEST_FILE)
    }

    fun isInstalled(extensionId: String): Boolean {
        return manifestOf(extensionId).isFile
    }

    /** Installed extension ids, excluding the staging directory. */
    fun installedIds(): List<String> {
        return root.listFiles()
            .orEmpty()
            .filter { file -> file.isDirectory && !file.name.startsWith(".") }
            .map(File::getName)
    }

    /**
     * Resolves `extension://<extensionId>/<relativePath>` to a file.
     *
     * @return `null` when the uri is malformed, points outside the extension
     * directory, or the file does not exist.
     */
    fun resolve(uri: String): File? {
        if (!uri.startsWith(ExtensionConstants.SCHEME_PREFIX)) {
            return null
        }
        val body = uri.removePrefix(ExtensionConstants.SCHEME_PREFIX)
        val separatorIndex = body.indexOf('/')
        if (separatorIndex <= 0 || separatorIndex == body.lastIndex) {
            return null
        }
        val extensionId = body.substring(0, separatorIndex)
        val relativePath = body.substring(separatorIndex + 1)
        return resolve(extensionId, relativePath)
    }

    fun resolve(extensionId: String, relativePath: String): File? {
        if (!PathSanitizer.isSafeRelativePath(relativePath)) {
            return null
        }
        val base = directoryOf(extensionId).canonicalFile
        val target = File(base, relativePath).canonicalFile
        if (!target.path.startsWith(base.path + File.separator)) {
            return null
        }
        return target.takeIf(File::exists)
    }

    companion object {

        const val MANIFEST_FILE = ExtensionConstants.MANIFEST_FILE

        const val SCHEME_PREFIX = ExtensionConstants.SCHEME_PREFIX
    }
}