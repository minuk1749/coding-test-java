package dev.minuk.algosync;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class NotionClient {
    record Response(int status, JsonObject body, String retryAfter) {}
    @FunctionalInterface interface Transport { Response call(String method, String path, JsonObject body) throws IOException, InterruptedException; }
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private final Transport transport;
    private final Sleeper sleeper;

    public NotionClient(String token) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.transport = (method, path, body) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://api.notion.com/v1/" + path))
                .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + token)
                .header("Notion-Version", "2025-09-03").header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
            HttpResponse<String> reply = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonObject parsed;
            try { parsed = JsonParser.parseString(reply.body()).getAsJsonObject(); }
            catch (RuntimeException e) { throw new IOException("Notion returned a non-JSON response (HTTP " + reply.statusCode() + ")."); }
            return new Response(reply.statusCode(), parsed, reply.headers().firstValue("Retry-After").orElse(""));
        };
        this.sleeper = Thread::sleep;
    }
    NotionClient(Transport transport, Sleeper sleeper) { this.transport = transport; this.sleeper = sleeper; }

    public List<JsonObject> query(String source, JsonObject filter) throws IOException, InterruptedException {
        List<JsonObject> result = new ArrayList<>();
        String cursor = null;
        Set<String> cursors = new HashSet<>();
        do {
            JsonObject body = new JsonObject(); body.addProperty("page_size", 100);
            if (filter != null) body.add("filter", filter);
            if (cursor != null) body.addProperty("start_cursor", cursor);
            JsonObject page = request("POST", "data_sources/" + source + "/query", body, true);
            if (page.has("request_status") && "incomplete".equals(string(page.getAsJsonObject("request_status"), "type")))
                throw new IOException("Notion query was incomplete; refusing writes based on a partial index.");
            for (JsonElement row : page.getAsJsonArray("results")) result.add(row.getAsJsonObject());
            cursor = page.get("has_more").getAsBoolean() ? string(page, "next_cursor") : null;
            if (cursor != null && (cursor.isBlank() || !cursors.add(cursor))) throw new IOException("Invalid pagination cursor.");
        } while (cursor != null);
        return result;
    }
    public JsonObject createUnique(String source, String property, String key, JsonObject payload) throws IOException, InterruptedException {
        // Never blindly repeat a create: an error response may follow a successful write.
        try { return request("POST", "pages", payload, false); }
        catch (UncertainWrite failure) {
            JsonObject filter = new JsonObject(); filter.addProperty("property", property);
            JsonObject equals = new JsonObject(); equals.addProperty("equals", key); filter.add("rich_text", equals);
            for (int i = 0; i < 3; i++) {
                sleeper.sleep(1000L << i);
                List<JsonObject> matches = query(source, filter);
                if (matches.size() == 1) return matches.getFirst();
                if (matches.size() > 1) throw new IOException("Duplicate key already exists. Resolve duplicates before retrying.");
            }
            throw new IOException("Write outcome remains uncertain. Check Notion, then rerun; no duplicate create was sent.", failure);
        }
    }
    private JsonObject request(String method, String path, JsonObject body, boolean readOnly) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            Response response;
            try { response = transport.call(method, path, body); }
            catch (IOException e) {
                if (!readOnly) throw new UncertainWrite(e);
                if (attempt == 4) throw e;
                sleeper.sleep(1000L << attempt); continue;
            }
            if (response.status >= 200 && response.status < 300) return response.body;
            if (!readOnly && response.status >= 500) throw new UncertainWrite(new IOException("HTTP " + response.status));
            if ((response.status == 429 || readOnly && response.status >= 500) && attempt < 4) {
                long wait = 1000L << attempt;
                try { if (!response.retryAfter.isBlank()) wait = Math.max(0, Long.parseLong(response.retryAfter)) * 1000L; }
                catch (NumberFormatException ignored) { /* bounded exponential fallback */ }
                // If the server needs a long delay, stop instead of retrying earlier than requested.
                if (wait > 60_000) throw new IOException("Notion asks for a longer cooldown; rerun after Retry-After.");
                sleeper.sleep(wait); continue;
            }
            throw new IOException("Notion HTTP " + response.status + " (" + string(response.body, "code") + "). Check access, schema, and request limits.");
        }
        throw new IOException("Notion retry budget exhausted.");
    }
    static String string(JsonObject value, String key) { return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : ""; }
    static final class UncertainWrite extends IOException { UncertainWrite(IOException cause) { super("Creation may have succeeded.", cause); } }
}
