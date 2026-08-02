package org.levimc.launcher.core.mods.inbuilt.cosmos;

import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CosmosSessionTracker {
    private static final String TAG = "CosmosSessionTracker";
    private static final String SESSION_START_URL = "https://bedrockcosmos.app/api/v1.0/session/start";
    private static final OkHttpClient client = new OkHttpClient();

    public static void trackSessionStartAsync() {
        Request request = new Request.Builder()
                .url(SESSION_START_URL)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
            }
        });
    }
}
