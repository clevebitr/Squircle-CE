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

package com.blacksquircle.ui.feature.extensions.ui.extensions.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.blacksquircle.ui.ds.divider.HorizontalDivider
import com.blacksquircle.ui.ds.preference.Preference
import com.blacksquircle.ui.ds.switcher.Switcher
import com.blacksquircle.ui.feature.extensions.R
import com.blacksquircle.ui.feature.extensions.domain.model.ExtensionModel

@Composable
internal fun ExtensionItem(
    extension: ExtensionModel,
    onToggleClicked: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Column {
        Preference(
            title = extension.displayName,
            subtitle = extension.subtitle(),
            onClick = onClick,
            trailingContent = {
                Switcher(
                    onClick = { onToggleClicked(!extension.enabled) },
                    checked = extension.enabled,
                )
            },
        )
        HorizontalDivider()
    }
}

@Composable
private fun ExtensionModel.subtitle(): String {
    val publisher = publisher.ifBlank {
        stringResource(R.string.extensions_item_unknown_publisher)
    }
    return stringResource(R.string.extensions_item_subtitle, version, publisher)
}