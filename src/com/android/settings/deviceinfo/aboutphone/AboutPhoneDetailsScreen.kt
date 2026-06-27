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

package com.android.settings.deviceinfo.aboutphone

import android.app.settings.SettingsEnums
import android.content.Context
import androidx.fragment.app.Fragment
import com.android.settings.R
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.deviceinfo.simstatus.SimEidPreference
import com.android.settings.flags.Flags
import com.android.settingslib.metadata.PreferenceCategory
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.widget.UntitledPreferenceCategoryMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@ProvidePreferenceScreen(AboutPhoneDetailsScreen.KEY)
open class AboutPhoneDetailsScreen : PreferenceScreenMixin {
    override val key: String
        get() = KEY

    override val title: Int
        get() = R.string.my_device_info_details_title

    override val highlightMenuKey: Int
        get() = R.string.menu_key_about_device

    override fun getMetricsCategory() = SettingsEnums.DEVICEINFO

    override fun fragmentClass(): Class<out Fragment>? = AboutPhoneDetailsFragment::class.java

    override fun isFlagEnabled(context: Context) = Flags.catalystMyDeviceInfoPrefScreen()

    override fun hasCompleteHierarchy() = false

    override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
        preferenceHierarchy(context) {
            +PreferenceCategory(
                DEVICE_IDENTIFIERS_CATEGORY,
                R.string.my_device_info_device_identifiers_category_title,
            ) +=
                {
                    addAsync(coroutineScope, Dispatchers.Default) {
                        +SimEidPreference(context) order 10
                    }
                }
            +UntitledPreferenceCategoryMetadata(RADIO_INFO_CATEGORY) order +300 += {
                +RadioInfoPreference()
            }
        }

    companion object {
        const val KEY = "about_phone_more_details"
        private const val DEVICE_IDENTIFIERS_CATEGORY = "device_identifiers_category"
        private const val RADIO_INFO_CATEGORY = "radio_info_category"
    }
}
