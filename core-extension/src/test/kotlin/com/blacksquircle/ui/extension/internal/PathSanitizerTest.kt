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
    fun `accepts nested relative path`() {
        assertTrue(PathSanitizer.isSafeRelativePath("syntaxes/toml/toml.tmLanguage.json"))
    }

    @Test
    fun `accepts directory entry with trailing separator`() {
        assertTrue(PathSanitizer.isSafeRelativePath("themes/"))
    }

    @Test
    fun `rejects traversal segments`() {
        assertFalse(PathSanitizer.isSafeRelativePath("../evil.json"))
        assertFalse(PathSanitizer.isSafeRelativePath("themes/../../evil.json"))
    }

    @Test
    fun `rejects absolute paths`() {
        assertFalse(PathSanitizer.isSafeRelativePath("/etc/passwd"))
    }

    @Test
    fun `rejects windows separators and drives`() {
        assertFalse(PathSanitizer.isSafeRelativePath("themes\\a.json"))
        assertFalse(PathSanitizer.isSafeRelativePath("c:/themes/a.json"))
    }

    @Test
    fun `rejects empty segments`() {
        assertFalse(PathSanitizer.isSafeRelativePath("themes//a.json"))
        assertFalse(PathSanitizer.isSafeRelativePath(""))
        assertFalse(PathSanitizer.isSafeRelativePath("/"))
    }

    @Test
    fun `matches directory prefix by segment`() {
        assertTrue(PathSanitizer.isInside("themes/a.json", "themes/"))
        assertFalse(PathSanitizer.isInside("themes_evil/a.json", "themes/"))
    }
}