package org.levimc.launcher.util;

import android.content.Context;
import android.content.pm.PackageManager;

public class PlayStoreValidator {
    private static final String MINECRAFT_PACKAGE_NAME = "com.mojang.minecraftpe";
    private static final String PLAY_STORE_INSTALLER = "com.android.vending";

    public static boolean isMinecraftFromPlayStore(Context context) {
        return isMinecraftInstalled(context);
    }

    public static boolean isMinecraftInstalled(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            packageManager.getPackageInfo(MINECRAFT_PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isLicenseVerified(Context context) {
        return isMinecraftInstalled(context);
    }
}
