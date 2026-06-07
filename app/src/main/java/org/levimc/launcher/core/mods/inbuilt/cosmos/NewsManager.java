package org.levimc.launcher.core.mods.inbuilt.cosmos;

import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NewsManager {

    private static final String TAG = "NewsManager";
    private static final String CURRENT_NEWS_ASSET = "cosmos/MainPages/CurrentNews_append.json";
    private static final String NEWS_ICON_URL = "https://bedrock-cosmos.app/icons/NewsIcon.png";

    private static String currentNewsUuid = "00000000-0000-4000-0000-000000000000";
    private static boolean sendNewsToInbox = true;
    private static boolean sendNewsToAnnouncement = true;

    private static JSONObject newsHistoryObj = null;
    private static JSONObject currentNewsObj = null;
    private static List<String> seenUuids = null;


    private static File newsHistoryFile = null;
    private static File newsHistoryUuidsFile = null;
    private static File filesDir = null;

    public static void init(File customJsonsDir, File miscDir) {
        newsHistoryFile = new File(customJsonsDir, "News.json");
        newsHistoryUuidsFile = new File(miscDir, "NewsHistory.json");
        filesDir = customJsonsDir != null ? customJsonsDir.getParentFile() : null;
    }

    public static boolean isSendToNewsInbox()        { return sendNewsToInbox; }
    public static boolean isSendToNewsAnnouncement() { return sendNewsToAnnouncement; }

    public static File getNewsHistoryFile() {
        return newsHistoryFile;
    }

    public static void createNewsHistoryFile() {
        try {
            newsHistoryObj = new JSONObject();
            newsHistoryObj.put("category", "BedrockCosmosNews");
            newsHistoryObj.put("totalNumberOfUnreadMessages", 0);
            newsHistoryObj.put("totalNumberOfMessages", 0);
            newsHistoryObj.put("messages", new JSONArray());

            JSONObject image = new JSONObject();
            image.put("id", "8642b05e-b0e1-4057-94d8-98552e53a23a");
            image.put("url", NEWS_ICON_URL);

            JSONObject categoryInfo = new JSONObject();
            categoryInfo.put("name", "Bedrock Cosmos News");
            categoryInfo.put("image", image);
            categoryInfo.put("type", "BedrockCosmosNews");

            newsHistoryObj.put("categoryInfo", categoryInfo);

            writeJson(newsHistoryFile, newsHistoryObj);
        } catch (JSONException e) {
            Log.e(TAG, "createNewsHistoryFile failed", e);
        }
    }

    public static void retrieveNewsHistory() {
        if (newsHistoryFile == null) {
            Log.w(TAG, "newsHistoryFile not set — call init().");
            return;
        }

        if (!newsHistoryFile.getParentFile().exists())
            newsHistoryFile.getParentFile().mkdirs();

        if (!newsHistoryFile.exists()) {
            createNewsHistoryFile();
            return;
        }

        try {
            String raw = readFile(newsHistoryFile);
            newsHistoryObj = new JSONObject(raw);

            // Re-update icon URL if stale
            JSONObject image = newsHistoryObj
                    .optJSONObject("categoryInfo") != null
                    ? newsHistoryObj.getJSONObject("categoryInfo").optJSONObject("image")
                    : null;

            if (image != null && !NEWS_ICON_URL.equals(image.optString("url"))) {
                image.put("url", NEWS_ICON_URL);
                writeJson(newsHistoryFile, newsHistoryObj);
            }

        } catch (Exception e) {
            Log.e(TAG, "retrieveNewsHistory failed", e);
        }
    }

    public static void retrieveCurrentNews(AssetManager assets) {
        sendNewsToInbox = true;
        sendNewsToAnnouncement = true;

        String raw;
        try {
            raw = readAsset(assets);
        } catch (IOException e) {
            Log.i(TAG, "No current news asset found.");
            return;
        }

        try {
            currentNewsObj = new JSONObject(raw);

            if (currentNewsObj.has("cosmosSendToInbox"))
                sendNewsToInbox = currentNewsObj.getBoolean("cosmosSendToInbox");

            if (currentNewsObj.has("cosmosSendToAnnouncement"))
                sendNewsToAnnouncement = currentNewsObj.getBoolean("cosmosSendToAnnouncement");

            String uuid = currentNewsObj.optString("id", "");
            if (!uuid.isEmpty()) currentNewsUuid = uuid;

            // Stamp current UTC time
            currentNewsObj.put("dateReceived", utcNow());

            Log.i(TAG, "Current news retrieved. ID: " + currentNewsUuid);

        } catch (JSONException e) {
            Log.e(TAG, "retrieveCurrentNews: failed to parse asset", e);
        }
    }

    public static boolean isCurrentNewsNew() {
        ensureSeenUuidsLoaded();
        return !seenUuids.contains(currentNewsUuid);
    }

    public static void markCurrentNewsAsSeen() {
        ensureSeenUuidsLoaded();

        if (seenUuids.contains(currentNewsUuid)) {
            Log.d(TAG, "UUID already recorded in news history, skipping.");
            return;
        }

        seenUuids.add(currentNewsUuid);
        saveSeenUuids();
        Log.i(TAG, "UUID " + currentNewsUuid + " added to news history.");
    }

    public static void queueLoginAnnouncementIfNew(File customJsonsDir) {
        if (!isCurrentNewsNew()) {
            Log.d(TAG, "No new news to queue.");
            return;
        }

        if (currentNewsObj == null) {
            Log.w(TAG, "Current news data is empty; cannot queue announcement.");
            return;
        }

        try {
            JSONObject announcement = new JSONObject(currentNewsObj.toString());
            announcement.put("surface", "LoginAnnouncement");

            if ("ContentListNoCTA".equals(announcement.optString("template")))
                announcement.put("template", "HeroImageCTA");

            writeJson(new File(customJsonsDir, "CurrentLoginAnnouncement.json"), announcement);
            Log.i(TAG, "LoginAnnouncement queued for ID: " + announcement.optString("id"));

        } catch (JSONException e) {
            Log.e(TAG, "queueLoginAnnouncementIfNew failed", e);
        }
    }

    public static void addNewsToHistory() {
        if (currentNewsObj == null) {
            Log.w(TAG, "No news data loaded. Call retrieveCurrentNews() first.");
            return;
        }

        if (newsHistoryObj == null) createNewsHistoryFile();

        try {
            JSONArray messages = newsHistoryObj.optJSONArray("messages");
            if (messages == null) messages = new JSONArray();

            // Deduplicate
            for (int i = 0; i < messages.length(); i++) {
                JSONObject existing = messages.optJSONObject(i);
                if (existing != null && currentNewsUuid.equals(existing.optString("id"))) {
                    Log.d(TAG, "News item already in history. Skipping.");
                    return;
                }
            }

            // Prepend
            JSONArray updated = new JSONArray();
            updated.put(new JSONObject(currentNewsObj.toString()));
            for (int i = 0; i < messages.length(); i++) updated.put(messages.get(i));

            newsHistoryObj.put("messages", updated);
            newsHistoryObj.put("totalNumberOfMessages", updated.length());
            updateUnreadCount(updated);
            writeJson(newsHistoryFile, newsHistoryObj);

            Log.i(TAG, "News item " + currentNewsUuid + " added to history. Total: " + updated.length());

        } catch (JSONException e) {
            Log.e(TAG, "addNewsToHistory failed", e);
        }
    }

    public static void interpretNewsEvent(String eventJson) {
        if (eventJson == null || eventJson.isEmpty()) {
            Log.w(TAG, "interpretNewsEvent: empty JSON.");
            return;
        }

        try {
            JSONObject eventObj = new JSONObject(eventJson);
            JSONArray events = eventObj.optJSONArray("events");
            if (events == null || events.length() == 0) {
                Log.d(TAG, "interpretNewsEvent: no events in payload.");
                return;
            }

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;

                String eventType = ev.optString("eventType");
                String instanceId = ev.optString("instanceId");

                Log.d(TAG, "interpretNewsEvent: processing '" + eventType + "'.");

                switch (eventType) {
                    case "Impression":
                        if (!instanceId.isEmpty()) onNewsImpression(instanceId);
                        break;
                    case "Delete":
                        if (!instanceId.isEmpty()) onNewsDelete(instanceId);
                        break;
                    case "ReadAll":
                        onNewsReadAll();
                        break;
                    case "DeleteAllRead":
                        onNewsDeleteAllRead();
                        break;
                    default:
                        Log.d(TAG, "interpretNewsEvent: unknown type '" + eventType + "'.");
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, "interpretNewsEvent: parse error", e);
        }
    }

//    public static void resetNewsVariables() {
//        currentNewsUuid = "00000000-0000-4000-0000-000000000000";
//        sendNewsToInbox = true;
//        sendNewsToAnnouncement = true;
//        newsHistoryObj = null;
//        currentNewsObj = null;
//        seenUuids = null;
//    }

    // Event handlers

    private static void onNewsImpression(String instanceId) {
        JSONArray messages = getMessagesOrWarn("onNewsImpression");
        if (messages == null) return;

        JSONObject target = findByInstanceId(messages, instanceId);
        if (target == null) { Log.w(TAG, "onNewsImpression: no message for id " + instanceId); return; }

        if ("Read".equalsIgnoreCase(target.optString("status"))) {
            Log.d(TAG, "onNewsImpression: already read, skipping.");
            return;
        }

        try {
            target.put("status", "Read");
            updateUnreadCount(messages);
            writeJson(newsHistoryFile, newsHistoryObj);
        } catch (JSONException e) {
            Log.e(TAG, "onNewsImpression failed", e);
        }
    }

    private static void onNewsDelete(String instanceId) {
        JSONArray messages = getMessagesOrWarn("onNewsDelete");
        if (messages == null) return;

        int indexToRemove = -1;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg != null && instanceId.equalsIgnoreCase(msg.optString("instanceId"))) {
                indexToRemove = i;
                break;
            }
        }

        if (indexToRemove < 0) { Log.w(TAG, "onNewsDelete: message not found."); return; }

        messages.remove(indexToRemove);

        try {
            newsHistoryObj.put("totalNumberOfMessages", messages.length());
            updateUnreadCount(messages);
            writeJson(newsHistoryFile, newsHistoryObj);
        } catch (JSONException e) {
            Log.e(TAG, "onNewsDelete failed", e);
        }
    }

    private static void onNewsReadAll() {
        JSONArray messages = getMessagesOrWarn("onNewsReadAll");
        if (messages == null) return;

        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.optJSONObject(i);
                if (msg != null) msg.put("status", "Read");
            }
            newsHistoryObj.put("totalNumberOfUnreadMessages", 0);
            writeJson(newsHistoryFile, newsHistoryObj);
            Log.i(TAG, "onNewsReadAll: marked " + messages.length() + " message(s) as read.");
        } catch (JSONException e) {
            Log.e(TAG, "onNewsReadAll failed", e);
        }
    }

    private static void onNewsDeleteAllRead() {
        JSONArray messages = getMessagesOrWarn("onNewsDeleteAllRead");
        if (messages == null) return;

        try {
            JSONArray remaining = new JSONArray();
            int deleted = 0;

            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.optJSONObject(i);
                if ("Read".equalsIgnoreCase(msg != null ? msg.optString("status") : ""))
                    deleted++;
                else if (msg != null)
                    remaining.put(new JSONObject(msg.toString()));
            }

            if (deleted == 0) { Log.d(TAG, "onNewsDeleteAllRead: nothing to delete."); return; }

            newsHistoryObj.put("messages", remaining);
            newsHistoryObj.put("totalNumberOfMessages", remaining.length());
            updateUnreadCount(remaining);
            writeJson(newsHistoryFile, newsHistoryObj);
            Log.i(TAG, "onNewsDeleteAllRead: deleted " + deleted + ", remaining " + remaining.length());

        } catch (JSONException e) {
            Log.e(TAG, "onNewsDeleteAllRead failed", e);
        }
    }

    // Helpers
    private static JSONArray getMessagesOrWarn(String caller) {
        if (newsHistoryObj == null) { Log.w(TAG, caller + ": history not loaded."); return null; }
        JSONArray messages = newsHistoryObj.optJSONArray("messages");
        if (messages == null || messages.length() == 0) { Log.d(TAG, caller + ": no messages."); return null; }
        return messages;
    }

    private static JSONObject findByInstanceId(JSONArray messages, String instanceId) {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg != null && instanceId.equalsIgnoreCase(msg.optString("instanceId")))
                return msg;
        }
        return null;
    }

    private static void updateUnreadCount(JSONArray messages) throws JSONException {
        int unread = 0;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg != null && "Unread".equalsIgnoreCase(msg.optString("status"))) unread++;
        }
        newsHistoryObj.put("totalNumberOfUnreadMessages", unread);
    }

    private static void ensureSeenUuidsLoaded() {
        if (seenUuids != null) return;

        if (newsHistoryUuidsFile == null) { seenUuids = new ArrayList<>(); return; }

        File parent = newsHistoryUuidsFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        if (!newsHistoryUuidsFile.exists()) {
            writeRaw(newsHistoryUuidsFile, "[]");
            seenUuids = new ArrayList<>();
            return;
        }

        try {
            JSONArray arr = new JSONArray(readFile(newsHistoryUuidsFile));
            seenUuids = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) seenUuids.add(arr.getString(i));
        } catch (Exception e) {
            Log.e(TAG, "ensureSeenUuidsLoaded failed", e);
            seenUuids = new ArrayList<>();
        }
    }

    private static void saveSeenUuids() {
        JSONArray arr = new JSONArray();
        for (String uuid : seenUuids) arr.put(uuid);
        writeRaw(newsHistoryUuidsFile, arr.toString());
    }

    private static void writeJson(File file, JSONObject obj) {
        try {
            writeRaw(file, obj.toString(2));
        } catch (JSONException e) {
            writeRaw(file, obj.toString());
        }
    }

    private static void writeRaw(File file, String content) {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        } catch (IOException e) {
            Log.e(TAG, "writeRaw failed: " + file.getPath(), e);
        }
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String readAsset(AssetManager mgr) throws IOException {
        if (filesDir != null) {
            File file = new File(filesDir, NewsManager.CURRENT_NEWS_ASSET);
            if (file.exists() && file.isFile()) {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }
        }
        try (InputStream is = mgr.open(NewsManager.CURRENT_NEWS_ASSET);
             DataInputStream dis = new DataInputStream(is)) {
            byte[] buf = new byte[is.available()];
            dis.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static String utcNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'0000Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}