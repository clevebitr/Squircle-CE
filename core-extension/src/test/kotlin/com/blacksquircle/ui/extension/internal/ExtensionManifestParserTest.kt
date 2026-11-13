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
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ExtensionManifestParserTest {

    private val parser = ExtensionManifestParser(Json { ignoreUnknownKeys = true })

    @Test
    fun `reads a manifest and tolerates fields from the future`() {
        val manifest = requireNotNull(
            parser.parse(
                """
                {
                  "schemaVersion": 1,
                  "id": "acme.toml",
                  "version": "1.0.0",
                  "futureField": { "unknown": true },
                  "contributes": {
                    "languages": [
                      {
                        "scopeName": "source.toml",
                        "name": "toml",
                        "displayName": "TOML",
                        "extensions": [".toml"],
                        "grammar": "extension://acme.toml/syntaxes/toml.tmLanguage.json"
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("acme.toml", manifest.id)
        assertEquals("source.toml", manifest.contributes.languages.single().scopeName)
        assertEquals(listOf(".toml"), manifest.contributes.languages.single().extensions)
    }

    @Test
    fun `returns null for content that is not a manifest`() {
        assertNull(parser.parse("{ not a manifest"))
        assertNull(parser.parse(File("/does/not/exist/extension.json")))
    }
}