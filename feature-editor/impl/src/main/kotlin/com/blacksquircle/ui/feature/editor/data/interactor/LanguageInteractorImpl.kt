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

package com.blacksquircle.ui.feature.editor.data.interactor

import android.content.Context
import com.blacksquircle.ui.core.provider.coroutine.DispatcherProvider
import com.blacksquircle.ui.extension.ExtensionRegistry
import com.blacksquircle.ui.extension.model.ContributedLanguage
import com.blacksquircle.ui.feature.editor.data.mapper.GrammarMapper
import com.blacksquircle.ui.feature.editor.data.model.GrammarData
import com.blacksquircle.ui.feature.editor.data.provider.ExtensionFileResolver
import com.blacksquircle.ui.feature.editor.domain.interactor.LanguageInteractor
import com.blacksquircle.ui.feature.editor.domain.model.GrammarModel
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

internal class LanguageInteractorImpl(
    private val dispatcherProvider: DispatcherProvider,
    private val jsonParser: Json,
    private val extensionRegistry: ExtensionRegistry,
    private val context: Context,
) : LanguageInteractor {

    private var merged = emptyList<GrammarModel>()
    private var contributedScopeNames = emptyList<String>()

    init {
        FileProviderRegistry.getInstance().apply {
            addFileProvider(AssetsFileResolver(context.assets))
            addFileProvider(ExtensionFileResolver(context))
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun loadGrammars(): List<GrammarModel> {
        return withContext(dispatcherProvider.io()) {
            val contributed = extensionRegistry.contributions().languages
            val scopeNames = contributed.map(ContributedLanguage::scopeName)
            if (merged.isNotEmpty() && scopeNames == contributedScopeNames) {
                return@withContext merged
            }

            val bundled = context.assets.open(ASSET_FILE).use { stream ->
                jsonParser.decodeFromStream<List<GrammarData>>(stream)
                    .map(GrammarMapper::toModel)
            }
            val bundledScopeNames = bundled.mapTo(mutableSetOf(), GrammarModel::scopeName)
            val contributedGrammars = contributed
                .filterNot { language -> language.scopeName in bundledScopeNames }
                .map { language -> language.toGrammarModel() }

            merged = bundled + contributedGrammars
            contributedScopeNames = scopeNames
            merged
        }
    }

    override suspend fun registerGrammar(language: String) {
        withContext(dispatcherProvider.io()) {
            val grammar = merged.find { it.scopeName == language }
            if (grammar == null) {
                return@withContext
            }
            if (GrammarRegistry.getInstance().findGrammar(language) != null) {
                return@withContext
            }

            val grammarDefinition = GrammarMapper.toDefinition(grammar)
            GrammarRegistry.getInstance().loadGrammar(grammarDefinition)

            grammar.embeddedLanguages.forEach { (_, name) ->
                val embeddedGrammar = merged.find { it.name == name }
                if (embeddedGrammar != null) {
                    registerGrammar(embeddedGrammar.scopeName)
                }
            }
        }
    }

    private fun ContributedLanguage.toGrammarModel(): GrammarModel {
        return GrammarModel(
            name = name,
            displayName = displayName,
            scopeName = scopeName,
            grammar = grammarPath,
            languageConfiguration = languageConfigurationPath.orEmpty(),
            embeddedLanguages = embeddedLanguages,
        )
    }

    companion object {
        private const val ASSET_FILE = "languages.json"
    }
}