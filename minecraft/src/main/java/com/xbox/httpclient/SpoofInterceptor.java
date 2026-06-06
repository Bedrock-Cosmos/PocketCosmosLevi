package com.xbox.httpclient;

import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

public class SpoofInterceptor implements Interceptor {

    public static final SpoofInterceptor INSTANCE = new SpoofInterceptor();
    private static final Map<String, List<SpoofRule>> ruleMap = new ConcurrentHashMap<>();
    private static AssetManager assetManager;
    private static NewsProvider newsProvider;

    public static void setAssetManager(AssetManager mgr) {
        assetManager = mgr;
    }

    public static void setNewsProvider(NewsProvider provider) {
        newsProvider = provider;
    }

    public static void addRule(String url, SpoofRule rule) {
        ruleMap.computeIfAbsent(url, k -> new CopyOnWriteArrayList<>()).add(rule);
    }

    public static void clearRules() {
        ruleMap.clear();
    }

    public interface NewsProvider {
        String appendNews(String responseBody);
        void onSessionStart(AssetManager assets);
        void onMessagesEvent(String requestBody);
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        List<SpoofRule> rules = ruleMap.get(url);
        if (rules != null && !rules.isEmpty()) {
            android.util.Log.d("mchook", "[Interceptor] matched rules for: " + url);
            PeekedRequest peeked = null;
            for (SpoofRule rule : rules) {
                if (rule.requiresRequestBody()) {
                    if (peeked == null) peeked = peekRequestBody(request);
                    if (peeked == null) break;
                    if (rule.matches(peeked.request, peeked.bodyStr))
                        return rule.handle(chain, peeked.request, peeked.bodyStr, assetManager);
                } else {
                    if (rule.matches(request, null))
                        return rule.handle(chain, request, null, assetManager);
                }
            }
            return chain.proceed(peeked != null ? peeked.request : request);
        }

        return chain.proceed(request);
    }

    // Rules

    public interface SpoofRule {
        default boolean requiresRequestBody() { return false; }
        boolean matches(Request request, String body);

        default Response handle(Chain chain, Request request, AssetManager assets) throws IOException {
            throw new UnsupportedOperationException("SpoofRule must implement at least one handle method");
        }

        /** Handle for rules that may need the request body. */
        default Response handle(Chain chain, Request request, String body, AssetManager assets) throws IOException {
            return handle(chain, request, assets);
        }
    }

    public record StaticRule(String jsonResponse) implements SpoofRule {
        @Override public boolean matches(Request r, String b) { return true; }
        @Override public Response handle(Chain c, Request r, AssetManager a) {
            return buildJsonResponse(r, jsonResponse);
        }
    }

    public static class MatchRule implements SpoofRule {
        private final String bodyPattern;
        private final String assetPath;
        private volatile String cachedAsset;

        public MatchRule(String bodyPattern, String assetPath) {
            this.bodyPattern = bodyPattern;
            this.assetPath = assetPath;
        }


        @Override public boolean requiresRequestBody() { return true; }

        @Override public boolean matches(Request r, String body) {
            return body != null && body.contains(bodyPattern);
        }

        @Override public Response handle(Chain c, Request r, AssetManager a) throws IOException {
            if (cachedAsset == null && assetPath != null) {
                synchronized (this) {
                    if (cachedAsset == null) cachedAsset = readAsset(a, assetPath);
                }
            }
            return buildJsonResponse(r, cachedAsset);
        }
    }

    public static class AppendRule implements SpoofRule {
        private final String assetPath;
        private final String preResolvedDescriptor;

        public AppendRule(String assetPathOrJson, boolean isPreResolved) {
            this.assetPath = isPreResolved ? null : assetPathOrJson;
            this.preResolvedDescriptor = isPreResolved ? assetPathOrJson : null;
        }

        @Override public boolean matches(Request r, String b) { return true; }

        @Override
        public Response handle(Chain chain, Request request, AssetManager assets) throws IOException {
            Response realResponse = chain.proceed(request);
            String bodyStr = realResponse.body().string();

            try {
                String descriptorJson = preResolvedDescriptor != null
                        ? preResolvedDescriptor
                        : readAsset(assets, assetPath);

                JSONObject descriptor = new JSONObject(descriptorJson);
                JSONObject row = descriptor.getJSONObject("row");
                String targetArray = descriptor.getString("targetArray");
                String targetField = descriptor.getString("targetField");

                JSONObject insertAfter = descriptor.optJSONObject("insertAfter");
                String afterField = insertAfter != null ? insertAfter.optString("field", "controlId") : null;
                String afterValue = insertAfter != null ? insertAfter.optString("value", "") : null;
                int occurrence = insertAfter != null ? insertAfter.optInt("occurrence", 1) : -1;

                JSONObject arrayFilter = descriptor.optJSONObject("arrayFilter");
                String filterField = arrayFilter != null ? arrayFilter.getString("field") : null;
                String filterValue = arrayFilter != null ? arrayFilter.getString("value") : null;

                JSONObject root = new JSONObject(bodyStr);
                JSONArray arr = resolvePath(root, targetArray);

                if (arr != null) {
                    if (filterField != null) {
                        // find the matching object and operate on targetField inside it
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.optJSONObject(i);
                            if (obj != null && filterValue.equals(obj.optString(filterField))) {
                                JSONArray inner = obj.optJSONArray(targetField);
                                if (inner != null) {
                                    JSONArray result = insertIntoArray(inner, row, afterField, afterValue, occurrence);
                                    obj.put(targetField, result);
                                }
                                break;
                            }
                        }
                    } else {
                        // no filter, operate directly on the array
                        JSONArray result = insertIntoArray(arr, row, afterField, afterValue, occurrence);
                        writePath(root, targetArray, result);
                    }
                    return buildJsonResponse(request, root.toString());
                } else {
                    android.util.Log.e("mchook", "[AppendRule] targetArray not found: " + targetArray);
                }
            } catch (Exception e) {
                android.util.Log.e("mchook", "[AppendRule] Error: " + android.util.Log.getStackTraceString(e));
            }
            return buildJsonResponse(request, bodyStr);
        }
    }

    public static class EntitlementRule implements SpoofRule {
        private static final String COSMOS_INVENTORY_URL =
                "https://bedrock-cosmos.app/api/v1.0/player/inventory?includeReceipt=true";

        @Override
        public Response handle(Chain chain, Request request, AssetManager assets) throws IOException {
            // Get the original inventory response
            Response realResponse = chain.proceed(request);
            String originalBody = realResponse.body().string();

            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                RequestBody cosmosBody = RequestBody.create(
                        originalBody.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        MediaType.get("application/json; charset=utf-8")
                );
                Request cosmosRequest = new Request.Builder()
                        .url(COSMOS_INVENTORY_URL)
                        .post(cosmosBody)
                        .build();

                try (Response cosmosResponse = client.newCall(cosmosRequest).execute()) {
                    if (cosmosResponse.isSuccessful()) {
                        String cosmosBody2 = cosmosResponse.body().string();
                        return buildJsonResponse(request, cosmosBody2);
                    } else {
                        android.util.Log.w("mchook", "[EntitlementRule] request failed: " + cosmosResponse.code());
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("mchook", "[EntitlementyRule] Error forwarding: "
                        + android.util.Log.getStackTraceString(e));
            }

            // Fallback return the original response
            return buildJsonResponse(request, originalBody);
        }

        @Override
        public boolean matches(Request r, String b) { return true; }
    }


    public static class SessionStartNewsRule implements SpoofRule {
        @Override public boolean matches(Request r, String b) { return true; }

        @Override
        public Response handle(Chain chain, Request request, String body, AssetManager assets) throws IOException {
            Response realResponse = chain.proceed(request);

            if (newsProvider == null)
                return realResponse;

            String responseBody = realResponse.body().string();

            try {
                newsProvider.onSessionStart(assets);
                responseBody = newsProvider.appendNews(responseBody);
            } catch (Exception e) {
                android.util.Log.e("mchook", "[SessionStartNewsRule] Error: "
                        + android.util.Log.getStackTraceString(e));
            }

            return buildJsonResponse(request, responseBody);
        }
    }

    public static class MessagesEventNewsRule implements SpoofRule {
        @Override public boolean requiresRequestBody() { return true; }
        @Override public boolean matches(Request r, String b) { return true; }

        @Override
        public Response handle(Chain chain, Request request, String body, AssetManager assets) throws IOException {
            if (newsProvider != null && body != null) {
                try {
                    newsProvider.onMessagesEvent(body);
                } catch (Exception e) {
                    android.util.Log.e("mchook", "[MessagesEventNewsRule] Error: "
                            + android.util.Log.getStackTraceString(e));
                }
            }
            return chain.proceed(request);
        }
    }

    public static class PersonaMenuAppendRule implements SpoofRule {
        private final String dropdownAppendPath;
        private final String featuredAppendPath;

        public PersonaMenuAppendRule(String dropdownAppendPath, String featuredAppendPath) {
            this.dropdownAppendPath = dropdownAppendPath;
            this.featuredAppendPath = featuredAppendPath;
        }

        @Override public boolean matches(Request r, String b) { return true; }

        @Override
        public Response handle(Chain chain, Request request, AssetManager assets) throws IOException {
            Response realResponse = chain.proceed(request);
            String bodyStr = realResponse.body().string();

            try {
                String dropdownRaw = readAsset(assets, dropdownAppendPath);
                String featuredRaw = readAsset(assets, featuredAppendPath);

                JSONArray appendArray = new JSONObject(dropdownRaw).optJSONArray("rows");
                JSONArray featuredItemsArray = new JSONObject(featuredRaw).optJSONArray("rows");

                JSONObject root = new JSONObject(bodyStr);
                JSONObject result = root.optJSONObject("result");

                if (result != null) {
                    JSONArray layout = result.optJSONArray("layout");
                    if (layout != null) {
                        JSONObject targetSection = null;

                        // Locate "rows"
                        for (int i = 0; i < layout.length(); i++) {
                            JSONObject section = layout.optJSONObject(i);
                            if (section != null && "rows".equals(section.optString("sectionName"))) {
                                targetSection = section;
                                break;
                            }
                        }

                        // Fallback to layout[0]
                        if (targetSection == null && layout.length() > 0) {
                            targetSection = layout.optJSONObject(0);
                        }

                        if (targetSection != null) {
                            JSONArray targetArray = targetSection.optJSONArray("rows");
                            if (targetArray != null) {

                                // Insert at index 1
                                if (featuredItemsArray != null) {
                                    for (int i = 0; i < featuredItemsArray.length(); i++) {
                                        targetArray = shiftInsert(targetArray, 1, featuredItemsArray.optJSONObject(i));
                                    }
                                }

                                // Find insertion for LightDropdown
                                int insertIndex = -1;
                                for (int i = 0; i < targetArray.length(); i++) {
                                    JSONObject rowObj = targetArray.optJSONObject(i);
                                    if (rowObj != null && "LightDropdown".equals(rowObj.optString("controlId"))) {
                                        insertIndex = i;
                                        break;
                                    }
                                }

                                // Insert main dropdown items sequentially
                                if (appendArray != null) {
                                    if (insertIndex != -1) {
                                        for (int i = 0; i < appendArray.length(); i++) {
                                            targetArray = shiftInsert(targetArray, insertIndex, appendArray.optJSONObject(i));
                                            insertIndex++; // Increment to preserve sequential file order
                                        }
                                    } else {
                                        // Fallback
                                        for (int i = 0; i < appendArray.length(); i++) {
                                            targetArray.put(appendArray.optJSONObject(i));
                                        }
                                    }
                                }

                                // Re-save mutated structure back into section root
                                targetSection.put("rows", targetArray);
                            }
                        }
                    }
                }

                return buildJsonResponse(request, root.toString());

            } catch (Exception e) {
                android.util.Log.e("mchook", "[PersonaMenuAppendRule] Error mutating arrays: " + android.util.Log.getStackTraceString(e));
            }

            return buildJsonResponse(request, bodyStr);
        }

        private JSONArray shiftInsert(JSONArray source, int index, JSONObject value) throws JSONException {
            JSONArray result = new JSONArray();
            int len = source.length();
            if (index < 0) index = 0;
            if (index > len) index = len;

            for (int i = 0; i < len; i++) {
                if (i == index) {
                    result.put(value);
                }
                result.put(source.get(i));
            }
            if (index == len) {
                result.put(value);
            }
            return result;
        }
    }

    public static class DressingRoomPersonaProfileRule implements SpoofRule {
        private final String personaAppendPath;
        private final String skinsAppendPath;

        public DressingRoomPersonaProfileRule(String personaAppendPath, String skinsAppendPath) {
            this.personaAppendPath = personaAppendPath;
            this.skinsAppendPath = skinsAppendPath;
        }

        @Override public boolean matches(Request r, String b) { return true; }

        @Override
        public Response handle(Chain chain, Request request, AssetManager assets) throws IOException {
            Response realResponse = chain.proceed(request);
            String bodyStr = realResponse.body().string();

            try {
                String personaRaw = readAsset(assets, personaAppendPath);
                String skinsRaw = readAsset(assets, skinsAppendPath);

                JSONArray personaItemsToAdd = new JSONObject(personaRaw).optJSONArray("items");
                JSONArray skinsItemsToAdd = new JSONObject(skinsRaw).optJSONArray("items");

                if (personaItemsToAdd == null || skinsItemsToAdd == null) {
                    android.util.Log.e("mchook", "[DressingRoomPersonaProfileRule] Could not find 'items' array in append files.");
                    return buildJsonResponse(request, bodyStr);
                }

                JSONObject root = new JSONObject(bodyStr);
                JSONObject result = root.optJSONObject("result");
                if (result == null) return buildJsonResponse(request, bodyStr);

                JSONArray layout = result.optJSONArray("layout");
                if (layout == null || layout.length() == 0) return buildJsonResponse(request, bodyStr);

                JSONObject firstLayout = layout.optJSONObject(0);
                if (firstLayout == null) return buildJsonResponse(request, bodyStr);

                JSONArray rows = firstLayout.optJSONArray("rows");
                if (rows == null) {
                    android.util.Log.e("mchook", "[DressingRoomPersonaProfileRule] Could not find array at path: 'result.layout[0].rows'");
                    return buildJsonResponse(request, bodyStr);
                }

                for (int i = 0; i < rows.length(); i++) {
                    JSONObject rowObj = rows.optJSONObject(i);
                    if (rowObj == null || !"StoreRow".equals(rowObj.optString("controlId"))) {
                        continue;
                    }

                    JSONArray components = rowObj.optJSONArray("components");
                    if (components == null) continue;

                    int dropdownId = -1;
                    JSONObject itemListComp = null;

                    // Locate component profiles inside the row
                    for (int j = 0; j < components.length(); j++) {
                        JSONObject comp = components.optJSONObject(j);
                        if (comp == null) continue;

                        String type = comp.optString("type");
                        if ("dropdownOptionComp".equals(type)) {
                            dropdownId = comp.optInt("dropdownId", -1);
                        } else if ("itemListComp".equals(type)) {
                            itemListComp = comp;
                        }
                    }

                    if (itemListComp == null) continue;

                    JSONArray existingItems = itemListComp.optJSONArray("items");
                    if (existingItems == null) continue;

                    // Choose matching items array depending on the row identity
                    JSONArray itemsToAdd;
                    if (dropdownId == 0) {
                        itemsToAdd = personaItemsToAdd;
                    } else if (dropdownId == 1) {
                        itemsToAdd = skinsItemsToAdd;
                    } else {
                        continue;
                    }

                    // Prepend items sequentially
                    JSONArray updatedItems = new JSONArray();
                    for (int k = 0; k < itemsToAdd.length(); k++) {
                        updatedItems.put(itemsToAdd.get(k));
                    }
                    for (int k = 0; k < existingItems.length(); k++) {
                        updatedItems.put(existingItems.get(k));
                    }

                    // Save back into current component
                    int newTotal = updatedItems.length();
                    itemListComp.put("items", updatedItems);
                    itemListComp.put("totalItems", newTotal);

                    JSONObject config = itemListComp.optJSONObject("customStoreRowConfiguration");
                    if (config != null) {
                        config.put("maxOffers", newTotal);
                    }
                }

                return buildJsonResponse(request, root.toString());

            } catch (Exception e) {
                android.util.Log.e("mchook", "[DressingRoomPersonaProfileRule] Error structural parsing: "
                        + android.util.Log.getStackTraceString(e));
            }

            return buildJsonResponse(request, bodyStr);
        }
    }

    // Static Utilities

    private static String readAsset(AssetManager mgr, String path) throws IOException {
        try (BufferedSource source = Okio.buffer(Okio.source(mgr.open(path)))) {
            return source.readUtf8();
        }
    }

    public static Response buildJsonResponse(Request request, String json) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "application/json; charset=utf-8")
                .body(ResponseBody.create(MediaType.get("application/json; charset=utf-8"), json))
                .build();
    }

    private record PeekedRequest(Request request, String bodyStr) {
    }

    private PeekedRequest peekRequestBody(Request request) {
        try {
            RequestBody rb = request.body();
            if (rb == null) return new PeekedRequest(request, "");
            Buffer buffer = new Buffer();
            rb.writeTo(buffer);
            String bodyStr = buffer.clone().readUtf8();
            RequestBody rebuiltBody = RequestBody.create(buffer.readByteArray(), rb.contentType());
            Request rebuiltRequest = request.newBuilder()
                    .method(request.method(), rebuiltBody)
                    .build();
            return new PeekedRequest(rebuiltRequest, bodyStr);
        } catch (Exception e) {
            android.util.Log.e("mchook", "[peekRequestBody] failed: " + android.util.Log.getStackTraceString(e));
            return null;
        }
    }

    // JSON Path Helpers

    private static JSONArray resolvePath(JSONObject root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (current instanceof JSONObject obj) {
                current = obj.opt(part);
            } else return null;

            if (current instanceof JSONArray arr && i == parts.length - 1) return arr;
        }
        return null;
    }

    private static void writePath(JSONObject root, String path, JSONArray newArray) throws JSONException {
        String[] parts = path.split("\\.");
        Object current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (current instanceof JSONObject obj) current = obj.opt(parts[i]);
        }
        if (current instanceof JSONObject obj) obj.put(parts[parts.length - 1], newArray);
    }

    private static JSONArray insertIntoArray(JSONArray source, JSONObject row, String field, String val, int occ) throws JSONException {
        JSONArray out = new JSONArray();
        if (field == null) {
            for (int i = 0; i < source.length(); i++) out.put(source.get(i));
            out.put(row);
            return out;
        }
        int seen = 0;
        boolean done = false;
        for (int i = 0; i < source.length(); i++) {
            out.put(source.get(i));
            if (!done && source.optJSONObject(i) != null && val.equals(source.optJSONObject(i).optString(field))) {
                if (++seen == occ) {
                    out.put(row);
                    done = true;
                }
            }
        }
        return done ? out : source;
    }
}