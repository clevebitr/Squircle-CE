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
 * Every way installing (or validating) an extension can go wrong.
 *
 * The extension system never throws to the UI: failures are values so that they
 * can be rendered per extension without taking the app down.
 */
sealed class ExtensionFailure {

    abstract val message: String

    class InvalidPackage(override val message: String) : ExtensionFailure()

    class UnsupportedSchema(val schemaVersion: Int) : ExtensionFailure() {
        override val message: String = "Unsupported manifest schema: $schemaVersion"
    }

    class UnsupportedVersion(
        val minVersionCode: Long,
        val currentVersionCode: Long,
    ) : ExtensionFailure() {
        override val message: String =
            "Extension requires version code $minVersionCode, current is $currentVersionCode"
    }

    class ChecksumMismatch(
        val expected: String,
        val actual: String,
    ) : ExtensionFailure() {
        override val message: String = "Checksum mismatch: expected $expected, actual $actual"
    }

    class NotFound(val extensionId: String) : ExtensionFailure() {
        override val message: String = "Extension $extensionId is not installed"
    }

    class Io(override val message: String, val cause: Throwable? = null) : ExtensionFailure()
}