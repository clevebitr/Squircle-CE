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

/**
 * Extension contributed resources live in the same id space as the bundled ones,
 * so every contributed id is namespaced with its extension id to avoid collisions.
 *
 * Theme: `ext:acme.toml/toml-dark`, font: `ext:acme.toml/acme-mono`.
 */
object ExtensionIds {

    private const val PREFIX = "ext:"
    private const val SEPARATOR = "/"

    fun theme(extensionId: String, themeId: String): String {
        return namespaced(extensionId, themeId)
    }

    fun font(extensionId: String, fontId: String): String {
        return namespaced(extensionId, fontId)
    }

    fun isExtensionId(id: String): Boolean {
        return id.startsWith(PREFIX)
    }

    /** Returns `extensionId to localId`, or `null` when [id] is not namespaced. */
    fun resolve(id: String): Pair<String, String>? {
        if (!isExtensionId(id)) {
            return null
        }
        val body = id.removePrefix(PREFIX)
        val separatorIndex = body.indexOf(SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex == body.lastIndex) {
            return null
        }
        return body.substring(0, separatorIndex) to body.substring(separatorIndex + 1)
    }

    private fun namespaced(extensionId: String, localId: String): String {
        return PREFIX + extensionId + SEPARATOR + localId
    }
}