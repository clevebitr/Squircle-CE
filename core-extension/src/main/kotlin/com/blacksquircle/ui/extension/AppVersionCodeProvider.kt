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

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * Provides the running app version code, used to check `minVersionCode`.
 *
 * Kept behind an interface so the installer can be unit tested without Android.
 */
fun interface AppVersionCodeProvider {

    fun versionCode(): Long
}

internal class PackageVersionCodeProvider(private val context: Context) : AppVersionCodeProvider {

    override fun versionCode(): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(packageInfo)
        } catch (e: Exception) {
            0L
        }
    }
}