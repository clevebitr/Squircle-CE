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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionIdsTest {

    @Test
    fun `keeps contributed ids in their own namespace`() {
        val themeId = ExtensionIds.theme("acme.toml", "toml-dark")

        assertEquals("ext:acme.toml/toml-dark", themeId)
        assertTrue(ExtensionIds.isExtensionId(themeId))
        assertEquals("acme.toml" to "toml-dark", ExtensionIds.resolve(themeId))

        assertFalse(ExtensionIds.isExtensionId("darcula"))
        assertNull(ExtensionIds.resolve("darcula"))
        assertNull(ExtensionIds.resolve("ext:acme.toml"))
    }
}