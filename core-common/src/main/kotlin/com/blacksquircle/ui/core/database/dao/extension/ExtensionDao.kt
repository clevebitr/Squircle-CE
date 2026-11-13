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

package com.blacksquircle.ui.core.database.dao.extension

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blacksquircle.ui.core.database.entity.extension.ExtensionEntity
import com.blacksquircle.ui.core.database.utils.Tables
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {

    @Query("SELECT * FROM `${Tables.EXTENSIONS}` ORDER BY `installed_at` DESC")
    fun flowAll(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM `${Tables.EXTENSIONS}` ORDER BY `installed_at` DESC")
    suspend fun loadAll(): List<ExtensionEntity>

    @Query("SELECT * FROM `${Tables.EXTENSIONS}` WHERE `id` = :extensionId")
    suspend fun load(extensionId: String): ExtensionEntity?

    @Query("DELETE FROM `${Tables.EXTENSIONS}` WHERE `id` = :extensionId")
    suspend fun delete(extensionId: String)

    @Query("UPDATE `${Tables.EXTENSIONS}` SET `enabled` = :enabled WHERE `id` = :extensionId")
    suspend fun setEnabled(extensionId: String, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(extensionEntity: ExtensionEntity): Long
}