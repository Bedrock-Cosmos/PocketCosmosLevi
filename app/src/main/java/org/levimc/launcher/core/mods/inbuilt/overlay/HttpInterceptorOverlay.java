package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.levimc.launcher.core.mods.inbuilt.nativemod.HttpInterceptorMod;

public class HttpInterceptorOverlay {
    private static final String TAG = "HttpInterceptorOverlay";

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean initialized = false;

    public HttpInterceptorOverlay(Activity activity) {
        this.activity = activity;
    }

    public void show(int x, int y) {
        initializeNative();
    }

    public void hide() {
        
    }

    private void initializeNative() {
        handler.postDelayed(() -> {
            if (HttpInterceptorMod.init(activity)) {
                initialized = true;
                Log.i(TAG, "HttpInterceptor native initialized successfully");
            } else {
                Log.e(TAG, "Failed to initialize HttpInterceptor native");
            }
        }, 1000);
    }

    public boolean isInitialized() {
        return initialized;
    }
}