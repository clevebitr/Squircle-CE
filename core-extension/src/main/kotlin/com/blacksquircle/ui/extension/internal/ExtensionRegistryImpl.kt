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
import com.blacksquircle.ui.extension.ExtensionPaths
import com.blacksquircle.ui.extension.ExtensionRegistry
import com.blacksquircle.ui.extension.model.ContributedFont
import com.blacksquircle.ui.extension.model.ContributedLanguage
import com.blacksquircle.ui.extension.model.ContributedTheme
import com.blacksquircle.ui.extension.model.ContributionFailure
import com.blacksquircle.ui.extension.model.ExtensionContributions
import com.blacksquircle.ui.extension.model.ExtensionIds
import com.blacksquircle.ui.extension.model.ExtensionManifest
import com.blacksquircle.ui.extension.model.InstallType
import com.blacksquircle.ui.extension.model.InstalledExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class ExtensionRegistryImpl(
    private val dispatcherProvider: DispatcherProvider,
    private val extensionDao: ExtensionDao,
    private val extensionPaths: ExtensionPaths,
    private val manifestParser: ExtensionManifestParser,
) : ExtensionRegistry {

    private val contributionsFlow = MutableStateFlow(ExtensionContributions())
    private val refreshMutex = Mutex()
    private var initialized = false

    override fun contributions(): ExtensionContributions {
        return contributionsFlow.value
    }

    override fun observeContributions(): Flow<ExtensionContributions> {
        return contributionsFlow.asStateFlow()
    }

    override fun observeInstalled(): Flow<List<InstalledExtension>> {
        return extensionDao.flowAll().map { entities -> entities.mapNotNull(::toInstalled) }
    }

    override suspend fun installed(): List<InstalledExtension> {
        return withContext(dispatcherProvider.io()) {
            extensionDao.loadAll().mapNotNull(::toInstalled)
        }
    }

    override suspend fun ensureLoaded(): ExtensionContributions {
        return refreshMutex.withLock {
            if (initialized) {
                contributionsFlow.value
            } else {
                rebuild()
            }
        }
    }

    override suspend fun refresh(): ExtensionContributions {
        return refreshMutex.withLock { rebuild() }
    }

    private suspend fun rebuild(): ExtensionContributions {
        return withContext(dispatcherProvider.io()) {
            val entities = extensionDao.loadAll()
            removeOrphanDirectories(entities)

            val languages = mutableListOf<ContributedLanguage>()
            val themes = mutableListOf<ContributedTheme>()
            val fonts = mutableListOf<ContributedFont>()
            val failures = mutableListOf<ContributionFailure>()

            for (entity in entities.filter(ExtensionEntity::enabled)) {
                val manifest = manifestParser.parse(extensionPaths.manifestOf(entity.extensionId))
                if (manifest == null) {
                    failures += ContributionFailure(
                        extensionId = entity.extensionId,
                        reason = "Unreadable manifest",
                    )
                    continue
                }
                collectLanguages(manifest, languages, failures)
                collectThemes(manifest, themes, failures)
                collectFonts(manifest, fonts, failures)
            }

            val contributions = ExtensionContributions(
                languages = languages,
                themes = themes,
                fonts = fonts,
                failures = failures,
            )
            contributionsFlow.value = contributions
            initialized = true
            Timber.d(
                "Extension contributions rebuilt: " +
                    "${languages.size} languages, ${themes.size} themes, ${fonts.size} fonts, " +
                    "${failures.size} failures"
            )
            contributions
        }
    }

    private fun collectLanguages(
        manifest: ExtensionManifest,
        destination: MutableList<ContributedLanguage>,
        failures: MutableList<ContributionFailure>,
    ) {
        for (language in manifest.contributes.languages) {
            if (extensionPaths.resolve(language.grammar) == null) {
                failures += ContributionFailure(manifest.id, "Missing grammar: ${language.grammar}")
                continue
            }
            val languageConfiguration = language.languageConfiguration?.let { path ->
                if (extensionPaths.resolve(path) == null) {
                    failures += ContributionFailure(manifest.id, "Missing language configuration: $path")
                    null
                } else {
                    path
                }
            }
            destination += ContributedLanguage(
                extensionId = manifest.id,
                scopeName = language.scopeName,
                name = language.name,
                displayName = language.displayName,
                extensions = language.extensions,
                filenames = language.filenames,
                mimeTypes = language.mimeTypes,
                grammarPath = language.grammar,
                languageConfigurationPath = languageConfiguration,
                embeddedLanguages = language.embeddedLanguages,
            )
        }
    }

    private fun collectThemes(
        manifest: ExtensionManifest,
        destination: MutableList<ContributedTheme>,
        failures: MutableList<ContributionFailure>,
    ) {
        for (theme in manifest.contributes.themes) {
            val file = extensionPaths.resolve(theme.path)
            if (file == null) {
                failures += ContributionFailure(manifest.id, "Missing theme: ${theme.path}")
                continue
            }
            destination += ContributedTheme(
                extensionId = manifest.id,
                themeId = theme.id,
                globalId = ExtensionIds.theme(manifest.id, theme.id),
                displayName = theme.displayName.ifBlank { theme.id },
                uiTheme = theme.uiTheme,
                file = file,
            )
        }
    }

    private fun collectFonts(
        manifest: ExtensionManifest,
        destination: MutableList<ContributedFont>,
        failures: MutableList<ContributionFailure>,
    ) {
        for (font in manifest.contributes.fonts) {
            val file = extensionPaths.resolve(font.path)
            if (file == null) {
                failures += ContributionFailure(manifest.id, "Missing font: ${font.path}")
                continue
            }
            destination += ContributedFont(
                extensionId = manifest.id,
                fontId = font.id,
                globalId = ExtensionIds.font(manifest.id, font.id),
                displayName = font.displayName.ifBlank { font.id },
                file = file,
            )
        }
    }

    /** Directories without a database record are leftovers of a crashed install. */
    private fun removeOrphanDirectories(entities: List<ExtensionEntity>) {
        val known = entities.mapTo(mutableSetOf(), ExtensionEntity::extensionId)
        for (extensionId in extensionPaths.installedIds()) {
            if (extensionId !in known) {
                extensionPaths.directoryOf(extensionId).deleteRecursively()
                Timber.w("Removed orphan extension directory: $extensionId")
            }
        }
    }

    private fun toInstalled(entity: ExtensionEntity): InstalledExtension? {
        val manifest = manifestParser.parse(extensionPaths.manifestOf(entity.extensionId))
        if (manifest == null) {
            Timber.w("Could not read manifest of installed extension ${entity.extensionId}")
        }
        return InstalledExtension(
            id = entity.extensionId,
            version = entity.version,
            displayName = manifest?.displayName?.ifBlank { entity.extensionId } ?: entity.extensionId,
            publisher = manifest?.publisher.orEmpty(),
            description = manifest?.description.orEmpty(),
            enabled = entity.enabled,
            installType = InstallType.of(entity.installType),
            sourceUrl = entity.sourceUrl,
            installedAt = entity.installedAt,
            directory = extensionPaths.directoryOf(entity.extensionId),
        )
    }
}