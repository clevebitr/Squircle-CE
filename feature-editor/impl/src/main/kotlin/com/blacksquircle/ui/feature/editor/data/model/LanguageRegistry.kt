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

package com.blacksquircle.ui.feature.editor.data.model

import com.blacksquircle.ui.extension.ExtensionRegistry
import com.blacksquircle.ui.filesystem.base.model.FileModel

/**
 * File name to language mapping: bundled associations first, then whatever
 * enabled extensions contribute. Bundled languages win, so installing an
 * extension can never silently change how a known file type behaves.
 */
internal class LanguageRegistry(private val extensionRegistry: ExtensionRegistry) {

    fun guessLanguage(fileModel: FileModel): String? {
        FileAssociation.guessLanguage(fileModel.extension)?.let { language ->
            return language
        }

        val contributed = extensionRegistry.contributions().languages
        if (contributed.isEmpty()) {
            return null
        }

        val byFileName = contributed.firstOrNull { language ->
            language.filenames.any { it.equals(fileModel.name, ignoreCase = true) }
        }
        if (byFileName != null) {
            return byFileName.scopeName
        }

        val extension = fileModel.extension.lowercase()
        return contributed.firstOrNull { language ->
            language.extensions.any { it.equals(extension, ignoreCase = true) }
        }?.scopeName
    }
}