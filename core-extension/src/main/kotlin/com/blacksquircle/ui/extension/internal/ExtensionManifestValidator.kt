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
import com.blacksquircle.ui.extension.model.ExtensionManifest
import com.blacksquircle.ui.extension.model.LanguageContribution

/**
 * Structural and compatibility validation of a parsed manifest.
 *
 * Returns every problem instead of failing fast so the extensions screen can
 * show a complete report for a rejected package.
 */
internal object ExtensionManifestValidator {

    private const val MAX_DISPLAY_NAME_LENGTH = 64
    private const val MAX_DESCRIPTION_LENGTH = 2000
    private const val MAX_VERSION_LENGTH = 32

    private val EXTENSION_ID = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")
    private val LOCAL_ID = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")
    private val SCOPE_NAME = Regex("^[a-zA-Z0-9][a-zA-Z0-9.+_-]*$")
    private val SEMANTIC_VERSION = Regex("^\\d+\\.\\d+\\.\\d+([-+][0-9A-Za-z.-]+)?$")
    private val MIME_TYPE = Regex("^[a-zA-Z0-9][a-zA-Z0-9.+_-]*/[a-zA-Z0-9][a-zA-Z0-9.+_-]*$")
    private val FONT_FILE_EXTENSION = Regex("^.*\\.(ttf|otf)$")

    fun validate(manifest: ExtensionManifest, currentVersionCode: Long?): List<ExtensionFailure> {
        val failures = mutableListOf<ExtensionFailure>()

        if (manifest.schemaVersion != ExtensionConstants.SCHEMA_VERSION) {
            return listOf(ExtensionFailure.UnsupportedSchema(manifest.schemaVersion))
        }
        if (!EXTENSION_ID.matches(manifest.id)) {
            failures += ExtensionFailure.InvalidPackage("Invalid extension id: '${manifest.id}'")
        }
        if (!hasPublisherPrefix(manifest)) {
            failures += ExtensionFailure.InvalidPackage(
                "Publisher '${manifest.publisher}' must be a prefix of the id"
            )
        }
        if (manifest.displayName.isBlank() || manifest.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
            failures += ExtensionFailure.InvalidPackage("Invalid display name")
        }
        if (manifest.description.length > MAX_DESCRIPTION_LENGTH) {
            failures += ExtensionFailure.InvalidPackage("Description is too long")
        }
        if (!SEMANTIC_VERSION.matches(manifest.version) || manifest.version.length > MAX_VERSION_LENGTH) {
            failures += ExtensionFailure.InvalidPackage("Invalid version: '${manifest.version}'")
        }
        if (manifest.minVersionCode < 0) {
            failures += ExtensionFailure.InvalidPackage("Invalid minVersionCode")
        }
        if (currentVersionCode != null && manifest.minVersionCode > currentVersionCode) {
            failures += ExtensionFailure.UnsupportedVersion(
                minVersionCode = manifest.minVersionCode,
                currentVersionCode = currentVersionCode,
            )
        }

        val contributes = manifest.contributes
        if (contributes.languages.isEmpty() && contributes.themes.isEmpty() && contributes.fonts.isEmpty()) {
            failures += ExtensionFailure.InvalidPackage("Extension contributes nothing")
        }

        validateLanguages(manifest, contributes.languages, failures)
        validateThemes(manifest, failures)
        validateFonts(manifest, failures)

        return failures
    }

    private fun validateLanguages(
        manifest: ExtensionManifest,
        languages: List<LanguageContribution>,
        failures: MutableList<ExtensionFailure>,
    ) {
        val scopeNames = mutableSetOf<String>()
        val fileAssociations = mutableSetOf<String>()

        for (language in languages) {
            if (!SCOPE_NAME.matches(language.scopeName)) {
                failures += ExtensionFailure.InvalidPackage("Invalid scope name: '${language.scopeName}'")
                continue
            }
            if (!scopeNames.add(language.scopeName)) {
                failures += ExtensionFailure.InvalidPackage("Duplicate scope name: '${language.scopeName}'")
            }
            if (language.name.isBlank()) {
                failures += ExtensionFailure.InvalidPackage("Missing language name for ${language.scopeName}")
            }
            if (language.displayName.isBlank()) {
                failures += ExtensionFailure.InvalidPackage("Missing display name for ${language.scopeName}")
            }
            if (!isContributionPath(language.grammar, manifest.id, "syntaxes/")) {
                failures += ExtensionFailure.InvalidPackage(
                    "Grammar must live in syntaxes/: '${language.grammar}'"
                )
            }
            val languageConfiguration = language.languageConfiguration
            if (languageConfiguration != null &&
                !isContributionPath(languageConfiguration, manifest.id, "language-configuration/")
            ) {
                failures += ExtensionFailure.InvalidPackage(
                    "Language configuration must live in language-configuration/: '$languageConfiguration'"
                )
            }
            for (extension in language.extensions) {
                if (!extension.startsWith('.') || extension.length < 2) {
                    failures += ExtensionFailure.InvalidPackage("Invalid file extension: '$extension'")
                } else if (!fileAssociations.add(extension.lowercase())) {
                    failures += ExtensionFailure.InvalidPackage("Duplicate file extension: '$extension'")
                }
            }
            for (filename in language.filenames) {
                if (filename.isBlank() || filename.contains('/') || filename.contains('\\')) {
                    failures += ExtensionFailure.InvalidPackage("Invalid file name: '$filename'")
                }
            }
            for (mimeType in language.mimeTypes) {
                if (!MIME_TYPE.matches(mimeType)) {
                    failures += ExtensionFailure.InvalidPackage("Invalid mime type: '$mimeType'")
                }
            }
            for ((_, embeddedScopeName) in language.embeddedLanguages) {
                if (!SCOPE_NAME.matches(embeddedScopeName)) {
                    failures += ExtensionFailure.InvalidPackage("Invalid embedded scope: '$embeddedScopeName'")
                }
            }
        }
    }

    private fun validateThemes(
        manifest: ExtensionManifest,
        failures: MutableList<ExtensionFailure>,
    ) {
        val themeIds = mutableSetOf<String>()

        for (theme in manifest.contributes.themes) {
            if (!LOCAL_ID.matches(theme.id)) {
                failures += ExtensionFailure.InvalidPackage("Invalid theme id: '${theme.id}'")
                continue
            }
            if (!themeIds.add(theme.id)) {
                failures += ExtensionFailure.InvalidPackage("Duplicate theme id: '${theme.id}'")
            }
            if (!isContributionPath(theme.path, manifest.id, "themes/")) {
                failures += ExtensionFailure.InvalidPackage("Theme must live in themes/: '${theme.path}'")
            }
            if (theme.uiTheme != "dark" && theme.uiTheme != "light") {
                failures += ExtensionFailure.InvalidPackage("Invalid uiTheme: '${theme.uiTheme}'")
            }
        }
    }

    private fun validateFonts(
        manifest: ExtensionManifest,
        failures: MutableList<ExtensionFailure>,
    ) {
        val fontIds = mutableSetOf<String>()

        for (font in manifest.contributes.fonts) {
            if (!LOCAL_ID.matches(font.id)) {
                failures += ExtensionFailure.InvalidPackage("Invalid font id: '${font.id}'")
                continue
            }
            if (!fontIds.add(font.id)) {
                failures += ExtensionFailure.InvalidPackage("Duplicate font id: '${font.id}'")
            }
            if (!isContributionPath(font.path, manifest.id, "fonts/")) {
                failures += ExtensionFailure.InvalidPackage("Font must live in fonts/: '${font.path}'")
            }
            if (!FONT_FILE_EXTENSION.matches(font.path.lowercase())) {
                failures += ExtensionFailure.InvalidPackage("Unsupported font format: '${font.path}'")
            }
        }
    }

    /** `<publisher>.<name>`, where the publisher itself may contain dots. */
    private fun hasPublisherPrefix(manifest: ExtensionManifest): Boolean {
        if (manifest.publisher.isEmpty()) {
            return false
        }
        return manifest.id.startsWith(manifest.publisher + '.')
    }

    private fun isContributionPath(value: String, extensionId: String, directory: String): Boolean {
        val prefix = ExtensionConstants.contributionUri(extensionId, "")
        if (!value.startsWith(prefix)) {
            return false
        }
        val relativePath = value.removePrefix(prefix)
        return PathSanitizer.isSafeRelativePath(relativePath) &&
            PathSanitizer.isInside(relativePath, directory)
    }
}