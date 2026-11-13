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

import android.content.Context
import com.blacksquircle.ui.core.database.AppDatabase
import com.blacksquircle.ui.core.provider.coroutine.DispatcherProvider
import com.blacksquircle.ui.extension.AppVersionCodeProvider
import com.blacksquircle.ui.extension.ExtensionInstaller
import com.blacksquircle.ui.extension.ExtensionPaths
import com.blacksquircle.ui.extension.ExtensionRegistry
import com.blacksquircle.ui.extension.PackageVersionCodeProvider
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
object CoreExtensionModule {

    @Provides
    @Singleton
    fun provideExtensionPaths(context: Context): ExtensionPaths {
        return ExtensionPaths(context)
    }

    @Provides
    @Singleton
    fun provideAppVersionCodeProvider(context: Context): AppVersionCodeProvider {
        return PackageVersionCodeProvider(context)
    }

    @Provides
    @Singleton
    fun provideExtensionRegistry(
        dispatcherProvider: DispatcherProvider,
        appDatabase: AppDatabase,
        extensionPaths: ExtensionPaths,
        json: Json,
    ): ExtensionRegistry {
        return ExtensionRegistryImpl(
            dispatcherProvider = dispatcherProvider,
            extensionDao = appDatabase.extensionDao(),
            extensionPaths = extensionPaths,
            manifestParser = ExtensionManifestParser(json),
        )
    }

    @Provides
    @Singleton
    fun provideExtensionInstaller(
        dispatcherProvider: DispatcherProvider,
        json: Json,
        appDatabase: AppDatabase,
        extensionPaths: ExtensionPaths,
        versionCodeProvider: AppVersionCodeProvider,
        extensionRegistry: ExtensionRegistry,
    ): ExtensionInstaller {
        return ExtensionInstallerImpl(
            dispatcherProvider = dispatcherProvider,
            json = json,
            extensionDao = appDatabase.extensionDao(),
            extensionPaths = extensionPaths,
            manifestParser = ExtensionManifestParser(json),
            versionCodeProvider = versionCodeProvider,
            registry = extensionRegistry,
        )
    }
}