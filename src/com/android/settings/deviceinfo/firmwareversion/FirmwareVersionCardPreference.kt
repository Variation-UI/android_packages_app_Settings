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

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import com.android.settings.R
import com.android.settings.deviceinfo.aboutphone.AboutPhoneCardPreference
import com.android.settings.deviceinfo.aboutphone.AboutPhoneCardStyle
import com.android.settings.utils.getLocale
import com.android.settingslib.DeviceInfoUtils

class AndroidVersionCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FirmwareVersionInfoCardPreference(context, attrs, defStyleAttr, defStyleRes) {

    override val iconResId: Int
        get() = R.drawable.ic_android16

    override val showTrailingIconBackground: Boolean = false

    override val tintTrailingIcon: Boolean = false

    override val showCardChevron: Boolean = false

    override val trailingIconFillsCard: Boolean = true

    override val trailingIconEndInsetDp: Int = 18

    override val titleDrawableResId: Int
        get() = R.drawable.ic_android16_text

    override val titleDrawableHeightDp: Int = 24

    override val hideCardScrollbars: Boolean = true

    override val fallbackValue: CharSequence
        get() = Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY
}

class SecurityPatchCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FirmwareVersionInfoCardPreference(context, attrs, defStyleAttr, defStyleRes) {

    override val iconResId: Int
        get() = R.drawable.ic_settings_security_filled

    override val showCardChevron: Boolean = false

    override val fallbackValue: CharSequence
        get() = DeviceInfoUtils.getSecurityPatch(context.getLocale()) ?: ""
}

abstract class FirmwareVersionInfoCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AboutPhoneCardPreference(context, attrs, defStyleAttr, defStyleRes) {

    override val cardStyle: AboutPhoneCardStyle
        get() = AboutPhoneCardStyle.DeviceName

    override val cardMinHeightDp: Int = 104

    override val cardVerticalPaddingDp: Int = 18

    protected abstract val fallbackValue: CharSequence

    override fun getCardTitle(): CharSequence =
        summary?.takeUnless { it.isPlaceholderSummary() } ?: fallbackValue

    override fun getCardSummary(): CharSequence =
        title ?: ""

    override fun onCardClick() {
        performClick()
    }

    private fun CharSequence.isPlaceholderSummary(): Boolean =
        isBlank() || toString() == context.getText(R.string.summary_placeholder).toString()
}
