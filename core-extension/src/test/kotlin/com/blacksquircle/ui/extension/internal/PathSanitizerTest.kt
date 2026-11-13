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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathSanitizerTest {

    @Test
    fun `accepts paths below an allow listed directory`() {
        assertTrue(PathSanitizer.isSafeRelativePath("syntaxes/toml/toml.tmLanguage.json"))
        assertTrue(PathSanitizer.isSafeRelativePath("themes/"))
        assertTrue(PathSanitizer.isInside("themes/a.json", "themes/"))
        assertTrue(PathSanitizer.isInside("syntaxes", "syntaxes/"))
    }

    @Test
    fun `rejects paths that could escape the extension directory`() {
        val unsafe = listOf(
            "../evil.json",
            "themes/../../evil.json",
            "/etc/passwd",
            "themes\\a.json",
            "c:/themes/a.json",
            "themes//a.json",
            "",
            "/",
        )

        for (path in unsafe) {
            assertFalse("must be unsafe: '$path'", PathSanitizer.isSafeRelativePath(path))
        }
        assertFalse(PathSanitizer.isInside("themes_evil/a.json", "themes/"))
    }
}