package coin3x.xposed.sysuitweaks.hooks;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressWarnings("rawtypes, unchecked")
public class QuickStatusBarHeaderBatteryHook {
    private QuickStatusBarHeaderBatteryHook() {
    }

    // WeakHashMap<QuickStatusBarHeader, WeakReference<BatteryMeterView>>
    WeakHashMap<Object, WeakReference<Object>> BatteryMeterViewHolder = new WeakHashMap<>();

    Class CActivityStarter;
    Method postStartActivityDismissingKeyguardM;

    Class CQuickStatusBarHeader;
    Field mActivityStarterF;
    Method getContextM;

    Class CBatteryMeterView;
    Constructor CtorBatteryMeterView;
    Field textAppearanceF;
    Field mIgnoreTunerUpdatesF;
    Method setPercentModeM;
    Method updateColorsM;
    Method unsubscribeFromTunerUpdatesM;

    public QuickStatusBarHeaderBatteryHook(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        CActivityStarter = XposedHelpers.findClass("com.android.systemui.plugins.ActivityStarter", lpparam.classLoader);
        postStartActivityDismissingKeyguardM = CActivityStarter.getDeclaredMethod("postStartActivityDismissingKeyguard", Intent.class, int.class);

        CQuickStatusBarHeader = XposedHelpers.findClass("com.android.systemui.qs.QuickStatusBarHeader", lpparam.classLoader);
        mActivityStarterF = CQuickStatusBarHeader.getDeclaredField("mActivityStarter");
        mActivityStarterF.setAccessible(true);
        getContextM = CQuickStatusBarHeader.getMethod("getContext");

        CBatteryMeterView = XposedHelpers.findClass("com.android.systemui.BatteryMeterView", lpparam.classLoader);
        CtorBatteryMeterView = CBatteryMeterView.getDeclaredConstructor(Context.class, AttributeSet.class, int.class);
        textAppearanceF = CBatteryMeterView.getDeclaredField("mPercentageStyleId");
        textAppearanceF.setAccessible(true);
        mIgnoreTunerUpdatesF = CBatteryMeterView.getDeclaredField("mIgnoreTunerUpdates");
        mIgnoreTunerUpdatesF.setAccessible(true);
        setPercentModeM = CBatteryMeterView.getDeclaredMethod("setPercentShowMode", int.class);
        updateColorsM = CBatteryMeterView.getDeclaredMethod("updateColors", int.class, int.class, int.class);
        updateColorsM.setAccessible(true);
        unsubscribeFromTunerUpdatesM = CBatteryMeterView.getDeclaredMethod("unsubscribeFromTunerUpdates");
        unsubscribeFromTunerUpdatesM.setAccessible(true);

        // Add a battery indicator at the clock row
        XposedHelpers.findAndHookMethod("com.android.systemui.qs.QuickStatusBarHeader", lpparam.classLoader, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                RelativeLayout header = (RelativeLayout) param.thisObject;
                Context ctx = (Context) getContextM.invoke(header);
                int parentId = ctx.getResources().getIdentifier("quick_status_bar_system_icons", "id", "com.android.systemui");
                LinearLayout batteryMeter = createBatteryMeter(ctx);

                Object starter = mActivityStarterF.get(header);
                batteryMeter.setOnClickListener((View v) -> {
                    try {
                        postStartActivityDismissingKeyguardM.invoke(starter, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY), 0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                LinearLayout parent = header.findViewById(parentId);
                if (parent.getChildCount() != 3) {
                    throw new IllegalStateException("unexpected child count: " + parent.getChildCount());
                }
                View container = parent.getChildAt(2);
                if (!(container instanceof LinearLayout)) {
                    throw new IllegalStateException("unexpected child type: " + container.getClass().getName());
                }
                ((LinearLayout) container).addView(batteryMeter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                BatteryMeterViewHolder.put(header, new WeakReference<>(batteryMeter));

                // Hide the original battery indicator
                int origBatteryId = ctx.getResources().getIdentifier("batteryRemainingIcon", "id", "com.android.systemui");
                View origBattery = header.findViewById(origBatteryId);
                mIgnoreTunerUpdatesF.set(origBattery, true);
                unsubscribeFromTunerUpdatesM.invoke(origBattery);
                origBattery.setVisibility(View.GONE);

                // Remove excess padding
                int statusIconsId = ctx.getResources().getIdentifier("statusIcons", "id", "com.android.systemui");
                LinearLayout statusIcons = header.findViewById(statusIconsId);
                statusIcons.setPadding(0, 0, 0, 0);
            }
        });

        // Override color received from context (background is always black)
        XposedHelpers.findAndHookMethod("com.android.systemui.qs.QuickStatusBarHeader", lpparam.classLoader, "setupHost", "com.android.systemui.qs.QSTileHost", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                updateColorsM.invoke(BatteryMeterViewHolder.get(param.thisObject).get(), Color.WHITE, 0xFFD4D4D4, Color.WHITE);
            }
        });
    }

    private LinearLayout createBatteryMeter(Context ctx) throws Throwable {
        LinearLayout instance = (LinearLayout) CtorBatteryMeterView.newInstance(ctx, null, 0);
        int clockTextAppearance = ctx.getResources().getIdentifier("TextAppearance.StatusBar.Clock", "style", "com.android.systemui");
        textAppearanceF.set(instance, clockTextAppearance);
        instance.setPadding(24, 0, 0, 0);
        setPercentModeM.invoke(instance, 3 /* MODE_ESTIMATE */);
        instance.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return instance;
    }
}
