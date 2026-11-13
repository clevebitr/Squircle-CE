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

package com.blacksquircle.ui.feature.editor.data.provider

import android.content.Context
import com.blacksquircle.ui.extension.ExtensionPaths
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver
import timber.log.Timber
import java.io.FileInputStream
import java.io.InputStream

/**
 * Serves contributed grammars and language configurations to sora-editor.
 *
 * Paths look like `extension://dev.squircle.diff/syntaxes/diff.tmLanguage.json`.
 * Resolvers are asked in registration order, so [AssetsFileResolver] keeps
 * precedence for bundled languages — contributed grammars only fill the gaps.
 */
internal class ExtensionFileResolver(context: Context) : FileResolver {

    private val extensionPaths = ExtensionPaths(context)

    override fun resolveStreamByPath(path: String): InputStream? {
        val file = extensionPaths.resolve(path) ?: return null
        return try {
            FileInputStream(file)
        } catch (e: Exception) {
            Timber.w(e, "Could not read contributed file: $path")
            null
        }
    }
}