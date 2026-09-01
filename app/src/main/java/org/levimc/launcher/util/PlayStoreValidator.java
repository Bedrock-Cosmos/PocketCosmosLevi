package org.levimc.launcher.util;

import android.content.Context;
import android.content.pm.PackageManager;

public class PlayStoreValidator {
    private static final String MINECRAFT_PACKAGE_NAME = "com.mojang.minecraftpe";
    private static final String PLAY_STORE_INSTALLER = "com.android.vending";

    // Trả về true luôn để BỎ QUA kiểm tra Play Store -> Không bao giờ bị hiện popup đó nữa
    public static boolean isMinecraftFromPlayStore(Context context) {
        return true;
    }

    // Vẫn giữ lại kiểm tra xem trong máy có cài Minecraft chưa
    public static boolean isMinecraftInstalled(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            packageManager.getPackageInfo(MINECRAFT_PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // Bypass xác thực bản quyền
    public static boolean isLicenseVerified(Context context) {
        return true;
    }
}
