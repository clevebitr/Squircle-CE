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
    fun `accepts a manifest the app can run`() {
        val dottedPublisher = manifest(id = "dev.squircle.diff", publisher = "dev.squircle")

        assertTrue(ExtensionManifestValidator.validate(manifest(), 10028).isEmpty())
        assertTrue(ExtensionManifestValidator.validate(dottedPublisher, 10028).isEmpty())
    }

    @Test
    fun `rejects manifest the app cannot run`() {
        val unknownSchema = manifest().copy(schemaVersion = 2)
        val tooNew = manifest().copy(minVersionCode = 20000)

        assertTrue(
            ExtensionManifestValidator.validate(unknownSchema, 10028)
                .single() is ExtensionFailure.UnsupportedSchema
        )
        assertTrue(
            ExtensionManifestValidator.validate(tooNew, 10028)
                .single() is ExtensionFailure.UnsupportedVersion
        )
    }

    @Test
    fun `rejects contributions that cannot be wired up`() {
        val cases = mapOf(
            "publisher is not a prefix of the id" to manifest(publisher = "other"),
            "publisher covers the whole id" to manifest(publisher = "acme.toml"),
            "empty contribution" to manifest(languages = emptyList(), themes = emptyList()),
            "grammar outside syntaxes" to manifest(
                languages = listOf(language().copy(grammar = "extension://acme.toml/themes/toml.json"))
            ),
            "grammar of another extension" to manifest(
                languages = listOf(language().copy(grammar = "extension://other/syntaxes/toml.json"))
            ),
            "duplicate scope" to manifest(languages = listOf(language(), language())),
            "duplicate association" to manifest(
                languages = listOf(language(), language().copy(scopeName = "source.toml2"))
            ),
            "extension without dot" to manifest(
                languages = listOf(language().copy(extensions = listOf("toml")))
            ),
            "unknown ui theme" to manifest(themes = listOf(theme().copy(uiTheme = "sepia"))),
            "unsupported font format" to manifest(
                fonts = listOf(font().copy(path = "extension://acme.toml/fonts/mono.woff2"))
            ),
        )

        for ((reason, manifest) in cases) {
            val failures = ExtensionManifestValidator.validate(manifest, currentVersionCode = 10028)
            assertTrue(reason, failures.any { it is ExtensionFailure.InvalidPackage })
        }
    }

    private fun manifest(
        id: String = "acme.toml",
        publisher: String = "acme",
        languages: List<LanguageContribution>? = null,
        themes: List<ThemeContribution>? = null,
        fonts: List<FontContribution>? = null,
    ): ExtensionManifest {
        return ExtensionManifest(
            schemaVersion = 1,
            id = id,
            publisher = publisher,
            displayName = "TOML Support",
            description = "TOML syntax highlighting",
            version = "1.0.0",
            minVersionCode = 10028,
            contributes = ExtensionContributes(
                languages = languages ?: listOf(language(id)),
                themes = themes ?: listOf(theme(id)),
                fonts = fonts.orEmpty(),
            ),
        )
    }

    private fun language(id: String = "acme.toml"): LanguageContribution {
        return LanguageContribution(
            scopeName = "source.toml",
            name = "toml",
            displayName = "TOML",
            extensions = listOf(".toml"),
            filenames = listOf("Cargo.lock"),
            mimeTypes = listOf("application/toml"),
            grammar = "extension://$id/syntaxes/toml.tmLanguage.json",
            languageConfiguration = "extension://$id/language-configuration/toml.json",
        )
    }

    private fun theme(id: String = "acme.toml"): ThemeContribution {
        return ThemeContribution(
            id = "toml-dark",
            path = "extension://$id/themes/dark.json",
            uiTheme = "dark",
            displayName = "TOML Dark",
        )
    }

    private fun font(id: String = "acme.toml"): FontContribution {
        return FontContribution(
            id = "acme-mono",
            path = "extension://$id/fonts/mono.ttf",
            displayName = "Acme Mono",
        )
    }
}