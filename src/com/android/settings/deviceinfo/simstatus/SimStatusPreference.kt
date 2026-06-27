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

package com.android.settings.deviceinfo.simstatus

import android.content.Context
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.Utils
import com.android.settings.wifi.utils.isAdminUser
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceLifecycleProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.PreferenceTitleProvider
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.preference.PreferenceBindingPlaceholder

/** Preference to show SIM status for single and multi modem devices. */
class SimStatusPreference(
    context: Context,
    private val slotIndex: Int,
    private val activeModemCount: Int,
) :
    PreferenceMetadata,
    PreferenceBinding,
    PreferenceBindingPlaceholder,
    PreferenceLifecycleProvider,
    PreferenceTitleProvider,
    PreferenceSummaryProvider,
    PreferenceAvailabilityProvider {

    private val slotSimStatus = SlotSimStatus(context)
    private val dialogTitle = context.getFormattedTitle()

    override val key: String
        get() = KEY_PREFIX + "${slotIndex + 1}"

    override val keywords: Int
        get() = R.string.keywords_sim_status

    override fun isAvailable(context: Context): Boolean =
        context.isAdminUser == true &&
            (Utils.isMobileDataCapable(context) || Utils.isVoiceCapable(context)) &&
            slotIndex < activeModemCount

    override fun isEnabled(context: Context): Boolean =
        slotSimStatus.getSubscriptionInfo(slotIndex) != null

    override fun getTitle(context: Context): CharSequence? = dialogTitle

    override fun getSummary(context: Context): CharSequence? =
        slotSimStatus.getSubscriptionInfo(slotIndex)?.carrierName
            ?: context.getText(R.string.device_info_not_available)

    override fun onCreate(context: PreferenceLifecycleContext) {
        val preference = context.requirePreference<Preference>(key)
        preference.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                SimStatusDialogFragment.show(context.childFragmentManager, slotIndex, dialogTitle)
                true
            }
    }

    private fun Context.getFormattedTitle(): String =
        if (activeModemCount <= 1) {
            getString(R.string.sim_status_title)
        } else {
            getString(R.string.sim_status_title_sim_slot, slotIndex + 1)
        }

    companion object {
        const val KEY_PREFIX = "sim_status"
    }
}
