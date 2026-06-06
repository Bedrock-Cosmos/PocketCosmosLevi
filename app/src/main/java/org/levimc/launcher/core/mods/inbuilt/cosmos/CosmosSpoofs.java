package org.levimc.launcher.core.mods.inbuilt.cosmos;

import android.content.res.AssetManager;
import android.util.Log;

import com.xbox.httpclient.SpoofInterceptor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;


public class CosmosSpoofs {

    private static final String TAG = "CosmosSpoofs";

    private static final String COSMOS_BASE = "cosmos/";
    private static final String MAIN_RESPONSES_PATH = "cosmos/LauncherJsons/MainResponses.json";
    private static final String PLAYFAB_ITEMS_PATH = "cosmos/LauncherJsons/PlayfabGetPublishItemResponses.json";
    private static final String PLAYFAB_SEARCH_ITEMS_PATH = "cosmos/LauncherJsons/PlayfabSearchResponses.json";
    private static final String PERSONA_FEATURED_APPEND_PATH = "cosmos/MainPages/PersonaCategories/FeaturedPersonaItems_append.json";
    private static final String PLAYFAB_ENDPOINT = "https://20ca2.playfabapi.com/Catalog/GetPublishedItem";
    private static final String PLAYFAB_SEARCH_ENDPOINT  = "https://20ca2.playfabapi.com/Catalog/Search";
    private static final String ENTITLEMENT_INVENTORY_ENDPOINT = "https://entitlements.mktpl.minecraft-services.net/api/v1.0/player/inventory?includeReceipt=true";
    private static final String SESSION_START_ENDPOINT  = "https://messaging.mktpl.minecraft-services.net/api/v1.0/session/start";
    private static final String MESSAGES_EVENT_ENDPOINT = "https://messaging.mktpl.minecraft-services.net/api/v1.0/messages/event";
    private static final String DRESSING_ROOM_PROFILE_ENDPOINT = "https://store.mktpl.minecraft-services.net/api/v2.0/layout/pages/DressingRoom_PersonaProfile";
    private static final String PROFILE_PERSONA_APPEND_PATH = "cosmos/MainPages/DressingRoom_PersonaProfile_Persona_append.json";
    private static final String PROFILE_SKINS_APPEND_PATH = "cosmos/MainPages/DressingRoom_PersonaProfile_Skins_append.json";
    private static final String APPEND_SUFFIX        = "_append.json";

    private final AssetManager mgr;
    private final File customJsonsDir;

    public CosmosSpoofs(AssetManager mgr, File customJsonsDir) {
        this.mgr = mgr;
        this.customJsonsDir = customJsonsDir;
    }

    public void register(boolean newsEnabled) {
        registerStaticRules();
        registerPlayfabItems();
        registerPlayfabSearch();
        registerEntitlementInventory();
        registerNewsProvider();
        if (newsEnabled) {
            registerNewsRules();
        }
        registerDressingRoomProfileRule();
    }

    private void registerDressingRoomProfileRule() {
        SpoofInterceptor.addRule(
                DRESSING_ROOM_PROFILE_ENDPOINT,
                new SpoofInterceptor.DressingRoomPersonaProfileRule(PROFILE_PERSONA_APPEND_PATH, PROFILE_SKINS_APPEND_PATH)
        );
    }

    // news
    private void registerNewsRules() {
        SpoofInterceptor.addRule(SESSION_START_ENDPOINT, new SpoofInterceptor.SessionStartNewsRule());
        SpoofInterceptor.addRule(MESSAGES_EVENT_ENDPOINT, new SpoofInterceptor.MessagesEventNewsRule());
    }

    private void registerNewsProvider() {
        SpoofInterceptor.setNewsProvider(new SpoofInterceptor.NewsProvider() {
            @Override
            public void onSessionStart(AssetManager assets) {
                NewsManager.retrieveNewsHistory();
                NewsManager.retrieveCurrentNews(assets);
                if (NewsManager.isSendToNewsAnnouncement())
                    NewsManager.queueLoginAnnouncementIfNew(customJsonsDir);
            }

            @Override
            public void onMessagesEvent(String requestBody) {
                NewsManager.interpretNewsEvent(requestBody);
            }

            @Override
            public String appendNews(String responseBody) {
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    Log.d("NewsProvider", "Skipping news append: response body is empty.");
                    return responseBody;
                }

                String trimmed = responseBody.trim();
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                    Log.d("NewsProvider", "Skipping news append: response does not look like JSON. Starts with: "
                            + truncateTo80(trimmed));
                    return responseBody;
                }

                try {
                    JSONObject root = new JSONObject(responseBody);

                    JSONObject result = root.optJSONObject("result");
                    JSONArray announcementArray = result != null
                            ? result.optJSONArray("messages")
                            : null;
                    JSONObject inboxSummary = result != null
                            ? result.optJSONObject("inboxSummary")
                            : null;
                    JSONArray inboxArray = inboxSummary != null
                            ? inboxSummary.optJSONArray("categories")
                            : null;

                    // Check if an official LoginAnnouncement is already present
                    boolean loginAnnouncementExists = false;
                    if (announcementArray != null) {
                        for (int i = 0; i < announcementArray.length(); i++) {
                            JSONObject msg = announcementArray.optJSONObject(i);
                            if (msg != null && "LoginAnnouncement".equals(msg.optString("surface"))) {
                                loginAnnouncementExists = true;
                                break;
                            }
                        }
                    }

                    if (!loginAnnouncementExists && NewsManager.isCurrentNewsNew()) {
                        // Append login announcement banner
                        if (NewsManager.isSendToNewsAnnouncement() && announcementArray != null) {
                            try {
                                String bannerRaw = readFile(new File(customJsonsDir, "CurrentLoginAnnouncement.json"));
                                announcementArray.put(new JSONObject(bannerRaw));
                            } catch (IOException e) {
                                Log.w("NewsProvider", "Could not read CurrentLoginAnnouncement.json", e);
                            }
                        }

                        if (NewsManager.isSendToNewsInbox())
                            NewsManager.addNewsToHistory();

                        NewsManager.markCurrentNewsAsSeen();

                    } else if (loginAnnouncementExists) {
                        Log.d("NewsProvider", "Skipped appending news: official Minecraft announcement is present.");
                    }

                    // Always prepend Cosmos inbox into categories
                    if (inboxArray != null) {
                        try {
                            String inboxRaw = readFile(NewsManager.getNewsHistoryFile());
                            JSONObject inboxJson = new JSONObject(inboxRaw);

                            // build new array with cosmos inbox first
                            JSONArray updatedInbox = new JSONArray();
                            updatedInbox.put(inboxJson);
                            for (int i = 0; i < inboxArray.length(); i++)
                                updatedInbox.put(inboxArray.get(i));

                            inboxSummary.put("categories", updatedInbox);
                        } catch (IOException e) {
                            Log.w("NewsProvider", "Could not read News.json for inbox append", e);
                        }
                    }

                    String updated = root.toString();
                    return updated.isEmpty() ? responseBody : updated;

                } catch (JSONException e) {
                    Log.e("NewsProvider", "Skipping news append: JSON parse error. Starts with: "
                            + truncateTo80(trimmed)
                            + " Error: " + e.getMessage());
                    return responseBody;
                }
            }

            private String truncateTo80(String text) {
                return text.substring(0, Math.min(80, text.length()));
            }

            private String readFile(File file) throws IOException {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }
        });
    }

    // Static + Append rules (MainResponses.json)
    private void registerStaticRules() {
        String raw;
        try {
            raw = readAsset(MAIN_RESPONSES_PATH);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + MAIN_RESPONSES_PATH, e);
            return;
        }

        JSONArray rules;
        try {
            rules = new JSONArray(stripComments(raw));
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse " + MAIN_RESPONSES_PATH, e);
            return;
        }

        for (int i = 0; i < rules.length(); i++) {
            try {
                JSONObject entry = rules.getJSONObject(i);
                String url      = entry.getString("url");
                String response = entry.getString("response");

                if (isSentinel(response)) {
                    Log.d(TAG, "Skipping sentinel entry for URL: " + url);
                    continue;
                }

                String normalised = response.replace("\\", "/");
                String assetPath  = COSMOS_BASE + normalised;

                if (normalised.endsWith(APPEND_SUFFIX)) {
                    registerAppendRule(url, assetPath);
                } else {
                    registerStaticRule(url, assetPath);
                }

            } catch (Exception e) {
                Log.w(TAG, "Skipping malformed entry at index " + i, e);
            }
        }
    }

    private void registerStaticRule(String url, String assetPath) {
        String json;
        try {
            json = readAsset(assetPath);
        } catch (IOException e) {
            Log.w(TAG, "Asset not found, skipping StaticRule: " + assetPath, e);
            return;
        }
        SpoofInterceptor.addRule(url, new SpoofInterceptor.StaticRule(json));
    }

    private void registerEntitlementInventory() {
        SpoofInterceptor.addRule(
                ENTITLEMENT_INVENTORY_ENDPOINT,
                new SpoofInterceptor.EntitlementRule()
        );
    }

    private void registerAppendRule(String url, String assetPath) {
        if (assetPath.contains("PersonaDropdown_append.json")) {
            Log.d(TAG, "Registering multi-file PersonaMenuAppendRule for: " + assetPath);
            SpoofInterceptor.addRule(url, new SpoofInterceptor.PersonaMenuAppendRule(assetPath, PERSONA_FEATURED_APPEND_PATH));
            return;
        }

        String rowJson;
        try {
            rowJson = readAsset(assetPath);
        } catch (IOException e) {
            Log.w(TAG, "Asset not found, skipping AppendRule: " + assetPath, e);
            return;
        }
        Log.d(TAG, "Registering AppendRule for: " + assetPath);

        try {
            JSONObject row        = new JSONObject(rowJson);
            JSONObject descriptor = assetPath.contains("MultiItemPage_PersonaSkinSelector_append")
                    ? buildPersonaSkinSelectorAppendDescriptor(row)
                    : buildAppendDescriptor(row);

            SpoofInterceptor.addRule(url, new SpoofInterceptor.AppendRule(descriptor.toString(), true));
        } catch (Exception e) {
            Log.w(TAG, "Failed to build AppendRule for: " + assetPath, e);
        }
    }

    private static JSONObject buildAppendDescriptor(JSONObject row) throws JSONException {
        JSONObject insertAfter = new JSONObject();
        insertAfter.put("field",      "controlId");
        insertAfter.put("value",      "Layout");
        insertAfter.put("occurrence", 1);

        JSONObject arrayFilter = new JSONObject();
        arrayFilter.put("field", "sectionName");
        arrayFilter.put("value", "rows");

        JSONObject descriptor = new JSONObject();
        descriptor.put("targetArray", "result.layout");
        descriptor.put("targetField", "rows");
        descriptor.put("arrayFilter", arrayFilter);
        descriptor.put("insertAfter", insertAfter);
        descriptor.put("row",         row);

        return descriptor;
    }

    private static JSONObject buildPersonaSkinSelectorAppendDescriptor(JSONObject row) throws JSONException {
        JSONObject insertAfter = new JSONObject();
        insertAfter.put("field",      "controlId");
        insertAfter.put("value",      "VerticalLineDivider");
        insertAfter.put("occurrence", 1);

        JSONObject arrayFilter = new JSONObject();
        arrayFilter.put("field", "sectionName");
        arrayFilter.put("value", "rows");

        JSONObject descriptor = new JSONObject();
        descriptor.put("targetArray", "result.layout");
        descriptor.put("targetField", "rows");
        descriptor.put("arrayFilter", arrayFilter);
        descriptor.put("insertAfter", insertAfter);
        descriptor.put("row",         row);

        return descriptor;
    }

    // Playfab MatchRules (PlayfabGetPublishItemResponses.json)

    private void registerPlayfabItems() {
        String raw;
        try {
            raw = readAsset(PLAYFAB_ITEMS_PATH);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + PLAYFAB_ITEMS_PATH, e);
            return;
        }

        JSONArray items;
        try {
            items = new JSONArray(stripComments(raw));
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse " + PLAYFAB_ITEMS_PATH, e);
            return;
        }

        int registered = 0;
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject entry = items.getJSONObject(i);
                String uuid     = entry.getString("uuid");
                String response = entry.getString("response");

                if (isSentinel(response)) {
                    Log.d(TAG, "Skipping sentinel playfab entry: " + uuid);
                    continue;
                }

                String assetPath   = COSMOS_BASE + response.replace("\\", "/");
                String bodyPattern = "\"ItemId\":\"" + uuid + "\"";

                SpoofInterceptor.addRule(
                        PLAYFAB_ENDPOINT,
                        new SpoofInterceptor.MatchRule(bodyPattern, assetPath)
                );

                registered++;

            } catch (Exception e) {
                Log.w(TAG, "Skipping malformed playfab entry at index " + i, e);
            }
        }

        Log.i(TAG, "CosmosSpoofs registered " + registered + " playfab MatchRule(s).");
    }

    private void registerPlayfabSearch() {
        String raw;
        try {
            raw = readAsset(PLAYFAB_SEARCH_ITEMS_PATH);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + PLAYFAB_SEARCH_ITEMS_PATH, e);
            return;
        }

        JSONArray items;
        try {
            items = new JSONArray(stripComments(raw));
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse " + PLAYFAB_SEARCH_ITEMS_PATH, e);
            return;
        }

        int registered = 0;
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject entry = items.getJSONObject(i);
                String uuid     = entry.getString("uuid");
                String response = entry.getString("response");

                if (isSentinel(response)) {
                    Log.d(TAG, "Skipping sentinel search entry: " + uuid);
                    continue;
                }

                String assetPath   = COSMOS_BASE + response.replace("\\", "/");
                String bodyPattern = "t eq '" + uuid + "'";

                SpoofInterceptor.addRule(
                        PLAYFAB_SEARCH_ENDPOINT,
                        new SpoofInterceptor.MatchRule(bodyPattern, assetPath)
                );

                registered++;

            } catch (Exception e) {
                Log.w(TAG, "Skipping malformed search entry at index " + i, e);
            }
        }

        Log.i(TAG, "CosmosSpoofs registered " + registered + " playfab search MatchRule(s).");
    }

    // Helpers

    // checks if its a valid asset path
    private static boolean isSentinel(String response) {
        if (response == null || response.isEmpty()) return true;
        return !response.contains(".") || response.startsWith("Processed");
    }

    // comments inside jsons?
    private static String stripComments(String raw) {
        return raw.replaceAll("(?m)^\\s*//[^\n]*", "");
    }

    private String readAsset(String path) throws IOException {
        try (InputStream is = mgr.open(path);
             DataInputStream dis = new DataInputStream(is)) {
            byte[] buf = new byte[is.available()];
            dis.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }
}