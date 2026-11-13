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

import com.blacksquircle.ui.extension.internal.ZipEntriesValidator.Entry
import com.blacksquircle.ui.extension.model.ExtensionFailure
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipEntriesValidatorTest {

    private val manifest = Entry("extension.json", 1024, 512)

    @Test
    fun `accepts the layout of a real archive`() {
        val entries = listOf(
            manifest,
            Entry("syntaxes/", 0, 0),
            Entry("syntaxes/diff.tmLanguage.json", 4096, 1024),
            Entry("language-configuration/", 0, 0),
            Entry("language-configuration/diff.json", 256, 128),
            Entry("LICENSES/", 0, 0),
            Entry("LICENSES/vscode-MIT.txt", 1024, 512),
            Entry("LICENSE", 512, 256),
            Entry("README.md", 256, 128),
        )

        assertNull(ZipEntriesValidator.validate(entries))
    }

    @Test
    fun `rejects everything that is not a data file`() {
        val rejected = listOf(
            "../evil.json" to "traversal",
            "syntaxes/../../evil.json" to "traversal below a directory",
            "/etc/passwd" to "absolute path",
            "themes\\a.json" to "windows separator",
            "NOTES.txt" to "unknown root file",
            "lib/tool.txt" to "unknown directory",
            "fonts/tool.so" to "executable content",
        )

        for ((name, reason) in rejected) {
            val failure = ZipEntriesValidator.validate(listOf(manifest, Entry(name, 10, 10)))
            assertTrue(
                "$reason must be rejected: $name",
                failure is ExtensionFailure.InvalidPackage,
            )
        }
    }

    @Test
    fun `rejects package without manifest`() {
        val failure = ZipEntriesValidator.validate(listOf(Entry("themes/dark.json", 10, 10)))

        assertTrue(failure is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects packages that are too large or too many`() {
        val oversized = listOf(manifest, Entry("themes/dark.json", ExtensionConstants.MAX_ENTRY_BYTES + 1, 1024))
        val bomb = listOf(manifest, Entry("themes/dark.json", 1024L * 1024, 1))
        val tooMany = buildList {
            add(manifest)
            repeat(ExtensionConstants.MAX_ENTRY_COUNT) { index ->
                add(Entry("themes/theme$index.json", 16, 8))
            }
        }

        assertTrue(ZipEntriesValidator.validate(oversized) is ExtensionFailure.InvalidPackage)
        assertTrue(ZipEntriesValidator.validate(bomb) is ExtensionFailure.InvalidPackage)
        assertTrue(ZipEntriesValidator.validate(tooMany) is ExtensionFailure.InvalidPackage)
        assertTrue(ZipEntriesValidator.validate(emptyList()) is ExtensionFailure.InvalidPackage)
    }
}