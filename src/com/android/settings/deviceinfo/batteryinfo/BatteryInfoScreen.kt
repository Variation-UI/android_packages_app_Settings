/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo.batteryinfo

import android.app.settings.SettingsEnums
import android.content.Context
import androidx.fragment.app.Fragment
import com.android.settings.R
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.overlay.FeatureFactory.Companion.featureFactory
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.preferenceHierarchy
import kotlinx.coroutines.CoroutineScope

@ProvidePreferenceScreen(BatteryInfoScreen.KEY)
open class BatteryInfoScreen : PreferenceScreenMixin, PreferenceAvailabilityProvider {
    override val key: String
        get() = KEY

    override val title: Int
        get() = R.string.battery_info

    override val keywords: Int
        get() = R.string.keywords_battery_info

    override val highlightMenuKey: Int
        get() = R.string.menu_key_battery

    override fun getMetricsCategory() = SettingsEnums.SETTINGS_BATTERY_INFORMATION

    override fun fragmentClass(): Class<out Fragment>? = BatteryInfoFragment::class.java

    override fun hasCompleteHierarchy() = false

    override fun isAvailable(context: Context) =
        featureFactory.batterySettingsFeatureProvider.isBatteryInfoEnabled(context)

    override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
        preferenceHierarchy(context) {}

    companion object {
        const val KEY = "battery_info"
    }
}
