package org.levimc.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

public class PlayStoreValidator {
    private static final String MINECRAFT_PACKAGE_NAME = "com.mojang.minecraftpe";
    private static final String PLAY_STORE_INSTALLER = "com.android.vending";

    public static boolean isMinecraftFromPlayStore(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            try {
                packageManager.getPackageInfo(MINECRAFT_PACKAGE_NAME, 0);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }

            String installerPackageName;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    installerPackageName = packageManager.getInstallSourceInfo(MINECRAFT_PACKAGE_NAME)
                            .getInstallingPackageName();
                } catch (PackageManager.NameNotFoundException e) {
                    return false;
                }
            } else {
                installerPackageName = packageManager.getInstallerPackageName(MINECRAFT_PACKAGE_NAME);
            }

            return PLAY_STORE_INSTALLER.equals(installerPackageName);
        } catch (Exception e) {
            return false;
        }
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
        return isMinecraftFromPlayStore(context);
    }

}