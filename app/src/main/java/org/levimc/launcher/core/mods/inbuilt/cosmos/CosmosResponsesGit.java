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
import java.util.concurrent.TimeUnit;
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
    private static final String KEY_TAG_NAME = "cosmos_tag_name";
    private static final String MAIN_RESPONSES_RELATIVE_PATH = "LauncherJsons/MainResponses.json";

    private final Activity activity;
    private final Context context;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private LoadingDialog loadingDialog;

    public CosmosResponsesGit(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    private void showProgress(String message) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
                loadingDialog = org.levimc.launcher.util.DialogUtils.ensure(activity, loadingDialog);
                org.levimc.launcher.util.DialogUtils.showWithMessage(loadingDialog, message);
            } catch (Exception e) {
                Log.w(TAG, "Could not show loading dialog: " + e.getMessage());
            }
        });
    }

    private void hideProgress() {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                org.levimc.launcher.util.DialogUtils.dismissQuietly(loadingDialog);
            } catch (Exception ignored) {}
        });
    }

    private void invokeCallback(Runnable callback) {
        if (callback != null && activity != null) {
            activity.runOnUiThread(callback);
        }
    }

    public String getLocalEtag() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ETAG, "");
    }

    public String getChangelog() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CHANGELOG, "");
    }

    public String getTagName() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TAG_NAME, "");
    }

    private void saveLocalData(String etag, String changelog, String tagName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ETAG, etag)
                .putString(KEY_CHANGELOG, changelog)
                .putString(KEY_TAG_NAME, tagName != null ? tagName : "")
                .apply();
    }

    public boolean areLocalResponsesValid() {
        File cosmosDir = new File(context.getFilesDir(), "cosmos");
        File mainResponses = new File(cosmosDir, MAIN_RESPONSES_RELATIVE_PATH);
        return cosmosDir.exists() && cosmosDir.isDirectory() && mainResponses.exists() && mainResponses.length() > 0;
    }

    public void checkUpdateOnLaunch() {
        checkUpdate(false, null);
    }

    public void forceUpdate(Runnable onComplete) {
        checkUpdate(true, onComplete);
    }

    public void checkUpdate(boolean force, Runnable onComplete) {
        CosmosSessionTracker.trackSessionStartAsync();
        String localEtag = getLocalEtag();
        boolean hasValidLocalFiles = areLocalResponsesValid();

        if (force || localEtag == null || localEtag.isEmpty() || !hasValidLocalFiles) {
            Log.d(TAG, "Forcing full release fetch (force=" + force + ", hasValidFiles=" + hasValidLocalFiles + ")");
            fetchLatestReleaseBody(onComplete);
        } else {
            Log.d(TAG, "Local ETag found (" + localEtag + ") and local files exist, checking update via HEAD request.");
            checkEtagWithHeadRequest(localEtag, onComplete);
        }
    }

    private void checkEtagWithHeadRequest(String localEtag, Runnable onComplete) {
        Request request = new Request.Builder()
                .url(GITHUB_RELEASE_API)
                .head()
                .header("If-None-Match", localEtag)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "HEAD request failed: " + e.getMessage());
                invokeCallback(onComplete);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (response.code() == 304) {
                        if (!areLocalResponsesValid()) {
                            Log.w(TAG, "Server returned 304 but local responses files are missing/invalid. Fetching full release.");
                            fetchLatestReleaseBody(onComplete);
                            return;
                        }
                        Log.d(TAG, "Responses are already up-to-date (HTTP 304 Not Modified)");
                        invokeCallback(onComplete);
                        return;
                    }
                    if (response.isSuccessful()) {
                        String serverEtag = response.header("ETag");
                        if (isEtagMatching(serverEtag, localEtag) && areLocalResponsesValid()) {
                            Log.d(TAG, "Responses are already up-to-date (ETag matched: " + serverEtag + ")");
                            invokeCallback(onComplete);
                            return;
                        }
                        Log.d(TAG, "ETag mismatch or missing local files (local: " + localEtag + ", server: " + serverEtag + "). Fetching latest release info.");
                        fetchLatestReleaseBody(onComplete);
                    } else {
                        Log.w(TAG, "HEAD request returned code: " + response.code() + ". Falling back to GET.");
                        fetchLatestReleaseBody(onComplete);
                    }
                }
            }
        });
    }

    private boolean isEtagMatching(String serverEtag, String localEtag) {
        if (serverEtag == null || localEtag == null) return false;
        return normalizeEtag(serverEtag).equals(normalizeEtag(localEtag));
    }

    private String normalizeEtag(String etag) {
        if (etag == null) return "";
        etag = etag.trim();
        if (etag.startsWith("W/") || etag.startsWith("w/")) {
            etag = etag.substring(2);
        }
        if (etag.startsWith("\"") && etag.endsWith("\"") && etag.length() >= 2) {
            etag = etag.substring(1, etag.length() - 1);
        }
        return etag;
    }

    private void fetchLatestReleaseBody(Runnable onComplete) {
        Request request = new Request.Builder()
                .url(GITHUB_RELEASE_API)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "GET request failed: " + e.getMessage());
                invokeCallback(onComplete);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "GET request failed with code: " + response.code());
                        invokeCallback(onComplete);
                        return;
                    }
                    String bodyStr = response.body().string();
                    JSONObject json = new JSONObject(bodyStr);
                    String serverEtag = response.header("ETag");
                    String zipballUrl = json.getString("zipball_url");
                    String changelog = json.optString("body", "");
                    String tagName = json.optString("tag_name", "");

                    Log.d(TAG, "Fetched release info (" + tagName + "). Downloading zipball: " + zipballUrl);
                    downloadAndExtractZipball(zipballUrl, serverEtag, changelog, tagName, onComplete);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse release info: " + e.getMessage());
                    invokeCallback(onComplete);
                }
            }
        });
    }

    private void downloadAndExtractZipball(String url, String serverEtag, String changelog, String tagName, Runnable onComplete) {
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
                invokeCallback(onComplete);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Zipball download failed with code: " + response.code());
                        hideProgress();
                        invokeCallback(onComplete);
                        return;
                    }
                    File cacheDir = context.getCacheDir();
                    File tempZip = new File(cacheDir, "cosmos_temp.zip");
                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(tempZip)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }

                    showProgress("Extracting Cosmos responses...");
                    File stagingDir = new File(cacheDir, "cosmos_staging");
                    deleteDirectory(stagingDir);
                    stagingDir.mkdirs();

                    extractZip(tempZip, stagingDir);
                    tempZip.delete();

                    File stagedMain = new File(stagingDir, MAIN_RESPONSES_RELATIVE_PATH);
                    if (!stagedMain.exists() || stagedMain.length() == 0) {
                        deleteDirectory(stagingDir);
                        throw new IOException("Extracted responses validation failed: " + stagedMain.getAbsolutePath() + " not found or empty.");
                    }

                    File cosmosDir = new File(context.getFilesDir(), "cosmos");
                    deleteDirectory(cosmosDir);
                    if (!stagingDir.renameTo(cosmosDir)) {
                        cosmosDir.mkdirs();
                        copyDirectory(stagingDir, cosmosDir);
                        deleteDirectory(stagingDir);
                    }

                    saveLocalData(serverEtag != null ? serverEtag : "", changelog, tagName);
                    Log.d(TAG, "Cosmos responses successfully updated (" + tagName + ") and extracted to: " + cosmosDir.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to extract downloaded zip: " + e.getMessage());
                } finally {
                    hideProgress();
                    invokeCallback(onComplete);
                }
            }
        });
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..")) {
                    zis.closeEntry();
                    continue;
                }
                String strippedPath = stripFirstSegment(name);
                if (strippedPath.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                File file = new File(destDir, strippedPath);
                if (entry.isDirectory() || name.endsWith("/")) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
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

    private void copyDirectory(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectory(new File(src, child), new File(dst, child));
                }
            }
        } else {
            try (InputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }
}

