package org.levimc.launcher.core.mods.inbuilt.cosmos;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.levimc.launcher.ui.dialogs.LoadingDialog;

public class CosmosResponsesGit {
    private static final String TAG = "CosmosResponsesGit";
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/Bedrock-Cosmos/Responses/releases/latest";
    private static final String PREF_NAME = "cosmos_responses_prefs";
    private static final String KEY_ETAG = "cosmos_etag";
    private static final String KEY_CHANGELOG = "cosmos_changelog";

    private final Activity activity;
    private final Context context;
    private final OkHttpClient client = new OkHttpClient();
    private LoadingDialog loadingDialog;

    public CosmosResponsesGit(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    private void showProgress(String message) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            loadingDialog = org.levimc.launcher.util.DialogUtils.ensure(activity, loadingDialog);
            org.levimc.launcher.util.DialogUtils.showWithMessage(loadingDialog, message);
        });
    }

    private void hideProgress() {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            org.levimc.launcher.util.DialogUtils.dismissQuietly(loadingDialog);
        });
    }

    public String getLocalEtag() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ETAG, "");
    }

    public String getChangelog() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CHANGELOG, "");
    }

    private void saveLocalData(String etag, String changelog) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ETAG, etag)
                .putString(KEY_CHANGELOG, changelog)
                .apply();
    }

    public void checkUpdateOnLaunch() {
        String localEtag = getLocalEtag();
        if (localEtag == null || localEtag.isEmpty()) {
            Log.d(TAG, "No local ETag found, doing full release GET request.");
            fetchLatestReleaseBody();
        } else {
            Log.d(TAG, "Local ETag found (" + localEtag + "), checking update via HEAD request.");
            checkEtagWithHeadRequest(localEtag);
        }
    }

    private void checkEtagWithHeadRequest(String localEtag) {
        Request request = new Request.Builder()
                .url(GITHUB_RELEASE_API)
                .head()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "HEAD request failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        String serverEtag = response.header("ETag");
                        if (serverEtag != null && serverEtag.equals(localEtag)) {
                            Log.d(TAG, "Responses are already up-to-date (ETag matched: " + serverEtag + ")");
                            return;
                        }
                        Log.d(TAG, "ETag mismatch or missing (local: " + localEtag + ", server: " + serverEtag + "). Fetching latest release info.");
                        fetchLatestReleaseBody();
                    } else {
                        Log.w(TAG, "HEAD request returned code: " + response.code() + ". Falling back to GET.");
                        fetchLatestReleaseBody();
                    }
                }
            }
        });
    }

    private void fetchLatestReleaseBody() {
        Request request = new Request.Builder()
                .url(GITHUB_RELEASE_API)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "GET request failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "GET request failed with code: " + response.code());
                        return;
                    }
                    String bodyStr = response.body().string();
                    JSONObject json = new JSONObject(bodyStr);
                    String serverEtag = response.header("ETag");
                    String zipballUrl = json.getString("zipball_url");
                    String changelog = json.optString("body", "");

                    Log.d(TAG, "Fetched release info. Downloading zipball: " + zipballUrl);
                    downloadAndExtractZipball(zipballUrl, serverEtag, changelog);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse release info: " + e.getMessage());
                }
            }
        });
    }

    private void downloadAndExtractZipball(String url, String serverEtag, String changelog) {
        showProgress("Downloading Cosmos responses...");
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Zipball download failed: " + e.getMessage());
                hideProgress();
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Zipball download failed with code: " + response.code());
                        hideProgress();
                        return;
                    }
                    File cacheDir = context.getCacheDir();
                    File tempZip = new File(cacheDir, "cosmos_temp.zip");
                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(tempZip)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }

                    showProgress("Extracting Cosmos responses...");
                    File cosmosDir = new File(context.getFilesDir(), "cosmos");
                    deleteDirectory(cosmosDir);
                    cosmosDir.mkdirs();

                    extractZip(tempZip, cosmosDir);
                    tempZip.delete();

                    saveLocalData(serverEtag != null ? serverEtag : "", changelog);
                    Log.d(TAG, "Cosmos responses successfully updated and extracted to: " + cosmosDir.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to extract downloaded zip: " + e.getMessage());
                } finally {
                    hideProgress();
                }
            }
        });
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                String strippedPath = stripFirstSegment(name);
                if (strippedPath.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                File file = new File(destDir, strippedPath);
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private String stripFirstSegment(String path) {
        int firstSlash = path.indexOf('/');
        if (firstSlash == -1) {
            return "";
        }
        return path.substring(firstSlash + 1);
    }

    private void deleteDirectory(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteDirectory(f);
                    } else {
                        f.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
