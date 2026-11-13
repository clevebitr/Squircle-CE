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

package com.blacksquircle.ui.feature.extensions.data.mapper

import com.blacksquircle.ui.extension.model.InstalledExtension
import com.blacksquircle.ui.feature.extensions.domain.model.ExtensionModel

internal object ExtensionMapper {

    fun toModel(extension: InstalledExtension): ExtensionModel {
        return ExtensionModel(
            id = extension.id,
            displayName = extension.displayName,
            version = extension.version,
            publisher = extension.publisher,
            description = extension.description,
            enabled = extension.enabled,
            installType = extension.installType,
        )
    }
}