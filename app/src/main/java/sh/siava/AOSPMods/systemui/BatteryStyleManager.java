package sh.siava.AOSPMods.systemui;

import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.nfx.android.rangebarpreference.RangeBarHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import sh.siava.AOSPMods.Utils.batteryStyles.BatteryBarView;
import sh.siava.AOSPMods.Utils.batteryStyles.BatteryDrawable;
import sh.siava.AOSPMods.Utils.batteryStyles.CircleBatteryDrawable;
import sh.siava.AOSPMods.Utils.batteryStyles.CircleFilledBatteryDrawable;
import sh.siava.AOSPMods.Utils.batteryStyles.hiddenBatteryDrawable;
import sh.siava.AOSPMods.XPrefs;
import sh.siava.AOSPMods.XposedModPack;


//TODO: unknown battery symbol / percent text beside icon / update shape upon request / other shapes / dual tone

public class BatteryStyleManager extends XposedModPack {
    public static final String listenPackage = "com.android.systemui";
    
    public static boolean customBatteryEnabled = false;
    
    private int frameColor;
    public static int BatteryStyle = 1;
    public static boolean ShowPercent = false;
    public static int scaleFactor = 100;
    public static boolean scaleWithPercent = false;
    private static boolean isFastCharging = false;
    private static int BatteryIconOpacity = 100;
    private static float[] batteryLevels = new float[]{20f, 40f};
    private static final ArrayList<Object> batteryViews = new ArrayList<>();
    
    public BatteryStyleManager(Context context) { super(context); }
    
    public static void setIsFastCharging(boolean isFastCharging)
    {
        BatteryStyleManager.isFastCharging = isFastCharging;
        for(Object view : batteryViews)
        {
            try {
                BatteryDrawable drawable = (BatteryDrawable) XposedHelpers.getAdditionalInstanceField(view, "mBatteryDrawable");
                drawable.setFastCharging(isFastCharging);
            }catch(Throwable ignored){} //it's probably default battery. no action needed
        }
    }
    
    public void updatePrefs(String...Key)
    {
        if(XPrefs.Xprefs == null) return;
        String BatteryStyleStr = XPrefs.Xprefs.getString("BatteryStyle", "0");
        scaleFactor = XPrefs.Xprefs.getInt("BatteryIconScaleFactor", 50)*2;
        int batteryStyle = Integer.parseInt(BatteryStyleStr);
        customBatteryEnabled = batteryStyle != 0;
        if(batteryStyle == 99)
        {
            scaleFactor = 0;
        }
    
        if(BatteryStyle != batteryStyle)
        {
            if(Key.length > 0 && batteryStyle == 0)
            {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
            BatteryStyle = batteryStyle;
            for(Object view : batteryViews) //distroy old drawables and make new ones :D
            {
                BatteryDrawable newDrawable = getNewDrawable(mContext);
                ImageView mBatteryIconView = (ImageView) XposedHelpers.getObjectField(view, "mBatteryIconView");
                mBatteryIconView.setImageDrawable(newDrawable);
                XposedHelpers.setAdditionalInstanceField(view,"mBatteryDrawable", newDrawable);
    
                boolean mCharging = (boolean) XposedHelpers.getObjectField(view, "mCharging");
                int mLevel = (int) XposedHelpers.getObjectField(view, "mLevel");
                newDrawable.setBatteryLevel(mLevel);
                newDrawable.setCharging(mCharging);
            }
        }
        
        ShowPercent = XPrefs.Xprefs.getBoolean("BatteryShowPercent", false);
        
        BatteryIconOpacity = XPrefs.Xprefs.getInt("BIconOpacity", 100);
        boolean BIconTransitColors = XPrefs.Xprefs.getBoolean("BIconTransitColors", false);
        boolean BIconColorful = XPrefs.Xprefs.getBoolean("BIconColorful", false);
        boolean BIconindicateFastCharging = XPrefs.Xprefs.getBoolean("BIconindicateFastCharging", false);
        int batteryIconFastChargingColor = XPrefs.Xprefs.getInt("batteryIconFastChargingColor", Color.BLUE);
        int batteryChargingColor = XPrefs.Xprefs.getInt("batteryIconChargingColor", Color.GREEN);
        boolean BIconindicateCharging = XPrefs.Xprefs.getBoolean("BIconindicateCharging", false);
    
        String jsonString = XPrefs.Xprefs.getString("BIconbatteryWarningRange", "");
        if(jsonString.length() > 0)
        {
            batteryLevels = new float[]{
                    RangeBarHelper.getLowValueFromJsonString(jsonString),
                    RangeBarHelper.getHighValueFromJsonString(jsonString)};
        }
    
        int[] batteryColors = new int[]{
                XPrefs.Xprefs.getInt("BIconbatteryCriticalColor", Color.RED),
                XPrefs.Xprefs.getInt("BIconbatteryWarningColor", Color.YELLOW)};
    
        BatteryDrawable.setStaticColor(batteryLevels, batteryColors, BIconindicateCharging, batteryChargingColor, BIconindicateFastCharging, batteryIconFastChargingColor, BIconTransitColors, BIconColorful);
        
        refreshBatteryIcons();
    }
    
    private static void refreshBatteryIcons() {
        for (Object view : batteryViews) {
            ImageView mBatteryIconView = (ImageView) XposedHelpers.getObjectField(view, "mBatteryIconView");
            scale(mBatteryIconView);
            try {
                BatteryDrawable drawable = (BatteryDrawable) XposedHelpers.getAdditionalInstanceField(view, "mBatteryDrawable");
                drawable.setShowPercent(ShowPercent);
                drawable.setAlpha(Math.round(BatteryIconOpacity*2.55f));
                drawable.refresh();
            }catch(Throwable ignored){} //it's probably the default battery. no action needed
        }
    }
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if(!lpparam.packageName.equals(listenPackage)) return;

        
        XposedHelpers.findAndHookConstructor("com.android.settingslib.graph.ThemedBatteryDrawable", lpparam.classLoader, Context.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                frameColor = (int) param.args[1];
            }
        });

        Class<?> BatteryMeterViewClass = XposedHelpers.findClassIfExists("com.android.systemui.battery.BatteryIconOpacity", lpparam.classLoader);

        if(BatteryMeterViewClass == null)
        {
            BatteryMeterViewClass = XposedHelpers.findClass("com.android.systemui.battery.BatteryMeterView", lpparam.classLoader);
        }

        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                batteryViews.add(v);
            }
    
            @Override
            public void onViewDetachedFromWindow(View v) {
                batteryViews.remove(v);
            }
        };
        
        XposedHelpers.findAndHookConstructor(BatteryMeterViewClass,
                    Context.class, AttributeSet.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            ImageView mBatteryIconView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mBatteryIconView");
                            ((View)param.thisObject).addOnAttachStateChangeListener(listener);

                            if (!customBatteryEnabled) return;
                            
                            BatteryDrawable mBatteryDrawable = getNewDrawable(mContext);
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "mBatteryDrawable", mBatteryDrawable);

                            mBatteryIconView.setImageDrawable(mBatteryDrawable);
                            XposedHelpers.setObjectField(param.thisObject, "mBatteryIconView", mBatteryIconView);
                        }
                    });


        XposedHelpers.findAndHookMethod(BatteryMeterViewClass,
                "onBatteryUnknownStateChanged", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if(!customBatteryEnabled) return;

                        ImageView mBatteryIconView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mBatteryIconView");
                        BatteryDrawable mBatteryDrawable = (BatteryDrawable) getAdditionalInstanceField(param.thisObject, "mBatteryDrawable");

                        mBatteryDrawable.setMeterStyle(BatteryStyle);

                        mBatteryIconView.setImageDrawable(mBatteryDrawable);
                    }
                });

        Method scaleBatteryMeterViewsMethod = XposedHelpers.findMethodExactIfExists(BatteryMeterViewClass, "scaleBatteryMeterViews");

        if(scaleBatteryMeterViewsMethod != null)
        {
            XposedBridge.hookMethod(scaleBatteryMeterViewsMethod, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            scale(param);
                        }
                    });

            XposedHelpers.findAndHookMethod(BatteryMeterViewClass,
                    "onPowerSaveChanged", boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if(!customBatteryEnabled) return;

                            BatteryDrawable mBatteryDrawable = (BatteryDrawable) getAdditionalInstanceField(param.thisObject, "mBatteryDrawable");
                            mBatteryDrawable.setPowerSaveEnabled((boolean) param.args[0]);
                        }
                    });
        }
        else
        {
            scaleWithPercent = true;
        }

        XposedHelpers.findAndHookMethod(BatteryMeterViewClass,
                "updateColors", int.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if(!customBatteryEnabled) return;

                        BatteryDrawable mBatteryDrawable = (BatteryDrawable) getAdditionalInstanceField(param.thisObject, "mBatteryDrawable");
                        if(mBatteryDrawable == null) return;
                        mBatteryDrawable.setColors((int) param.args[0], (int) param.args[1], (int) param.args[2]);
                    }
                });
    }
    
    private BatteryDrawable getNewDrawable(Context context) {
        BatteryDrawable mBatteryDrawable = null;
        switch (BatteryStyle)
        {
            case 1:
            case 2:
                mBatteryDrawable = new CircleBatteryDrawable(context, frameColor);
                break;
            case 3:
                mBatteryDrawable = new CircleFilledBatteryDrawable(context, frameColor);
                break;
            case 99:
                mBatteryDrawable = new hiddenBatteryDrawable();
        }
        if(mBatteryDrawable != null) {
            mBatteryDrawable.setShowPercent(ShowPercent);
            mBatteryDrawable.setMeterStyle(BatteryStyle);
            mBatteryDrawable.setFastCharging(isFastCharging);
            mBatteryDrawable.setAlpha(Math.round(BatteryIconOpacity * 2.55f));
        }
        return mBatteryDrawable;
    }
    
    static class batteryUpdater extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            boolean mCharging = (boolean) XposedHelpers.getObjectField(param.thisObject, "mCharging");
            int mLevel = (int) XposedHelpers.getObjectField(param.thisObject, "mLevel");

            //Feeding battery bar
            BatteryBarView.setStaticLevel(mLevel, mCharging);

            if (!customBatteryEnabled) return;

            BatteryDrawable mBatteryDrawable = (BatteryDrawable) getAdditionalInstanceField(param.thisObject, "mBatteryDrawable");
            if (mBatteryDrawable == null) return;
            
            mBatteryDrawable.setCharging(mCharging);
            mBatteryDrawable.setBatteryLevel(mLevel);
            
            if(scaleWithPercent) scale(param);
        }
    }
    public static void scale(XC_MethodHook.MethodHookParam param)
    {
        ImageView mBatteryIconView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mBatteryIconView");
        scale(mBatteryIconView);
        param.setResult(null);
    }

    public static void scale(ImageView mBatteryIconView)
    {
        if (mBatteryIconView == null) {
            return;
        }

        Context context = mBatteryIconView.getContext();
        Resources res = context.getResources();

        TypedValue typedValue = new TypedValue();

        res.getValue(res.getIdentifier("status_bar_icon_scale_factor", "dimen", context.getPackageName()), typedValue, true);
        float iconScaleFactor = typedValue.getFloat() * (scaleFactor/100f);

        int batteryHeight = res.getDimensionPixelSize(res.getIdentifier("status_bar_battery_icon_height", "dimen", context.getPackageName()));
        int batteryWidth = res.getDimensionPixelSize(res.getIdentifier("status_bar_battery_icon_height", "dimen", context.getPackageName()));
        int marginBottom = res.getDimensionPixelSize(res.getIdentifier("battery_margin_bottom", "dimen", context.getPackageName()));

        LinearLayout.LayoutParams scaledLayoutParams = new LinearLayout.LayoutParams(
                (int) (batteryWidth * iconScaleFactor), (int) (batteryHeight * iconScaleFactor));

        scaledLayoutParams.setMargins(0, 0, 0, marginBottom);

        mBatteryIconView.setLayoutParams(scaledLayoutParams);
    }

    @Override
    public boolean listensTo(String packageName) { return listenPackage.equals(packageName); }

}
