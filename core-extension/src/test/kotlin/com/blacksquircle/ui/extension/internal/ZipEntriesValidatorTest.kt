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

    private val manifestEntry = Entry("extension.json", 1024, 512)

    @Test
    fun `accepts a well formed package`() {
        val entries = listOf(
            manifestEntry,
            Entry("syntaxes/toml.tmLanguage.json", 4096, 1024),
            Entry("language-configuration/toml.json", 256, 128),
            Entry("themes/dark.json", 2048, 512),
        )

        assertNull(ZipEntriesValidator.validate(entries))
    }

    @Test
    fun `accepts directory entries as they appear in real archives`() {
        val entries = listOf(
            manifestEntry,
            Entry("syntaxes/", 0, 0),
            Entry("syntaxes/diff.tmLanguage.json", 4096, 1024),
            Entry("LICENSES/", 0, 0),
            Entry("LICENSES/vscode-MIT.txt", 1024, 512),
            Entry("LICENSE", 512, 256),
            Entry("README.md", 256, 128),
        )

        assertNull(ZipEntriesValidator.validate(entries))
    }

    @Test
    fun `rejects unexpected root text file`() {
        val entries = listOf(manifestEntry, Entry("NOTES.txt", 10, 10))

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects package without manifest`() {
        val entries = listOf(Entry("themes/dark.json", 1024, 512))

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects traversal entry`() {
        val entries = listOf(manifestEntry, Entry("../evil.json", 10, 10))

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects unexpected root file`() {
        val entries = listOf(manifestEntry, Entry("readme.md", 10, 10))

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects unexpected directory`() {
        val entries = listOf(manifestEntry, Entry("lib/tool.txt", 10, 10))

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects executable content inside allowed directory`() {
        val entries = listOf(manifestEntry, Entry("fonts/tool.so", 10, 10))

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects oversized entry`() {
        val entries = listOf(
            manifestEntry,
            Entry("themes/dark.json", ExtensionConstants.MAX_ENTRY_BYTES + 1, 1024),
        )

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects suspicious compression ratio`() {
        val entries = listOf(manifestEntry, Entry("themes/dark.json", 1024L * 1024, 1))

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects too many entries`() {
        val entries = buildList {
            add(manifestEntry)
            repeat(ExtensionConstants.MAX_ENTRY_COUNT) { index ->
                add(Entry("themes/theme$index.json", 16, 8))
            }
        }

        assertTrue(ZipEntriesValidator.validate(entries) is ExtensionFailure.InvalidPackage)
    }

    @Test
    fun `rejects empty package`() {
        assertTrue(ZipEntriesValidator.validate(emptyList()) is ExtensionFailure.InvalidPackage)
    }
}