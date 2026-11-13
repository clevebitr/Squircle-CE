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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Declarative extension manifest. This is the only file an extension is required
 * to provide in the root of its archive.
 *
 * L1 consumes [ExtensionContributes.languages], [ExtensionContributes.themes] and
 * [ExtensionContributes.fonts]. The remaining sections are parsed and kept for
 * forward compatibility, see docs/design/declarative-extensions-l1.md §17.
 */
@Serializable
data class ExtensionManifest(
    @SerialName("schemaVersion") val schemaVersion: Int = 0,
    @SerialName("id") val id: String = "",
    @SerialName("publisher") val publisher: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("minVersionCode") val minVersionCode: Long = 0L,
    @SerialName("contributes") val contributes: ExtensionContributes = ExtensionContributes(),
)

@Serializable
data class ExtensionContributes(
    @SerialName("languages") val languages: List<LanguageContribution> = emptyList(),
    @SerialName("themes") val themes: List<ThemeContribution> = emptyList(),
    @SerialName("fonts") val fonts: List<FontContribution> = emptyList(),
    @SerialName("snippets") val snippets: List<SnippetContribution> = emptyList(),
    @SerialName("keybindings") val keybindings: List<KeybindingContribution> = emptyList(),
    @SerialName("completions") val completions: List<CompletionContribution> = emptyList(),
    @SerialName("lsp") val lsp: List<LspContribution> = emptyList(),
)

@Serializable
data class LanguageContribution(
    @SerialName("scopeName") val scopeName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("extensions") val extensions: List<String> = emptyList(),
    @SerialName("filenames") val filenames: List<String> = emptyList(),
    @SerialName("mimeTypes") val mimeTypes: List<String> = emptyList(),
    @SerialName("grammar") val grammar: String = "",
    @SerialName("languageConfiguration") val languageConfiguration: String? = null,
    @SerialName("embeddedLanguages") val embeddedLanguages: Map<String, String> = emptyMap(),
)

@Serializable
data class ThemeContribution(
    @SerialName("id") val id: String = "",
    @SerialName("path") val path: String = "",
    @SerialName("uiTheme") val uiTheme: String = "dark",
    @SerialName("displayName") val displayName: String = "",
)

@Serializable
data class FontContribution(
    @SerialName("id") val id: String = "",
    @SerialName("path") val path: String = "",
    @SerialName("displayName") val displayName: String = "",
)

@Serializable
data class SnippetContribution(
    @SerialName("language") val language: String = "",
    @SerialName("path") val path: String = "",
)

@Serializable
data class KeybindingContribution(
    @SerialName("command") val command: String = "",
    @SerialName("keys") val keys: List<String> = emptyList(),
)

@Serializable
data class CompletionContribution(
    @SerialName("language") val language: String = "",
    @SerialName("path") val path: String = "",
)

@Serializable
data class LspContribution(
    @SerialName("language") val language: String = "",
    @SerialName("transport") val transport: LspTransport = LspTransport(),
    @SerialName("launch") val launch: LspLaunch = LspLaunch(),
    @SerialName("installHint") val installHint: String = "",
    @SerialName("initializationOptions") val initializationOptions: JsonObject? = null,
)

@Serializable
data class LspTransport(
    @SerialName("type") val type: String = "tcp",
    @SerialName("host") val host: String = "127.0.0.1",
    @SerialName("port") val port: Int = 0,
)

@Serializable
data class LspLaunch(
    @SerialName("type") val type: String = "none",
    @SerialName("command") val command: String = "",
    @SerialName("background") val background: Boolean = true,
)