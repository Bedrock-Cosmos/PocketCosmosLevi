package org.levimc.launcher.core.mods.inbuilt.nativemod;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.xbox.httpclient.SpoofInterceptor;

import org.levimc.launcher.core.mods.inbuilt.cosmos.CosmosSpoofs;
import org.levimc.launcher.core.mods.inbuilt.cosmos.NewsManager;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

import java.io.File;


public class HttpInterceptorMod {

    public static boolean init(Context context) {
        AssetManager mgr = context.getAssets();
        SpoofInterceptor.setAssetManager(mgr);
        SpoofInterceptor.setFilesDir(context.getApplicationContext().getFilesDir());
        InbuiltModManager manager = InbuiltModManager.getInstance(context);

        SpoofInterceptor.clearRules();

        if (manager.isCosmosEnabled()) {
            try {
                File customJsonsDir = new File(context.getApplicationContext().getFilesDir(), "customJsons");
                File miscDir = new File(context.getApplicationContext().getFilesDir(), "misc");

                NewsManager.init(customJsonsDir, miscDir);
                new CosmosSpoofs(mgr, customJsonsDir).register(manager.isNewsEnabled());
            } catch (Exception e) {
                Log.e("HttpInterceptorMod", "Cosmos spoofs failed", e);
            }
        }

        return true;
    }
}