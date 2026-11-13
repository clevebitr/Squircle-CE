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

/**
 * Immutable snapshot of everything enabled extensions contribute.
 *
 * Consumers (themes, fonts, editor) read this snapshot instead of the database,
 * so a broken extension can never take down a whole feature — its problems land
 * in [failures] while every other extension keeps working.
 */
data class ExtensionContributions(
    val languages: List<ContributedLanguage> = emptyList(),
    val themes: List<ContributedTheme> = emptyList(),
    val fonts: List<ContributedFont> = emptyList(),
    val failures: List<ContributionFailure> = emptyList(),
)

data class ContributedLanguage(
    val extensionId: String,
    val scopeName: String,
    val name: String,
    val displayName: String,
    val extensions: List<String>,
    val filenames: List<String>,
    val mimeTypes: List<String>,
    /** Original manifest path, e.g. `extension://acme.toml/syntaxes/toml.tmLanguage.json`. */
    val grammarPath: String,
    val languageConfigurationPath: String?,
    val embeddedLanguages: Map<String, String>,
)

data class ContributedTheme(
    val extensionId: String,
    /** Local id from the manifest, e.g. `toml-dark`. */
    val themeId: String,
    /** Globally unique id used by settings, e.g. `ext:acme.toml/toml-dark`. */
    val globalId: String,
    val displayName: String,
    val uiTheme: String,
    val file: File,
)

data class ContributedFont(
    val extensionId: String,
    val fontId: String,
    /** Globally unique id, e.g. `ext:acme.toml/acme-mono`. */
    val globalId: String,
    val displayName: String,
    val file: File,
)

data class ContributionFailure(
    val extensionId: String,
    val reason: String,
    val cause: Throwable? = null,
)