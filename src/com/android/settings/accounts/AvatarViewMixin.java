/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.accounts;

import android.accounts.Account;
import android.app.settings.SettingsEnums;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.settings.R;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.homepage.SettingsHomepageActivity;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.utils.ThreadUtils;

import java.util.List;

/**
 * Avatar related work to the onStart method of registered observable classes
 * in {@link SettingsHomepageActivity}.
 */
public class AvatarViewMixin implements LifecycleObserver {
    private static final String TAG = "AvatarViewMixin";

    @VisibleForTesting
    static final Intent INTENT_GET_ACCOUNT_DATA =
            new Intent("android.content.action.SETTINGS_ACCOUNT_DATA");

    private static final String METHOD_GET_ACCOUNT_AVATAR = "getAccountAvatar";
    private static final String KEY_AVATAR_BITMAP = "account_avatar";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_AVATAR_ICON = "avatar_icon";
    private static final String ACTION_ADD_ACCOUNT_SETTINGS =
            "android.settings.ADD_ACCOUNT_SETTINGS";
    private static final String GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms";
    private static final String GOOGLE_SETTINGS_ACTIVITY =
            "com.google.android.gms.googlesettings.ui.GoogleSettingsActivity";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private final Context mContext;
    private final SettingsHomepageActivity mActivity;
    private final ImageView mAvatarView;
    private final int mIconPaddingLeft;
    private final int mIconPaddingTop;
    private final int mIconPaddingRight;
    private final int mIconPaddingBottom;
    private final ImageView.ScaleType mIconScaleType;
    private volatile int mAvatarLoadId;

    @VisibleForTesting
    String mAccountName;

    /**
     * @return true if the avatar icon is supported.
     */
    public static boolean isAvatarSupported(Context context) {
        if (!context.getResources().getBoolean(R.bool.config_show_avatar_in_homepage)) {
            Log.d(TAG, "Feature disabled by config. Skipping");
            return false;
        }
        return true;
    }

    public AvatarViewMixin(SettingsHomepageActivity activity, ImageView avatarView) {
        mContext = activity.getApplicationContext();
        mActivity = activity;
        mAvatarView = avatarView;
        mIconPaddingLeft = avatarView.getPaddingLeft();
        mIconPaddingTop = avatarView.getPaddingTop();
        mIconPaddingRight = avatarView.getPaddingRight();
        mIconPaddingBottom = avatarView.getPaddingBottom();
        mIconScaleType = avatarView.getScaleType();

        View.OnClickListener clickListener = v -> launchAccountPage();
        mAvatarView.setOnClickListener(clickListener);
        if (mAvatarView.getParent() instanceof View) {
            ((View) mAvatarView.getParent()).setOnClickListener(clickListener);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        final int avatarLoadId = ++mAvatarLoadId;
        if (hasAccount()) {
            loadAccount(avatarLoadId);
        } else {
            mAccountName = null;
            showGoogleIcon();
        }
    }

    private void showGoogleIcon() {
        mAvatarView.setImageDrawable(null);
        mAvatarView.setPadding(mIconPaddingLeft, mIconPaddingTop, mIconPaddingRight,
                mIconPaddingBottom);
        mAvatarView.setScaleType(mIconScaleType);
        mAvatarView.setImageResource(R.drawable.ic_homepage_google);
    }

    private void prepareAvatarLoadingState() {
        mAvatarView.setImageDrawable(null);
        mAvatarView.setPadding(0, 0, 0, 0);
        mAvatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    private void showAvatarIfCurrent(int avatarLoadId, Bitmap bitmap) {
        if (avatarLoadId != mAvatarLoadId || bitmap == null) {
            return;
        }
        mAvatarView.setImageDrawable(null);
        mAvatarView.setPadding(0, 0, 0, 0);
        mAvatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mAvatarView.setImageBitmap(bitmap);
    }

    private void showGoogleIconIfCurrent(int avatarLoadId) {
        if (avatarLoadId == mAvatarLoadId) {
            showGoogleIcon();
        }
    }

    @VisibleForTesting
    boolean hasAccount() {
        final Account[] accounts = FeatureFactory.getFeatureFactory().getAccountFeatureProvider()
                .getAccounts(mContext);
        return (accounts != null) && (accounts.length > 0);
    }

    private void loadAccount(int avatarLoadId) {
        prepareAvatarLoadingState();

        final String authority = queryProviderAuthority();
        if (TextUtils.isEmpty(authority)) {
            showGoogleIconIfCurrent(avatarLoadId);
            return;
        }

        ThreadUtils.postOnBackgroundThread(() -> {
            final Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(authority)
                    .build();
            final Bundle bundle = mContext.getContentResolver().call(uri,
                    METHOD_GET_ACCOUNT_AVATAR, null /* arg */, null /* extras */);
            if (bundle == null) {
                ThreadUtils.postOnMainThread(() -> showGoogleIconIfCurrent(avatarLoadId));
                return;
            }

            final Bitmap bitmap = bundle.getParcelable(KEY_AVATAR_BITMAP);
            mAccountName = bundle.getString(KEY_ACCOUNT_NAME, "" /* defaultValue */);
            if (bitmap == null) {
                ThreadUtils.postOnMainThread(() -> showGoogleIconIfCurrent(avatarLoadId));
                return;
            }
            ThreadUtils.postOnMainThread(() -> showAvatarIfCurrent(avatarLoadId, bitmap));
        });
    }

    private void launchAccountPage() {
        FeatureFactory.getFeatureFactory().getMetricsFeatureProvider()
                .logSettingsTileClick(KEY_AVATAR_ICON, SettingsEnums.SETTINGS_HOMEPAGE);

        if (launchGoogleSettings()) {
            return;
        }

        launchFallbackAccountPage();
    }

    private boolean launchGoogleSettings() {
        Intent intent = new Intent().setClassName(GOOGLE_PLAY_SERVICES_PACKAGE,
                GOOGLE_SETTINGS_ACTIVITY);
        try {
            mActivity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Cannot launch Google settings activity.", e);
            return false;
        }
    }

    private void launchFallbackAccountPage() {
        if (hasAccount()) {
            ActivityEmbeddingRulesController.registerSubSettingsPairRule(mActivity,
                    true /* clearTop */);
            new SubSettingLauncher(mActivity)
                    .setDestination(AccountDashboardFragment.class.getName())
                    .setSourceMetricsCategory(SettingsEnums.SETTINGS_HOMEPAGE)
                    .setTitleRes(R.string.account_settings_title)
                    .setIsSecondLayerPage(true)
                    .launch();
            return;
        }

        Intent addAccountIntent = new Intent(ACTION_ADD_ACCOUNT_SETTINGS)
                .putExtra(AccountPreferenceBase.ACCOUNT_TYPES_FILTER_KEY,
                        new String[] {GOOGLE_ACCOUNT_TYPE});
        mActivity.startActivity(addAccountIntent);
    }

    @VisibleForTesting
    String queryProviderAuthority() {
        final List<ResolveInfo> providers =
                mContext.getPackageManager().queryIntentContentProviders(INTENT_GET_ACCOUNT_DATA,
                        PackageManager.MATCH_SYSTEM_ONLY);
        if (providers.size() == 1) {
            return providers.get(0).providerInfo.authority;
        } else {
            Log.w(TAG, "The size of the provider is " + providers.size());
            return null;
        }
    }
}
