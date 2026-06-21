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
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.SystemProperties
import android.os.storage.StorageManager
import android.os.storage.VolumeInfo
import android.provider.Settings
import android.text.format.Formatter
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import com.android.settings.deviceinfo.StorageDashboardFragment
import com.android.settings.system.SystemUpdateRepository
import com.android.settingslib.deviceinfo.PrivateStorageInfo
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider
import com.android.settingslib.utils.ThreadUtils
import kotlin.math.roundToInt

private const val TAG = "AboutPhoneCardPreference"
private const val PRIMARY_CARD_MIN_HEIGHT_DP = 101
private const val REGULAR_CARD_MIN_HEIGHT_DP = 81
private const val CARD_HORIZONTAL_MARGIN_DP = 16
private const val CARD_VERTICAL_MARGIN_DP = 4
private const val CARD_ICON_BACKGROUND_SIZE_DP = 42
private const val CARD_ICON_SIZE_DP = 24
private const val DEVICE_NAME_CARD_ICON_SIZE_DP = 20
private const val STORAGE_CARD_ICON_BACKGROUND_SIZE_DP = 38
private const val STORAGE_CARD_ICON_SIZE_DP = 20
private const val DEFAULT_CARD_TITLE_TEXT_SIZE_SP = 17f
private const val PRIMARY_CARD_TITLE_TEXT_SIZE_SP = 24f
private const val CARD_BACKGROUND_SURFACE_BLEND_RATIO = 0.68f
private const val WALLPAPER_CARD_BACKGROUND_SURFACE_BLEND_RATIO = 0.58f
private const val WALLPAPER_GRADIENT_FLOW_DURATION_MS = 16_000L
private const val WALLPAPER_GRADIENT_FLOW_FRAME_DELAY_MS = 120L
private const val CARD_ICON_SURFACE_BLEND_RATIO = 0.32f
private const val UNKNOWN_PROPERTY_VALUE = "UNKNOWN"

private val CARD_BACKGROUND_CONTAINER_COLOR_RES_ID =
    com.android.settingslib.widget.theme.R.color.settingslib_materialColorSecondaryContainer

private class FlowingGradientRippleDrawable(
    rippleColor: ColorStateList,
    private val gradientDrawable: GradientDrawable,
    maskDrawable: GradientDrawable,
    private val sourceColors: IntArray,
) : RippleDrawable(rippleColor, gradientDrawable, maskDrawable) {

    private val flowRunnable = object : Runnable {
        override fun run() {
            updateGradientColors()
            scheduleNextFrame()
        }
    }
    private var flowStartTimeMs = 0L

    init {
        gradientDrawable.orientation = GradientDrawable.Orientation.TL_BR
        gradientDrawable.setColors(sourceColors)
    }

    fun startFlow() {
        if (flowStartTimeMs == 0L) {
            flowStartTimeMs = SystemClock.uptimeMillis()
        }
        unscheduleSelf(flowRunnable)
        scheduleNextFrame()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) {
            if (restart) {
                flowStartTimeMs = SystemClock.uptimeMillis()
            }
            startFlow()
        } else {
            unscheduleSelf(flowRunnable)
        }
        return changed
    }

    private fun scheduleNextFrame() {
        if (isVisible) {
            scheduleSelf(
                flowRunnable,
                SystemClock.uptimeMillis() + WALLPAPER_GRADIENT_FLOW_FRAME_DELAY_MS,
            )
        }
    }

    private fun updateGradientColors() {
        if (sourceColors.size < 2) {
            return
        }
        val elapsedMs = (SystemClock.uptimeMillis() - flowStartTimeMs) %
            WALLPAPER_GRADIENT_FLOW_DURATION_MS
        val progress = elapsedMs.toFloat() / WALLPAPER_GRADIENT_FLOW_DURATION_MS
        gradientDrawable.setColors(
            IntArray(sourceColors.size) { index ->
                sampleGradientColor(
                    (progress + index.toFloat() / sourceColors.size) % 1f,
                )
            },
        )
        invalidateSelf()
    }

    private fun sampleGradientColor(position: Float): Int {
        val scaledPosition = position * sourceColors.size
        val fromIndex = scaledPosition.toInt() % sourceColors.size
        val toIndex = (fromIndex + 1) % sourceColors.size
        val fraction = scaledPosition - scaledPosition.toInt()
        return blendArgb(sourceColors[fromIndex], sourceColors[toIndex], fraction)
    }

    private fun blendArgb(fromColor: Int, toColor: Int, toRatio: Float): Int {
        val fromRatio = 1f - toRatio
        return Color.argb(
            (Color.alpha(fromColor) * fromRatio + Color.alpha(toColor) * toRatio).roundToInt(),
            (Color.red(fromColor) * fromRatio + Color.red(toColor) * toRatio).roundToInt(),
            (Color.green(fromColor) * fromRatio + Color.green(toColor) * toRatio).roundToInt(),
            (Color.blue(fromColor) * fromRatio + Color.blue(toColor) * toRatio).roundToInt(),
        )
    }
}

abstract class AboutPhoneCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.about_phone_card_preference
    }

    final override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.clearItemBackground()
        holder.bindCardStyle(cardStyle)
        holder.bindIcon(iconResId)
        holder.setText(R.id.about_phone_card_title, getCardTitle())
        holder.setText(R.id.about_phone_card_summary, getCardSummary())
        holder.findViewById(R.id.about_phone_card)?.setOnClickListener { onCardClick() }
    }

    open fun refresh() {
        notifyChanged()
    }

    protected abstract val iconResId: Int

    protected open val cardStyle: AboutPhoneCardStyle = AboutPhoneCardStyle.Regular

    protected abstract fun getCardTitle(): CharSequence

    protected abstract fun getCardSummary(): CharSequence

    protected abstract fun onCardClick()

    private fun PreferenceViewHolder.bindIcon(iconResId: Int) {
        (findViewById(R.id.about_phone_card_icon) as? ImageView)?.setImageResource(iconResId)
    }

    private fun PreferenceViewHolder.clearItemBackground() {
        itemView.setBackgroundResource(android.R.color.transparent)
        itemView.foreground = null
    }

    private fun PreferenceViewHolder.bindCardStyle(style: AboutPhoneCardStyle) {
        val card = findViewById(R.id.about_phone_card)
        val iconFrame = findViewById(R.id.about_phone_card_icon_frame)
        val icon = findViewById(R.id.about_phone_card_icon) as? ImageView
        val chevron = findViewById(R.id.about_phone_card_chevron) as? ImageView
        val title = findViewById(R.id.about_phone_card_title) as? TextView
        val summary = findViewById(R.id.about_phone_card_summary) as? TextView

        card?.apply {
            setMinimumHeight(context.dp(style.minHeightDp))
            val cardBackground = context.createCardBackground(style)
            background = cardBackground
            (cardBackground as? FlowingGradientRippleDrawable)?.startFlow()
            layoutParams = layoutParams.applyMargins(
                marginStart = context.dp(CARD_HORIZONTAL_MARGIN_DP),
                marginTop = context.dp(CARD_VERTICAL_MARGIN_DP),
                marginEnd = context.dp(CARD_HORIZONTAL_MARGIN_DP),
                marginBottom = context.dp(CARD_VERTICAL_MARGIN_DP),
            )
            setPaddingRelative(
                context.dp(25),
                context.dp(style.verticalPaddingDp),
                context.dp(20),
                context.dp(style.verticalPaddingDp),
            )
        }
        iconFrame?.apply {
            visibility = if (style.showTrailingIcon) View.VISIBLE else View.GONE
            if (style.showTrailingIcon) {
                setBackgroundResource(style.iconBackgroundResId)
                backgroundTintList = ColorStateList.valueOf(
                    context.getPaleDynamicColor(
                        style.iconContainerColorResId,
                        CARD_ICON_SURFACE_BLEND_RATIO,
                    ),
                )
                layoutParams = layoutParams.applyDimensions(
                    width = context.dp(style.iconBackgroundSizeDp),
                    height = context.dp(style.iconBackgroundSizeDp),
                )
            }
        }
        icon?.apply {
            visibility = if (style.showTrailingIcon) View.VISIBLE else View.GONE
            if (style.showTrailingIcon) {
                imageTintList = ColorStateList.valueOf(context.getColor(style.iconTintColorResId))
                layoutParams = layoutParams.applyDimensions(
                    width = context.dp(style.iconSizeDp),
                    height = context.dp(style.iconSizeDp),
                )
            }
        }
        chevron?.apply {
            visibility = if (style.showChevron) View.VISIBLE else View.GONE
            imageTintList = ColorStateList.valueOf(
                context.getColor(style.chevronTintColorResId),
            )
        }
        title?.apply {
            setTextColor(context.getColor(style.titleColorResId))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.titleTextSizeSp)
        }
        summary?.setTextColor(context.getColor(style.summaryColorResId))
        updateHorizontalSpacing(iconFrame, chevron)
    }

    private fun PreferenceViewHolder.updateHorizontalSpacing(
        iconFrame: View?,
        chevron: ImageView?,
    ) {
        iconFrame?.layoutParams = iconFrame.layoutParams.applyMarginStart(context.dp(16))
        chevron?.layoutParams = chevron.layoutParams.applyMarginStart(context.dp(8))
        (findViewById(R.id.about_phone_card) as? LinearLayout)?.gravity = Gravity.CENTER_VERTICAL
    }

    private fun PreferenceViewHolder.setText(viewId: Int, text: CharSequence) {
        (findViewById(viewId) as? TextView)?.apply {
            this.text = text
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun Context.getPaleDynamicColor(colorResId: Int, surfaceBlendRatio: Float): Int =
        blendArgb(
            getColor(colorResId),
            getColor(
                com.android.settingslib.widget.theme.R.color
                    .settingslib_materialColorSurfaceContainerLowest,
            ),
            surfaceBlendRatio,
        )

    private fun Context.createCardBackground(style: AboutPhoneCardStyle): RippleDrawable {
        val fillDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(25).toFloat()
            if (style.useWallpaperColor) {
                orientation = GradientDrawable.Orientation.TL_BR
            } else {
                setColor(
                    getPaleDynamicColor(
                        style.cardContainerColorResId,
                        CARD_BACKGROUND_SURFACE_BLEND_RATIO,
                    ),
                )
            }
        }
        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(25).toFloat()
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(
            getThemeColor(android.R.attr.colorControlHighlight),
        )
        return if (style.useWallpaperColor) {
            val wallpaperGradientColors = getWallpaperGradientColors()
            fillDrawable.setColors(wallpaperGradientColors)
            FlowingGradientRippleDrawable(
                rippleColor,
                fillDrawable,
                maskDrawable,
                wallpaperGradientColors,
            )
        } else {
            RippleDrawable(
                rippleColor,
                fillDrawable,
                maskDrawable,
            )
        }
    }

    private fun Context.getThemeColor(attrResId: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrResId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun Context.getWallpaperGradientColors(): IntArray {
        val fallbackColors = intArrayOf(
            getColor(CARD_BACKGROUND_CONTAINER_COLOR_RES_ID),
            getColor(
                com.android.settingslib.widget.theme.R.color
                    .settingslib_materialColorTertiaryContainer,
            ),
            getColor(
                com.android.settingslib.widget.theme.R.color
                    .settingslib_materialColorPrimaryContainer,
            ),
        )
        val wallpaperColors = getWallpaperColors()
        val sourceColors = intArrayOf(
            wallpaperColors?.primaryColor?.toArgb() ?: fallbackColors[0],
            wallpaperColors?.secondaryColor?.toArgb() ?: fallbackColors[1],
            wallpaperColors?.tertiaryColor?.toArgb() ?: fallbackColors[2],
        )
        val surfaceColor = getColor(
            com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorSurfaceContainerLowest,
        )
        return sourceColors.map {
            blendArgb(it, surfaceColor, WALLPAPER_CARD_BACKGROUND_SURFACE_BLEND_RATIO)
        }.toIntArray()
    }

    private fun Context.getWallpaperColors(): WallpaperColors? =
        try {
            getSystemService(WallpaperManager::class.java)
                ?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to load wallpaper color", e)
            null
        }

    private fun blendArgb(color: Int, surfaceColor: Int, surfaceBlendRatio: Float): Int {
        val colorRatio = 1f - surfaceBlendRatio
        return Color.argb(
            (Color.alpha(color) * colorRatio + Color.alpha(surfaceColor) * surfaceBlendRatio)
                .roundToInt(),
            (Color.red(color) * colorRatio + Color.red(surfaceColor) * surfaceBlendRatio)
                .roundToInt(),
            (Color.green(color) * colorRatio + Color.green(surfaceColor) * surfaceBlendRatio)
                .roundToInt(),
            (Color.blue(color) * colorRatio + Color.blue(surfaceColor) * surfaceBlendRatio)
                .roundToInt(),
        )
    }

    private fun ViewGroup.LayoutParams?.applyDimensions(
        width: Int,
        height: Int,
    ): ViewGroup.LayoutParams = (this ?: ViewGroup.LayoutParams(width, height)).apply {
        this.width = width
        this.height = height
    }

    private fun ViewGroup.LayoutParams?.applyMarginStart(
        marginStart: Int,
    ): ViewGroup.LayoutParams = (this as? ViewGroup.MarginLayoutParams)?.apply {
        setMarginStart(marginStart)
    } ?: this ?: ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        setMarginStart(marginStart)
    }

    private fun ViewGroup.LayoutParams?.applyMargins(
        marginStart: Int,
        marginTop: Int,
        marginEnd: Int,
        marginBottom: Int,
    ): ViewGroup.LayoutParams = (this as? ViewGroup.MarginLayoutParams)?.apply {
        setMarginStart(marginStart)
        topMargin = marginTop
        setMarginEnd(marginEnd)
        bottomMargin = marginBottom
    } ?: LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        setMarginStart(marginStart)
        topMargin = marginTop
        setMarginEnd(marginEnd)
        bottomMargin = marginBottom
    }
}

class AboutPhoneSystemUpdatePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AboutPhoneCardPreference(context, attrs, defStyleAttr, defStyleRes) {

    override val iconResId: Int
        get() = R.drawable.ic_settings_system_dashboard_filled

    override val cardStyle: AboutPhoneCardStyle
        get() = AboutPhoneCardStyle.Primary

    override fun getCardTitle(): CharSequence =
        "VARIATION"

    override fun getCardSummary(): CharSequence {
        val version = getPropertyOrUnknown(PROP_VARIA_VERSION)
        val maintainer = getPropertyOrUnknown(PROP_VARIA_MASTER)
        return "$version | Maintainer: $maintainer"
    }

    override fun onCardClick() {
        val intent = SystemUpdateRepository(context).getSystemUpdateIntent()
            ?: Intent(Settings.ACTION_SYSTEM_UPDATE_SETTINGS)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity can handle system update settings", e)
        }
    }

    companion object {
        const val KEY = "about_phone_system_update_card"
        private const val PROP_VARIA_VERSION = "ro.varia.version"
        private const val PROP_VARIA_MASTER = "ro.varia.master"

        private fun getPropertyOrUnknown(key: String): String =
            SystemProperties.get(key, UNKNOWN_PROPERTY_VALUE)
                .trim()
                .ifEmpty { UNKNOWN_PROPERTY_VALUE }
    }
}

sealed class AboutPhoneCardStyle(
    val minHeightDp: Int,
    val verticalPaddingDp: Int,
    val backgroundResId: Int,
    val iconBackgroundResId: Int,
    val cardContainerColorResId: Int,
    val iconContainerColorResId: Int,
    val showTrailingIcon: Boolean,
    val showChevron: Boolean,
    val iconBackgroundSizeDp: Int,
    val iconSizeDp: Int,
    val useWallpaperColor: Boolean,
    val titleTextSizeSp: Float,
    val titleColorResId: Int,
    val summaryColorResId: Int,
    val iconTintColorResId: Int,
    val chevronTintColorResId: Int,
) {
    object Primary : AboutPhoneCardStyle(
        minHeightDp = PRIMARY_CARD_MIN_HEIGHT_DP,
        verticalPaddingDp = 16,
        backgroundResId = R.drawable.about_phone_card_primary_background,
        iconBackgroundResId = R.drawable.about_phone_card_primary_icon_background,
        cardContainerColorResId = CARD_BACKGROUND_CONTAINER_COLOR_RES_ID,
        iconContainerColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorPrimaryContainer,
        showTrailingIcon = false,
        showChevron = false,
        iconBackgroundSizeDp = CARD_ICON_BACKGROUND_SIZE_DP,
        iconSizeDp = CARD_ICON_SIZE_DP,
        useWallpaperColor = true,
        titleTextSizeSp = PRIMARY_CARD_TITLE_TEXT_SIZE_SP,
        titleColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        summaryColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        iconTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnPrimaryContainer,
        chevronTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
    )

    object Regular : AboutPhoneCardStyle(
        minHeightDp = REGULAR_CARD_MIN_HEIGHT_DP,
        verticalPaddingDp = 12,
        backgroundResId = R.drawable.about_phone_card_background,
        iconBackgroundResId = R.drawable.about_phone_card_icon_background,
        cardContainerColorResId = CARD_BACKGROUND_CONTAINER_COLOR_RES_ID,
        iconContainerColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorSecondaryContainer,
        showTrailingIcon = true,
        showChevron = true,
        iconBackgroundSizeDp = CARD_ICON_BACKGROUND_SIZE_DP,
        iconSizeDp = CARD_ICON_SIZE_DP,
        useWallpaperColor = false,
        titleTextSizeSp = DEFAULT_CARD_TITLE_TEXT_SIZE_SP,
        titleColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        summaryColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        iconTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        chevronTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
    )

    object DeviceName : AboutPhoneCardStyle(
        minHeightDp = REGULAR_CARD_MIN_HEIGHT_DP,
        verticalPaddingDp = 12,
        backgroundResId = R.drawable.about_phone_card_background,
        iconBackgroundResId = R.drawable.ic_ap_icon1,
        cardContainerColorResId = CARD_BACKGROUND_CONTAINER_COLOR_RES_ID,
        iconContainerColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorSecondaryContainer,
        showTrailingIcon = true,
        showChevron = true,
        iconBackgroundSizeDp = CARD_ICON_BACKGROUND_SIZE_DP,
        iconSizeDp = DEVICE_NAME_CARD_ICON_SIZE_DP,
        useWallpaperColor = false,
        titleTextSizeSp = DEFAULT_CARD_TITLE_TEXT_SIZE_SP,
        titleColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        summaryColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        iconTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        chevronTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
    )

    object Storage : AboutPhoneCardStyle(
        minHeightDp = REGULAR_CARD_MIN_HEIGHT_DP,
        verticalPaddingDp = 12,
        backgroundResId = R.drawable.about_phone_card_background,
        iconBackgroundResId = R.drawable.ic_ap_icon2,
        cardContainerColorResId = CARD_BACKGROUND_CONTAINER_COLOR_RES_ID,
        iconContainerColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorTertiaryContainer,
        showTrailingIcon = true,
        showChevron = true,
        iconBackgroundSizeDp = STORAGE_CARD_ICON_BACKGROUND_SIZE_DP,
        iconSizeDp = STORAGE_CARD_ICON_SIZE_DP,
        useWallpaperColor = false,
        titleTextSizeSp = DEFAULT_CARD_TITLE_TEXT_SIZE_SP,
        titleColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        summaryColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnSecondaryContainer,
        iconTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnTertiaryContainer,
        chevronTintColorResId = com.android.settingslib.widget.theme.R.color
            .settingslib_materialColorOnTertiaryContainer,
    )
}

class AboutPhoneDeviceNamePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AboutPhoneCardPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var clickListener: Runnable? = null

    override val iconResId: Int
        get() = R.drawable.ic_settings_about_device_filled

    override val cardStyle: AboutPhoneCardStyle
        get() = AboutPhoneCardStyle.DeviceName

    override fun getCardTitle(): CharSequence =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?: Build.MODEL

    override fun getCardSummary(): CharSequence =
        context.getText(R.string.my_device_info_device_name_preference_title)

    override fun onCardClick() {
        clickListener?.run()
    }

    fun setCardClickListener(listener: Runnable?) {
        clickListener = listener
    }

    companion object {
        const val KEY = "about_phone_device_name_card"
    }
}

class AboutPhoneStoragePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AboutPhoneCardPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var storageTitle: CharSequence = context.getText(R.string.memory_calculating_size)
    private var storageSummary: CharSequence = context.getText(R.string.memory_calculating_size)
    private var isLoadingStorageSummary = false

    override val iconResId: Int
        get() = R.drawable.ic_storage_filled

    override val cardStyle: AboutPhoneCardStyle
        get() = AboutPhoneCardStyle.Storage

    override fun getCardTitle(): CharSequence = storageTitle

    override fun getCardSummary(): CharSequence = storageSummary

    override fun onCardClick() {
        val args = Bundle().apply {
            putString(VolumeInfo.EXTRA_VOLUME_ID, VolumeInfo.ID_PRIVATE_INTERNAL)
        }
        SubSettingLauncher(context)
            .setDestination(StorageDashboardFragment::class.java.name)
            .setArguments(args)
            .setSourceMetricsCategory(SettingsEnums.DEVICEINFO)
            .setTitleRes(R.string.storage_settings)
            .launch()
    }

    override fun refresh() {
        super.refresh()
        loadStorageSummary()
    }

    private fun loadStorageSummary() {
        if (isLoadingStorageSummary) {
            return
        }
        isLoadingStorageSummary = true
        ThreadUtils.postOnBackgroundThread {
            val text = getStorageText()
            ThreadUtils.postOnMainThread {
                isLoadingStorageSummary = false
                storageTitle = text.title
                storageSummary = text.summary
                notifyChanged()
            }
        }
    }

    private fun getStorageText(): StorageCardText {
        return try {
            val storageManager = context.getSystemService(StorageManager::class.java)
            val storageInfo = PrivateStorageInfo.getPrivateStorageInfo(
                StorageManagerVolumeProvider(storageManager),
            )
            storageInfo.toStorageCardText()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to load storage summary", e)
            StorageCardText(
                title = context.getText(R.string.memory_calculating_size),
                summary = context.getText(R.string.memory_calculating_size),
            )
        }
    }

    private fun PrivateStorageInfo.toStorageCardText(): StorageCardText {
        val usedBytes = totalBytes - freeBytes
        val usedSize = Formatter.formatFileSize(context, usedBytes)
        val totalSize = Formatter.formatFileSize(context, totalBytes)
        return StorageCardText(
            title = context.getString(R.string.about_phone_storage_used_title, usedSize),
            summary = context.getString(R.string.about_phone_storage_total_summary, totalSize),
        )
    }

    private data class StorageCardText(
        val title: CharSequence,
        val summary: CharSequence,
    )

    companion object {
        const val KEY = "about_phone_storage_card"
    }
}
