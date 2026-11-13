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

import com.blacksquircle.ui.extension.model.ExtensionContributes
import com.blacksquircle.ui.extension.model.ExtensionFailure
import com.blacksquircle.ui.extension.model.ExtensionManifest
import com.blacksquircle.ui.extension.model.FontContribution
import com.blacksquircle.ui.extension.model.LanguageContribution
import com.blacksquircle.ui.extension.model.ThemeContribution
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionManifestValidatorTest {

    @Test
    fun `accepts a valid manifest`() {
        val failures = ExtensionManifestValidator.validate(manifest(), currentVersionCode = 10028)

        assertTrue(failures.isEmpty())
    }

    @Test
    fun `rejects unsupported schema version`() {
        val failures = ExtensionManifestValidator.validate(
            manifest().copy(schemaVersion = 2),
            currentVersionCode = 10028,
        )

        assertTrue(failures.single() is ExtensionFailure.UnsupportedSchema)
    }

    @Test
    fun `rejects publisher that is not part of the id`() {
        val failures = ExtensionManifestValidator.validate(
            manifest().copy(publisher = "other"),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects manifest requiring a newer app`() {
        val failures = ExtensionManifestValidator.validate(
            manifest().copy(minVersionCode = 20000),
            currentVersionCode = 10028,
        )

        assertTrue(failures.single() is ExtensionFailure.UnsupportedVersion)
    }

    @Test
    fun `rejects extension that contributes nothing`() {
        val failures = ExtensionManifestValidator.validate(
            manifest(languages = emptyList(), themes = emptyList()),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects grammar outside syntaxes directory`() {
        val language = language().copy(grammar = "extension://acme.toml/themes/toml.tmLanguage.json")
        val failures = ExtensionManifestValidator.validate(
            manifest(languages = listOf(language)),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects grammar of another extension`() {
        val language = language().copy(grammar = "extension://other.toml/syntaxes/toml.tmLanguage.json")
        val failures = ExtensionManifestValidator.validate(
            manifest(languages = listOf(language)),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects duplicated scope names`() {
        val failures = ExtensionManifestValidator.validate(
            manifest(languages = listOf(language(), language())),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects duplicated file associations`() {
        val other = language().copy(scopeName = "source.toml2", name = "toml2")
        val failures = ExtensionManifestValidator.validate(
            manifest(languages = listOf(language(), other)),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects file association without dot`() {
        val failures = ExtensionManifestValidator.validate(
            manifest(languages = listOf(language().copy(extensions = listOf("toml")))),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects theme with unknown ui theme`() {
        val failures = ExtensionManifestValidator.validate(
            manifest(themes = listOf(theme().copy(uiTheme = "sepia"))),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `rejects font with unsupported format`() {
        val font = FontContribution(
            id = "acme-mono",
            path = "extension://acme.toml/fonts/acme-mono.woff2",
            displayName = "Acme Mono",
        )
        val failures = ExtensionManifestValidator.validate(
            manifest(fonts = listOf(font)),
            currentVersionCode = 10028,
        )

        assertTrue(failures.any { it is ExtensionFailure.InvalidPackage })
    }

    @Test
    fun `accepts manifest when version code is unknown`() {
        val failures = ExtensionManifestValidator.validate(
            manifest().copy(minVersionCode = Long.MAX_VALUE),
            currentVersionCode = null,
        )

        assertTrue(failures.isEmpty())
    }

    private fun manifest(
        languages: List<LanguageContribution> = listOf(language()),
        themes: List<ThemeContribution> = listOf(theme()),
        fonts: List<FontContribution> = emptyList(),
    ): ExtensionManifest {
        return ExtensionManifest(
            schemaVersion = 1,
            id = "acme.toml",
            publisher = "acme",
            displayName = "TOML Support",
            description = "TOML syntax highlighting",
            version = "1.0.0",
            minVersionCode = 10028,
            contributes = ExtensionContributes(
                languages = languages,
                themes = themes,
                fonts = fonts,
            ),
        )
    }

    private fun language(): LanguageContribution {
        return LanguageContribution(
            scopeName = "source.toml",
            name = "toml",
            displayName = "TOML",
            extensions = listOf(".toml"),
            filenames = listOf("Cargo.lock"),
            mimeTypes = listOf("application/toml"),
            grammar = "extension://acme.toml/syntaxes/toml.tmLanguage.json",
            languageConfiguration = "extension://acme.toml/language-configuration/toml.json",
        )
    }

    private fun theme(): ThemeContribution {
        return ThemeContribution(
            id = "toml-dark",
            path = "extension://acme.toml/themes/dark.json",
            uiTheme = "dark",
            displayName = "TOML Dark",
        )
    }
}