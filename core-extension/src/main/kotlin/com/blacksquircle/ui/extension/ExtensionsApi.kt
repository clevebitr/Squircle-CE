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

package com.blacksquircle.ui.extension

/**
 * Extension system surface available to other features.
 *
 * Features receive it as a Dagger component dependency (see
 * `docs/design/declarative-extensions-l1.md` §8), exactly like `EditorApi`
 * or `ExplorerApi`, so no feature depends on another feature's implementation.
 */
interface ExtensionsApi {

    fun provideExtensionRegistry(): ExtensionRegistry

    fun provideExtensionInstaller(): ExtensionInstaller
}