package coin3x.xposed.sysuitweaks.hooks;

import android.annotation.SuppressLint;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

@SuppressLint("PrivateApi")
public class TheHook implements IXposedHookLoadPackage {
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) {
            return;
        }
        new QuickStatusBarHeaderBatteryHook(lpparam);
    }
}
