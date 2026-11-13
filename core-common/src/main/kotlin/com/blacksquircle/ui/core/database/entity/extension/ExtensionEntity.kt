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

package com.blacksquircle.ui.core.database.entity.extension

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.blacksquircle.ui.core.database.utils.Tables

/**
 * Installed extension record.
 *
 * The manifest is stored as raw JSON on purpose: new contribution types don't
 * need a database migration, only a parser update.
 */
@Entity(tableName = Tables.EXTENSIONS)
data class ExtensionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val extensionId: String,
    @ColumnInfo(name = "version")
    val version: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "install_type")
    val installType: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String?,
    @ColumnInfo(name = "installed_at")
    val installedAt: Long,
    @ColumnInfo(name = "manifest")
    val manifest: String,
)
