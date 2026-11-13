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

package com.blacksquircle.ui.feature.extensions.data.repository

import android.content.Context
import android.net.Uri
import com.blacksquircle.ui.core.files.Directories
import com.blacksquircle.ui.core.provider.coroutine.DispatcherProvider
import com.blacksquircle.ui.extension.ExtensionInstaller
import com.blacksquircle.ui.extension.ExtensionRegistry
import com.blacksquircle.ui.extension.model.ExtensionFailure
import com.blacksquircle.ui.extension.model.InstallResult
import com.blacksquircle.ui.extension.model.InstallType
import com.blacksquircle.ui.feature.extensions.data.mapper.ExtensionMapper
import com.blacksquircle.ui.feature.extensions.domain.model.ExtensionModel
import com.blacksquircle.ui.feature.extensions.domain.model.InstallOutcome
import com.blacksquircle.ui.feature.extensions.domain.repository.ExtensionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal class ExtensionsRepositoryImpl(
    private val dispatcherProvider: DispatcherProvider,
    private val extensionRegistry: ExtensionRegistry,
    private val extensionInstaller: ExtensionInstaller,
    private val context: Context,
) : ExtensionsRepository {

    override fun observeExtensions(): Flow<List<ExtensionModel>> {
        return extensionRegistry.observeInstalled().map { extensions ->
            extensions.map(ExtensionMapper::toModel)
        }
    }

    override suspend fun install(uri: Uri): InstallOutcome {
        return withContext(dispatcherProvider.io()) {
            val archive = File(Directories.tmpDir(context), "extension-${System.nanoTime()}.zip")
            try {
                if (!copyToCache(uri, archive)) {
                    return@withContext InstallOutcome.Rejected(
                        ExtensionFailure.Io("Could not read the extension package")
                    )
                }
                when (val result = extensionInstaller.install(archive, installType = InstallType.LOCAL)) {
                    is InstallResult.Success -> {
                        InstallOutcome.Installed(ExtensionMapper.toModel(result.extension))
                    }

                    is InstallResult.Failure -> {
                        InstallOutcome.Rejected(result.failure)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Could not install extension")
                InstallOutcome.Rejected(ExtensionFailure.Io("Could not install the extension", e))
            } finally {
                archive.delete()
            }
        }
    }

    override suspend fun setEnabled(extensionId: String, enabled: Boolean) {
        withContext(dispatcherProvider.io()) {
            extensionInstaller.setEnabled(extensionId, enabled)
        }
    }

    override suspend fun uninstall(extensionId: String) {
        withContext(dispatcherProvider.io()) {
            extensionInstaller.uninstall(extensionId)
        }
    }

    private fun copyToCache(uri: Uri, destination: File): Boolean {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return false
        return try {
            inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Could not copy extension package")
            false
        }
    }
}