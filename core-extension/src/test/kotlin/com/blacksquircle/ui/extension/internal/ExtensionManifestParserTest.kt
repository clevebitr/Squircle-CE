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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionManifestParserTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val parser = ExtensionManifestParser(json)

    @Test
    fun `parses a full manifest`() {
        val manifest = requireNotNull(parser.parse(SAMPLE_MANIFEST))

        assertEquals("acme.toml", manifest.id)
        assertEquals(1, manifest.contributes.languages.size)
        assertEquals("source.toml", manifest.contributes.languages.single().scopeName)
        assertEquals(listOf(".toml"), manifest.contributes.languages.single().extensions)
        assertEquals(1, manifest.contributes.themes.size)
    }

    @Test
    fun `ignores unknown fields for forward compatibility`() {
        val manifest = parser.parse(
            """
            {
              "schemaVersion": 1,
              "id": "acme.toml",
              "futureField": { "unknown": true },
              "contributes": {}
            }
            """.trimIndent()
        )

        assertNotNull(manifest)
        assertEquals("acme.toml", manifest?.id)
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(parser.parse("{ not a manifest"))
    }

    @Test
    fun `returns null when file is missing`() {
        assertNull(parser.parse(java.io.File("/does/not/exist/extension.json")))
    }

    companion object {

        private val SAMPLE_MANIFEST = """
            {
              "schemaVersion": 1,
              "id": "acme.toml",
              "publisher": "acme",
              "displayName": "TOML Support",
              "description": "TOML syntax highlighting",
              "version": "1.0.0",
              "minVersionCode": 10028,
              "contributes": {
                "languages": [
                  {
                    "scopeName": "source.toml",
                    "name": "toml",
                    "displayName": "TOML",
                    "extensions": [".toml"],
                    "filenames": ["Cargo.lock"],
                    "mimeTypes": ["application/toml"],
                    "grammar": "extension://acme.toml/syntaxes/toml.tmLanguage.json",
                    "languageConfiguration": "extension://acme.toml/language-configuration/toml.json",
                    "embeddedLanguages": {}
                  }
                ],
                "themes": [
                  {
                    "id": "toml-dark",
                    "path": "extension://acme.toml/themes/dark.json",
                    "uiTheme": "dark",
                    "displayName": "TOML Dark"
                  }
                ]
              }
            }
        """.trimIndent()
    }
}