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

import com.blacksquircle.ui.core.database.dao.extension.ExtensionDao
import com.blacksquircle.ui.core.database.entity.extension.ExtensionEntity
import com.blacksquircle.ui.core.provider.coroutine.DispatcherProvider
import com.blacksquircle.ui.extension.AppVersionCodeProvider
import com.blacksquircle.ui.extension.ExtensionInstaller
import com.blacksquircle.ui.extension.ExtensionPaths
import com.blacksquircle.ui.extension.ExtensionRegistry
import com.blacksquircle.ui.extension.model.ExtensionFailure
import com.blacksquircle.ui.extension.model.ExtensionManifest
import com.blacksquircle.ui.extension.model.InstallResult
import com.blacksquircle.ui.extension.model.InstallType
import com.blacksquircle.ui.extension.model.InstalledExtension
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import timber.log.Timber
import java.io.File

internal class ExtensionInstallerImpl(
    private val dispatcherProvider: DispatcherProvider,
    private val json: Json,
    private val extensionDao: ExtensionDao,
    private val extensionPaths: ExtensionPaths,
    private val manifestParser: ExtensionManifestParser,
    private val versionCodeProvider: AppVersionCodeProvider,
    private val registry: ExtensionRegistry,
) : ExtensionInstaller {

    override suspend fun install(
        archive: File,
        expectedSha256: String?,
        installType: InstallType,
        sourceUrl: String?,
    ): InstallResult {
        return withContext(dispatcherProvider.io()) {
            var stagingDirectory: File? = null
            try {
                validateArchive(archive, expectedSha256)

                val staging = newStagingDirectory()
                stagingDirectory = staging
                extractArchive(archive, staging)

                val manifest = readManifest(staging)
                val failures = ExtensionManifestValidator.validate(
                    manifest = manifest,
                    currentVersionCode = versionCodeProvider.versionCode(),
                )
                if (failures.isNotEmpty()) {
                    throw InstallAborted(failures.first())
                }
                verifyContributedFiles(manifest, staging)

                val target = extensionPaths.directoryOf(manifest.id)
                replaceDirectory(staging, target)
                staging.deleteRecursively()
                stagingDirectory = null

                val installedExtension = InstalledExtension(
                    id = manifest.id,
                    version = manifest.version,
                    displayName = manifest.displayName,
                    publisher = manifest.publisher,
                    description = manifest.description,
                    enabled = true,
                    installType = installType,
                    sourceUrl = sourceUrl,
                    installedAt = System.currentTimeMillis(),
                    directory = target,
                )

                extensionDao.upsert(
                    ExtensionEntity(
                        extensionId = manifest.id,
                        version = manifest.version,
                        enabled = true,
                        installType = installType.value,
                        sourceUrl = sourceUrl,
                        installedAt = installedExtension.installedAt,
                        manifest = json.encodeToString(ExtensionManifest.serializer(), manifest),
                    )
                )
                registry.refresh()

                Timber.i("Extension ${manifest.id} ${manifest.version} installed")
                InstallResult.Success(installedExtension)
            } catch (e: InstallAborted) {
                Timber.w("Extension install rejected: ${e.failure.message}")
                InstallResult.Failure(e.failure)
            } catch (e: Exception) {
                Timber.e(e, "Could not install extension")
                InstallResult.Failure(ExtensionFailure.Io("Could not install extension", e))
            } finally {
                stagingDirectory?.deleteRecursively()
            }
        }
    }

    override suspend fun uninstall(extensionId: String): Boolean {
        return withContext(dispatcherProvider.io()) {
            val known = extensionDao.load(extensionId) != null
            val directory = extensionPaths.directoryOf(extensionId)
            if (!known && !directory.exists()) {
                return@withContext false
            }
            directory.deleteRecursively()
            extensionDao.delete(extensionId)
            registry.refresh()
            Timber.i("Extension $extensionId uninstalled, registry will be fully clean after restart")
            true
        }
    }

    override suspend fun setEnabled(extensionId: String, enabled: Boolean) {
        withContext(dispatcherProvider.io()) {
            if (extensionDao.load(extensionId) == null) {
                Timber.w("Could not toggle unknown extension $extensionId")
                return@withContext
            }
            extensionDao.setEnabled(extensionId, enabled)
            registry.refresh()
        }
    }

    private fun validateArchive(archive: File, expectedSha256: String?) {
        if (!archive.isFile || !archive.canRead()) {
            throw InstallAborted(ExtensionFailure.InvalidPackage("Archive not found"))
        }
        if (archive.length() <= 0L || archive.length() > ExtensionConstants.MAX_ARCHIVE_BYTES) {
            throw InstallAborted(ExtensionFailure.InvalidPackage("Unexpected archive size"))
        }
        if (expectedSha256 != null) {
            val actual = Sha256.of(archive)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw InstallAborted(ExtensionFailure.ChecksumMismatch(expectedSha256, actual))
            }
        }

        val zipFile = try {
            ZipFile(archive)
        } catch (e: ZipException) {
            throw InstallAborted(ExtensionFailure.InvalidPackage("Not a zip archive"))
        }
        if (!zipFile.isValidZipFile || zipFile.isEncrypted) {
            throw InstallAborted(ExtensionFailure.InvalidPackage("Not a readable zip archive"))
        }

        val entries = zipFile.fileHeaders.map { fileHeader ->
            ZipEntriesValidator.Entry(
                name = fileHeader.fileName,
                sizeBytes = fileHeader.uncompressedSize,
                compressedSizeBytes = fileHeader.compressedSize,
            )
        }
        val failure = ZipEntriesValidator.validate(entries)
        if (failure != null) {
            throw InstallAborted(failure)
        }
    }

    private fun newStagingDirectory(): File {
        return File(extensionPaths.staging, "install-${System.nanoTime()}").also { it.mkdirs() }
    }

    private fun extractArchive(archive: File, destination: File) {
        try {
            ZipFile(archive).extractAll(destination.absolutePath)
        } catch (e: ZipException) {
            throw InstallAborted(ExtensionFailure.InvalidPackage("Could not unpack archive"))
        }
    }

    private fun readManifest(directory: File): ExtensionManifest {
        val manifestFile = File(directory, ExtensionConstants.MANIFEST_FILE)
        val manifest = manifestParser.parse(manifestFile)
            ?: throw InstallAborted(ExtensionFailure.InvalidPackage("Unreadable manifest"))
        return manifest
    }

    private fun verifyContributedFiles(manifest: ExtensionManifest, directory: File) {
        val contributes = manifest.contributes
        val paths = mutableListOf<String>()
        contributes.languages.forEach { language ->
            paths += language.grammar
            language.languageConfiguration?.let(paths::add)
        }
        contributes.themes.forEach { theme -> paths += theme.path }
        contributes.fonts.forEach { font -> paths += font.path }
        contributes.snippets.forEach { snippet -> paths += snippet.path }
        contributes.completions.forEach { completion -> paths += completion.path }

        val prefix = ExtensionConstants.contributionUri(manifest.id, "")
        for (path in paths) {
            val relativePath = path.removePrefix(prefix)
            if (relativePath == path || !File(directory, relativePath).isFile) {
                throw InstallAborted(ExtensionFailure.InvalidPackage("Missing contributed file: $path"))
            }
        }
    }

    private fun replaceDirectory(stagingDirectory: File, target: File) {
        if (target.exists() && !target.deleteRecursively()) {
            throw InstallAborted(ExtensionFailure.InvalidPackage("Could not replace previous version"))
        }
        if (!stagingDirectory.copyRecursively(target, overwrite = true)) {
            throw InstallAborted(ExtensionFailure.InvalidPackage("Could not copy extension files"))
        }
    }

    private class InstallAborted(val failure: ExtensionFailure) : Exception(failure.message)
}