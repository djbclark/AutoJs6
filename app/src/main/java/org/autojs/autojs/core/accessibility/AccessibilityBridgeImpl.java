package org.autojs.autojs.core.accessibility;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig;

public class AccessibilityBridgeImpl extends AccessibilityBridge {

    private final AbstractAutoJs mAutoJs;
    private final AccessibilityTool mA11yTool = new AccessibilityTool();
    private final String TAG = AccessibilityBridgeImpl.class.getSimpleName();

    public AccessibilityBridgeImpl(AbstractAutoJs autoJs) {
        super(autoJs.getApplicationContext(), new AccessibilityConfig(), autoJs.getUiHandler());
        mAutoJs = autoJs;
    }

    @Override
    public void ensureServiceStarted(boolean isForcibleRestart) {
        // Forcible restart, or sticky "enabled but not bound" (isMalfunctioning):
        // prefer stop→start rebind over a no-op when Settings already lists us.
        if (isForcibleRestart && mA11yTool.hasService()) {
            mA11yTool.stopService(false);
            Log.d(TAG, "isForcibleRestart");
        } else if (mA11yTool.isMalfunctioning()) {
            Log.w(TAG, "a11y malfunctioning (enabled in settings, not bound); ensureService will rebind");
        }
        mA11yTool.ensureService();
    }

    public void ensureServiceStarted() {
        ensureServiceStarted(false);
    }

    @Nullable
    @Override
    public AccessibilityService getService() {
        return AccessibilityService.Companion.getInstance();
    }

    @Override
    public ActivityInfoProvider getInfoProvider() {
        return mAutoJs.getInfoProvider();
    }

    @NonNull
    @Override
    public AccessibilityNotificationObserver getNotificationObserver() {
        return mAutoJs.getNotificationObserver();
    }

}
